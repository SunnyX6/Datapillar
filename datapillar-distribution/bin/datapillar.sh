#!/bin/bash
#
# Datapillar 统一启动脚本
#
# 用法:
#   ./bin/datapillar.sh start workbench-service    # 后台启动 workbench-service
#   ./bin/datapillar.sh run workbench-service      # 前台启动（Docker/K8s）
#   ./bin/datapillar.sh stop workbench-service     # 停止
#   ./bin/datapillar.sh restart workbench-service  # 重启
#   ./bin/datapillar.sh status workbench-service   # 查看状态
#
# 支持的服务: workbench-service, gateway, auth
#

set -e

# 服务配置映射
declare -A SERVICE_MAP=(
    ["workbench-service"]="com.sunny.datapillar.workbench.DatapillarWorkbenchApplication"
    ["gateway"]="com.sunny.datapillar.gateway.DatapillarGatewayApplication"
    ["auth"]="com.sunny.datapillar.auth.DatapillarAuthApplication"
)

declare -A SERVICE_PORT=(
    ["workbench-service"]="8081"
    ["gateway"]="8080"
    ["auth"]="8082"
)

# 获取脚本所在目录
BIN_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATAPILLAR_HOME="$(cd "${BIN_DIR}/.." && pwd)"

# 设置目录
DATAPILLAR_CONF_DIR="${DATAPILLAR_CONF_DIR:-${DATAPILLAR_HOME}/conf}"
DATAPILLAR_LOG_DIR="${DATAPILLAR_LOG_DIR:-${DATAPILLAR_HOME}/logs}"
DATAPILLAR_LIB_DIR="${DATAPILLAR_HOME}/libs"
DATAPILLAR_DATA_DIR="${DATAPILLAR_HOME}/data"

# 加载环境变量
if [[ -f "${DATAPILLAR_CONF_DIR}/datapillar-env.sh" ]]; then
    source "${DATAPILLAR_CONF_DIR}/datapillar-env.sh"
fi

# 默认 JVM 参数
DATAPILLAR_MEM="${DATAPILLAR_MEM:--Xms512m -Xmx1024m}"

# Java 运行器
if [[ -n "${JAVA_HOME}" ]]; then
    JAVA_RUNNER="${JAVA_HOME}/bin/java"
else
    JAVA_RUNNER="java"
fi

# 打印 Banner
print_banner() {
    local version="${DATAPILLAR_VERSION:-1.0.0}"
    echo "========================================"
    echo "  ____        _          _ _ _         "
    echo " |  _ \\  __ _| |_ __ _  | (_) | __ _ _ __ "
    echo " | | | |/ _\` | __/ _\` | | | | |/ _\` | '__|"
    echo " | |_| | (_| | || (_| | | | | | (_| | |   "
    echo " |____/ \\__,_|\\__\\__,_| |_|_|_|\\__,_|_|   "
    echo "                                         "
    echo "  Version: ${version}"
    echo "========================================"
}

# 打印用法
print_usage() {
    echo "用法: $0 {start|run|stop|restart|status} <service>"
    echo ""
    echo "服务列表:"
    echo "  workbench-service  - Workbench 服务 (端口 8081)"
    echo "  gateway    - API Gateway 服务 (端口 8080)"
    echo "  auth       - Auth 认证服务 (端口 8082)"
    echo ""
    echo "操作:"
    echo "  start      - 后台启动服务"
    echo "  run        - 前台启动服务（适用于 Docker/K8s）"
    echo "  stop       - 停止服务"
    echo "  restart    - 重启服务"
    echo "  status     - 查看服务状态"
    echo ""
    echo "示例:"
    echo "  $0 start workbench-service"
    echo "  $0 stop gateway"
    echo "  $0 status auth"
}

