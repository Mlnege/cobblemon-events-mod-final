package com.cobblemonevents.ai

import com.cobblemonevents.CobblemonEventsMod
import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.io.File
import kotlin.random.Random

data class AiProfileEntry(
    val id: String = "default",
    var prompt: String = "Create lightweight addon world events without overlapping base events.",
    var enabled: Boolean = true,
    var createdAt: Long = System.currentTimeMillis()
)

data class AiProfileConfig(
    var conceptPrompt: String = "Base schedule first. AI runs only as a lightweight addon.",
    var dynamicEventDurationMinutes: Int = 5,
    var autoPlanIntervalMinutesMin: Int = 15,
    var autoPlanIntervalMinutesMax: Int = 30,
    var autoStartupProfessionalPrompt: Boolean = true,
    var forceOverwriteStartupPrompt: Boolean = true,
    var startupPromptProfile: String = "pokemon_server_ops_v1_ko",
    var promptTemplateCatalogEnabled: Boolean = true,
    var promptTemplateCatalogBatchSize: Int = 80,
    var advisor: AiExternalAdvisorConfig = AiExternalAdvisorConfig(),
    var profiles: MutableList<AiProfileEntry> = mutableListOf(
        AiProfileEntry(
            id = "default",
            prompt = "Prefer explorer and lucky style short events for active players.",
            enabled = true
        )
    )
)

data class AiExternalAdvisorConfig(
    var enabled: Boolean = false,
    var endpoint: String = "",
    var bearerToken: String = "",
    var model: String = "",
    var timeoutMs: Int = 2500
)

object AiProfileRegistry {
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val configFile: File
        get() = FabricLoader.getInstance().configDir.resolve("cobblemon-events-ai.json").toFile()

    @Volatile
    private var loaded = false
    private var config: AiProfileConfig = AiProfileConfig()

    fun configPathFile(): File = configFile

    fun load() {
        synchronized(this) {
            if (loaded) return
            config = readOrCreateDefault()
            loaded = true
        }
    }

    fun reloadFromDisk(): Boolean {
        synchronized(this) {
            return try {
                config = readOrCreateDefault()
                loaded = true
                true
            } catch (e: Exception) {
                CobblemonEventsMod.LOGGER.error("[AI] Failed to reload AI profile config.", e)
                false
            }
        }
    }

    fun save() {
        synchronized(this) {
            ensureLoaded()
            try {
                configFile.parentFile.mkdirs()
                configFile.writeText(gson.toJson(config))
            } catch (e: Exception) {
                CobblemonEventsMod.LOGGER.error("[AI] Failed to save AI profile config.", e)
            }
        }
    }

    fun getConceptPrompt(): String {
        synchronized(this) {
            ensureLoaded()
            return config.conceptPrompt
        }
    }

    fun getDynamicEventDurationMinutes(): Int {
        synchronized(this) {
            ensureLoaded()
            val configured = config.dynamicEventDurationMinutes
            if (configured <= 0) return 5
            return configured.coerceAtMost(20)
        }
    }

    fun getAutoPlanIntervalRangeMinutes(): Pair<Int, Int> {
        synchronized(this) {
            ensureLoaded()
            val min = config.autoPlanIntervalMinutesMin.coerceIn(1, 180)
            val max = config.autoPlanIntervalMinutesMax.coerceIn(1, 180)
            return if (min <= max) {
                min to max
            } else {
                max to min
            }
        }
    }

    fun setAutoPlanIntervalFixed(minutes: Int) {
        synchronized(this) {
            ensureLoaded()
            val safe = minutes.coerceIn(1, 180)
            config.autoPlanIntervalMinutesMin = safe
            config.autoPlanIntervalMinutesMax = safe
            save()
        }
    }

    fun setAutoPlanIntervalRange(minMinutes: Int, maxMinutes: Int) {
        synchronized(this) {
            ensureLoaded()
            val a = minMinutes.coerceIn(1, 180)
            val b = maxMinutes.coerceIn(1, 180)
            config.autoPlanIntervalMinutesMin = minOf(a, b)
            config.autoPlanIntervalMinutesMax = maxOf(a, b)
            save()
        }
    }

    fun isPromptTemplateCatalogEnabled(): Boolean {
        synchronized(this) {
            ensureLoaded()
            return config.promptTemplateCatalogEnabled
        }
    }

    fun getPromptTemplateCatalogBatchSize(): Int {
        synchronized(this) {
            ensureLoaded()
            return config.promptTemplateCatalogBatchSize.coerceIn(20, 120)
        }
    }

    fun applyStartupProfessionalPrompt() {
        synchronized(this) {
            ensureLoaded()
            if (!config.autoStartupProfessionalPrompt) return

            val generated = buildStartupProfessionalPrompt()
            val shouldApply = config.forceOverwriteStartupPrompt || config.conceptPrompt.isBlank()
            if (!shouldApply) return

            if (config.conceptPrompt != generated) {
                config.conceptPrompt = generated
                save()
                CobblemonEventsMod.LOGGER.info("[AI] startup professional prompt applied (${config.startupPromptProfile})")
            }
        }
    }

