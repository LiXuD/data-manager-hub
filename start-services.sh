#!/bin/bash
# 启动所有数据平台服务

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 服务配置: 端口 -> 模块名
declare -A SERVICES=(
    [8081]="data-platform-vendor"
    [8082]="data-platform-caller"
    [8083]="data-platform-billing"
    [8084]="data-platform-call"
    [8085]="data-platform-monitor"
    [8086]="data-platform-tenant"
    [8087]="data-platform-user"
    [8088]="data-platform-role"
    [8089]="data-platform-datatype"
    [8090]="data-platform-log"
    [8091]="data-platform-config"
    [8092]="data-platform-graylog"
    [8093]="data-platform-sdk"
    [8094]="data-platform-security"
    [8095]="data-platform-trace"
    [8096]="data-platform-quality"
    [8888]="data-platform-gateway"
)

echo "========================================"
echo "开始启动数据平台服务..."
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
start_order=(8081 8082 8083 8084 8085 8086 8087 8088 8089 8090 8091 8092 8093 8094 8095 8096 8888)

for port in "${start_order[@]}"; do
    module="${SERVICES[$port]}"
    if [ -d "$SCRIPT_DIR/$module" ]; then
        echo "启动 $module (端口: $port)..."
        cd "$SCRIPT_DIR/$module"
        nohup mvn spring-boot:run -q > "/tmp/${module}.log" 2>&1 &
        echo "  - $module 已启动 (日志: /tmp/${module}.log)"
    else
        echo "  - $module 不存在，跳过"
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
    if [ -d "$SCRIPT_DIR/$module" ]; then
        if lsof -i :$port 2>/dev/null | grep -q LISTEN; then
            echo "✅ 端口 $port ($module) - 运行中"
        else
            echo "❌ 端口 $port ($module) - 未运行"
            # 显示日志
            if [ -f "/tmp/${module}.log" ]; then
                echo "   日志最后几行:"
                tail -5 "/tmp/${module}.log" | sed 's/^/   /'
            fi
        fi
    fi
done

echo ""
echo "========================================"
echo "所有服务已启动完成!"
echo "========================================"