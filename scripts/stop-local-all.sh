#!/bin/bash

# Datapillar 本地调试一键停止脚本
# 作者: Sunny
# 版本: 1.0.0

echo "🛑 Datapillar 本地服务停止中..."
echo ""

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# 停止服务（通过 PID 文件）
stop_service_by_pid() {
    local service_name=$1
    local pid_file="/tmp/${service_name}.pid"

    if [ -f "$pid_file" ]; then
        local pid=$(cat "$pid_file")
        if kill -0 $pid 2>/dev/null; then
            echo "⏹️  停止 $service_name (PID: $pid)"
            kill $pid 2>/dev/null || true
            # 等待进程结束
            for i in {1..10}; do
                if ! kill -0 $pid 2>/dev/null; then
                    break
                fi
                sleep 1
            done
            # 如果还没结束，强制杀掉
            if kill -0 $pid 2>/dev/null; then
                kill -9 $pid 2>/dev/null || true
            fi
            rm -f "$pid_file"
            echo -e "   ${GREEN}✅ 已停止${NC}"
        else
            echo -e "${YELLOW}⚠️  $service_name 进程不存在 (PID: $pid)${NC}"
            rm -f "$pid_file"
        fi
    else
        echo -e "${YELLOW}⚠️  $service_name PID 文件不存在${NC}"
    fi
}

# 通过端口停止服务
stop_service_by_port() {
    local service_name=$1
    local port=$2

    local pid=$(lsof -ti :$port 2>/dev/null | head -1)
    if [ -n "$pid" ]; then
        echo "⏹️  停止 $service_name (端口: $port, PID: $pid)"
        kill $pid 2>/dev/null || true
        sleep 2
        # 如果还没结束，强制杀掉
        if kill -0 $pid 2>/dev/null; then
            kill -9 $pid 2>/dev/null || true
        fi
        echo -e "   ${GREEN}✅ 已停止${NC}"
    fi
}

# 停止所有服务
echo "📋 停止服务（通过 PID 文件）..."

stop_service_by_pid "datapillar-auth"
stop_service_by_pid "datapillar-studio-service"
stop_service_by_pid "datapillar-api-gateway"
stop_service_by_pid "datapillar-ai"

echo ""
echo "📋 清理残留进程（通过端口）..."


echo ""
echo -e "${GREEN}✅ 所有服务已停止${NC}"
