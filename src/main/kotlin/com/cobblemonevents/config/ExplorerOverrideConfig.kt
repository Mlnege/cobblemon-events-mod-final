package com.cobblemonevents.config

import com.cobblemonevents.CobblemonEventsMod
import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.io.File

/**
 * 탐험(Explorer) 이벤트 전용 오버라이드 JSON
 * config/cobblemon-explorer.json
 */
data class ExplorerOverrideConfig(
    val enabled: Boolean = false,
    val intervalMinutes: Int? = 30,
    val durationMinutes: Int? = 15,
    val stopRewards: RewardPool = RewardPool(
        items = listOf(
            ItemRewardEntry("cobblemon:ultra_ball", 5),
            ItemRewardEntry("cobblemon:rare_candy", 1),
            ItemRewardEntry("cobblemon:exp_candy_l", 3)
        ),
        pokemon = listOf(
            PokemonRewardEntry("eevee", 15, shinyChance = 0.08)
        ),
        rewardMode = "RANDOM_MULTI",
        randomCount = 2,
        broadcastReward = true
    )
) {
    companion object {
        private val GSON = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
        private val CONFIG_FILE: File
            get() = FabricLoader.getInstance().configDir.resolve("cobblemon-explorer.json").toFile()
        private val LOCAL_FILE: File
            get() = File("cobblemon-explorer.json")

        @Volatile
        private var cachedConfig: ExplorerOverrideConfig? = null

        @Volatile
        private var cachedLastModified: Long = -1L
        @Volatile
        private var cachedPath: String = ""

        fun current(): ExplorerOverrideConfig {
            return try {
                val file = resolveFile()
                if (!file.exists()) {
                    createDefaultFile()
                }

                val lastModified = file.lastModified()
                val cached = cachedConfig
                if (cached != null && lastModified == cachedLastModified && file.absolutePath == cachedPath) {
                    return cached
                }

                val parsed = GSON.fromJson(file.readText(), ExplorerOverrideConfig::class.java)
                    ?: ExplorerOverrideConfig()

                cachedConfig = parsed
                cachedLastModified = lastModified
                cachedPath = file.absolutePath
                parsed
            } catch (e: Exception) {
                CobblemonEventsMod.LOGGER.error("[ExplorerOverride] cobblemon-explorer.json 로드 실패", e)
                ExplorerOverrideConfig()
            }
        }

        private fun resolveFile(): File {
            return if (LOCAL_FILE.exists()) LOCAL_FILE else CONFIG_FILE
        }

        private fun createDefaultFile() {
            try {
                val file = resolveFile()
                file.parentFile?.mkdirs()
                file.writeText(GSON.toJson(ExplorerOverrideConfig()))
                CobblemonEventsMod.LOGGER.info("[ExplorerOverride] 기본 파일 생성: ${file.absolutePath}")
            } catch (e: Exception) {
                CobblemonEventsMod.LOGGER.error("[ExplorerOverride] 기본 파일 생성 실패", e)
            }
        }
    }
}
