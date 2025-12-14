#!/bin/bash
# =============================================================================
# 停止 Datapillar Job Server
# =============================================================================
# 用法：
#   ./stop-server.sh              # 停止默认端口的 Server
#   ./stop-server.sh -p 8080      # 停止指定端口的 Server
#   ./stop-server.sh -f           # 强制停止（kill -9）
#
# @author SunnyX6
# @date 2025-12-14
# =============================================================================

set -e

# 加载环境变量
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/env.sh"

# 解析参数
FORCE_KILL=false
while getopts "fp:" opt; do
    case $opt in
        f) FORCE_KILL=true ;;
        p) SERVER_PORT="$OPTARG" ;;
        *) echo "用法: $0 [-f] [-p port]"; exit 1 ;;
    esac
done

PID_FILE="${DATAPILLAR_LOG_DIR}/server-${SERVER_PORT}.pid"

if [[ ! -f "$PID_FILE" ]]; then
    echo "[WARN] PID file not found: $PID_FILE"
    echo "[INFO] Trying to find Server process..."

    # 尝试通过进程名查找
    PID=$(pgrep -f "datapillar-job-server.*server.port=${SERVER_PORT}" || true)
    if [[ -z "$PID" ]]; then
        echo "[INFO] No Server process found for port ${SERVER_PORT}"
        exit 0
    fi
else
    PID=$(cat "$PID_FILE")
fi

if ! ps -p "$PID" > /dev/null 2>&1; then
    echo "[INFO] Server process not running (PID: $PID)"
    rm -f "$PID_FILE"
    exit 0
fi

echo "[INFO] Stopping Server (PID: $PID)..."

if [[ "$FORCE_KILL" == "true" ]]; then
    kill -9 "$PID"
    echo "[INFO] Server force killed"
else
    # 优雅停止
    kill "$PID"

    # 等待进程退出（最多 30 秒）
    for i in {1..30}; do
        if ! ps -p "$PID" > /dev/null 2>&1; then
            echo "[INFO] Server stopped gracefully"
            rm -f "$PID_FILE"
            exit 0
        fi
        sleep 1
        echo -n "."
    done

    echo ""
    echo "[WARN] Server did not stop gracefully, force killing..."
    kill -9 "$PID" 2>/dev/null || true
fi

rm -f "$PID_FILE"
echo "[INFO] Server stopped"
