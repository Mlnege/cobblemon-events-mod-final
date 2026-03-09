package com.cobblemonevents.events.types

import com.cobblemonevents.CobblemonEventsMod
import com.cobblemonevents.config.GymTypeEntry
import com.cobblemonevents.config.RewardPool
import com.cobblemonevents.events.ActiveEvent
import com.cobblemonevents.events.EventHandler
import com.cobblemonevents.integration.ExternalModApiRegistry
import com.cobblemonevents.rewards.RewardManager
import com.cobblemonevents.util.BroadcastUtil
import com.cobblemonevents.util.SpawnHelper
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.BlockPos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class GymChallengeEvent : EventHandler {
    companion object {
        private const val DATA_GYM_TYPE = "gym_type"
        private const val DATA_GYM_TARGET = "gym_target"
        private const val DATA_GYM_INTEGRATION_COUNT = "gym_integration_count"
    }

    override fun onStart(event: ActiveEvent, server: MinecraftServer) {
        val gymConfig = event.definition.gymConfig ?: return
        val gymTypes = gymConfig.gymTypes.ifEmpty {
            listOf(GymTypeEntry("electric", "§e⚡ 번개 배지 체육관", 3, 1.0))
        }

        val selectedType = gymTypes.random()
        val target = resolveTarget(gymConfig.randomTargetMin, gymConfig.randomTargetMax, selectedType.targetWins)
        event.setData(DATA_GYM_TYPE, selectedType)
        event.setData(DATA_GYM_TARGET, target)

        val location = SpawnHelper.findRandomEventLocation(server, gymConfig.gymSearchRadius)
        val gymPos = location?.second ?: server.overworld.spawnPos
        event.eventLocation = gymPos
        buildGymArena(server.overworld, gymPos)

        val integrationCommandCount = ExternalModApiRegistry.runGymIntegrationCommands(
            server = server,
            commands = gymConfig.integrationCommands,
            variables = mapOf(
                "x" to gymPos.x.toString(),
                "y" to gymPos.y.toString(),
                "z" to gymPos.z.toString(),
                "type_id" to selectedType.id,
                "type_name" to selectedType.displayName,
                "target" to target.toString()
            )
        )
        event.setData(DATA_GYM_INTEGRATION_COUNT, integrationCommandCount)

        BroadcastUtil.announceEventStart(
            server = server,
            eventName = "커스텀 체육관 챌린지",
            description = "${selectedType.displayName} §f체육관 배지를 획득하세요.",
            duration = event.definition.durationMinutes,
            extraLines = listOf(
                "체육관 타입: ${selectedType.displayName}",
                "승리 목표: ${target}회",
                "연동 훅 실행: ${integrationCommandCount}개"
            )
        )

        CobblemonEventsMod.LOGGER.info(
            "[GymChallenge] 시작 - 타입:${selectedType.id}, 목표:$target, 좌표:${gymPos.x},${gymPos.y},${gymPos.z}, 연동훅:${integrationCommandCount}"
        )
    }

    override fun onTick(event: ActiveEvent, server: MinecraftServer) {
        val pos = event.eventLocation ?: return
        val selectedType = event.getData<GymTypeEntry>(DATA_GYM_TYPE)

        if (event.ticksRemaining % 20L == 0L) {
            server.overworld.spawnParticles(
                ParticleTypes.END_ROD,
                pos.x + 0.5,
                pos.y + 2.2,
                pos.z + 0.5,
                4,
                2.0,
                0.6,
                2.0,
                0.02
            )
        }

        if (event.ticksRemaining > 0 && event.ticksRemaining % (20L * 60L) == 0L) {
            val target = event.getData<Int>(DATA_GYM_TARGET) ?: 0
            BroadcastUtil.broadcast(
                server,
                "${CobblemonEventsMod.config.prefix}🏟 ${selectedType?.displayName ?: "체육관"} §f챌린지 진행 중! 목표 승리 ${target}회"
            )
        }
    }

    override fun onEnd(event: ActiveEvent, server: MinecraftServer) {
        val gymConfig = event.definition.gymConfig ?: return
        val selectedType = event.getData<GymTypeEntry>(DATA_GYM_TYPE)
        var participationCount = 0

        for ((playerUUID, progress) in event.participants) {
            if (progress <= 0 || event.completedPlayers.contains(playerUUID)) continue
            val player = server.playerManager.getPlayer(playerUUID) ?: continue
            RewardManager.giveRewards(player, gymConfig.participationRewards, event.definition)
            participationCount++
        }

        BroadcastUtil.announceEventEnd(
            server = server,
            eventName = "커스텀 체육관 챌린지",
            stats = listOf(
                "체육관 타입: ${selectedType?.displayName ?: "미정"}",
                "참가자: ${event.participants.size}명",
                "완주자: ${event.completedPlayers.size}명",
                "참가 보상: ${participationCount}명"
            )
        )
    }

    override fun onBattleWon(event: ActiveEvent, player: ServerPlayerEntity, defeatedSpecies: String) {
        val gymConfig = event.definition.gymConfig ?: return
        val selectedType = event.getData<GymTypeEntry>(DATA_GYM_TYPE)
        val target = event.getData<Int>(DATA_GYM_TARGET) ?: return

        val progress = event.addProgress(player.uuid, 1)
        BroadcastUtil.sendProgress(
            player,
            "§b체육관 승리 진행도: §f${progress}/${target} §7(${selectedType?.displayName ?: "체육관"}§7)"
        )

        if (progress < target) return
        if (!event.completedPlayers.add(player.uuid)) return

        val rewardMultiplier = selectedType?.rewardMultiplier ?: 1.0
        val scaledRewards = scaleRewardPool(gymConfig.completionRewards, rewardMultiplier)
        RewardManager.giveRewards(player, scaledRewards, event.definition)
        CobblemonEventsMod.rankingManager.recordEventComplete(player.uuid, player.name.string)
        BroadcastUtil.sendPersonal(player, "${CobblemonEventsMod.config.prefix}§b배지 획득 성공! 완주 보상을 지급했습니다.")
    }

    private fun resolveTarget(min: Int, max: Int, fallback: Int): Int {
        val safeMin = min.coerceAtLeast(1)
        val safeMax = max.coerceAtLeast(safeMin)
        return Random.nextInt(safeMin, safeMax + 1).coerceAtLeast(fallback.coerceAtLeast(1))
    }

    private fun buildGymArena(world: net.minecraft.server.world.ServerWorld, center: BlockPos) {
        val radius = 9
        for (x in center.x - radius..center.x + radius) {
            for (z in center.z - radius..center.z + radius) {
                val dx = x - center.x
                val dz = z - center.z
                val dist = dx * dx + dz * dz
                val floor = when {
                    dist <= 25 -> Blocks.SMOOTH_QUARTZ.defaultState
                    dist <= 49 -> Blocks.LIGHT_BLUE_CONCRETE.defaultState
                    dist <= 81 -> Blocks.POLISHED_DEEPSLATE.defaultState
                    else -> null
                }
                if (floor != null) {
                    world.setBlockState(BlockPos(x, center.y, z), floor, Block.NOTIFY_ALL)
                }
                world.setBlockState(BlockPos(x, center.y + 1, z), Blocks.AIR.defaultState, Block.NOTIFY_ALL)
                world.setBlockState(BlockPos(x, center.y + 2, z), Blocks.AIR.defaultState, Block.NOTIFY_ALL)
            }
        }

        repeat(8) { i ->
            val angle = (Math.PI * 2.0) * (i / 8.0)
            val px = center.x + (cos(angle) * 8).toInt()
            val pz = center.z + (sin(angle) * 8).toInt()
            for (h in 1..4) {
                world.setBlockState(BlockPos(px, center.y + h, pz), Blocks.QUARTZ_PILLAR.defaultState, Block.NOTIFY_ALL)
            }
            world.setBlockState(BlockPos(px, center.y + 5, pz), Blocks.SEA_LANTERN.defaultState, Block.NOTIFY_ALL)
        }
    }

    private fun scaleRewardPool(pool: RewardPool, multiplier: Double): RewardPool {
        val clamped = multiplier.coerceAtLeast(0.5)
        val scaledItems = pool.items.map { item ->
            val count = (item.count * clamped).toInt().coerceAtLeast(1)
            item.copy(count = count)
        }
        return pool.copy(items = scaledItems)
    }
}
