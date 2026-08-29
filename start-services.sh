#!/bin/bash
# 启动所有数据平台服务
# 可选: 设置 SW_AGENT_ENABLED=true 启用 SkyWalking 链路追踪
# 示例: SW_AGENT_ENABLED=true ./start-services.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$SCRIPT_DIR/logs"
mkdir -p "$LOG_DIR"

# 开发环境使用临时 RSA 密钥；生产环境应由密钥管理系统挂载并显式传入路径。
RUNTIME_DIR="$SCRIPT_DIR/.runtime"
mkdir -p "$RUNTIME_DIR"
export INTERNAL_AUTH_PRIVATE_KEY_PATH="${INTERNAL_AUTH_PRIVATE_KEY_PATH:-$RUNTIME_DIR/internal-auth-private.pem}"
export INTERNAL_AUTH_PUBLIC_KEY_PATH="${INTERNAL_AUTH_PUBLIC_KEY_PATH:-$RUNTIME_DIR/internal-auth-public.pem}"
export INTERNAL_AUTH_TOKEN_URI="${INTERNAL_AUTH_TOKEN_URI:-http://localhost:8086/internal-auth/v1/token}"
export INTERNAL_AUTH_ENABLED="${INTERNAL_AUTH_ENABLED:-true}"
export PLATFORM_ENCRYPTION_MASTER_KEY="${PLATFORM_ENCRYPTION_MASTER_KEY:-}"

active_profile="${SPRING_PROFILES_ACTIVE:-dev}"
START_LOCAL_INFRA="${START_LOCAL_INFRA:-}"
if [ -z "$START_LOCAL_INFRA" ]; then
    if [ "$active_profile" = "dev" ]; then
        START_LOCAL_INFRA=true
    else
        START_LOCAL_INFRA=false
    fi
fi

wait_for_infra_health() {
    container="$1"
    max_attempts="${INFRA_STARTUP_ATTEMPTS:-60}"
    attempt=1
    while [ "$attempt" -le "$max_attempts" ]; do
        status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || true)"
        if [ "$status" = "healthy" ]; then
            echo "  - $container 已就绪"
            return 0
        fi
        if [ "$status" = "unhealthy" ] || [ "$status" = "exited" ] || [ "$status" = "dead" ]; then
            echo "错误: $container 状态异常: $status" >&2
            return 1
        fi
        sleep 1
        attempt=$((attempt + 1))
    done
    echo "错误: $container 未在 ${max_attempts}s 内就绪" >&2
    return 1
}

start_local_infra() {
    [ "$START_LOCAL_INFRA" = "true" ] || return 0
    command -v docker >/dev/null 2>&1 || {
        echo "错误: START_LOCAL_INFRA=true 但未找到 docker" >&2
        return 1
    }
    [ -f "$SCRIPT_DIR/docker-compose.local-infra.yml" ] || {
        echo "错误: 本地基础设施 Compose 文件不存在" >&2
        return 1
    }

    # Keep the local defaults aligned with docker-compose.local-infra.yml.
    export DMH_POSTGRES_DB="${DMH_POSTGRES_DB:-${DB_NAME:-dataplatform}}"
    export DMH_POSTGRES_USER="${DMH_POSTGRES_USER:-${DB_USERNAME:-postgres}}"
    export DMH_POSTGRES_PASSWORD="${DMH_POSTGRES_PASSWORD:-${DB_PASSWORD:-123456}}"
    export DMH_POSTGRES_PORT="${DMH_POSTGRES_PORT:-15432}"
    export DMH_REDIS_PORT="${DMH_REDIS_PORT:-6379}"
    export DMH_REDIS_PASSWORD="${DMH_REDIS_PASSWORD:-${REDIS_PASSWORD:-redis_password}}"
    export DMH_KAFKA_PORT="${DMH_KAFKA_PORT:-9092}"
    export DMH_NACOS_PORT="${DMH_NACOS_PORT:-8848}"
    export DB_HOST="${DB_HOST:-127.0.0.1}"
    export DB_PORT="${DB_PORT:-$DMH_POSTGRES_PORT}"
    export DB_USERNAME="${DB_USERNAME:-$DMH_POSTGRES_USER}"
    export DB_PASSWORD="${DB_PASSWORD:-$DMH_POSTGRES_PASSWORD}"
    export REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
    export REDIS_PORT="${REDIS_PORT:-$DMH_REDIS_PORT}"
    export REDIS_PASSWORD="${REDIS_PASSWORD:-$DMH_REDIS_PASSWORD}"
    export KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS:-127.0.0.1:$DMH_KAFKA_PORT}"
    export NACOS_SERVER_ADDR="${NACOS_SERVER_ADDR:-127.0.0.1:$DMH_NACOS_PORT}"

    echo "启动本地开发基础设施..."
    docker compose -f "$SCRIPT_DIR/docker-compose.local-infra.yml" up -d
    wait_for_infra_health dmh-local-postgres
    wait_for_infra_health dmh-local-redis
    wait_for_infra_health dmh-local-kafka
    wait_for_infra_health dmh-local-nacos
}

