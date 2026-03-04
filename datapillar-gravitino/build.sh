#!/bin/bash

cd "$(dirname "$0")"

# settings JDK environment variables（support JDK 17 or JDK 21）
# priority use JDK 21，If not then use JDK 17
if [ -d "/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home" ]; then
    export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home"
elif [ -d "/opt/homebrew/opt/openjdk@21" ]; then
    export JAVA_HOME="/opt/homebrew/opt/openjdk@21"
elif [ -d "/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home" ]; then
    export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home"
elif [ -d "/opt/homebrew/opt/openjdk@17" ]; then
    export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
elif command -v /usr/libexec/java_home &> /dev/null; then
    # Prioritize search JDK 21
    JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null)
    if [ -z "$JAVA_HOME" ]; then
        # if not found JDK 21，try JDK 17
        JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null)
    fi
    export JAVA_HOME
fi

if [ -z "$JAVA_HOME" ]; then
    echo "Error: not found JDK 17 or JDK 21"
    echo "Please install first JDK: brew install openjdk@21"
    exit 1
fi

echo "use JAVA_HOME: $JAVA_HOME"
java -version

./gradlew clean assembleDistribution -x test -x javadoc