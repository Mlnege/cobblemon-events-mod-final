# Cobblemon Events Mod (Fabric 1.21.1)

Cobblemon 서버용 월드 이벤트 모드입니다.

## 주요 변경점 (Temporal Rift Remake + League 확장)
- 차원의 틈: 타입별 돔 구조물을 우선 `CobbleDomes` 데이터팩 함수로 생성
- 체육관 챌린지: 타입별 돔 체육관 생성 + Generations Core 배지 지급
- 포켓몬 리그: 100/200/300 티어 입장, APS 트로피 보상
- 리그 입장 심사 NPC: 티어별 요구 배지 수 검사 후 입장 허용
- 전당 등록 방: 리그 클리어 시 전당 방 생성 + 플레이어 전당 칭호 적용
- 150레벨 이상: Legendary Monuments 보상 구간 유지

## 필수/권장 모드
- 필수
  - Java 21
  - Fabric Loader 0.16+
  - Minecraft 1.21.1
  - Cobblemon Fabric `1.7.3+1.21.1`
- 보상/연동 권장
  - Legendary Monuments
  - APS Trophies (`aps_trophies`)
  - Generations Core (`generations_core`)
  - Cobblemon Trainer Battle (`cobblemontrainerbattle`)
  - Cobblemon Ultra Beasts (`cobblemon_ultrabeast`)

## CobbleDomes 데이터팩 사용
이 레포에는 아래 데이터팩이 포함되어 있습니다.
- `datapacks/CobbleDomes_18Type_Datapack_1_21x.zip`

설치 방법:
1. 서버 월드 폴더의 `datapacks/`에 위 zip 파일을 복사
2. 서버에서 `/reload`
3. 이후 이벤트가 시작되면 모드가 자동으로 `cobbledomes:build/types/<type>` 또는 `cobbledomes:build/hub` 함수를 사용

참고:
- 데이터팩이 없으면 모드는 기존 코드 기반 구조물 빌더로 자동 폴백합니다.

## 리그/배지 규칙
- 체육관 클리어 시 타입별 `generations_core:*_badge` 지급(모드 설치 시 아이템 지급, 미설치 시 배지 기록은 저장)
- 리그 티어 요구 배지 수
  - 티어 100: 6종
  - 티어 200: 12종
  - 티어 300: 18종
- 리그 입장 시 심사 NPC가 생성되며, 배지 조건 미달이면 입장 거부
- 리그 클리어 시 전당 등록 방 생성 후 전당 칭호(prefix) 적용

## 빌드
Windows:
```powershell
.\gradlew.bat build -x test
```

Linux/macOS:
```bash
./gradlew build -x test
```

빌드 산출물:
- `build/libs/` 아래 remap jar

## Claude Agent Teams (CODE_EXPERIMENTAL_AGENT_TEAMS)
- 팀 기능 활성화 설정 파일:
  - `.claude/settings.json`
- 팀 에이전트 정의:
  - `.claude/agents/datapack-generator.md`
  - `.claude/agents/mod-generator.md`
  - `.claude/agents/qa-build-verifier.md`
  - `.claude/agents/gradle-build-engineer.md`
  - `.claude/agents/mod-api-integrator.md`
- 팀 시작 프롬프트 템플릿:
  - `.claude/TEAM_KICKOFF_PROMPT.md`

현재 설정:
- `CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1`
- teammate mode: `in-process`
