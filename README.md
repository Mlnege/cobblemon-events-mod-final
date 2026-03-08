# Cobblemon Events Mod (Fabric 1.21.1)

전설 레이드, 시공의 균열, 울트라 워프홀, 대탐험 등 이벤트를 서버에서 주기적으로 운영하는 모드입니다.

## 요구 사항
- Java 21
- Fabric Loader 0.16.0
- Minecraft 1.21.1
- Cobblemon Fabric `1.7.3+1.21.1` JAR

## 로컬 Cobblemon JAR 배치
이 저장소는 Cobblemon JAR를 포함하지 않습니다.
아래 경로에 직접 넣어주세요.

```text
libs/Cobblemon-fabric-1.7.3+1.21.1.jar
```

## 빌드
Windows:

```powershell
.\gradlew.bat build --no-daemon
```

Linux/macOS:

```bash
./gradlew build --no-daemon
```

## 빌드 결과물
```text
build/libs/cobblemon-events-2.1.0.jar
```

## 주요 기능
- 이벤트 스케줄 기반 자동 시작/종료
- 전설 레이드: 보스 소환 검증, 보스 5배 스케일, 참가자 자동 인식 보정
- 울트라 워프홀: 웜홀 생성/유지/정리 명령 연동
- 포켓스탑/보상 JSON 설정 기반 조정
- `/이벤트 ...`, `/event ...`, `/ce ...` 명령 지원

## 참고
- `cobblemon-explorer.json` 등 설정 파일 수정 후 서버 재시작 또는 리로드로 반영하세요.
- Cobblemon JAR는 라이선스/배포 정책에 따라 별도 관리하세요.