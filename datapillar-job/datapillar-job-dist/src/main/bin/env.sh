#!/bin/bash
# =============================================================================
# Datapillar Job 环境变量配置
# =============================================================================
# 启动脚本会自动加载此文件，可在此配置 JVM 参数、环境变量等
#
# @author SunnyX6
# @date 2025-12-14
# =============================================================================

# 获取脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export DATAPILLAR_HOME="$(dirname "$SCRIPT_DIR")"

# 配置目录
export DATAPILLAR_CONF_DIR="${DATAPILLAR_HOME}/conf"
export DATAPILLAR_LOG_DIR="${DATAPILLAR_HOME}/logs"
export DATAPILLAR_LIB_DIR="${DATAPILLAR_HOME}/lib"

# -----------------------------------------------------------------------------
# JVM 配置
# -----------------------------------------------------------------------------
# Worker JVM 参数（生产环境建议 4G+）
export WORKER_JVM_OPTS="${WORKER_JVM_OPTS:--Xms2g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=100}"

# Server JVM 参数
export SERVER_JVM_OPTS="${SERVER_JVM_OPTS:--Xms1g -Xmx1g -XX:+UseG1GC}"

# 通用 JVM 参数
export JAVA_OPTS="${JAVA_OPTS:--XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=${DATAPILLAR_LOG_DIR}}"

# -----------------------------------------------------------------------------
# 网络配置（集群部署时需要修改）
# -----------------------------------------------------------------------------
# Worker 地址（用于集群通信，建议使用内网 IP）
export WORKER_ADDRESS="${WORKER_ADDRESS:-$(hostname -I | awk '{print $1}')}"

# Pekko 集群配置
export PEKKO_HOST="${PEKKO_HOST:-${WORKER_ADDRESS}}"
export PEKKO_PORT="${PEKKO_PORT:-2551}"

# 种子节点（生产环境配置多个，逗号分隔）
# 示例：pekko://datapillar-job@10.0.0.1:2551,pekko://datapillar-job@10.0.0.2:2551
export PEKKO_SEED_NODES="${PEKKO_SEED_NODES:-pekko://datapillar-job@127.0.0.1:2551}"

# -----------------------------------------------------------------------------
# 端口配置
# -----------------------------------------------------------------------------
export SERVER_PORT="${SERVER_PORT:-8080}"
export WORKER_PORT="${WORKER_PORT:-8081}"

# -----------------------------------------------------------------------------
# 数据库配置（可选，优先使用 datapillar-job.yml）
# -----------------------------------------------------------------------------
# export DB_URL="jdbc:mysql://localhost:3306/datapillar"
# export DB_USERNAME="root"
# export DB_PASSWORD="your_password"

# -----------------------------------------------------------------------------
# 日志配置
# -----------------------------------------------------------------------------
export LOG_PATH="${DATAPILLAR_LOG_DIR}"
