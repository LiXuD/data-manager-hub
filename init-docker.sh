#!/bin/bash
# 文件路径: /Users/lixd/IdeaProjects/Git/traesoloproj/data-manager-hub/init-docker.sh

set -e

echo "========================================="
echo "数据管理平台 - Docker 环境初始化"
echo "========================================="

# 获取项目根目录
PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"

# 1. 创建必要的目录
echo ""
echo "📁 创建必要的目录..."
mkdir -p "$PROJECT_ROOT/prometheus"
mkdir -p "$PROJECT_ROOT/filebeat"
mkdir -p "$PROJECT_ROOT/logs"

# 2. 检查并创建 Prometheus 配置文件
PROMETHEUS_CONFIG="$PROJECT_ROOT/prometheus/prometheus.yml"
if [ -f "$PROMETHEUS_CONFIG" ]; then
    echo "✅ Prometheus 配置文件已存在"
else
    echo "📝 创建 Prometheus 配置文件..."
    cat > "$PROMETHEUS_CONFIG" << 'EOF'
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']

  - job_name: 'data-platform-services'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets:
          - 'host.docker.internal:8081'
          - 'host.docker.internal:8082'
          - 'host.docker.internal:8083'
          - 'host.docker.internal:8084'
          - 'host.docker.internal:8085'
          - 'host.docker.internal:8086'
          - 'host.docker.internal:8087'
          - 'host.docker.internal:8090'
          - 'host.docker.internal:8092'
          - 'host.docker.internal:8093'
          - 'host.docker.internal:8094'
          - 'host.docker.internal:8095'
          - 'host.docker.internal:8096'
          - 'host.docker.internal:8097'
          - 'host.docker.internal:8888'
EOF
    echo "✅ Prometheus 配置文件创建成功"
fi

# 3. 检查并创建 Filebeat 配置文件
FILEBEAT_CONFIG="$PROJECT_ROOT/filebeat/filebeat.yml"
if [ -f "$FILEBEAT_CONFIG" ]; then
    echo "✅ Filebeat 配置文件已存在"
else
    echo "📝 创建 Filebeat 配置文件..."
    cat > "$FILEBEAT_CONFIG" << 'EOF'
filebeat.inputs:
  - type: log
    enabled: true
    paths:
      - /var/log/data-platform/*.log
    fields:
      service: data-platform
    fields_under_root: true
    multiline.pattern: '^[0-9]{4}-[0-9]{2}-[0-9]{2}'
    multiline.negate: true
    multiline.match: after

output.elasticsearch:
  hosts: ["elasticsearch:9200"]
  index: "data-platform-logs-%{+yyyy.MM.dd}"

setup.template.name: "data-platform"
setup.template.pattern: "data-platform-*"
EOF
    echo "✅ Filebeat 配置文件创建成功"
fi

# 4. 验证文件类型（确保不是目录）
echo ""
echo "🔍 验证配置文件..."
if [ -d "$PROMETHEUS_CONFIG" ]; then
    echo "❌ 错误: prometheus.yml 是目录而不是文件！"
    echo "正在修复..."
    rm -rf "$PROMETHEUS_CONFIG"
    # 重新创建文件（内容同上）
    cat > "$PROMETHEUS_CONFIG" << 'EOF'
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']

  - job_name: 'data-platform-services'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets:
          - 'host.docker.internal:8081'
          - 'host.docker.internal:8082'
          - 'host.docker.internal:8083'
          - 'host.docker.internal:8084'
          - 'host.docker.internal:8085'
          - 'host.docker.internal:8086'
          - 'host.docker.internal:8087'
          - 'host.docker.internal:8090'
          - 'host.docker.internal:8092'
          - 'host.docker.internal:8093'
          - 'host.docker.internal:8094'
          - 'host.docker.internal:8095'
          - 'host.docker.internal:8096'
          - 'host.docker.internal:8097'
          - 'host.docker.internal:8888'
EOF
    echo "✅ 已修复 prometheus.yml"
fi

if [ -d "$FILEBEAT_CONFIG" ]; then
    echo "❌ 错误: filebeat.yml 是目录而不是文件！"
    echo "正在修复..."
    rm -rf "$FILEBEAT_CONFIG"
    # 重新创建文件（内容同上）
    cat > "$FILEBEAT_CONFIG" << 'EOF'
filebeat.inputs:
  - type: log
    enabled: true
    paths:
      - /var/log/data-platform/*.log
    fields:
      service: data-platform
    fields_under_root: true
    multiline.pattern: '^[0-9]{4}-[0-9]{2}-[0-9]{2}'
    multiline.negate: true
    multiline.match: after

output.elasticsearch:
  hosts: ["elasticsearch:9200"]
  index: "data-platform-logs-%{+yyyy.MM.dd}"

setup.template.name: "data-platform"
setup.template.pattern: "data-platform-*"
EOF
    echo "✅ 已修复 filebeat.yml"
fi

# 5. 显示文件信息
echo ""
echo "📋 配置文件信息:"
ls -lh "$PROMETHEUS_CONFIG"
ls -lh "$FILEBEAT_CONFIG"

echo ""
echo "========================================="
echo "✅ 初始化完成！"
echo "========================================="
echo ""
echo "现在可以运行以下命令启动服务："
echo "  docker-compose up -d"
echo ""
echo "查看服务状态："
echo "  docker-compose ps"
echo ""
echo "查看日志："
echo "  docker-compose logs -f"
echo ""
