package com.cobblemonevents.integration

import com.cobblemonevents.CobblemonEventsMod
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos

object DatapackStructureService {
    private const val COBBLEDOMES_NAMESPACE = "cobbledomes"
    private const val DOME_FUNCTION_PREFIX = "build/types"
    private const val HUB_FUNCTION_PATH = "build/hub"
    private const val STRUCTURE_Y_OFFSET = 12

    private val supportedDomeTypes = setOf(
        "fire", "water", "grass", "electric", "ice", "rock", "psychic", "ghost", "dragon",
        "fairy", "dark", "steel", "ground", "bug", "poison", "flying", "fighting", "normal"
    )

    fun resolveDomeType(typeId: String): String {
        val normalized = typeId.trim().lowercase()
        if (normalized in supportedDomeTypes) return normalized
        return when (normalized) {
            "legendary" -> "dragon"
            else -> "normal"
        }
    }

    fun placeTypeDome(
        server: MinecraftServer,
        dimensionId: String,
        center: BlockPos,
        typeId: String
    ): Boolean {
        val domeType = resolveDomeType(typeId)
        val functionPath = "$DOME_FUNCTION_PREFIX/$domeType"
        if (!hasFunctionResource(server, functionPath)) return false
        val command = "execute in $dimensionId positioned ${center.x} ${center.y + STRUCTURE_Y_OFFSET} ${center.z} run function $COBBLEDOMES_NAMESPACE:$functionPath"
        val placed = executeServerCommand(server, command)
        if (placed) {
            CobblemonEventsMod.LOGGER.info("[DatapackStructure] CobbleDomes type dome placed: $domeType at ${center.x},${center.y},${center.z} in $dimensionId")
        }
        return placed
    }

    fun placeHub(
        server: MinecraftServer,
        dimensionId: String,
        center: BlockPos
    ): Boolean {
        if (!hasFunctionResource(server, HUB_FUNCTION_PATH)) return false
        val command = "execute in $dimensionId positioned ${center.x} ${center.y + STRUCTURE_Y_OFFSET} ${center.z} run function $COBBLEDOMES_NAMESPACE:$HUB_FUNCTION_PATH"
        val placed = executeServerCommand(server, command)
        if (placed) {
            CobblemonEventsMod.LOGGER.info("[DatapackStructure] CobbleDomes hub placed at ${center.x},${center.y},${center.z} in $dimensionId")
        }
        return placed
    }

    private fun hasFunctionResource(server: MinecraftServer, functionPath: String): Boolean {
        return try {
            val resourceId = Identifier.of(COBBLEDOMES_NAMESPACE, "functions/$functionPath.mcfunction")
            server.resourceManager.getResource(resourceId).isPresent
        } catch (_: Exception) {
            false
        }
    }

    private fun executeServerCommand(server: MinecraftServer, rawCommand: String): Boolean {
        val command = rawCommand.trim().removePrefix("/")
        return try {
            val result = server.commandManager.dispatcher.execute(command, server.commandSource)
            if (result <= 0) {
                CobblemonEventsMod.LOGGER.warn("[DatapackStructure] command result=0: $command")
                false
            } else {
                true
            }
        } catch (e: Exception) {
            CobblemonEventsMod.LOGGER.warn("[DatapackStructure] command failed: $command", e)
            false
        }
    }
}
