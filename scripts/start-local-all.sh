#!/bin/bash

# Datapillar 本地调试一键启动脚本
# 作者: Sunny
# 版本: 1.4.0

set -o pipefail
export NO_PROXY=127.0.0.1,localhost
export no_proxy=127.0.0.1,localhost
# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# 获取脚本所在目录的父目录（项目根目录）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# 设置日志目录环境变量
export LOG_HOME="/tmp/datapillar-logs"
mkdir -p "$LOG_HOME"

# Nacos 统一环境变量（本地）
export NACOS_SERVER_ADDR="127.0.0.1:8848"
export NACOS_NAMESPACE="dev"
export NACOS_USERNAME="datapillar-svc"
export NACOS_PASSWORD="123456asd"
export NACOS_GROUP="DATAPILLAR"
export NACOS_FORCE_SYNC="${NACOS_FORCE_SYNC:-true}"

# 校验 Dubbo 注册 IP，避免 loopback 导致 Dubbo 启动失败
is_invalid_dubbo_ip() {
    local ip="${1:-}"
    [ -z "$ip" ] || [ "$ip" = "127.0.0.1" ] || [ "$ip" = "0.0.0.0" ] || [ "$ip" = "localhost" ]
}

# 使用默认路由网卡 IP 作为 Dubbo 注册地址，避免被 VPN/utun 网卡误选
if is_invalid_dubbo_ip "${DUBBO_IP_TO_REGISTRY:-}"; then
    DEFAULT_IF="$(route -n get default 2>/dev/null | awk '/interface:/{print $2; exit}')"
    [ -z "$DEFAULT_IF" ] && DEFAULT_IF="en0"
    DUBBO_IP_TO_REGISTRY="$(ipconfig getifaddr "$DEFAULT_IF" 2>/dev/null)"
    if [ -z "$DUBBO_IP_TO_REGISTRY" ]; then
        DUBBO_IP_TO_REGISTRY="$(ifconfig "$DEFAULT_IF" 2>/dev/null | awk '/inet / && $2 != "127.0.0.1" {print $2; exit}')"
    fi
fi
if is_invalid_dubbo_ip "${DUBBO_IP_TO_REGISTRY}"; then
    echo -e "${RED}❌ 未找到可用的 DUBBO_IP_TO_REGISTRY，请手动设置后重试${NC}"
    exit 1
fi
export DUBBO_IP_TO_REGISTRY
if is_invalid_dubbo_ip "${TRI_DUBBO_IP_TO_REGISTRY:-}"; then
    export TRI_DUBBO_IP_TO_REGISTRY="$DUBBO_IP_TO_REGISTRY"
else
    export TRI_DUBBO_IP_TO_REGISTRY
fi
# 统一服务注册 IP，强制显式配置，禁止自动探测网卡导致注册漂移
export NACOS_SERVICE_IP="$DUBBO_IP_TO_REGISTRY"
# 服务监听 IP（Java/Python），可按环境覆盖；默认全网卡监听
export SERVER_ADDRESS="${SERVER_ADDRESS:-0.0.0.0}"

# 本地构建/运行目录（避免写入用户目录权限问题）
export MAVEN_REPO_LOCAL="${MAVEN_REPO_LOCAL:-/tmp/m2}"
export UV_CACHE_DIR="${UV_CACHE_DIR:-/tmp/datapillar-uv-cache}"
mkdir -p "$MAVEN_REPO_LOCAL" "$UV_CACHE_DIR"

# Java 本地用户目录（避免 Dubbo 默认写 ~/.dubbo）
export JAVA_LOCAL_HOME="${JAVA_LOCAL_HOME:-/tmp/datapillar-java-home}"
mkdir -p "$JAVA_LOCAL_HOME"

# Nacos 客户端日志目录（避免默认写到 ~/logs/nacos）
export NACOS_LOG_DIR="${NACOS_LOG_DIR:-$LOG_HOME/nacos}"
export NACOS_CACHE_DIR="${NACOS_CACHE_DIR:-$LOG_HOME/nacos/cache}"
mkdir -p "$NACOS_LOG_DIR" "$NACOS_CACHE_DIR"

