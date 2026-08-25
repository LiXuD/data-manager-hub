#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROFILE="${1:-dev}"
NACOS_SERVER_ADDR="${NACOS_SERVER_ADDR:-}"
NACOS_NAMESPACE="${NACOS_NAMESPACE:-$PROFILE}"
NACOS_GROUP="${NACOS_GROUP:-DEFAULT_GROUP}"
NACOS_MODE="${NACOS_MODE:-apply}"
SOURCE_PROFILE="$PROFILE"
[[ "$PROFILE" == staging ]] && SOURCE_PROFILE=prod
NACOS_CONFIG_DIR="$SCRIPT_DIR/nacos-config/$SOURCE_PROFILE"
RUNTIME_DIR="$SCRIPT_DIR/.runtime"
group_state="unknown"

case "$PROFILE" in
    dev|staging|prod) ;;
    *)
        echo "错误: 只支持 dev、staging 或 prod profile" >&2
        exit 1
        ;;
esac

if [[ "$PROFILE" != dev ]]; then
    if [[ "$NACOS_MODE" == plan || "${NACOS_CONFIG_DRY_RUN:-false}" == true ]]; then
        # Plan is deliberately offline.  Use a non-routable placeholder so
        # URL construction below remains deterministic without making a
        # network request or requiring a production endpoint. Dry-run apply
        # shares the same offline boundary and only renders the Data IDs.
        NACOS_SERVER_ADDR="${NACOS_SERVER_ADDR:-nacos.invalid:8848}"
    else
        [[ -n "${NACOS_SERVER_ADDR:-}" ]] || {
            echo "错误: staging/prod 必须显式提供 NACOS_SERVER_ADDR" >&2
            exit 2
        }
        nacos_host="${NACOS_SERVER_ADDR#http://}"
        nacos_host="${nacos_host#https://}"
        case "$nacos_host" in
            localhost|localhost:*|127.0.0.1|127.0.0.1:*|0.0.0.0|0.0.0.0:*|'[::1]'|'[::1]':*)
                echo "错误: staging/prod 禁止使用 loopback NACOS_SERVER_ADDR" >&2
                exit 2
                ;;
        esac
    fi
else
    NACOS_SERVER_ADDR="${NACOS_SERVER_ADDR:-localhost:8848}"
fi

NACOS_SCHEME="${NACOS_SCHEME:-}"
case "$NACOS_SERVER_ADDR" in
    https://*)
        [[ -z "$NACOS_SCHEME" || "$NACOS_SCHEME" == https ]] || {
            echo "错误: https:// NACOS_SERVER_ADDR 不允许降级为 http" >&2
            exit 2
        }
        NACOS_SCHEME=https
        ;;
    http://*)
        [[ -z "$NACOS_SCHEME" || "$NACOS_SCHEME" == http ]] || {
            echo "错误: http:// NACOS_SERVER_ADDR 不允许伪装为 https" >&2
            exit 2
        }
        NACOS_SCHEME=http
        ;;
    *) NACOS_SCHEME="${NACOS_SCHEME:-http}" ;;
esac
case "$NACOS_SCHEME" in
    http|https) ;;
    *) echo "错误: NACOS_SCHEME 只支持 http 或 https" >&2; exit 2 ;;
esac
NACOS_BASE_URL="${NACOS_SERVER_ADDR#http://}"
NACOS_BASE_URL="${NACOS_BASE_URL#https://}"
NACOS_BASE_URL="$NACOS_SCHEME://$NACOS_BASE_URL"

case "$NACOS_MODE" in
    plan|apply|verify) ;;
    *) echo "错误: NACOS_MODE 只支持 plan、apply 或 verify" >&2; exit 1 ;;
esac

case "$PROFILE" in
    staging)
        [[ "$NACOS_GROUP" =~ ^DMH_STAGING_[0-9a-f]{40}$ ]] || {
            echo "错误: staging 必须使用不可变组 DMH_STAGING_<40-char-master-sha>" >&2
            exit 2
        }
        ;;
    prod)
        [[ "$NACOS_GROUP" =~ ^DMH_PROD_[0-9a-f]{40}$ ]] || {
            echo "错误: prod 必须使用不可变组 DMH_PROD_<40-char-master-sha>" >&2
            exit 2
        }
        ;;
esac

[[ -d "$NACOS_CONFIG_DIR" ]] || {
    echo "错误: Nacos 配置目录不存在: $NACOS_CONFIG_DIR" >&2
    exit 1
}

for command_name in curl openssl; do
    command -v "$command_name" >/dev/null 2>&1 || {
        echo "错误: 缺少命令: $command_name" >&2
        exit 1
    }
done

