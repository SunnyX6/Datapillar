#!/bin/bash

# Datapillar Local debugging one-click startup script
# Author: Sunny
# version: 1.4.0

set -o pipefail
export NO_PROXY=127.0.0.1,localhost
export no_proxy=127.0.0.1,localhost
# color definition
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# Get the parent directory of the directory where the script is located（Project root directory）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Set the log directory environment variable
export LOG_HOME="/tmp/datapillar-logs"
mkdir -p "$LOG_HOME"

# Nacos Unify environment variables（local）
export NACOS_SERVER_ADDR="127.0.0.1:8848"
export NACOS_NAMESPACE="dev"
export NACOS_USERNAME="datapillar-svc"
export NACOS_PASSWORD="123456asd"
export NACOS_GROUP="DATAPILLAR"
export NACOS_FORCE_SYNC="${NACOS_FORCE_SYNC:-true}"

# Verify Dubbo Register IP，avoid loopback cause Dubbo Startup failed
is_invalid_dubbo_ip() {
    local ip="${1:-}"
    [ -z "$ip" ] || [ "$ip" = "127.0.0.1" ] || [ "$ip" = "0.0.0.0" ] || [ "$ip" = "localhost" ]
}

# Use default routing network card IP as Dubbo Registered address，avoid being VPN/utun Wrong selection of network card
if is_invalid_dubbo_ip "${DUBBO_IP_TO_REGISTRY:-}"; then
    DEFAULT_IF="$(route -n get default 2>/dev/null | awk '/interface:/{print $2; exit}')"
    [ -z "$DEFAULT_IF" ] && DEFAULT_IF="en0"
    DUBBO_IP_TO_REGISTRY="$(ipconfig getifaddr "$DEFAULT_IF" 2>/dev/null)"
    if [ -z "$DUBBO_IP_TO_REGISTRY" ]; then
        DUBBO_IP_TO_REGISTRY="$(ifconfig "$DEFAULT_IF" 2>/dev/null | awk '/inet / && $2 != "127.0.0.1" {print $2; exit}')"
    fi
fi
if is_invalid_dubbo_ip "${DUBBO_IP_TO_REGISTRY}"; then
    echo -e "${RED}❌ Not found available DUBBO_IP_TO_REGISTRY，Please set it manually and try again${NC}"
    exit 1
fi
export DUBBO_IP_TO_REGISTRY
if is_invalid_dubbo_ip "${TRI_DUBBO_IP_TO_REGISTRY:-}"; then
    export TRI_DUBBO_IP_TO_REGISTRY="$DUBBO_IP_TO_REGISTRY"
else
    export TRI_DUBBO_IP_TO_REGISTRY
fi
# Unified service registration IP，Force explicit configuration，Disable automatic detection of network cards causing registration drift
export NACOS_SERVICE_IP="$DUBBO_IP_TO_REGISTRY"
# Service monitoring IP（Java/Python），Can be covered by environment；Default full network card monitoring
export SERVER_ADDRESS="${SERVER_ADDRESS:-0.0.0.0}"

# local build/run cache directory（prefer persistent user cache, can override via env）
export MAVEN_REPO_LOCAL="${MAVEN_REPO_LOCAL:-$HOME/.m2/repository}"
export UV_CACHE_DIR="${UV_CACHE_DIR:-$HOME/.cache/uv}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
mkdir -p "$MAVEN_REPO_LOCAL" "$UV_CACHE_DIR"
mkdir -p "$GRADLE_USER_HOME"

# Java local user directory（avoid Dubbo Default write ~/.dubbo）
export JAVA_LOCAL_HOME="${JAVA_LOCAL_HOME:-/tmp/datapillar-java-home}"
mkdir -p "$JAVA_LOCAL_HOME"

# Nacos Client log directory（Avoid writing by default ~/logs/nacos）
export NACOS_LOG_DIR="${NACOS_LOG_DIR:-$LOG_HOME/nacos}"
export NACOS_CACHE_DIR="${NACOS_CACHE_DIR:-$LOG_HOME/nacos/cache}"
mkdir -p "$NACOS_LOG_DIR" "$NACOS_CACHE_DIR"

# AI local Home（Avoid third parties SDK Write to user directory）
export AI_LOCAL_HOME="${AI_LOCAL_HOME:-/tmp/datapillar-ai-home}"
mkdir -p "$AI_LOCAL_HOME"

# Nacos HTTP address（for verification/Sync configuration）
export NACOS_HTTP_ADDR="${NACOS_HTTP_ADDR:-http://${NACOS_SERVER_ADDR}}"

# AI service Nacos Startup parameters
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
echo "🚀 Datapillar Local debugging is starting..."
echo "📁 Project directory: $PROJECT_ROOT"
echo "📝 Log directory: $LOG_HOME"
echo "🌐 Dubbo RegisterIP: $DUBBO_IP_TO_REGISTRY"
echo "🌐 Service monitoringIP: $SERVER_ADDRESS"
echo ""

