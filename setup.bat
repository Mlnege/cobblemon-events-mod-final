@echo off
chcp 65001 >nul 2>&1
title 코블몬 이벤트 모드 - 초기 설정

echo.
echo ══════════════════════════════════════════
echo   코블몬 이벤트 모드 - 초기 설정
echo ══════════════════════════════════════════
echo.
echo   이 스크립트는 빌드에 필요한 Gradle Wrapper를
echo   다운로드합니다. (최초 1회만 실행)
echo.

if not exist "gradle\wrapper" mkdir "gradle\wrapper"

echo [1/2] gradle-wrapper.jar 다운로드 중...
echo.

powershell -Command ^
    "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; " ^
    "try { " ^
    "  $url = 'https://raw.githubusercontent.com/gradle/gradle/v8.8.0/gradle/wrapper/gradle-wrapper.jar'; " ^
    "  $out = 'gradle\wrapper\gradle-wrapper.jar'; " ^
    "  Write-Host '  소스: GitHub (gradle/gradle v8.8.0)'; " ^
    "  Invoke-WebRequest -Uri $url -OutFile $out -UseBasicParsing; " ^
    "  if (Test-Path $out) { " ^
    "    $size = (Get-Item $out).Length; " ^
    "    if ($size -gt 10000) { " ^
    "      Write-Host \"  ✓ 다운로드 완료 ($size bytes)\"; " ^
    "    } else { " ^
    "      throw 'File too small'; " ^
    "    } " ^
    "  } " ^
    "} catch { " ^
    "  Write-Host '  첫 번째 소스 실패, 대체 소스 시도...'; " ^
    "  try { " ^
    "    $url2 = 'https://github.com/nicoulaj/gradle-wrapper/raw/refs/heads/master/gradle/wrapper/gradle-wrapper.jar'; " ^
    "    Invoke-WebRequest -Uri $url2 -OutFile 'gradle\wrapper\gradle-wrapper.jar' -UseBasicParsing; " ^
    "    Write-Host '  ✓ 대체 소스에서 다운로드 완료'; " ^
    "  } catch { " ^
    "    Write-Host '  ❌ 다운로드 실패'; " ^
    "    Write-Host '  수동 다운로드: https://gradle.org/releases/'; " ^
    "  } " ^
    "}"

echo.

if exist "gradle\wrapper\gradle-wrapper.jar" (
    echo [2/2] 설정 확인...
    echo.
    echo   ✓ gradle-wrapper.jar  OK
    
    if exist "gradlew.bat" (
        echo   ✓ gradlew.bat         OK
    ) else (
        echo   ❌ gradlew.bat 없음
    )
    
    if exist "build.gradle.kts" (
        echo   ✓ build.gradle.kts    OK
    ) else (
        echo   ❌ build.gradle.kts 없음
    )
    
    if exist "src\main\kotlin" (
        echo   ✓ 소스 코드            OK
    ) else (
        echo   ❌ src 폴더 없음
    )
    
    echo.
    echo ══════════════════════════════════════════
    echo   ✅ 설정 완료! 이제 build.bat을 실행하세요.
    echo ══════════════════════════════════════════
) else (
    echo ══════════════════════════════════════════
    echo   ❌ 설정 실패
    echo ══════════════════════════════════════════
    echo.
    echo   수동 설치 방법:
    echo     1. https://gradle.org/install/ 에서 Gradle 8.8 설치
    echo     2. 명령 프롬프트에서: gradle wrapper --gradle-version 8.8
    echo     3. build.bat 실행
)

echo.
pause