if [ ! -f "$INTERNAL_AUTH_PRIVATE_KEY_PATH" ] || [ ! -f "$INTERNAL_AUTH_PUBLIC_KEY_PATH" ]; then
    echo "生成开发环境内部服务认证密钥..."
    openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$INTERNAL_AUTH_PRIVATE_KEY_PATH" >/dev/null 2>&1
    openssl pkey -in "$INTERNAL_AUTH_PRIVATE_KEY_PATH" -pubout -out "$INTERNAL_AUTH_PUBLIC_KEY_PATH" >/dev/null 2>&1
    chmod 600 "$INTERNAL_AUTH_PRIVATE_KEY_PATH"
fi

if [ -z "$PLATFORM_ENCRYPTION_MASTER_KEY" ]; then
    if [ ! -s "$RUNTIME_DIR/encryption-master-key.txt" ]; then
        openssl rand -base64 32 > "$RUNTIME_DIR/encryption-master-key.txt"
        chmod 600 "$RUNTIME_DIR/encryption-master-key.txt"
    fi
    PLATFORM_ENCRYPTION_MASTER_KEY="$(<"$RUNTIME_DIR/encryption-master-key.txt")"
    export PLATFORM_ENCRYPTION_MASTER_KEY
fi

# When a database is selected explicitly (for example by an isolated E2E
# fixture), bind Spring's datasource properties to that database before
# Nacos configuration is loaded. This keeps DB_NAME from silently falling
# back to the default database name.
if [ -n "${DB_NAME:-}" ]; then
    datasource_host="${DB_HOST:-localhost}"
    datasource_port="${DB_PORT:-5432}"
    export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://${datasource_host}:${datasource_port}/${DB_NAME}}"
    if [ -n "${DB_USERNAME:-}" ]; then
        export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-$DB_USERNAME}"
    fi
    if [ -n "${DB_PASSWORD:-}" ]; then
        export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-$DB_PASSWORD}"
    fi
fi

# SkyWalking Agent 配置
SW_AGENT_ENABLED="${SW_AGENT_ENABLED:-false}"
SW_AGENT_DIR="$SCRIPT_DIR/skywalking/agent"
SW_AGENT_CONFIG="$SCRIPT_DIR/skywalking/agent.config"
SW_AGENT_JVM_ARGS=""

if [ "$SW_AGENT_ENABLED" = "true" ]; then
    if [ ! -f "$SW_AGENT_DIR/skywalking-agent.jar" ]; then
        echo "SkyWalking Agent 未安装，正在下载..."
        bash "$SCRIPT_DIR/skywalking/setup-agent.sh"
    fi
    if [ -f "$SW_AGENT_DIR/skywalking-agent.jar" ]; then
        SW_AGENT_JVM_ARGS="-javaagent:$SW_AGENT_DIR/skywalking-agent.jar -Dskywalking_config=$SW_AGENT_CONFIG"
        echo "SkyWalking Agent 已启用"
    else
        echo "警告: SkyWalking Agent 下载失败，将以无 Agent 模式启动"
    fi
fi

cd "$SCRIPT_DIR"

if ! start_local_infra; then
    echo "本地基础设施启动失败，终止服务启动" >&2
    exit 1
fi

# 应用只保留 Nacos 连接信息；启动前将版本化配置幂等发布到对应 namespace。
if [ "${NACOS_CONFIG_SYNC:-true}" = "true" ]; then
    echo "同步 $active_profile 环境 Nacos 配置..."
    if ! bash "$SCRIPT_DIR/publish-nacos-config.sh" "$active_profile"; then
        echo "Nacos 配置同步失败，终止启动"
        exit 1
    fi
else
    echo "警告: NACOS_CONFIG_SYNC=false，已显式跳过 Nacos 配置同步"
fi

if [ "${SKIP_BUILD:-false}" != "true" ]; then
    echo "构建并安装最新模块依赖..."
    if ! mvn -q -DskipTests clean install; then
        echo "构建失败，终止启动"
        exit 1
    fi
fi

# 服务配置：保持 Bash 3 兼容（macOS 默认 Bash 不支持关联数组）
service_name() {
    case "$1" in
        8081) echo "data-platform-masterdata" ;;
        8082) echo "data-platform-access" ;;
        8084) echo "data-platform-billing" ;;
        8085) echo "data-platform-governance" ;;
        8086) echo "data-platform-identity" ;;
        8888) echo "data-platform-gateway" ;;
        *) echo "" ;;
    esac
}

