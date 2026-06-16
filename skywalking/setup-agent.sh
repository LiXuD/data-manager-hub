#!/bin/bash
# 下载并配置 SkyWalking Java Agent
# 用法: ./skywalking/setup-agent.sh [version]

SW_VERSION="${1:-9.4.0}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AGENT_DIR="$SCRIPT_DIR/agent"
DOWNLOAD_URL="https://archive.apache.org/dist/skywalking/java-agent/${SW_VERSION}/apache-skywalking-java-agent-${SW_VERSION}.tgz"

if [ -f "$AGENT_DIR/skywalking-agent.jar" ]; then
    echo "SkyWalking Agent 已存在: $AGENT_DIR/skywalking-agent.jar"
    exit 0
fi

echo "下载 SkyWalking Java Agent v${SW_VERSION}..."
mkdir -p "$AGENT_DIR"

TMP_FILE=$(mktemp)
TMP_DIR=$(mktemp -d)
curl -L -o "$TMP_FILE" "$DOWNLOAD_URL" 2>/dev/null

if [ $? -ne 0 ] || [ ! -s "$TMP_FILE" ]; then
    echo "下载失败，请手动下载:"
    echo "  URL: $DOWNLOAD_URL"
    echo "  解压到: $AGENT_DIR/"
    rm -f "$TMP_FILE"
    rm -rf "$TMP_DIR"
    exit 1
fi

tar -xzf "$TMP_FILE" -C "$TMP_DIR"
rm -f "$TMP_FILE"

EXTRACTED_AGENT_DIR=$(find "$TMP_DIR" -type f -name skywalking-agent.jar -exec dirname {} \; | head -1)
if [ -n "$EXTRACTED_AGENT_DIR" ]; then
    rm -rf "$AGENT_DIR"
    mkdir -p "$AGENT_DIR"
    cp -R "$EXTRACTED_AGENT_DIR"/. "$AGENT_DIR"/
fi
rm -rf "$TMP_DIR"

if [ -f "$AGENT_DIR/skywalking-agent.jar" ]; then
    echo "SkyWalking Agent 安装成功: $AGENT_DIR/skywalking-agent.jar"
else
    echo "解压后未找到 skywalking-agent.jar，请检查下载内容"
    exit 1
fi