    fun setConceptPrompt(prompt: String) {
        synchronized(this) {
            ensureLoaded()
            config.conceptPrompt = prompt.trim().ifBlank {
                buildStartupProfessionalPrompt()
            }
            save()
        }
    }

    fun listProfiles(): List<AiProfileEntry> {
        synchronized(this) {
            ensureLoaded()
            return config.profiles.map { it.copy() }
        }
    }

    fun getAdvisorConfig(): AiExternalAdvisorConfig {
        synchronized(this) {
            ensureLoaded()
            return config.advisor.copy()
        }
    }

    fun setAdvisorEnabled(enabled: Boolean) {
        synchronized(this) {
            ensureLoaded()
            config.advisor.enabled = enabled
            save()
        }
    }

    fun setAdvisorEndpoint(endpoint: String) {
        synchronized(this) {
            ensureLoaded()
            config.advisor.endpoint = endpoint.trim()
            save()
        }
    }

    fun setAdvisorToken(token: String) {
        synchronized(this) {
            ensureLoaded()
            config.advisor.bearerToken = token.trim()
            save()
        }
    }

    fun setAdvisorModel(model: String) {
        synchronized(this) {
            ensureLoaded()
            config.advisor.model = model.trim()
            save()
        }
    }

    fun setAdvisorTimeoutMs(timeoutMs: Int) {
        synchronized(this) {
            ensureLoaded()
            config.advisor.timeoutMs = timeoutMs.coerceIn(500, 15000)
            save()
        }
    }

    fun registerProfile(id: String, prompt: String): Boolean {
        synchronized(this) {
            ensureLoaded()
            val normalizedId = id.trim().lowercase()
            if (normalizedId.isBlank()) return false
            if (config.profiles.any { it.id.equals(normalizedId, ignoreCase = true) }) return false

            val cleanedPrompt = prompt.trim()
            if (cleanedPrompt.isBlank()) return false

            config.profiles.add(
                AiProfileEntry(
                    id = normalizedId,
                    prompt = cleanedPrompt,
                    enabled = true,
                    createdAt = System.currentTimeMillis()
                )
            )
            save()
            return true
        }
    }

    fun removeProfile(id: String): Boolean {
        synchronized(this) {
            ensureLoaded()
            val removed = config.profiles.removeIf { it.id.equals(id, ignoreCase = true) }
            if (removed) save()
            return removed
        }
    }

    fun setEnabled(id: String, enabled: Boolean): Boolean {
        synchronized(this) {
            ensureLoaded()
            val profile = config.profiles.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: return false
            profile.enabled = enabled
            save()
            return true
        }
    }

    fun pickEnabledProfile(): AiProfileEntry? {
        synchronized(this) {
            ensureLoaded()
            val enabledProfiles = config.profiles.filter { it.enabled }
            if (enabledProfiles.isEmpty()) return null
            return enabledProfiles[Random.nextInt(enabledProfiles.size)].copy()
        }
    }

    private fun ensureLoaded() {
        if (!loaded) {
            config = readOrCreateDefault()
            loaded = true
        }
    }

    private fun readOrCreateDefault(): AiProfileConfig {
        return try {
            if (configFile.exists()) {
                gson.fromJson(configFile.readText(), AiProfileConfig::class.java) ?: createDefaultConfig()
            } else {
                createDefaultConfig().also { writeDefault(it) }
            }
        } catch (e: Exception) {
            CobblemonEventsMod.LOGGER.error("[AI] Failed to load AI profile config, fallback to default.", e)
            createDefaultConfig()
        }
    }

    private fun createDefaultConfig(): AiProfileConfig = AiProfileConfig()

    private fun writeDefault(defaultConfig: AiProfileConfig) {
        try {
            configFile.parentFile.mkdirs()
            configFile.writeText(gson.toJson(defaultConfig))
        } catch (e: Exception) {
            CobblemonEventsMod.LOGGER.error("[AI] Failed to write default AI profile config.", e)
        }
    }

