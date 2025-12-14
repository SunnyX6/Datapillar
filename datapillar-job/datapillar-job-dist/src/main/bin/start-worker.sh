#!/bin/bash
# =============================================================================
# 启动 Datapillar Job Worker
# =============================================================================
# 用法：
#   ./start-worker.sh              # 前台启动
#   ./start-worker.sh -d           # 后台启动（daemon）
#   ./start-worker.sh -d -p 2552   # 后台启动，指定 Pekko 端口
#
# @author SunnyX6
# @date 2025-12-14
# =============================================================================

set -e

# 加载环境变量
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/env.sh"

# 解析参数
DAEMON_MODE=false
while getopts "dp:" opt; do
    case $opt in
        d) DAEMON_MODE=true ;;
        p) PEKKO_PORT="$OPTARG" ;;
        *) echo "用法: $0 [-d] [-p pekko_port]"; exit 1 ;;
    esac
done

# 检查 Java
if ! command -v java &> /dev/null; then
    echo "[ERROR] Java not found. Please install Java 21+."
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [[ "$JAVA_VERSION" -lt 21 ]]; then
    echo "[ERROR] Java 21+ required, found: $JAVA_VERSION"
    exit 1
fi

# 查找 Worker JAR
WORKER_JAR=$(find "${DATAPILLAR_LIB_DIR}" -name "datapillar-job-worker-*.jar" | head -1)
if [[ -z "$WORKER_JAR" ]]; then
    echo "[ERROR] Worker JAR not found in ${DATAPILLAR_LIB_DIR}"
    exit 1
fi

# PID 文件
PID_FILE="${DATAPILLAR_LOG_DIR}/worker-${PEKKO_PORT}.pid"

# 检查是否已运行
if [[ -f "$PID_FILE" ]]; then
    OLD_PID=$(cat "$PID_FILE")
    if ps -p "$OLD_PID" > /dev/null 2>&1; then
        echo "[WARN] Worker already running with PID: $OLD_PID"
        exit 1
    fi
    rm -f "$PID_FILE"
fi

# 创建日志目录
mkdir -p "${DATAPILLAR_LOG_DIR}"

# 构建启动命令
JAVA_CMD="java"
JAVA_CMD="${JAVA_CMD} ${WORKER_JVM_OPTS}"
JAVA_CMD="${JAVA_CMD} ${JAVA_OPTS}"
JAVA_CMD="${JAVA_CMD} -DAPP_NAME=datapillar-job-worker"
JAVA_CMD="${JAVA_CMD} -DLOG_PATH=${DATAPILLAR_LOG_DIR}"
JAVA_CMD="${JAVA_CMD} -Dlogging.config=${DATAPILLAR_CONF_DIR}/logback.xml"
JAVA_CMD="${JAVA_CMD} -Dconfig.file=${DATAPILLAR_CONF_DIR}/worker.conf"
JAVA_CMD="${JAVA_CMD} -DPEKKO_HOST=${PEKKO_HOST}"
JAVA_CMD="${JAVA_CMD} -DPEKKO_PORT=${PEKKO_PORT}"
JAVA_CMD="${JAVA_CMD} -DPEKKO_SEED_NODES=${PEKKO_SEED_NODES}"
JAVA_CMD="${JAVA_CMD} -DWORKER_ADDRESS=${WORKER_ADDRESS}"
JAVA_CMD="${JAVA_CMD} -jar ${WORKER_JAR}"
JAVA_CMD="${JAVA_CMD} --spring.config.location=${DATAPILLAR_CONF_DIR}/datapillar-job.yml"
JAVA_CMD="${JAVA_CMD} --server.port=${WORKER_PORT}"

echo "=============================================="
echo " Datapillar Job Worker"
echo "=============================================="
echo " DATAPILLAR_HOME : ${DATAPILLAR_HOME}"
echo " Worker Address  : ${WORKER_ADDRESS}"
echo " HTTP Port       : ${WORKER_PORT}"
echo " Pekko Port      : ${PEKKO_PORT}"
echo " Seed Nodes      : ${PEKKO_SEED_NODES}"
echo " JVM Options     : ${WORKER_JVM_OPTS}"
echo "=============================================="

if [[ "$DAEMON_MODE" == "true" ]]; then
    # 后台启动
    nohup ${JAVA_CMD} > "${DATAPILLAR_LOG_DIR}/worker-startup.log" 2>&1 &
    echo $! > "$PID_FILE"
    echo "[INFO] Worker started in background, PID: $(cat $PID_FILE)"
    echo "[INFO] Startup log: ${DATAPILLAR_LOG_DIR}/worker-startup.log"
else
    # 前台启动
    exec ${JAVA_CMD}
fi
