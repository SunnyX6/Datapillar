#!/bin/bash

# Datapillar 本地调试一键启动脚本
# 作者: Sunny
# 版本: 1.0.0

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

# 第一步：编译整个项目
echo "📦 编译项目中..."
cd "$PROJECT_ROOT"
mvn clean package -DskipTests
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

    # 启动服务（不重定向日志，由服务自己管理）
    nohup java -jar -Dfile.encoding=UTF-8 -DLOG_HOME="$LOG_HOME" "$jar_path" > /dev/null 2>&1 &
    local pid=$!
    echo "$pid" > /tmp/${service_name}.pid

    echo -e "   ${GREEN}✅ $service_name 启动中 (PID: $pid)${NC}"
}

# 启动 Python AI 服务
start_ai_service() {
    # 从 .env 读取端口配置，默认 6003
    cd "$PROJECT_ROOT/datapillar-ai"
    if [ -f ".env" ]; then
        AI_PORT=$(grep "^APP_PORT=" .env | cut -d'=' -f2)
        AI_PORT=${AI_PORT:-6003}
    else
        AI_PORT=6003
    fi

    echo "▶️  启动 datapillar-ai (端口: $AI_PORT)"

    if ! check_port $AI_PORT "datapillar-ai"; then
        echo -e "   ${YELLOW}跳过启动，端口已占用${NC}"
        cd "$PROJECT_ROOT"
        return 0
    fi

    # 使用 uv run 启动服务
    LOG_HOME="$LOG_HOME" nohup uv run uvicorn src.app:app --host 0.0.0.0 --port $AI_PORT > /dev/null 2>&1 &
    echo $! > /tmp/datapillar-ai.pid

    echo -e "   ${GREEN}✅ datapillar-ai 启动中 (PID: $(cat /tmp/datapillar-ai.pid))${NC}"

    cd "$PROJECT_ROOT"
}

echo "🚀 启动服务..."
echo ""

# 1. 启动认证服务
start_java_service "datapillar-auth" \
    "$PROJECT_ROOT/datapillar-auth/target/datapillar-auth-1.0.0.jar" 6001

# 2. 启动核心业务服务
start_java_service "datapillar-web-admin" \
    "$PROJECT_ROOT/datapillar-web-admin/target/datapillar-web-admin-1.0.0.jar" 6002

# 3. 启动 API 网关
start_java_service "datapillar-api-gateway" \
    "$PROJECT_ROOT/datapillar-api-gateway/target/datapillar-api-gateway-1.0.0.jar" 6000

# 4. 启动 AI 服务
start_ai_service

# 5. 启动 datapillar-job-admin
start_java_service "datapillar-job-admin" \
    "$PROJECT_ROOT/datapillar-job/datapillar-job-admin/target/datapillar-job-admin-1.0.0.jar" 6004

# 6. 启动 datapillar-job-executor
start_java_service "datapillar-job-executor" \
    "$PROJECT_ROOT/datapillar-job/datapillar-job-executor/target/datapillar-job-executor-1.0.0.jar" 6005

echo ""
echo "=========================================="
echo -e "${GREEN}✅ 所有服务启动命令已执行！${NC}"
echo "=========================================="
echo ""
echo "📋 服务列表："
echo "   • API 网关:           http://localhost:6000"
echo "   • 认证服务:           http://localhost:6001"
echo "   • 核心业务:           http://localhost:6002"
echo "   • AI 服务:            http://localhost:6003"
echo "   • Job Admin:          http://localhost:6004"
echo "   • Job Executor:       http://localhost:6005"
echo ""
echo "📝 日志目录: $LOG_HOME"
echo "   tail -f $LOG_HOME/datapillar-*.log"
echo ""
echo "🛑 停止服务: ./scripts/stop-local-all.sh"
echo ""