    private fun buildStartupProfessionalPrompt(): String {
        val loader = FabricLoader.getInstance()
        val loadedMods = listOf(
            "cobblemon",
            "tim_core",
            "cobblemonraiddens",
            "luckperms",
            "placeholderapi",
            "spark"
        ).filter { loader.isModLoaded(it) }

        val modContext = if (loadedMods.isEmpty()) "none" else loadedMods.joinToString(",")
        return """
            [시스템 목적]
            이 AI는 포켓몬(코블몬) 서버의 운영 품질을 최우선으로 유지한다.
            AI는 다음 요소를 동시에 고려한다: 이벤트 기획, 서버 밸런스 유지, 공지 및 알림 관리, 서버 성능 보호, 경제 시스템 안정성.
            모든 제안은 실제 라이브 서버 환경에서 안정적으로 작동해야 한다.

            [운영 원칙]
            1. 서버 안정성 최우선
            - 금지: TPS 저하, 엔티티 과도 생성, 반복 명령어 스팸, 무한 루프 이벤트, 과도한 월드 스캔
            - 서버 성능에 위험이 있는 구조는 절대 제안하지 않는다.

            2. 밸런스 우선
            - 금지: 단기적인 OP 보상, 경제 인플레이션 유발 보상, 신규/복귀 유저 박탈감 유발, 특정 플레이어 독점 구조
            - 보상 기준: 노력 대비 합리성, 장기 플레이 유도, 경제 안정성 유지

            [이벤트 설계 규칙]
            - 목표 행동 횟수: 4~10회
            - 이벤트 발생 주기: 15~30분 랜덤
            - 최대 지속 시간: 20분
            - 서버 동시 이벤트 수: 1개
            - 이벤트 중복 실행 금지
            - 이벤트 종료 후 쿨다운 적용

            [전설 포켓몬 이벤트 종료 규칙]
            다음 조건 중 하나라도 만족하면 즉시 종료:
            - 포켓몬 포획
            - 포켓몬 쓰러짐
            - 이벤트 시간 초과
            종료 시 서버 전체 알림 + 쿨다운 적용

            [알림 시스템]
            이벤트 시작 시 최소 하나 이상 사용:
            - 채팅 알림 / 타이틀 / 보스바 / 사운드
            알림 스팸 금지

            [경제 보호 규칙]
            금지 보상:
            - 대량 희귀 아이템
            - 무제한 반복 보상
            - 경제 붕괴 가능 보상
            보상 우선순위:
            1) 코스메틱
            2) 장식 아이템
            3) 제한 재화
            4) 낮은 확률 희귀 아이템

            [TPS 최적화 이벤트 시스템]
            1) 엔티티 제한: 이벤트 생성 포켓몬 최대 10마리
            2) 이벤트 위치: 플레이어 반경 300~500블록, 로딩된 청크만 사용
            3) 우선 트리거: 포켓몬 포획, 포켓몬 처치, 특정 바이옴 진입, 플레이어 위치 기반
            4) TPS 보호:
            - TPS < 17: 이벤트 생성 중단 + 스폰 이벤트 비활성화

            [이벤트 난이도 자동 조정]
            난이도 단계: 쉬움, 보통, 어려움, 매우 어려움 (기본: 보통)
            조정 기준: 참여 인원, 최근 성공률, 평균 완료 시간, 서버 TPS 상태
            행동 횟수 범위:
            - 쉬움 4~5
            - 보통 6~7
            - 어려움 8~9
            - 매우 어려움 10

            [플레이어 수 기반 스케일링]
            참여 인원:
            - 1~3 소규모
            - 4~7 중규모
            - 8~12 대규모
            - 13+ 초과밀
            조정 대상: 목표 횟수, 단서 수, 보스 체력, 협동 목표
            제한: 목표 4~10, 보스 체력 증가는 최대 25%

            [TPS 기반 이벤트 자동 제한]
            TPS 구간:
            - 안전 19 이상
            - 주의 18~19
            - 경고 17~18
            - 위험 17 미만
            경고 이상: 고부하 이벤트 제한 + 레이드 이벤트 중단
            위험: 신규 이벤트 전면 중단

            [핵심 월드 이벤트]
            - 포켓몬 대이동: 특정 포켓몬 스폰 증가, 목표 6~10 포획
            - 전설 포켓몬 추적: 단서 기반 위치 탐색, 포획/쓰러짐 시 종료
            - 월드 보스 레이드: 강력한 포켓몬, 협동 전투 필요
            - 타입 대폭주: 특정 타입 포켓몬 대량 등장

            [이벤트 발생 확률]
            - 일반 이벤트 55%
            - 포켓몬 대이동 20%
            - 월드 레이드 15%
            - 전설 추적 5%
            - 타입 대폭주 5%

            [이벤트 데이터베이스]
            - 총 이벤트 수: 150개
            - 유형: 포획 / 전투 / 탐험 / 연구 / 협동 / 특별

            [AI 이벤트 응답 형식]
            반드시 아래 순서로 작성:
            1) 이벤트 이름
            2) 이벤트 설명
            3) 발생 조건
            4) 진행 방식
            5) 목표 행동 횟수
            6) 종료 조건
            7) 보상 구조
            8) 서버 성능 영향
            9) 밸런스 검토

            [실행 안전 제약]
            - 미구현 메커니즘, 불명확 명령어, 전역 스캔, 반복 스팸은 금지
            - 실제 적용 가능한 제안만 허용
            - 가능하면 보수적으로 제안하고, 위험 시 즉시 제한/중단 기준을 제시

            [서버 컨텍스트]
            - loaded_mods=$modContext
            - 기본 스케줄 이벤트와 AI 보조 이벤트는 겹치지 않게 분리 운용
            - 장기 운영(경제 안정/유저 유지/피로도 관리)을 최우선
        """.trimIndent()
    }
}
