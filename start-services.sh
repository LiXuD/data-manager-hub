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

if [ ! -f "$INTERNAL_AUTH_PRIVATE_KEY_PATH" ] || [ ! -f "$INTERNAL_AUTH_PUBLIC_KEY_PATH" ]; then
    echo "生成开发环境内部服务认证密钥..."
    openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$INTERNAL_AUTH_PRIVATE_KEY_PATH" >/dev/null 2>&1
    openssl pkey -in "$INTERNAL_AUTH_PRIVATE_KEY_PATH" -pubout -out "$INTERNAL_AUTH_PUBLIC_KEY_PATH" >/dev/null 2>&1
    chmod 600 "$INTERNAL_AUTH_PRIVATE_KEY_PATH"
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

if [ "${SKIP_BUILD:-false}" != "true" ]; then
    echo "构建并安装最新模块依赖..."
    if ! mvn -q -DskipTests install; then
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

echo "========================================"
echo "开始启动数据平台服务..."
echo "日志目录: $LOG_DIR"
echo "========================================"

# 先停止所有运行中的服务
echo "正在停止可能存在的旧服务..."
service_ports=(8081 8082 8084 8085 8086 8888)

for port in "${service_ports[@]}"; do
    pid=$(lsof -t -i:$port 2>/dev/null)
    if [ -n "$pid" ]; then
        kill $pid 2>/dev/null
        echo "  - 已停止端口 $port 的进程"
    fi
done
sleep 2

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

        if [ -n "$jvm_args" ]; then
            nohup mvn spring-boot:run -q -Dspring-boot.run.jvmArguments="$jvm_args" > "$log_file" 2>&1 &
        else
            nohup mvn spring-boot:run -q > "$log_file" 2>&1 &
        fi
        echo "  - $module 已启动 (日志: $log_file)"
        if [ "$port" = "8086" ] && ! wait_for_identity; then
            exit 1
        fi
    else
        echo "  - $module 启动目录不存在 ($start_dir)，跳过"
    fi
done

# 等待服务启动
echo ""
echo "等待服务启动..."
sleep 45

# 检查服务状态
echo ""
echo "========================================"
echo "服务启动检查"
echo "========================================"

for port in "${start_order[@]}"; do
    module="$(service_name "$port")"
    log_file="$LOG_DIR/${module}.log"
    if lsof -i :$port 2>/dev/null | grep -q LISTEN; then
        echo "✅ 端口 $port ($module) - 运行中"
    else
        echo "❌ 端口 $port ($module) - 未运行"
        if [ -f "$log_file" ]; then
            echo "   日志最后几行:"
            tail -5 "$log_file" | sed 's/^/   /'
        fi
    fi
done

echo ""
echo "========================================"
echo "所有服务已启动完成!"
echo "========================================"
