package com.cobblemonevents.integration

import com.cobblemonevents.CobblemonEventsMod
import com.cobblemonevents.config.GymIntegrationCommand
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.MinecraftServer

object ExternalModApiRegistry {
    data class ApiStatus(
        val modId: String,
        val displayName: String,
        val featureSummary: String,
        val loaded: Boolean
    )

    private data class ApiSpec(
        val modId: String,
        val displayName: String,
        val featureSummary: String
    )

    private val trackedSpecs = listOf(
        ApiSpec("cobblemon", "Cobblemon", "포획/배틀 이벤트 훅"),
        ApiSpec("immersive_portals", "Immersive Portals", "균열 포털/차원 이동"),
        ApiSpec("cobblemon-ultra-beasts", "Cobblemon Ultra Beasts", "울트라비스트/웜홀 연동"),
        ApiSpec("ultrabeasts", "Ultra Beasts", "웜홀 명령/차원"),
        ApiSpec("mega_showdown", "Mega Showdown", "메가 연동 보상"),
        ApiSpec("legendarymonuments", "Legendary Monuments", "전설 구조물 연동"),
        ApiSpec("cobblemontrainers", "Cobblemon Trainers", "체육관 리더/NPC 배틀"),
        ApiSpec("cobblemon_trainers", "Cobblemon Trainers", "체육관 리더/NPC 배틀")
    )

    fun getStatuses(): List<ApiStatus> {
        return trackedSpecs.map { spec ->
            ApiStatus(
                modId = spec.modId,
                displayName = spec.displayName,
                featureSummary = spec.featureSummary,
                loaded = isLoaded(spec.modId)
            )
        }
    }

    fun getLoadedStatuses(): List<ApiStatus> = getStatuses().filter { it.loaded }

    fun isLoaded(modId: String): Boolean {
        return FabricLoader.getInstance().isModLoaded(modId)
    }

    fun isAnyLoaded(modIds: Collection<String>): Boolean {
        if (modIds.isEmpty()) return true
        return modIds.any { isLoaded(it) }
    }

    fun runGymIntegrationCommands(
        server: MinecraftServer,
        commands: List<GymIntegrationCommand>,
        variables: Map<String, String>
    ): Int {
        var executed = 0
        for (command in commands) {
            if (!isAnyLoaded(command.requiredMods)) continue
            val prepared = applyVariables(command.command, variables)
            if (prepared.isBlank()) continue
            if (executeServerCommand(server, prepared)) {
                executed++
            }
        }
        return executed
    }

    fun logLoadedIntegrations() {
        val loaded = getLoadedStatuses()
        if (loaded.isEmpty()) {
            CobblemonEventsMod.LOGGER.info("[연동] 감지된 외부 모드 API 없음")
            return
        }

        val summary = loaded.joinToString { "${it.displayName}(${it.modId})" }
        CobblemonEventsMod.LOGGER.info("[연동] 활성 API: $summary")
    }

    private fun applyVariables(template: String, variables: Map<String, String>): String {
        var result = template
        for ((key, value) in variables) {
            result = result.replace("{$key}", value)
        }
        return result
    }

    private fun executeServerCommand(server: MinecraftServer, rawCommand: String): Boolean {
        val command = rawCommand.trim().removePrefix("/")
        return try {
            server.commandManager.executeWithPrefix(server.commandSource, command)
            true
        } catch (e: Exception) {
            CobblemonEventsMod.LOGGER.warn("[연동] 외부 모드 연동 명령 실패: $command", e)
            false
        }
    }
}