# AI 本地 Home（避免第三方 SDK 写入用户目录）
export AI_LOCAL_HOME="${AI_LOCAL_HOME:-/tmp/datapillar-ai-home}"
mkdir -p "$AI_LOCAL_HOME"

# Nacos HTTP 地址（用于校验/同步配置）
export NACOS_HTTP_ADDR="${NACOS_HTTP_ADDR:-http://${NACOS_SERVER_ADDR}}"

# AI 服务 Nacos 启动参数
export NACOS_DATA_ID="${NACOS_DATA_ID:-datapillar-ai.yaml}"
export NACOS_SERVICE_NAME="${NACOS_SERVICE_NAME:-datapillar-ai}"
export NACOS_CLUSTER_NAME="${NACOS_CLUSTER_NAME:-DEFAULT}"
export NACOS_EPHEMERAL="${NACOS_EPHEMERAL:-true}"
export NACOS_HEARTBEAT_INTERVAL="${NACOS_HEARTBEAT_INTERVAL:-5}"
export NACOS_CONFIG_WATCH="${NACOS_CONFIG_WATCH:-true}"

echo "=========================================="
echo "   ____        _              _ _ _            "
echo "  |  _ \  __ _| |_ __ _ _ __ (_) | | __ _ _ __ "
echo "  | | | |/ _\` | __/ _\` | '_ \| | | |/ _\` | '__|"
echo "  | |_| | (_| | || (_| | |_) | | | | (_| | |   "
echo "  |____/ \__,_|\__\__,_| .__/|_|_|_|\__,_|_|   "
echo "                       |_|    [LOCAL DEBUG]    "
echo "=========================================="
echo ""
echo "🚀 Datapillar 本地调试启动中..."
echo "📁 项目目录: $PROJECT_ROOT"
echo "📝 日志目录: $LOG_HOME"
echo "🌐 Dubbo 注册IP: $DUBBO_IP_TO_REGISTRY"
echo "🌐 服务监听IP: $SERVER_ADDRESS"
echo ""

# 检查端口是否被占用
check_port() {
    local port=$1
    local service=$2

    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
        echo -e "${YELLOW}⚠️  端口 $port ($service) 已被占用${NC}"
        return 1
    fi
    return 0
}

wait_for_port() {
    local port=$1
    local max_wait=$2
    local i

    for ((i = 1; i <= max_wait; i++)); do
        if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
            return 0
        fi
        sleep 1
    done
    return 1
}

sync_nacos_config() {
    local data_id=$1
    local local_file="$PROJECT_ROOT/config/nacos/${NACOS_NAMESPACE}/DATAPILLAR/${data_id}"

    if [ ! -f "$local_file" ]; then
        echo -e "${RED}❌ 缺少本地 Nacos 配置模板: $local_file${NC}"
        return 1
    fi

    if [ "${NACOS_FORCE_SYNC}" != "true" ]; then
        local query_resp
        query_resp=$(curl -sS "${NACOS_HTTP_ADDR}/nacos/v1/cs/configs?dataId=${data_id}&group=${NACOS_GROUP}&tenant=${NACOS_NAMESPACE}&username=${NACOS_USERNAME}&password=${NACOS_PASSWORD}")

        if [ -n "$query_resp" ] && [ "$query_resp" != "config data not exist" ] && [[ "$query_resp" != *'"status":403'* ]]; then
            echo -e "   ${GREEN}✅ 已存在: ${data_id}${NC}"
            return 0
        fi
    fi

    if [ "${NACOS_FORCE_SYNC}" = "true" ]; then
        echo -e "   ${YELLOW}强制覆盖 Nacos 配置: ${data_id}${NC}"
    else
        echo -e "   ${YELLOW}同步配置到 Nacos: ${data_id}${NC}"
    fi
    local publish_resp
    publish_resp=$(curl -sS -X POST "${NACOS_HTTP_ADDR}/nacos/v1/cs/configs" \
        --data-urlencode "username=${NACOS_USERNAME}" \
        --data-urlencode "password=${NACOS_PASSWORD}" \
        --data-urlencode "tenant=${NACOS_NAMESPACE}" \
        --data-urlencode "group=${NACOS_GROUP}" \
        --data-urlencode "dataId=${data_id}" \
        --data-urlencode "type=yaml" \
        --data-urlencode "content@${local_file}")

    if [ "$publish_resp" != "true" ]; then
        echo -e "${RED}❌ 同步 Nacos 配置失败: ${data_id}${NC}"
        echo "   响应: ${publish_resp}"
        return 1
    fi

    echo -e "   ${GREEN}✅ 同步成功: ${data_id}${NC}"
    return 0
}