wait_for_identity() {
    max_attempts="${IDENTITY_STARTUP_ATTEMPTS:-60}"
    attempt=1
    while [ "$attempt" -le "$max_attempts" ]; do
        if curl --noproxy '*' --silent --fail --max-time 2 \
            http://127.0.0.1:8086/actuator/health >/dev/null 2>&1; then
            echo "  - data-platform-identity 已就绪"
            return 0
        fi
        sleep 1
        attempt=$((attempt + 1))
    done
    echo "错误: data-platform-identity 未在 ${max_attempts}s 内就绪"
    return 1
}

wait_for_service_health() {
    health_port="$1"
    health_label="$2"
    max_attempts="${SERVICE_STARTUP_ATTEMPTS:-90}"
    attempt=1
    while [ "$attempt" -le "$max_attempts" ]; do
        if curl --noproxy '*' --silent --fail --max-time 2 \
            "http://127.0.0.1:${health_port}/actuator/health" >/dev/null 2>&1; then
            echo "  - $health_label 已就绪"
            return 0
        fi
        sleep 1
        attempt=$((attempt + 1))
    done
    echo "错误: $health_label 未在 ${max_attempts}s 内就绪"
    return 1
}

echo "========================================"
echo "开始启动数据平台服务..."
echo "日志目录: $LOG_DIR"
echo "========================================"

# 先停止所有运行中的服务
echo "正在停止当前端口上可能存在的服务进程..."
service_ports=(8081 8082 8084 8085 8086 8888)
if [ "${CONNECTOR_SECOND_ACCESS_ENABLED:-false}" = "true" ]; then
    service_ports+=("${CONNECTOR_SECOND_ACCESS_PORT:-8083}")
fi

for port in "${service_ports[@]}"; do
    pid=$(lsof -t -i:$port 2>/dev/null)
    if [ -n "$pid" ]; then
        kill $pid 2>/dev/null
        echo "  - 已停止端口 $port 的进程"
    fi
done
sleep 2

# 在任何服务启动前统一应用数据库迁移。Liquibase 会加锁并校验已执行变更的校验和；
# 迁移失败时保持服务停止，避免新代码运行在旧数据库结构上。
if [ "${MIGRATE_DB:-true}" = "true" ]; then
    echo "校验并应用数据库迁移..."
    if ! bash "$SCRIPT_DIR/migrate-db.sh" update; then
        echo "数据库迁移失败，终止启动"
        exit 1
    fi
else
    echo "警告: MIGRATE_DB=false，已显式跳过数据库迁移"
fi

# 身份服务先启动以签发机器凭证，Gateway 最后启动。
start_order=(8086 8081 8084 8085 8082 8888)

for port in "${start_order[@]}"; do
    module="$(service_name "$port")"

    # 确定启动目录：优先使用 service 子模块，否则使用模块根目录
    if [ -d "$SCRIPT_DIR/$module/${module}-service" ]; then
        start_dir="$SCRIPT_DIR/$module/${module}-service"
    else
        start_dir="$SCRIPT_DIR/$module"
    fi

    if [ -d "$start_dir" ]; then
        log_file="$LOG_DIR/${module}.log"
        echo "启动 $module (端口: $port)..."
        cd "$start_dir"

        # 构建 JVM 参数
        jvm_args=""
        if [ -n "$SW_AGENT_JVM_ARGS" ]; then
            sw_name=$(echo "$module" | sed 's/data-platform-//')
            jvm_args="$SW_AGENT_JVM_ARGS -Dsw.agent.service_name=$sw_name"
        fi

        # The isolated connector E2E fixture uses a self-signed HTTPS endpoint.
        # Apply its truststore only to services that download or call connector artifacts;
        # do not leak fixture TLS settings into identity, billing, governance, or gateway.
        if [ -n "${CONNECTOR_JAVA_TLS_OPTIONS:-}" ] \
            && { [ "$module" = "data-platform-masterdata" ] || [ "$module" = "data-platform-access" ]; }; then
            jvm_args="${CONNECTOR_JAVA_TLS_OPTIONS} ${jvm_args}"
        fi

        if [ -n "$jvm_args" ]; then
            nohup mvn spring-boot:run -q -Dspring-boot.run.jvmArguments="$jvm_args" > "$log_file" 2>&1 &
        else
            nohup mvn spring-boot:run -q > "$log_file" 2>&1 &
        fi
        echo "  - $module 已启动 (日志: $log_file)"
        if [ "$port" = "8086" ] && ! wait_for_identity; then
            exit 1
        fi
        if [ "$port" = "8082" ] \
            && [ "${CONNECTOR_SECOND_ACCESS_ENABLED:-false}" = "true" ] \
            && ! wait_for_service_health "$port" "$module"; then
            exit 1
        fi
    else
        echo "  - $module 启动目录不存在 ($start_dir)，跳过"
    fi