wait_for_nacos() {
    local attempt=1
    local max_attempts="${NACOS_STARTUP_ATTEMPTS:-60}"
    while [[ "$attempt" -le "$max_attempts" ]]; do
        if curl --noproxy '*' --silent --fail --max-time 2 \
            "$NACOS_BASE_URL/nacos/v1/console/health/readiness" >/dev/null 2>&1; then
            return 0
        fi
        sleep 1
        attempt=$((attempt + 1))
    done
    echo "错误: Nacos 未在 ${max_attempts}s 内就绪: $NACOS_BASE_URL" >&2
    return 1
}

ACCESS_TOKEN="${NACOS_ACCESS_TOKEN:-}"
login_if_required() {
    local response
    if [[ -n "$ACCESS_TOKEN" || -z "${NACOS_USERNAME:-}" ]]; then
        return 0
    fi
    response="$(curl --noproxy '*' --silent --show-error --fail \
        -X POST "$NACOS_BASE_URL/nacos/v1/auth/login" \
        --data-urlencode "username=$NACOS_USERNAME" \
        --data-urlencode "password=${NACOS_PASSWORD:-}")"
    ACCESS_TOKEN="$(printf '%s' "$response" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')"
    [[ -n "$ACCESS_TOKEN" ]] || {
        echo "错误: Nacos 登录成功但未返回 accessToken" >&2
        exit 1
    }
}

append_auth_argument() {
    if [[ -n "$ACCESS_TOKEN" ]]; then
        printf '%s' "$ACCESS_TOKEN"
    fi
}

ensure_namespace() {
    local namespaces
    local response
    local token
    token="$(append_auth_argument)"
    if [[ -n "$token" ]]; then
        namespaces="$(curl --noproxy '*' --silent --show-error --fail -G \
            "$NACOS_BASE_URL/nacos/v1/console/namespaces" \
            --data-urlencode "accessToken=$token")"
    else
        namespaces="$(curl --noproxy '*' --silent --show-error --fail \
            "$NACOS_BASE_URL/nacos/v1/console/namespaces")"
    fi
    if printf '%s' "$namespaces" | grep -Fq "\"namespace\":\"$NACOS_NAMESPACE\""; then
        return 0
    fi

    if [[ "$NACOS_MODE" == verify ]]; then
        echo "错误: Nacos namespace 不存在，verify 模式禁止创建: $NACOS_NAMESPACE" >&2
        return 2
    fi

    if [[ -n "$token" ]]; then
        response="$(curl --noproxy '*' --silent --show-error --fail -X POST \
            "$NACOS_BASE_URL/nacos/v1/console/namespaces" \
            --data-urlencode "customNamespaceId=$NACOS_NAMESPACE" \
            --data-urlencode "namespaceName=$PROFILE" \
            --data-urlencode "namespaceDesc=$PROFILE environment" \
            --data-urlencode "accessToken=$token")"
    else
        response="$(curl --noproxy '*' --silent --show-error --fail -X POST \
            "$NACOS_BASE_URL/nacos/v1/console/namespaces" \
            --data-urlencode "customNamespaceId=$NACOS_NAMESPACE" \
            --data-urlencode "namespaceName=$PROFILE" \
            --data-urlencode "namespaceDesc=$PROFILE environment")"
    fi
    [[ "$response" == "true" ]] || {
        echo "错误: 创建 Nacos namespace 失败: $response" >&2
        exit 1
    }
}

prepare_dev_secrets() {
    [[ "$PROFILE" == "dev" ]] || return 0
    mkdir -p "$RUNTIME_DIR"
    if [[ ! -f "$RUNTIME_DIR/internal-auth-private.pem" || \
          ! -f "$RUNTIME_DIR/internal-auth-public.pem" ]]; then
        openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
            -out "$RUNTIME_DIR/internal-auth-private.pem" >/dev/null 2>&1
        openssl pkey -in "$RUNTIME_DIR/internal-auth-private.pem" -pubout \
            -out "$RUNTIME_DIR/internal-auth-public.pem" >/dev/null 2>&1
        chmod 600 "$RUNTIME_DIR/internal-auth-private.pem"
    fi
    if [[ ! -s "$RUNTIME_DIR/encryption-master-key.txt" ]]; then
        openssl rand -base64 32 > "$RUNTIME_DIR/encryption-master-key.txt"
        chmod 600 "$RUNTIME_DIR/encryption-master-key.txt"
    fi
}

render_config() {
    local file="$1"
    local content
    local master_key
    content="$(<"$file")"
    if [[ "$PROFILE" == "dev" ]]; then
        master_key="$(<"$RUNTIME_DIR/encryption-master-key.txt")"
        content="${content//__PROJECT_ROOT__/$SCRIPT_DIR}"
        content="${content//__PLATFORM_ENCRYPTION_MASTER_KEY__/$master_key}"
    fi
    if [[ "$content" == *"__PROJECT_ROOT__"* || "$content" == *"__PLATFORM_ENCRYPTION_MASTER_KEY__"* ]]; then
        echo "错误: Nacos 配置包含未渲染的内部占位符: $file" >&2
        return 2
    fi
    printf '%s' "$content"
}

