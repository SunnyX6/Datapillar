#!/bin/bash
# =============================================================================
# 停止 Datapillar Job Worker
# =============================================================================
# 用法：
#   ./stop-worker.sh              # 停止默认端口的 Worker
#   ./stop-worker.sh -p 2552      # 停止指定端口的 Worker
#   ./stop-worker.sh -f           # 强制停止（kill -9）
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
        p) PEKKO_PORT="$OPTARG" ;;
        *) echo "用法: $0 [-f] [-p pekko_port]"; exit 1 ;;
    esac
done

PID_FILE="${DATAPILLAR_LOG_DIR}/worker-${PEKKO_PORT}.pid"

if [[ ! -f "$PID_FILE" ]]; then
    echo "[WARN] PID file not found: $PID_FILE"
    echo "[INFO] Trying to find Worker process..."

    # 尝试通过进程名查找
    PID=$(pgrep -f "datapillar-job-worker.*PEKKO_PORT=${PEKKO_PORT}" || true)
    if [[ -z "$PID" ]]; then
        echo "[INFO] No Worker process found for port ${PEKKO_PORT}"
        exit 0
    fi
else
    PID=$(cat "$PID_FILE")
fi

if ! ps -p "$PID" > /dev/null 2>&1; then
    echo "[INFO] Worker process not running (PID: $PID)"
    rm -f "$PID_FILE"
    exit 0
fi

echo "[INFO] Stopping Worker (PID: $PID)..."

if [[ "$FORCE_KILL" == "true" ]]; then
    kill -9 "$PID"
    echo "[INFO] Worker force killed"
else
    # 优雅停止
    kill "$PID"

    # 等待进程退出（最多 30 秒）
    for i in {1..30}; do
        if ! ps -p "$PID" > /dev/null 2>&1; then
            echo "[INFO] Worker stopped gracefully"
            rm -f "$PID_FILE"
            exit 0
        fi
        sleep 1
        echo -n "."
    done

    echo ""
    echo "[WARN] Worker did not stop gracefully, force killing..."
    kill -9 "$PID" 2>/dev/null || true
fi

rm -f "$PID_FILE"
echo "[INFO] Worker stopped"