prepare_nacos_configs() {
    if [ "${NACOS_FORCE_SYNC}" = "true" ]; then
        echo "🔧 强制覆盖 Nacos 配置..."
    else
        echo "🔧 校验 Nacos 配置..."
    fi
    local items=(
        "datapillar-auth.yaml"
        "datapillar-studio-service.yaml"
        "datapillar-api-gateway.yaml"
        "datapillar-ai.yaml"
    )

    for item in "${items[@]}"; do
        sync_nacos_config "$item" || return 1
    done
    echo -e "${GREEN}✅ Nacos 配置就绪${NC}"
    echo ""
    return 0
}

prepare_nacos_configs || exit 1

# 第一步：编译整个项目
# 本地启动不应被 testCompile 阻塞，这里显式跳过测试编译与执行
echo "📦 编译项目中..."
cd "$PROJECT_ROOT"
mvn clean package -Dmaven.test.skip=true -Dmaven.repo.local="$MAVEN_REPO_LOCAL"
if [ $? -ne 0 ]; then
    echo -e "${RED}❌ 编译失败${NC}"
    exit 1
fi
echo -e "${GREEN}✅ 编译完成${NC}"
echo ""

# 启动 Java 服务
start_java_service() {
    local service_name=$1
    local jar_path=$2
    local port=$3
    local startup_log="$LOG_HOME/${service_name}.startup.log"

    echo "▶️  启动 $service_name (端口: $port)"

    # 检查端口
    if ! check_port $port $service_name; then
        echo -e "   ${YELLOW}跳过启动，端口已占用${NC}"
        return 0
    fi

    # 检查 jar 文件
    if [ ! -f "$jar_path" ]; then
        echo -e "   ${RED}❌ JAR 文件不存在: $jar_path${NC}"
        return 1
    fi

    # 启动服务（保留启动日志，便于排障）
    nohup java -Duser.home="$JAVA_LOCAL_HOME" -Dfile.encoding=UTF-8 -DLOG_HOME="$LOG_HOME" -DJM.LOG.PATH="$NACOS_LOG_DIR" -DJM.SNAPSHOT.PATH="$NACOS_LOG_DIR/snapshot" -Ddubbo.application.register-mode=interface -Ddubbo.registry.register-mode=interface -jar "$jar_path" --server.address="$SERVER_ADDRESS" >"$startup_log" 2>&1 &
    local pid=$!
    echo "$pid" > /tmp/${service_name}.pid

    # 快速校验进程与端口
    sleep 2
    if ! kill -0 $pid 2>/dev/null; then
        echo -e "   ${RED}❌ $service_name 启动失败，进程已退出${NC}"
        echo -e "   ${YELLOW}查看日志: $startup_log${NC}"
        tail -n 60 "$startup_log" 2>/dev/null || true
        return 1
    fi

    if ! wait_for_port $port 20; then
        echo -e "   ${RED}❌ $service_name 启动超时，端口 $port 未监听${NC}"
        echo -e "   ${YELLOW}查看日志: $startup_log${NC}"
        tail -n 60 "$startup_log" 2>/dev/null || true
        return 1
    fi

    echo -e "   ${GREEN}✅ $service_name 启动成功 (PID: $pid)${NC}"
    echo -e "   ${YELLOW}启动日志: $startup_log${NC}"
    return 0
}

