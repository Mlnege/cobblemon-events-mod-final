package com.cobblemonevents.ai

import com.cobblemonevents.CobblemonEventsMod
import com.google.gson.GsonBuilder

data class PromptTemplateCatalog(
    val version: Int = 1,
    val source: String = "",
    val templateCount: Int = 0,
    val templates: List<PromptTemplateEntry> = emptyList()
)

data class PromptTemplateEntry(
    val id: String = "",
    val displayName: String = "",
    val description: String = "",
    val category: String = "general",
    val mode: String = "catch",
    val targetHint: Int = 6,
    val rewardTier: Int = 1,
    val weight: Double = 1.0,
    val cooldownGroup: String = "catch",
    val themeType: String? = null,
    val targetBiome: String? = null,
    val coreMechanism: String? = null,
    val specialEncounter: String? = null,
    val sourceCategory: String? = null,
    val sourceEventName: String? = null
)

object AiPromptTemplateCatalog {
    private const val RESOURCE_PATH = "/assets/cobblemonevents/ai/prompt_template_catalog.json"
    private val gson = GsonBuilder().disableHtmlEscaping().create()

    @Volatile
    private var cachedTemplates: List<PromptTemplateEntry>? = null

    @Volatile
    private var cachedSource: String = "none"

    fun getTemplates(): List<PromptTemplateEntry> {
        cachedTemplates?.let { return it }

        synchronized(this) {
            cachedTemplates?.let { return it }

            val loaded = runCatching {
                val stream = javaClass.getResourceAsStream(RESOURCE_PATH) ?: return@runCatching emptyList()
                stream.bufferedReader(Charsets.UTF_8).use { reader ->
                    val parsed = gson.fromJson(reader, PromptTemplateCatalog::class.java)
                    cachedSource = parsed?.source ?: "unknown"
                    parsed?.templates.orEmpty()
                }
            }.onFailure {
                CobblemonEventsMod.LOGGER.warn("[AI] failed to load prompt template catalog: ${it.message}")
            }.getOrElse { emptyList() }
                .filter { it.id.isNotBlank() && it.displayName.isNotBlank() }

            cachedTemplates = loaded
            if (loaded.isNotEmpty()) {
                CobblemonEventsMod.LOGGER.info(
                    "[AI] prompt template catalog loaded: ${loaded.size} templates (source=$cachedSource)"
                )
            }
            return loaded
        }
    }

    fun templateCount(): Int = getTemplates().size

    fun sourceName(): String {
        getTemplates()
        return cachedSource
    }

    fun clearCache() {
        synchronized(this) {
            cachedTemplates = null
            cachedSource = "none"
        }
    }
}
