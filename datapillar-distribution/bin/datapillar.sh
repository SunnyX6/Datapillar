#!/bin/bash
#
# Datapillar Unified startup script
#
# Usage:
#   ./bin/datapillar.sh start studio-service    # Start in background studio-service
#   ./bin/datapillar.sh run studio-service      # Start in foreground（Docker/K8s）
#   ./bin/datapillar.sh stop studio-service     # stop
#   ./bin/datapillar.sh restart studio-service  # Restart
#   ./bin/datapillar.sh status studio-service   # View status
#
# Supported services: studio-service, gateway, auth, openlineage
#

set -e

# Service configuration mapping
declare -A SERVICE_MAP=(
    ["studio-service"]="com.sunny.datapillar.studio.DatapillarStudioApplication"
    ["gateway"]="com.sunny.datapillar.gateway.DatapillarGatewayApplication"
    ["auth"]="com.sunny.datapillar.auth.DatapillarAuthApplication"
    ["openlineage"]="com.sunny.datapillar.openlineage.DatapillarOpenLineageApplication"
)

declare -A SERVICE_PORT=(
    ["studio-service"]="8081"
    ["gateway"]="8080"
    ["auth"]="8082"
    ["openlineage"]="8083"
)

# Get the directory where the script is located
BIN_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATAPILLAR_HOME="$(cd "${BIN_DIR}/.." && pwd)"

# Set directory
DATAPILLAR_CONF_DIR="${DATAPILLAR_CONF_DIR:-${DATAPILLAR_HOME}/conf}"
DATAPILLAR_LOG_DIR="${DATAPILLAR_LOG_DIR:-${DATAPILLAR_HOME}/logs}"
DATAPILLAR_LIB_DIR="${DATAPILLAR_HOME}/libs"
DATAPILLAR_DATA_DIR="${DATAPILLAR_HOME}/data"

# Load environment variables
if [[ -f "${DATAPILLAR_CONF_DIR}/datapillar-env.sh" ]]; then
    source "${DATAPILLAR_CONF_DIR}/datapillar-env.sh"
fi

# Default JVM parameters
DATAPILLAR_MEM="${DATAPILLAR_MEM:--Xms512m -Xmx1024m}"

# Java runner
if [[ -n "${JAVA_HOME}" ]]; then
    JAVA_RUNNER="${JAVA_HOME}/bin/java"
else
    JAVA_RUNNER="java"
fi

# Print Banner
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

# Print usage
print_usage() {
    echo "Usage: $0 {start|run|stop|restart|status} <service>"
    echo ""
    echo "Service list:"
    echo "  studio-service  - Studio service (port 8081)"
    echo "  gateway    - API Gateway service (port 8080)"
    echo "  auth       - Auth Authentication services (port 8082)"
    echo "  openlineage - OpenLineage Sink service (port 8083)"
    echo ""
    echo "Operation:"
    echo "  start      - Start service in background"
    echo "  run        - Start service in front desk（Applicable to Docker/K8s）"
    echo "  stop       - Stop service"
    echo "  restart    - Restart service"
    echo "  status     - Check service status"
    echo ""
    echo "Example:"
    echo "  $0 start studio-service"
    echo "  $0 stop gateway"
    echo "  $0 status auth"
    echo "  $0 start openlineage"
}