# Check whether the port is occupied
check_port() {
    local port=$1
    local service=$2

    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
        echo -e "${YELLOW}⚠️  port $port ($service) Already occupied${NC}"
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
        echo -e "${RED}❌ Missing local Nacos Configuration template: $local_file${NC}"
        return 1
    fi

    if [ "${NACOS_FORCE_SYNC}" != "true" ]; then
        local query_resp
        query_resp=$(curl -sS "${NACOS_HTTP_ADDR}/nacos/v1/cs/configs?dataId=${data_id}&group=${NACOS_GROUP}&tenant=${NACOS_NAMESPACE}&username=${NACOS_USERNAME}&password=${NACOS_PASSWORD}")

        if [ -n "$query_resp" ] && [ "$query_resp" != "config data not exist" ] && [[ "$query_resp" != *'"status":403'* ]]; then
            echo -e "   ${GREEN}✅ Already exists: ${data_id}${NC}"
            return 0
        fi
    fi

    if [ "${NACOS_FORCE_SYNC}" = "true" ]; then
        echo -e "   ${YELLOW}Force coverage Nacos Configuration: ${data_id}${NC}"
    else
        echo -e "   ${YELLOW}Synchronize configuration to Nacos: ${data_id}${NC}"
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
        echo -e "${RED}❌ sync Nacos Configuration failed: ${data_id}${NC}"
        echo "   response: ${publish_resp}"
        return 1
    fi

    echo -e "   ${GREEN}✅ Synchronization successful: ${data_id}${NC}"
    return 0
}

prepare_nacos_configs() {
    if [ "${NACOS_FORCE_SYNC}" = "true" ]; then
        echo "🔧 Force coverage Nacos Configuration..."
    else
        echo "🔧 Verify Nacos Configuration..."
    fi
    local items=(
        "datapillar-auth.yaml"
        "datapillar-studio-service.yaml"
        "datapillar-api-gateway.yaml"
        "datapillar-ai.yaml"
        "datapillar-openlineage.yaml"
    )

    for item in "${items[@]}"; do
        sync_nacos_config "$item" || return 1
    done
    echo -e "${GREEN}✅ Nacos Configuration ready${NC}"
    echo ""
    return 0
}

publish_gravitino_java_client() {
    local publish_log="$LOG_HOME/datapillar-gravitino.java-client.publish.log"
    local gravitino_home="$PROJECT_ROOT/datapillar-gravitino"
    local gradle_runner="$gravitino_home/gradlew"

    echo "📦 Publish Datapillar-Gravitino Java client to local Maven repository..."
    echo -e "   ${YELLOW}Publish logs are output to both the terminal and file.: $publish_log${NC}"

    if [ ! -x "$gradle_runner" ]; then
        echo -e "${RED}❌ Gravitino Gradle wrapper does not exist: $gradle_runner${NC}"
        return 1
    fi

    cd "$gravitino_home"
    if ! (
        "$gradle_runner" \
            -Dmaven.repo.local="$MAVEN_REPO_LOCAL" \
            :api:publishToMavenLocal \
            :common:publishToMavenLocal \
            :clients:client-java-runtime:publishToMavenLocal \
            :clients:client-java:publishToMavenLocal \
            -x test \
            -x javadoc
    ) 2>&1 | tee "$publish_log"; then
        echo -e "${RED}❌ Datapillar-Gravitino Java client publish failed${NC}"
        echo -e "   ${YELLOW}View log: $publish_log${NC}"
        tail -n 60 "$publish_log" 2>/dev/null || true
        cd "$PROJECT_ROOT"
        return 1
    fi

    echo -e "${GREEN}✅ Datapillar-Gravitino Java client published to local Maven repository${NC}"
    echo -e "   ${YELLOW}Publish log: $publish_log${NC}"
    echo ""
    cd "$PROJECT_ROOT"
    return 0
}

prepare_nacos_configs || exit 1

# first step：Publish Gravitino Java client to local Maven repository
publish_gravitino_java_client || exit 1

