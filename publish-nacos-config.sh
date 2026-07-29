#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROFILE="${1:-dev}"
NACOS_SERVER_ADDR="${NACOS_SERVER_ADDR:-localhost:8848}"
NACOS_NAMESPACE="${NACOS_NAMESPACE:-$PROFILE}"
NACOS_GROUP="${NACOS_GROUP:-DEFAULT_GROUP}"
NACOS_CONFIG_DIR="$SCRIPT_DIR/nacos-config/$PROFILE"
NACOS_BASE_URL="${NACOS_SERVER_ADDR#http://}"
NACOS_BASE_URL="${NACOS_BASE_URL#https://}"
NACOS_SCHEME="${NACOS_SCHEME:-http}"
NACOS_BASE_URL="$NACOS_SCHEME://$NACOS_BASE_URL"
RUNTIME_DIR="$SCRIPT_DIR/.runtime"

case "$PROFILE" in
    dev|prod) ;;
    *)
        echo "错误: 只支持 dev 或 prod profile" >&2
        exit 1
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
    printf '%s' "$content"
}

publish_file() {
    local file="$1"
    local data_id
    local content
    local response
    local token
    data_id="$(basename "$file")"
    content="$(render_config "$file")"
    token="$(append_auth_argument)"

    if [[ "${NACOS_CONFIG_DRY_RUN:-false}" == "true" ]]; then
        echo "  - dry-run: $data_id"
        return 0
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

wait_for_nacos
login_if_required
ensure_namespace
prepare_dev_secrets

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
