#!/bin/bash
echo
echo "Building Server Maintainer..."
echo

cd "$(dirname "$0")"

# Use Java 21 explicitly (Minecraft requires Java 21)
JAVA_HOME=""
for base in "/c/Program Files/Eclipse Adoptium/jdk-21" "/c/Program Files/Java/jdk-21" "/c/Program Files/OpenJDK/jdk-21" "/c/Program Files/Microsoft/jdk-21" "/c/Program Files/Amazon Corretto/jdk21" "/c/Program Files/Zulu/zulu-21" "/usr/lib/jvm/java-21" "/usr/lib/jvm/jdk-21"; do
    for dir in "$base"*; do
        if [ -d "$dir" ]; then export JAVA_HOME="$dir"; break 2; fi
    done
done
if [ -z "$JAVA_HOME" ]; then echo "Java 21 not found! Install a JDK 21."; exit 1; fi
export PATH="$JAVA_HOME/bin:$PATH"

# Download ProGuard if not present
if [ ! -f "tools/proguard.jar" ]; then
    echo "Downloading ProGuard..."
    mkdir -p tools
    curl -sL "https://github.com/Guardsquare/proguard/releases/download/v7.4.2/proguard-7.4.2.zip" -o tools/proguard.zip
    unzip -q tools/proguard.zip -d tools
    cp tools/proguard-7.4.2/lib/proguard.jar tools/proguard.jar
    rm -rf tools/proguard-7.4.2 tools/proguard.zip
fi

# Clean
rm -rf build
mkdir -p build/classes

# Compile (target Java 21 for ProGuard compatibility)
echo "Compiling..."
javac -g:none -d build/classes -sourcepath src src/dev/woflo/fabric/*.java
if [ $? -ne 0 ]; then echo "Compilation failed!"; exit 1; fi

# Optimize
echo "Optimizing..."
java -jar tools/proguard.jar @proguard.cfg
if [ $? -ne 0 ]; then echo "Optimization failed!"; exit 1; fi

# Package
echo "Packaging..."
cat > build/MANIFEST.MF << 'EOF'
Main-Class: dev.woflo.fabric.Main
Implementation-Title: Server Maintainer
Implementation-Version: 1.0.0
Implementation-Vendor: woflo
EOF
cd build/optimized
jar cfm "../server maintainer by woflo.jar" ../MANIFEST.MF .
cd ../..

# Show sizes
echo
echo "Size comparison:"
du -b build/classes/dev/woflo/fabric/*.class 2>/dev/null | awk '{sum+=$1} END {print "Original classes: " sum " bytes"}'
stat --printf="Final JAR: %s bytes\n" "build/server maintainer by woflo.jar" 2>/dev/null || stat -f "Final JAR: %z bytes" "build/server maintainer by woflo.jar"
echo
echo "Done!"