# 检查 Java 版本
check_java() {
    if ! command -v ${JAVA_RUNNER} &> /dev/null; then
        echo "错误: 未找到 Java，请设置 JAVA_HOME 或确保 java 在 PATH 中"
        exit 1
    fi

    local java_version=$(${JAVA_RUNNER} -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [[ "${java_version}" -lt 17 ]]; then
        echo "错误: Datapillar 需要 Java 17 或更高版本，当前版本: ${java_version}"
        exit 1
    fi
}

# 获取服务 PID
get_pid() {
    local service=$1
    local main_class=${SERVICE_MAP[$service]}
    ps aux | grep "${main_class}" | grep -v grep | awk '{print $2}' | head -1
}

# 检查服务状态
check_status() {
    local service=$1
    local pid=$(get_pid $service)

    if [[ -n "${pid}" ]]; then
        echo "Datapillar ${service} 正在运行 [PID: ${pid}]"
        return 0
    else
        echo "Datapillar ${service} 未运行"
        return 1
    fi
}

# 启动服务（后台）
start_service() {
    local service=$1
    local main_class=${SERVICE_MAP[$service]}
    local port=${SERVICE_PORT[$service]}

    if [[ -z "${main_class}" ]]; then
        echo "错误: 未知服务 '${service}'"
        print_usage
        exit 1
    fi

    local pid=$(get_pid $service)
    if [[ -n "${pid}" ]]; then
        echo "Datapillar ${service} 已在运行 [PID: ${pid}]"
        return 0
    fi

    # 创建日志目录
    mkdir -p "${DATAPILLAR_LOG_DIR}"

    # 构建 classpath
    local classpath="${DATAPILLAR_CONF_DIR}:${DATAPILLAR_LIB_DIR}/*"

    # 构建 JVM 参数
    local java_opts="${DATAPILLAR_MEM}"
    java_opts+=" -Dfile.encoding=UTF-8"
    java_opts+=" -Dlogging.file.path=${DATAPILLAR_LOG_DIR}"
    java_opts+=" -Dspring.config.location=${DATAPILLAR_CONF_DIR}/"
    java_opts+=" -Dspring.profiles.active=${service}"
    java_opts+=" -Dserver.port=${port}"

    # JDK 17+ 模块参数
    java_opts+=" --add-opens java.base/java.lang=ALL-UNNAMED"
    java_opts+=" --add-opens java.base/java.util=ALL-UNNAMED"
    java_opts+=" --add-opens java.base/java.nio=ALL-UNNAMED"

    local out_file="${DATAPILLAR_LOG_DIR}/datapillar-${service}.out"

    echo "正在启动 Datapillar ${service}..."
    nohup ${JAVA_RUNNER} ${java_opts} -cp ${classpath} ${main_class} >> "${out_file}" 2>&1 &

    local new_pid=$!
    sleep 2

    if kill -0 ${new_pid} 2>/dev/null; then
        print_banner
        echo "Datapillar ${service} 启动成功 [PID: ${new_pid}]"
        echo "日志文件: ${out_file}"
    else
        echo "错误: Datapillar ${service} 启动失败，请检查日志: ${out_file}"
        exit 1
    fi
}

# 前台运行服务（适用于 Docker/K8s）
run_service() {
    local service=$1
    local main_class=${SERVICE_MAP[$service]}
    local port=${SERVICE_PORT[$service]}

    if [[ -z "${main_class}" ]]; then
        echo "错误: 未知服务 '${service}'"
        print_usage
        exit 1
    fi

    # 创建日志目录
    mkdir -p "${DATAPILLAR_LOG_DIR}"

    # 构建 classpath
    local classpath="${DATAPILLAR_CONF_DIR}:${DATAPILLAR_LIB_DIR}/*"

    # 构建 JVM 参数
    local java_opts="${DATAPILLAR_MEM}"
    java_opts+=" -Dfile.encoding=UTF-8"
    java_opts+=" -Dlogging.file.path=${DATAPILLAR_LOG_DIR}"
    java_opts+=" -Dspring.config.location=${DATAPILLAR_CONF_DIR}/"
    java_opts+=" -Dspring.profiles.active=${service}"
    java_opts+=" -Dserver.port=${port}"

    # JDK 17+ 模块参数
    java_opts+=" --add-opens java.base/java.lang=ALL-UNNAMED"
    java_opts+=" --add-opens java.base/java.util=ALL-UNNAMED"
    java_opts+=" --add-opens java.base/java.nio=ALL-UNNAMED"

    print_banner
    echo "正在前台运行 Datapillar ${service} (端口: ${port})..."
    exec ${JAVA_RUNNER} ${java_opts} -cp ${classpath} ${main_class}
}

# 停止服务
stop_service() {
    local service=$1
    local pid=$(get_pid $service)

    if [[ -z "${pid}" ]]; then
        echo "Datapillar ${service} 未运行"
        return 0
    fi

    echo "正在停止 Datapillar ${service} [PID: ${pid}]..."

    # 优雅停止
    kill ${pid} 2>/dev/null

    # 等待进程退出
    local timeout=30
    local count=0
    while kill -0 ${pid} 2>/dev/null && [[ $count -lt $timeout ]]; do
        sleep 1
        ((count++))
    done

    # 强制停止
    if kill -0 ${pid} 2>/dev/null; then
        echo "优雅停止超时，强制停止..."
        kill -9 ${pid} 2>/dev/null
    fi

    echo "Datapillar ${service} 已停止"
}

# 重启服务
restart_service() {
    local service=$1
    stop_service $service
    sleep 2
    start_service $service
}

# 主逻辑
main() {
    local action=$1
    local service=$2

    if [[ -z "${action}" ]]; then
        print_usage
        exit 1
    fi

    # 检查 Java
    check_java

    case "${action}" in
        start)
            if [[ -z "${service}" ]]; then
                echo "错误: 请指定服务名称"
                print_usage
                exit 1
            fi
            start_service $service
            ;;
        run)
            if [[ -z "${service}" ]]; then
                echo "错误: 请指定服务名称"
                print_usage
                exit 1
            fi
            run_service $service
            ;;
        stop)
            if [[ -z "${service}" ]]; then
                echo "错误: 请指定服务名称"
                print_usage
                exit 1
            fi
            stop_service $service
            ;;
        restart)
            if [[ -z "${service}" ]]; then
                echo "错误: 请指定服务名称"
                print_usage
                exit 1
            fi
            restart_service $service
            ;;
        status)
            if [[ -z "${service}" ]]; then
                echo "错误: 请指定服务名称"
                print_usage
                exit 1
            fi
            check_status $service
            ;;
        *)
            echo "错误: 未知操作 '${action}'"
            print_usage
            exit 1
            ;;
    esac
}

main "$@"
