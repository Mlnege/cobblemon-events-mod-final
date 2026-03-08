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
    var profiles: MutableList<AiProfileEntry> = mutableListOf(
        AiProfileEntry(
            id = "default",
            prompt = "Prefer explorer and lucky style short events for active players.",
            enabled = true
        )
    )
)

object AiProfileRegistry {
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val configFile: File
        get() = FabricLoader.getInstance().configDir.resolve("cobblemon-events-ai.json").toFile()

    @Volatile
    private var loaded = false
    private var config: AiProfileConfig = AiProfileConfig()

    fun load() {
        synchronized(this) {
            if (loaded) return
            config = readOrCreateDefault()
            loaded = true
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
}
