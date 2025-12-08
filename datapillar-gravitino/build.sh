#!/bin/bash

cd "$(dirname "$0")"

# 设置 JDK 环境变量（支持 JDK 17 或 JDK 21）
# 优先使用 JDK 21，如果没有则使用 JDK 17
if [ -d "/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home" ]; then
    export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home"
elif [ -d "/opt/homebrew/opt/openjdk@21" ]; then
    export JAVA_HOME="/opt/homebrew/opt/openjdk@21"
elif [ -d "/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home" ]; then
    export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home"
elif [ -d "/opt/homebrew/opt/openjdk@17" ]; then
    export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
elif command -v /usr/libexec/java_home &> /dev/null; then
    # 优先查找 JDK 21
    JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null)
    if [ -z "$JAVA_HOME" ]; then
        # 如果找不到 JDK 21，尝试 JDK 17
        JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null)
    fi
    export JAVA_HOME
fi

if [ -z "$JAVA_HOME" ]; then
    echo "错误: 未找到 JDK 17 或 JDK 21"
    echo "请先安装 JDK: brew install openjdk@21"
    exit 1
fi

echo "使用 JAVA_HOME: $JAVA_HOME"
java -version

./gradlew clean assembleDistribution -x test -x javadoc