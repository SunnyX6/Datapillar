#!/bin/bash

# AI服务重启脚本 - 停止旧服务并启动新服务
# 使用方式：
#   ./restart.sh

cd "$(dirname "$0")"

echo "========================================="
echo "重启AI服务 (端口: 5000)"
echo "========================================="

# 1. 停止旧服务
echo "停止旧的AI服务..."
lsof -ti:5000 | xargs kill -9 2>/dev/null
sleep 2

# 2. 清理Python缓存
echo "清理Python缓存(__pycache__/*.pyc)..."
find . -name "__pycache__" -type d -exec rm -rf {} + 2>/dev/null
find . -name "*.pyc" -delete 2>/dev/null

# 3. 激活虚拟环境并启动服务
echo "启动AI服务..."
source .venv/bin/activate

python -m uvicorn src.app:app --host 0.0.0.0 --port 5000 --reload 