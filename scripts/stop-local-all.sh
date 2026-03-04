#!/bin/bash

# Datapillar Local debugging stops the script with one click
# Author: Sunny
# version: 1.1.0

echo "🛑 Datapillar Local service is stopped..."
echo ""

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
LOG_HOME="${LOG_HOME:-/tmp/datapillar-logs}"
mkdir -p "$LOG_HOME"

# Stop service（Pass PID File）
stop_service_by_pid() {
    local service_name=$1
    local pid_file="/tmp/${service_name}.pid"

    if [ -f "$pid_file" ]; then
        local pid=$(cat "$pid_file")
        if kill -0 $pid 2>/dev/null; then
            echo "⏹️  stop $service_name (PID: $pid)"
            kill $pid 2>/dev/null || true
            # Wait for process to end
            for i in {1..10}; do
                if ! kill -0 $pid 2>/dev/null; then
                    break
                fi
                sleep 1
            done
            # if its not over yet，Forced to kill
            if kill -0 $pid 2>/dev/null; then
                kill -9 $pid 2>/dev/null || true
            fi
            rm -f "$pid_file"
            echo -e "   ${GREEN}✅ Stopped${NC}"
        else
            echo -e "${YELLOW}⚠️  $service_name Process does not exist (PID: $pid)${NC}"
            rm -f "$pid_file"
        fi
    else
        echo -e "${YELLOW}⚠️  $service_name PID File does not exist${NC}"
    fi
}

# Stop service by port
stop_service_by_port() {
    local service_name=$1
    local port=$2

    local pid=$(lsof -ti :$port 2>/dev/null | head -1)
    if [ -n "$pid" ]; then
        echo "⏹️  stop $service_name (port: $port, PID: $pid)"
        kill $pid 2>/dev/null || true
        sleep 2
        # if its not over yet，Forced to kill
        if kill -0 $pid 2>/dev/null; then
            kill -9 $pid 2>/dev/null || true
        fi
        echo -e "   ${GREEN}✅ Stopped${NC}"
    fi
}

stop_gravitino_via_script() {
    local service_name="datapillar-gravitino"
    local service_display_name="Datapillar-Gravitino"
    local gravitino_home="$PROJECT_ROOT/datapillar-gravitino/distribution/package"
    local gravitino_bin="$gravitino_home/bin/gravitino.sh"
    local gravitino_conf="$gravitino_home/conf"
    local default_gravitino_log_dir="$gravitino_home/logs"
    local stop_log="$LOG_HOME/${service_name}.stop.log"

    if [ ! -x "$gravitino_bin" ]; then
        echo -e "${YELLOW}⚠️  not found $service_name startup script，skip gravitino.sh stop${NC}"
        return 0
    fi

    echo "⏹️  stop $service_display_name (gravitino.sh stop)"
    GRAVITINO_HOME="$gravitino_home" \
    GRAVITINO_LOG_DIR="${GRAVITINO_LOG_DIR:-$default_gravitino_log_dir}" \
    "$gravitino_bin" --config "$gravitino_conf" stop >"$stop_log" 2>&1 || true
    echo -e "   ${GREEN}✅ Executed gravitino.sh stop${NC}"
}

# Stop all services
echo "📋 Stop service（Pass PID File）..."

stop_gravitino_via_script

stop_service_by_pid "datapillar-auth"
stop_service_by_pid "datapillar-studio-service"
stop_service_by_pid "datapillar-api-gateway"
stop_service_by_pid "datapillar-ai"
stop_service_by_pid "datapillar-openlineage"
stop_service_by_pid "datapillar-gravitino"

echo ""
echo "📋 Clean up residual processes（via port）..."
stop_service_by_port "datapillar-api-gateway" 7000
stop_service_by_port "datapillar-auth" 7001
stop_service_by_port "datapillar-studio-service" 7002
stop_service_by_port "datapillar-ai" 7003
stop_service_by_port "datapillar-openlineage" 7004
stop_service_by_port "datapillar-gravitino" 8090

echo ""
echo -e "${GREEN}✅ All services have been stopped${NC}"
