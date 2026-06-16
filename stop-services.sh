#!/bin/bash
# 停止所有数据平台服务

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 服务端口列表
PORTS=(8081 8082 8083 8084 8085 8086 8087 8090 8092 8093 8094 8095 8096 8097 8888)

echo "========================================"
echo "开始停止数据平台服务..."
echo "========================================"

stopped=0
for port in "${PORTS[@]}"; do
    pid=$(lsof -t -i:$port 2>/dev/null)
    if [ -n "$pid" ]; then
        kill $pid 2>/dev/null
        echo "✅ 已停止端口 $port (PID: $pid)"
        stopped=$((stopped + 1))
    else
        echo "⚪ 端口 $port 无进程运行"
    fi
done

sleep 2

echo ""
echo "========================================"
echo "已停止 $stopped 个服务"
echo "========================================"

# 可选：清理日志文件
if [ "$1" = "--clean-logs" ]; then
    echo "清理日志文件..."
    for module in data-platform-{vendor,caller,call,billing,monitor,tenant,sdk,log,graylog,iam,security,trace,quality,interface,gateway}; do
        rm -f "/tmp/${module}.log" 2>/dev/null
    done
    echo "✅ 日志文件已清理"
fi
