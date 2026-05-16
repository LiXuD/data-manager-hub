#!/bin/bash
# 启动所有数据平台服务

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$SCRIPT_DIR/logs"
mkdir -p "$LOG_DIR"

cd "$SCRIPT_DIR"

# 服务配置: 端口 -> 模块名
declare -A SERVICES=(
    [8081]="data-platform-masterdata"
    [8082]="data-platform-access"
    [8084]="data-platform-billing"
    [8085]="data-platform-governance"
    [8086]="data-platform-identity"
    [8888]="data-platform-gateway"
)

echo "========================================"
echo "开始启动数据平台服务..."
echo "日志目录: $LOG_DIR"
echo "========================================"

# 先停止所有运行中的服务
echo "正在停止可能存在的旧服务..."
for port in "${!SERVICES[@]}"; do
    pid=$(lsof -t -i:$port 2>/dev/null)
    if [ -n "$pid" ]; then
        kill $pid 2>/dev/null
        echo "  - 已停止端口 $port 的进程"
    fi
done
sleep 2

# 按顺序启动服务（Gateway最后启动）
start_order=(8081 8082 8084 8085 8086 8888)

for port in "${start_order[@]}"; do
    module="${SERVICES[$port]}"

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
        nohup mvn spring-boot:run -q > "$log_file" 2>&1 &
        echo "  - $module 已启动 (日志: $log_file)"
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
    module="${SERVICES[$port]}"
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
