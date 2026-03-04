#!/bin/bash
#
# Datapillar Environment variable configuration
#
# Place this file in conf/ under directory，The startup script will be loaded automatically
#

# Datapillar version
export DATAPILLAR_VERSION="1.0.0"

# JVM Memory configuration
# export DATAPILLAR_MEM="-Xms512m -Xmx1024m"

# Java Home（If not set，Use system default）
# export JAVA_HOME="/usr/lib/jvm/java-21"

# Log directory（Default: ${DATAPILLAR_HOME}/logs）
# export DATAPILLAR_LOG_DIR="${DATAPILLAR_HOME}/logs"

# Configuration directory（Default: ${DATAPILLAR_HOME}/conf）
# export DATAPILLAR_CONF_DIR="${DATAPILLAR_HOME}/conf"

# Database configuration
export DB_HOST="${DB_HOST:-localhost}"
export DB_PORT="${DB_PORT:-3306}"
export DB_USERNAME="${DB_USERNAME:-root}"
export DB_PASSWORD="${DB_PASSWORD:-Sunny.123456}"

# Redis Configuration
export REDIS_HOST="${REDIS_HOST:-localhost}"
export REDIS_PORT="${REDIS_PORT:-6379}"
export REDIS_PASSWORD="${REDIS_PASSWORD:-}"
export REDIS_DATABASE="${REDIS_DATABASE:-0}"

# Airflow Configuration
export AIRFLOW_BASE_URL="${AIRFLOW_BASE_URL:-http://localhost:8080}"
export AIRFLOW_USERNAME="${AIRFLOW_USERNAME:-datapillar}"
export AIRFLOW_PASSWORD="${AIRFLOW_PASSWORD:-123456asd}"

# Gravitino Configuration（used for Flink SQL execute）
export GRAVITINO_URI="${GRAVITINO_URI:-http://localhost:8090}"
export GRAVITINO_METALAKE="${GRAVITINO_METALAKE:-datapillar}"

# debug mode（Optional）
# export DATAPILLAR_DEBUG_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
