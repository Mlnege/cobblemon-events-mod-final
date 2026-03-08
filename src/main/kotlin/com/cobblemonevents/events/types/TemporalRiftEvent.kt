package com.cobblemonevents.events.types

import com.cobblemonevents.CobblemonEventsMod
import com.cobblemonevents.config.RiftTypeEntry
import com.cobblemonevents.events.ActiveEvent
import com.cobblemonevents.events.EventHandler
import com.cobblemonevents.rewards.RewardManager
import com.cobblemonevents.util.BroadcastUtil
import com.cobblemonevents.util.SpawnHelper
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import kotlin.random.Random

/**
 * 🌀 시공 균열 이벤트
 */
class TemporalRiftEvent : EventHandler {

    override fun onStart(event: ActiveEvent, server: MinecraftServer) {
        val riftConfig = event.definition.riftConfig ?: return
        val riftTypes = riftConfig.riftTypes
        if (riftTypes.isEmpty()) return

        val selectedRift = riftTypes.random()
        event.setData("selectedRift", selectedRift)

        val location = SpawnHelper.findRandomEventLocation(server, riftConfig.riftSearchRadius)
        if (location == null) {
            CobblemonEventsMod.LOGGER.warn("[시공 균열] 적절한 위치를 찾을 수 없습니다.")
            return
        }

        val (_, pos) = location
        event.eventLocation = pos

        val world = server.overworld
        SpawnHelper.spawnMultiplePokemon(
            world = world,
            speciesList = selectedRift.pokemonPool,
            centerPos = pos,
            radius = riftConfig.spawnRadius,
            count = riftConfig.spawnCount,
            levelMin = riftConfig.pokemonLevelMin,
            levelMax = riftConfig.pokemonLevelMax,
            shinyChance = riftConfig.shinyBoost
        )

        BroadcastUtil.announceRift(
            server,
            selectedRift.displayName,
            pos.x, pos.y, pos.z,
            event.definition.durationMinutes
        )

        CobblemonEventsMod.LOGGER.info(
            "[시공 균열] '${selectedRift.id}' 균열 활성화 - 좌표: ${pos.x}, ${pos.z}"
        )
    }

    override fun onTick(event: ActiveEvent, server: MinecraftServer) {
        val pos = event.eventLocation ?: return
        val world = server.overworld

        if (event.ticksRemaining % 60 == 0L) {
            spawnRiftParticles(world, pos)
        }

        val riftConfig = event.definition.riftConfig ?: return
        val selectedRift = event.getData<RiftTypeEntry>("selectedRift") ?: return

        if (event.ticksRemaining > 0 && event.ticksRemaining % (20 * 120) == 0L) {
            SpawnHelper.spawnMultiplePokemon(
                world = world,
                speciesList = selectedRift.pokemonPool,
                centerPos = pos,
                radius = riftConfig.spawnRadius,
                count = riftConfig.spawnCount / 2,
                levelMin = riftConfig.pokemonLevelMin,
                levelMax = riftConfig.pokemonLevelMax,
                shinyChance = riftConfig.shinyBoost
            )
            BroadcastUtil.broadcast(server,
                "${CobblemonEventsMod.config.prefix}§d균열에서 추가 포켓몬이 나타났습니다! §7(남은 시간: ${event.getRemainingMinutes()}분)"
            )
        }
    }

    override fun onEnd(event: ActiveEvent, server: MinecraftServer) {
        val selectedRift = event.getData<RiftTypeEntry>("selectedRift")
        val riftName = selectedRift?.displayName ?: "시공 균열"

        val riftConfig = event.definition.riftConfig ?: return
        for (playerUUID in event.participants.keys) {
            val player = server.playerManager.getPlayer(playerUUID) ?: continue
            val catchCount = event.getProgress(playerUUID)
            if (catchCount > 0) {
                RewardManager.giveRewards(player, riftConfig.dropRewards, event.definition)
                CobblemonEventsMod.rankingManager.recordEventComplete(playerUUID, player.name.string)
            }
        }

        BroadcastUtil.announceEventEnd(server, riftName, listOf(
            "§7참가자: §f${event.participants.size}명",
            "§7총 포획: §e${event.participants.values.sum()}마리"
        ))
    }

    override fun onPokemonCaught(event: ActiveEvent, player: ServerPlayerEntity, species: String) {
        val selectedRift = event.getData<RiftTypeEntry>("selectedRift") ?: return

        val isRiftPokemon = selectedRift.pokemonPool.any { it.equals(species, ignoreCase = true) }
        if (!isRiftPokemon) return

        val count = event.addProgress(player.uuid)
        BroadcastUtil.sendProgress(player,
            "§d🌀 시공 균열 포획: §f${count}마리 §7(${selectedRift.displayName}§7)"
        )

        val riftConfig = event.definition.riftConfig ?: return
        if (Random.nextDouble() < 0.3) {
            RewardManager.giveRewards(player, riftConfig.dropRewards, event.definition)
        }
    }

    private fun spawnRiftParticles(world: ServerWorld, center: BlockPos) {
        try {
            val positions = SpawnHelper.getCirclePositions(center, 5, 16)
            for (pos in positions) {
                world.spawnParticles(
                    ParticleTypes.PORTAL,
                    pos.x.toDouble(), pos.y.toDouble() + 1.5, pos.z.toDouble(),
                    5, 0.5, 1.0, 0.5, 0.02
                )
                world.spawnParticles(
                    ParticleTypes.REVERSE_PORTAL,
                    pos.x.toDouble(), pos.y.toDouble() + 2.0, pos.z.toDouble(),
                    3, 0.3, 0.5, 0.3, 0.01
                )
            }
        } catch (_: Exception) { }
    }
}
