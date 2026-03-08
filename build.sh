#!/bin/bash
set -e

echo "=========================================="
echo "  코블몬 이벤트 모드 v2.1 빌드 시작"
echo "=========================================="

if [ ! -f "libs/cobblemon-fabric-1.7.3+1.21.1.jar" ]; then
    echo "❌ libs/cobblemon-fabric-1.7.3+1.21.1.jar 파일이 없습니다."
    echo "   서버/클라이언트 mods 폴더에서 Cobblemon JAR를 복사해 넣어주세요."
    exit 1
fi

if [ ! -f "gradlew" ]; then
    echo "❌ gradlew 파일이 없습니다."
    exit 1
fi

if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    echo "❌ gradle-wrapper.jar가 없습니다. gradle wrapper를 먼저 실행하세요."
    exit 1
fi

echo "[1/2] 의존성 다운로드 및 컴파일..."
./gradlew build --no-daemon

echo ""
echo "✅ 빌드 성공"
ls -la build/libs/*.jar
