@echo off
chcp 65001 >nul 2>&1
title 코블몬 이벤트 모드 v2.1 빌드

echo.
echo ==========================================
echo   코블몬 이벤트 모드 v2.1 - Windows 빌드
echo ==========================================
echo.

echo [1/4] Java 확인 중...
where java >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo Java가 설치되지 않았습니다. JDK 21을 설치하세요.
    pause
    exit /b 1
)
java -version
echo.

echo [2/4] Cobblemon JAR 확인 중...
if not exist "libs\cobblemon-fabric-1.7.3+1.21.1.jar" (
    echo.
    echo ❌ libs\cobblemon-fabric-1.7.3+1.21.1.jar 파일이 없습니다.
    echo    서버나 클라이언트 mods 폴더에서 Cobblemon JAR를 복사해 넣어주세요.
    echo.
    pause
    exit /b 1
)
echo    ✓ Cobblemon 로컬 JAR 확인 완료
echo.

echo [3/4] Gradle Wrapper 확인 중...
if not exist "gradlew.bat" (
    echo gradlew.bat 파일이 없습니다.
    pause
    exit /b 1
)
if not exist "gradle\wrapper\gradle-wrapper.jar" (
    echo gradle-wrapper.jar가 없습니다. setup.bat을 먼저 실행하세요.
    pause
    exit /b 1
)
echo    ✓ Gradle Wrapper 확인 완료
echo.

echo [4/4] 빌드 시작...
call gradlew.bat build --no-daemon

if %ERRORLEVEL% equ 0 (
    echo.
    echo ✅ 빌드 성공!
    for %%f in (build\libs\*.jar) do echo    %%f
) else (
    echo.
    echo ❌ 빌드 실패
    echo    디버그: gradlew.bat build --stacktrace --info
)

echo.
pause