# Check Java version
check_java() {
    if ! command -v ${JAVA_RUNNER} &> /dev/null; then
        echo "Error: not found Java，Please set JAVA_HOME or ensure java in PATH in"
        exit 1
    fi

    local java_version=$(${JAVA_RUNNER} -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [[ "${java_version}" -lt 17 ]]; then
        echo "Error: Datapillar need Java 17 or higher，Current version: ${java_version}"
        exit 1
    fi
}

# Get services PID
get_pid() {
    local service=$1
    local main_class=${SERVICE_MAP[$service]}
    ps aux | grep "${main_class}" | grep -v grep | awk '{print $2}' | head -1
}

# Check service status
check_status() {
    local service=$1
    local pid=$(get_pid $service)

    if [[ -n "${pid}" ]]; then
        echo "Datapillar ${service} Running [PID: ${pid}]"
        return 0
    else
        echo "Datapillar ${service} Not running"
        return 1
    fi
}

# Start service（Backstage）
start_service() {
    local service=$1
    local main_class=${SERVICE_MAP[$service]}
    local port=${SERVICE_PORT[$service]}

    if [[ -z "${main_class}" ]]; then
        echo "Error: Unknown service '${service}'"
        print_usage
        exit 1
    fi

    local pid=$(get_pid $service)
    if [[ -n "${pid}" ]]; then
        echo "Datapillar ${service} Already running [PID: ${pid}]"
        return 0
    fi

    # Create log directory
    mkdir -p "${DATAPILLAR_LOG_DIR}"

    # Build classpath
    local classpath="${DATAPILLAR_CONF_DIR}:${DATAPILLAR_LIB_DIR}/*"

    # Build JVM parameters
    local java_opts="${DATAPILLAR_MEM}"
    java_opts+=" -Dfile.encoding=UTF-8"
    java_opts+=" -Dlogging.file.path=${DATAPILLAR_LOG_DIR}"
    java_opts+=" -Dspring.config.location=${DATAPILLAR_CONF_DIR}/"
    java_opts+=" -Dspring.profiles.active=${service}"
    java_opts+=" -Dserver.port=${port}"

    # JDK 17+ Module parameters
    java_opts+=" --add-opens java.base/java.lang=ALL-UNNAMED"
    java_opts+=" --add-opens java.base/java.util=ALL-UNNAMED"
    java_opts+=" --add-opens java.base/java.nio=ALL-UNNAMED"

    local out_file="${DATAPILLAR_LOG_DIR}/datapillar-${service}.out"

    echo "Starting Datapillar ${service}..."
    nohup ${JAVA_RUNNER} ${java_opts} -cp ${classpath} ${main_class} >> "${out_file}" 2>&1 &

    local new_pid=$!
    sleep 2

    if kill -0 ${new_pid} 2>/dev/null; then
        print_banner
        echo "Datapillar ${service} Started successfully [PID: ${new_pid}]"
        echo "log file: ${out_file}"
    else
        echo "Error: Datapillar ${service} Startup failed，Please check the logs: ${out_file}"
        exit 1
    fi
}

# Front-end service（Applicable to Docker/K8s）
run_service() {
    local service=$1
    local main_class=${SERVICE_MAP[$service]}
    local port=${SERVICE_PORT[$service]}

    if [[ -z "${main_class}" ]]; then
        echo "Error: Unknown service '${service}'"
        print_usage
        exit 1
    fi

    # Create log directory
    mkdir -p "${DATAPILLAR_LOG_DIR}"

    # Build classpath
    local classpath="${DATAPILLAR_CONF_DIR}:${DATAPILLAR_LIB_DIR}/*"

    # Build JVM parameters
    local java_opts="${DATAPILLAR_MEM}"
    java_opts+=" -Dfile.encoding=UTF-8"
    java_opts+=" -Dlogging.file.path=${DATAPILLAR_LOG_DIR}"
    java_opts+=" -Dspring.config.location=${DATAPILLAR_CONF_DIR}/"
    java_opts+=" -Dspring.profiles.active=${service}"
    java_opts+=" -Dserver.port=${port}"

    # JDK 17+ Module parameters
    java_opts+=" --add-opens java.base/java.lang=ALL-UNNAMED"
    java_opts+=" --add-opens java.base/java.util=ALL-UNNAMED"
    java_opts+=" --add-opens java.base/java.nio=ALL-UNNAMED"

    print_banner
    echo "Running in the foreground Datapillar ${service} (port: ${port})..."
    exec ${JAVA_RUNNER} ${java_opts} -cp ${classpath} ${main_class}
}

# Stop service
stop_service() {
    local service=$1
    local pid=$(get_pid $service)

    if [[ -z "${pid}" ]]; then
        echo "Datapillar ${service} Not running"
        return 0
    fi

    echo "Stopping Datapillar ${service} [PID: ${pid}]..."

    # graceful stop
    kill ${pid} 2>/dev/null

    # Wait for process to exit
    local timeout=30
    local count=0
    while kill -0 ${pid} 2>/dev/null && [[ $count -lt $timeout ]]; do
        sleep 1
        ((count++))
    done

    # Forced stop
    if kill -0 ${pid} 2>/dev/null; then
        echo "graceful stop timeout，Forced stop..."
        kill -9 ${pid} 2>/dev/null
    fi

    echo "Datapillar ${service} Stopped"
}

# Restart service
restart_service() {
    local service=$1
    stop_service $service
    sleep 2
    start_service $service
}

# main logic
main() {
    local action=$1
    local service=$2

    if [[ -z "${action}" ]]; then
        print_usage
        exit 1
    fi

    # Check Java
    check_java

    case "${action}" in
        start)
            if [[ -z "${service}" ]]; then
                echo "Error: Please specify service name"
                print_usage
                exit 1
            fi
            start_service $service
            ;;
        run)
            if [[ -z "${service}" ]]; then
                echo "Error: Please specify service name"
                print_usage
                exit 1
            fi
            run_service $service
            ;;
        stop)
            if [[ -z "${service}" ]]; then
                echo "Error: Please specify service name"
                print_usage
                exit 1
            fi
            stop_service $service
            ;;
        restart)
            if [[ -z "${service}" ]]; then
                echo "Error: Please specify service name"
                print_usage
                exit 1
            fi
            restart_service $service
            ;;
        status)
            if [[ -z "${service}" ]]; then
                echo "Error: Please specify service name"
                print_usage
                exit 1
            fi
            check_status $service
            ;;
        *)
            echo "Error: Unknown operation '${action}'"
            print_usage
            exit 1
            ;;
    esac
}

main "$@"