publish_file() {
    local file="$1"
    local data_id
    local content
    local response
    local token
    data_id="$(basename "$file")"
    if [[ "$PROFILE" == staging ]]; then
        data_id="${data_id/-prod./-staging.}"
    fi
    content="$(render_config "$file")"
    token="$(append_auth_argument)"

    local rendered_sha existing_file status_code existing_sha
    rendered_sha="$(printf '%s' "$content" | sha256sum | awk '{print $1}')"

    if [[ "${NACOS_CONFIG_DRY_RUN:-false}" == "true" || "$NACOS_MODE" == "plan" ]]; then
        echo "  - plan: $data_id sha256=$rendered_sha"
        return 0
    fi

    existing_file="$(mktemp)"
    trap 'rm -f "$existing_file"' RETURN
    if [[ -n "$token" ]]; then
        status_code="$(curl --noproxy '*' --silent --show-error --output "$existing_file" --write-out '%{http_code}' -G \
            "$NACOS_BASE_URL/nacos/v1/cs/configs" \
            --data-urlencode "dataId=$data_id" --data-urlencode "group=$NACOS_GROUP" \
            --data-urlencode "tenant=$NACOS_NAMESPACE" --data-urlencode "accessToken=$token")"
    else
        status_code="$(curl --noproxy '*' --silent --show-error --output "$existing_file" --write-out '%{http_code}' -G \
            "$NACOS_BASE_URL/nacos/v1/cs/configs" \
            --data-urlencode "dataId=$data_id" --data-urlencode "group=$NACOS_GROUP" \
            --data-urlencode "tenant=$NACOS_NAMESPACE")"
    fi
    if [[ "$status_code" == 200 ]]; then
        if [[ "$group_state" == "missing" ]]; then
            echo "错误: Nacos Group 处于部分存在状态，拒绝继续写入: $NACOS_GROUP" >&2
            return 2
        fi
        group_state="existing"
        existing_sha="$(sha256sum "$existing_file" | awk '{print $1}')"
        if [[ "$existing_sha" != "$rendered_sha" ]]; then
            echo "错误: 不可变 Nacos Group 已存在但内容不同: $data_id" >&2
            return 2
        fi
        if [[ "$NACOS_MODE" == "verify" ]]; then
            echo "  - verify: $data_id sha256=$rendered_sha"
        else
            echo "  - 幂等: $data_id sha256=$rendered_sha"
        fi
        return 0
    elif [[ "$status_code" != 404 ]]; then
        echo "错误: 读取 Nacos 配置失败: $data_id HTTP $status_code" >&2
        return 1
    fi
    if [[ "$group_state" == "existing" ]]; then
        echo "错误: Nacos Group 处于部分存在状态，拒绝补写: $NACOS_GROUP" >&2
        return 2
    fi
    group_state="missing"
    if [[ "$NACOS_MODE" == "verify" ]]; then
        echo "错误: Nacos 配置不存在: $data_id" >&2
        return 2
    fi

    if [[ -n "$token" ]]; then
        response="$(curl --noproxy '*' --silent --show-error --fail -X POST \
            "$NACOS_BASE_URL/nacos/v1/cs/configs" \
            --data-urlencode "dataId=$data_id" \
            --data-urlencode "group=$NACOS_GROUP" \
            --data-urlencode "tenant=$NACOS_NAMESPACE" \
            --data-urlencode "type=${file##*.}" \
            --data-urlencode "content=$content" \
            --data-urlencode "accessToken=$token")"
    else
        response="$(curl --noproxy '*' --silent --show-error --fail -X POST \
            "$NACOS_BASE_URL/nacos/v1/cs/configs" \
            --data-urlencode "dataId=$data_id" \
            --data-urlencode "group=$NACOS_GROUP" \
            --data-urlencode "tenant=$NACOS_NAMESPACE" \
            --data-urlencode "type=${file##*.}" \
            --data-urlencode "content=$content")"
    fi
    [[ "$response" == "true" ]] || {
        echo "错误: 发布 $data_id 失败: $response" >&2
        exit 1
    }
    echo "  - 已发布: $data_id"
}

prepare_dev_secrets
if [[ "$NACOS_MODE" != "plan" && "${NACOS_CONFIG_DRY_RUN:-false}" != true ]]; then
    wait_for_nacos
    login_if_required
    ensure_namespace
fi

echo "发布 Nacos 配置: profile=$PROFILE namespace=$NACOS_NAMESPACE group=$NACOS_GROUP"
published=0
for config_file in "$NACOS_CONFIG_DIR"/*.properties "$NACOS_CONFIG_DIR"/*.yml; do
    [[ -f "$config_file" ]] || continue
    publish_file "$config_file"
    published=$((published + 1))
done
[[ "$published" -gt 0 ]] || {
    echo "错误: 没有找到待发布的 Nacos 配置" >&2
    exit 1
}

echo "Nacos 配置发布完成，共 $published 个 Data ID"