done

# Stage-5 migration observation requires two independently registered Access
# instances. Keep this opt-in so the normal local stack remains unchanged.
if [ "${CONNECTOR_SECOND_ACCESS_ENABLED:-false}" = "true" ]; then
    second_access_port="${CONNECTOR_SECOND_ACCESS_PORT:-8083}"
    second_access_instance="${CONNECTOR_SECOND_ACCESS_INSTANCE_ID:-data-platform-access:${second_access_port}}"
    second_access_dir="$SCRIPT_DIR/data-platform-access/data-platform-access-service"
    second_access_log="$LOG_DIR/data-platform-access-${second_access_port}.log"
    if [ -d "$second_access_dir" ]; then
        echo "启动 data-platform-access 第二实例 (端口: $second_access_port, 实例: $second_access_instance)..."
        cd "$second_access_dir"
        second_jvm_args=""
        if [ -n "$SW_AGENT_JVM_ARGS" ]; then
            second_jvm_args="$SW_AGENT_JVM_ARGS -Dsw.agent.service_name=access-$second_access_port"
        fi
        if [ -n "${CONNECTOR_JAVA_TLS_OPTIONS:-}" ]; then
            second_jvm_args="${CONNECTOR_JAVA_TLS_OPTIONS} ${second_jvm_args}"
        fi
        if [ -n "$second_jvm_args" ]; then
            CONNECTOR_INSTANCE_ID="$second_access_instance" SERVER_PORT="$second_access_port" \
                FLOWABLE_CHECK_PROCESS_DEFINITIONS=false \
                nohup mvn spring-boot:run -q \
                -Dspring-boot.run.arguments="--server.port=$second_access_port" \
                -Dspring-boot.run.jvmArguments="$second_jvm_args" > "$second_access_log" 2>&1 &
        else
            CONNECTOR_INSTANCE_ID="$second_access_instance" SERVER_PORT="$second_access_port" \
                FLOWABLE_CHECK_PROCESS_DEFINITIONS=false \
                nohup mvn spring-boot:run -q \
                -Dspring-boot.run.arguments="--server.port=$second_access_port" > "$second_access_log" 2>&1 &
        fi
        echo "  - data-platform-access 第二实例已启动 (日志: $second_access_log)"
    else
        echo "  - data-platform-access 第二实例目录不存在 ($second_access_dir)，跳过"
    fi
fi

# 等待服务启动
echo ""
echo "等待服务启动..."
sleep 45

# 检查服务状态
echo ""
echo "========================================"
echo "服务启动检查"
echo "========================================"

startup_failed=0
for port in "${start_order[@]}"; do
    module="$(service_name "$port")"
    log_file="$LOG_DIR/${module}.log"
    if lsof -i :$port 2>/dev/null | grep -q LISTEN \
        && curl --noproxy '*' --silent --fail --max-time 2 \
            "http://127.0.0.1:${port}/actuator/health" >/dev/null 2>&1; then
        echo "✅ 端口 $port ($module) - 运行中"
    else
        startup_failed=1
        echo "❌ 端口 $port ($module) - 未运行"
        if [ -f "$log_file" ]; then
            echo "   日志最后几行:"
            tail -5 "$log_file" | sed 's/^/   /'
        fi
    fi
done

if [ "${CONNECTOR_SECOND_ACCESS_ENABLED:-false}" = "true" ]; then
    second_access_port="${CONNECTOR_SECOND_ACCESS_PORT:-8083}"
    second_access_log="$LOG_DIR/data-platform-access-${second_access_port}.log"
    if lsof -i :$second_access_port 2>/dev/null | grep -q LISTEN; then
        echo "✅ 端口 $second_access_port (data-platform-access 第二实例) - 运行中"
    else
        startup_failed=1
        echo "❌ 端口 $second_access_port (data-platform-access 第二实例) - 未运行"
        if [ -f "$second_access_log" ]; then
            echo "   日志最后几行:"
            tail -5 "$second_access_log" | sed 's/^/   /'
        fi
    fi
fi

if [ "$startup_failed" -ne 0 ]; then
    echo "错误: 至少一个开发服务未通过端口与健康检查" >&2
    exit 1
fi

echo ""
echo "========================================"
echo "所有服务已启动完成!"
echo "========================================"