# 启动 Python AI 服务
start_ai_service() {
    local startup_log="$LOG_HOME/datapillar-ai.startup.log"

    # 从 .env 读取端口配置，默认 7003
    cd "$PROJECT_ROOT/datapillar-ai"
    if [ -f ".env" ]; then
        AI_PORT=$(grep "^APP_PORT=" .env | cut -d'=' -f2)
        AI_PORT=${AI_PORT:-7003}
    else
        AI_PORT=7003
    fi

    echo "▶️  启动 datapillar-ai (端口: $AI_PORT)"

    if ! check_port $AI_PORT "datapillar-ai"; then
        echo -e "   ${YELLOW}跳过启动，端口已占用${NC}"
        cd "$PROJECT_ROOT"
        return 0
    fi

    # 使用 uv run 启动服务
    LOG_HOME="$LOG_HOME" \
    UV_CACHE_DIR="$UV_CACHE_DIR" \
    XDG_CACHE_HOME="$UV_CACHE_DIR" \
    HOME="$AI_LOCAL_HOME" \
    NACOS_LOG_DIR="$NACOS_LOG_DIR" \
    NACOS_CACHE_DIR="$NACOS_CACHE_DIR" \
    nohup uv run uvicorn src.app:app --host "$SERVER_ADDRESS" --port $AI_PORT >"$startup_log" 2>&1 &
    local pid=$!
    echo "$pid" > /tmp/datapillar-ai.pid

    sleep 2
    if ! kill -0 $pid 2>/dev/null; then
        echo -e "   ${RED}❌ datapillar-ai 启动失败，进程已退出${NC}"
        echo -e "   ${YELLOW}查看日志: $startup_log${NC}"
        tail -n 60 "$startup_log" 2>/dev/null || true
        cd "$PROJECT_ROOT"
        return 1
    fi

    if ! wait_for_port $AI_PORT 20; then
        echo -e "   ${RED}❌ datapillar-ai 启动超时，端口 $AI_PORT 未监听${NC}"
        echo -e "   ${YELLOW}查看日志: $startup_log${NC}"
        tail -n 60 "$startup_log" 2>/dev/null || true
        cd "$PROJECT_ROOT"
        return 1
    fi

    echo -e "   ${GREEN}✅ datapillar-ai 启动成功 (PID: $pid)${NC}"
    echo -e "   ${YELLOW}启动日志: $startup_log${NC}"

    cd "$PROJECT_ROOT"
    return 0
}

echo "🚀 启动服务..."
echo ""

FAILED=0

# 1. 启动认证服务
start_java_service "datapillar-auth" \
    "$PROJECT_ROOT/datapillar-auth/target/datapillar-auth-1.0.0.jar" 7001 || FAILED=1

# 2. 启动核心业务服务
start_java_service "datapillar-studio-service" \
    "$PROJECT_ROOT/datapillar-studio-service/target/datapillar-studio-service-1.0.0.jar" 7002 || FAILED=1

# 3. 启动 API 网关
start_java_service "datapillar-api-gateway" \
    "$PROJECT_ROOT/datapillar-api-gateway/target/datapillar-api-gateway-1.0.0.jar" 7000 || FAILED=1

# 4. 启动 AI 服务
start_ai_service || FAILED=1


echo ""
echo "=========================================="
if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}✅ 所有服务启动成功${NC}"
else
    echo -e "${RED}❌ 存在服务启动失败，请检查 $LOG_HOME/*.startup.log${NC}"
fi
echo "=========================================="
echo ""
echo "📋 服务列表："
echo "   • API 网关:           http://localhost:7000"
echo "   • 认证服务:           http://localhost:7001"
echo "   • 核心业务:           http://localhost:7002"
echo "   • AI 服务:            http://localhost:7003"
echo ""
echo "📝 日志目录: $LOG_HOME"
echo "   tail -f $LOG_HOME/*.startup.log"
echo ""
echo "🛑 停止服务: ./scripts/stop-local-all.sh"
echo ""

if [ $FAILED -ne 0 ]; then
    exit 1
fi
