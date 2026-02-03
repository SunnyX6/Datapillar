#!/bin/bash
#
# Datapillar 环境变量配置
#
# 将此文件放在 conf/ 目录下，启动脚本会自动加载
#

# Datapillar 版本
export DATAPILLAR_VERSION="1.0.0"

# JVM 内存配置
# export DATAPILLAR_MEM="-Xms512m -Xmx1024m"

# Java Home（如果不设置，使用系统默认）
# export JAVA_HOME="/usr/lib/jvm/java-21"

# 日志目录（默认: ${DATAPILLAR_HOME}/logs）
# export DATAPILLAR_LOG_DIR="${DATAPILLAR_HOME}/logs"

# 配置目录（默认: ${DATAPILLAR_HOME}/conf）
# export DATAPILLAR_CONF_DIR="${DATAPILLAR_HOME}/conf"

# 数据库配置
export DB_HOST="${DB_HOST:-localhost}"
export DB_PORT="${DB_PORT:-3306}"
export DB_USERNAME="${DB_USERNAME:-root}"
export DB_PASSWORD="${DB_PASSWORD:-Sunny.123456}"

# Redis 配置
export REDIS_HOST="${REDIS_HOST:-localhost}"
export REDIS_PORT="${REDIS_PORT:-6379}"
export REDIS_PASSWORD="${REDIS_PASSWORD:-}"
export REDIS_DATABASE="${REDIS_DATABASE:-0}"

# Airflow 配置
export AIRFLOW_BASE_URL="${AIRFLOW_BASE_URL:-http://localhost:8080}"
export AIRFLOW_USERNAME="${AIRFLOW_USERNAME:-datapillar}"
export AIRFLOW_PASSWORD="${AIRFLOW_PASSWORD:-123456asd}"

# Gravitino 配置（用于 Flink SQL 执行）
export GRAVITINO_URI="${GRAVITINO_URI:-http://localhost:8090}"
export GRAVITINO_METALAKE="${GRAVITINO_METALAKE:-datapillar}"

# 调试模式（可选）
# export DATAPILLAR_DEBUG_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
