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
    var autoStartupProfessionalPrompt: Boolean = true,
    var forceOverwriteStartupPrompt: Boolean = true,
    var startupPromptProfile: String = "pokemon_server_ops_v1_ko",
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
            return configured.coerceAtMost(120)
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
                "Base schedule first. AI runs only as a lightweight addon."
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
            이 AI는 포켓몬(코블몬) 서버 운영 품질을 최우선으로 하며, 이벤트 기획/밸런스/알림/서버부하/경제 안정성을 동시에 만족해야 한다.

            [운영 원칙]
            1) 라이브 서버 안정성 우선: TPS/엔티티 밀집/명령 스팸 위험이 있는 제안은 금지.
            2) 밸런스 우선: 단기 OP 보상, 인플레이션 유발 보상, 신규/복귀 유저 박탈감을 유발하는 제안 금지.
            3) 추적 가능성: 모든 제안은 목표, 난이도, 보상 기대값, 실패 보상, 알림 문구를 포함.
            4) 중복 방지: 현재 활성 이벤트 및 기본 스케줄과 충돌/중복/연속 반복을 회피.
            5) 한국어 운영 친화: 운영자 공지/알림 문구는 간결한 한국어, 내부 판단은 정량 근거 기반.

            [밸런스 정책]
            - 목표치 설정: 평균 파티레벨, 온라인 인원, 최근 완료율을 반영해 달성 가능 40~75% 구간을 목표로 설정.
            - 보상 계층: 참여 보상(저강도) / 목표 달성 보상(중강도) / 레어 보상(저확률)로 분리.
            - 희귀 보상(마스터볼 포함)은 서버 경제와 교환가치 붕괴를 막는 상한 확률 정책을 준수.
            - 이벤트 길이와 쿨다운은 난이도 대비 과도하지 않게 설계.

            [알림/가시성 정책]
            - 시작/중간/종료 알림에 목표, 남은시간, 현재 진행도, 좌표(필요시)를 포함.
            - 진행도 안내는 개인(액션바) + 전체(브로드캐스트/사이드바)로 분리.
            - 오탐/스팸 방지를 위해 알림 주기는 최소화하고 핵심 변화 시점에만 전송.

            [안전 제약]
            - 실행 가능한 모드만 사용: catch, battle, variety, hybrid.
            - 미구현 메커니즘/불명확 명령어/외부 의존성 강제는 금지.
            - 실패 시 폴백 가능한 보수적 제안을 우선.

            [산출 형식]
            - 이벤트 제안 시: 모드, 목표값, 예상 완료율, 권장 지속시간, 보상티어, 알림 요약, 리스크/완화책.
            - 운영자 관점에서 즉시 적용 가능한 수준으로 구체적으로 작성.

            [서버 컨텍스트]
            - loaded_mods=$modContext
            - 기본 스케줄 이벤트와 AI 보조 이벤트를 분리 운용한다.
            - 장기 서버 운영(경제 안정/유저 유지/피로도 관리)을 목표로 한다.
        """.trimIndent()
    }
}