# second step：Compile the entire project
# Local boot should not be testCompile blocking，Test compilation and execution are explicitly skipped here
echo "📦 Compiling project..."
cd "$PROJECT_ROOT"
mvn clean package -Dmaven.test.skip=true -Dmaven.repo.local="$MAVEN_REPO_LOCAL"
if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Compilation failed${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Compilation completed${NC}"
echo ""

build_gravitino_distribution() {
    local build_log="$LOG_HOME/datapillar-gravitino.build.log"
    local gravitino_home="$PROJECT_ROOT/datapillar-gravitino"
    local package_dir="$gravitino_home/distribution/package"

    echo "📦 Pack Datapillar-Gravitino in distribution package..."
    echo "   ${YELLOW}Build logs are output to both the terminal and file.: $build_log${NC}"
    cd "$gravitino_home"
    if ! ./build.sh 2>&1 | tee "$build_log"; then
        echo -e "${RED}❌ Datapillar-Gravitino Packaging failed${NC}"
        echo -e "   ${YELLOW}View log: $build_log${NC}"
        tail -n 60 "$build_log" 2>/dev/null || true
        cd "$PROJECT_ROOT"
        return 1
    fi

    if [ ! -x "$package_dir/bin/gravitino.sh" ]; then
        echo -e "${RED}❌ Gravitino Startup script does not exist: $package_dir/bin/gravitino.sh${NC}"
        cd "$PROJECT_ROOT"
        return 1
    fi

    echo -e "${GREEN}✅ Datapillar-Gravitino Packaging completed${NC}"
    echo -e "   ${YELLOW}Pack log: $build_log${NC}"
    echo ""
    cd "$PROJECT_ROOT"
    return 0
}

build_gravitino_distribution || exit 1

# start Java service
start_java_service() {
    local service_name=$1
    local jar_path=$2
    local port=$3
    local startup_log="$LOG_HOME/${service_name}.startup.log"

    echo "▶️  start $service_name (port: $port)"

    # Check port
    if ! check_port $port $service_name; then
        echo -e "   ${YELLOW}Skip startup，Port is occupied${NC}"
        return 0
    fi

    # Check jar File
    if [ ! -f "$jar_path" ]; then
        echo -e "   ${RED}❌ JAR File does not exist: $jar_path${NC}"
        return 1
    fi

    # Start service（Keep startup log，Easy to troubleshoot）
    nohup java -Duser.home="$JAVA_LOCAL_HOME" -Dfile.encoding=UTF-8 -DLOG_HOME="$LOG_HOME" -DJM.LOG.PATH="$NACOS_LOG_DIR" -DJM.SNAPSHOT.PATH="$NACOS_LOG_DIR/snapshot" -Ddubbo.application.register-mode=interface -Ddubbo.registry.register-mode=interface -jar "$jar_path" --server.address="$SERVER_ADDRESS" >"$startup_log" 2>&1 &
    local pid=$!
    echo "$pid" > /tmp/${service_name}.pid

    # Quickly verify processes and ports
    sleep 2
    if ! kill -0 $pid 2>/dev/null; then
        echo -e "   ${RED}❌ $service_name Startup failed，Process has exited${NC}"
        echo -e "   ${YELLOW}View log: $startup_log${NC}"
        tail -n 60 "$startup_log" 2>/dev/null || true
        return 1
    fi

    if ! wait_for_port $port 20; then
        echo -e "   ${RED}❌ $service_name Start timeout，port $port Not listening${NC}"
        echo -e "   ${YELLOW}View log: $startup_log${NC}"
        tail -n 60 "$startup_log" 2>/dev/null || true
        return 1
    fi

    echo -e "   ${GREEN}✅ $service_name Started successfully (PID: $pid)${NC}"
    echo -e "   ${YELLOW}Startup log: $startup_log${NC}"
    return 0
}

# start Python AI service
start_ai_service() {
    local startup_log="$LOG_HOME/datapillar-ai.startup.log"

    # from .env Read port configuration，Default 7003
    cd "$PROJECT_ROOT/datapillar-ai"
    if [ -f ".env" ]; then
        AI_PORT=$(grep "^APP_PORT=" .env | cut -d'=' -f2)
        AI_PORT=${AI_PORT:-7003}
    else
        AI_PORT=7003
    fi

    echo "▶️  start datapillar-ai (port: $AI_PORT)"

    if ! check_port $AI_PORT "datapillar-ai"; then
        echo -e "   ${YELLOW}Skip startup，Port is occupied${NC}"
        cd "$PROJECT_ROOT"
        return 0
    fi

    # use uv run Start service
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
        echo -e "   ${RED}❌ datapillar-ai Startup failed，Process has exited${NC}"
        echo -e "   ${YELLOW}View log: $startup_log${NC}"
        tail -n 60 "$startup_log" 2>/dev/null || true
        cd "$PROJECT_ROOT"
        return 1
    fi

    if ! wait_for_port $AI_PORT 20; then
        echo -e "   ${RED}❌ datapillar-ai Start timeout，port $AI_PORT Not listening${NC}"
        echo -e "   ${YELLOW}View log: $startup_log${NC}"
        tail -n 60 "$startup_log" 2>/dev/null || true
        cd "$PROJECT_ROOT"
        return 1
    fi

    echo -e "   ${GREEN}✅ datapillar-ai Started successfully (PID: $pid)${NC}"
    echo -e "   ${YELLOW}Startup log: $startup_log${NC}"

    cd "$PROJECT_ROOT"
    return 0
}

start_gravitino_service() {
    local service_name="datapillar-gravitino"
    local service_display_name="Datapillar-Gravitino"
    local port=8090
    local startup_log="$LOG_HOME/${service_name}.startup.log"
    local gravitino_home="$PROJECT_ROOT/datapillar-gravitino/distribution/package"
    local gravitino_bin="$gravitino_home/bin/gravitino.sh"
    local gravitino_conf="$gravitino_home/conf"
    local default_gravitino_log_dir="$gravitino_home/logs"

    echo "▶️  start $service_display_name (port: $port)"

    if ! check_port $port $service_name; then
        echo -e "   ${YELLOW}Skip startup，Port is occupied${NC}"
        return 0
    fi

    if [ ! -x "$gravitino_bin" ]; then
        echo -e "   ${RED}❌ Startup script does not exist: $gravitino_bin${NC}"
        return 1
    fi

    if [ ! -f "$gravitino_conf/gravitino.conf" ]; then
        echo -e "   ${RED}❌ Configuration file does not exist: $gravitino_conf/gravitino.conf${NC}"
        return 1
    fi

    if [ ! -f "$gravitino_conf/gravitino-env.sh" ]; then
        echo -e "   ${RED}❌ Environment file does not exist: $gravitino_conf/gravitino-env.sh${NC}"
        return 1
    fi

    echo "   Use native Gravitino Configuration directory: $gravitino_conf"
    export GRAVITINO_HOME="$gravitino_home"
    export GRAVITINO_LOG_DIR="${GRAVITINO_LOG_DIR:-$default_gravitino_log_dir}"
    mkdir -p "$GRAVITINO_LOG_DIR"
    echo "   Gravitino Run log directory: $GRAVITINO_LOG_DIR"

    cd "$gravitino_home"
    if ! "$gravitino_bin" --config "$gravitino_conf" start >"$startup_log" 2>&1; then
        echo -e "   ${RED}❌ $service_display_name Startup failed${NC}"
        echo -e "   ${YELLOW}View log: $startup_log${NC}"
        tail -n 60 "$startup_log" 2>/dev/null || true
        cd "$PROJECT_ROOT"
        return 1
    fi
    cd "$PROJECT_ROOT"

    if ! wait_for_port $port 30; then
        echo -e "   ${RED}❌ $service_display_name Start timeout，port $port Not listening${NC}"
        echo -e "   ${YELLOW}View log: $startup_log${NC}"
        tail -n 60 "$startup_log" 2>/dev/null || true
        return 1
    fi

    local pid
    pid=$(lsof -ti :$port -sTCP:LISTEN 2>/dev/null | head -1)
    if [ -n "$pid" ]; then
        echo "$pid" > /tmp/${service_name}.pid
    fi

    echo -e "   ${GREEN}✅ $service_display_name Started successfully${NC}"
    echo -e "   ${YELLOW}Startup log: $startup_log${NC}"
    return 0
}

abort_startup() {
    echo ""
    echo -e "${RED}❌ Service startup failed，abort remaining startup tasks${NC}"
    exit 1
}

echo "🚀 Start service..."
echo ""

# 1. Start authentication service
start_java_service "datapillar-auth" \
    "$PROJECT_ROOT/datapillar-auth/target/datapillar-auth-1.0.0.jar" 7001 || abort_startup

# 2. Start core business services
start_java_service "datapillar-studio-service" \
    "$PROJECT_ROOT/datapillar-studio-service/target/datapillar-studio-service-1.0.0.jar" 7002 || abort_startup

# 3. start API gateway
start_java_service "datapillar-api-gateway" \
    "$PROJECT_ROOT/datapillar-api-gateway/target/datapillar-api-gateway-1.0.0.jar" 7000 || abort_startup

# 4. start AI service
start_ai_service || abort_startup

# 5. start OpenLineage service
start_java_service "datapillar-openlineage" \
    "$PROJECT_ROOT/datapillar-openlineage/target/datapillar-openlineage-1.0.0.jar" 7004 || abort_startup

# 6. start Gravitino service
start_gravitino_service || abort_startup


echo ""
echo "=========================================="
echo -e "${GREEN}✅ All services started successfully${NC}"
echo "=========================================="
echo ""
echo "📋 Service list："
echo "   • API gateway:           http://localhost:7000"
echo "   • Auth service:          http://localhost:7001"
echo "   • Studio service:        http://localhost:7002"
echo "   • AI service:            http://localhost:7003"
echo "   • OpenLineage service:   http://localhost:7004"
echo "   • Datapillar-Gravitino:  http://localhost:8090"
echo ""
echo "📝 Log directory: $LOG_HOME"
echo "   tail -f $LOG_HOME/*.startup.log"
echo ""
echo "🛑 Stop service: ./scripts/stop-local-all.sh"
echo ""
