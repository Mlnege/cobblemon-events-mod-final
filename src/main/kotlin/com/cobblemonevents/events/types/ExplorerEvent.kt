package com.cobblemonevents.events.types

import com.cobblemonevents.CobblemonEventsMod
import com.cobblemonevents.config.ExplorerOverrideConfig
import com.cobblemonevents.events.ActiveEvent
import com.cobblemonevents.events.EventHandler
import com.cobblemonevents.rewards.RewardManager
import com.cobblemonevents.util.BroadcastUtil
import com.cobblemonevents.util.EventProgressHud
import com.cobblemonevents.util.SpawnHelper
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * 탐험 이벤트 (Explorer / Pokestop)
 */
class ExplorerEvent : EventHandler {

    companion object {
        private const val DATA_STOPS = "stops"
        private const val DATA_CLAIMED_STOPS = "claimedStops"

        private data class BonusItem(
            val itemId: String,
            val minCount: Int,
            val maxCount: Int
        )

        private data class CandyDrop(
            val itemId: String,
            val chance: Double,
            val minCount: Int,
            val maxCount: Int
        )

        // 기본 보너스: 비교적 유용한 아이템 위주
        private val bonusPool = listOf(
            BonusItem("cobblemon:poke_ball", 8, 20),
            BonusItem("cobblemon:great_ball", 4, 12),
            BonusItem("cobblemon:ultra_ball", 2, 8),
            BonusItem("cobblemon:rare_candy", 1, 2),
            BonusItem("cobblemon:beast_ball", 1, 2),
            BonusItem("minecraft:ender_pearl", 1, 4),
            BonusItem("minecraft:amethyst_shard", 2, 8),
            BonusItem("minecraft:echo_shard", 1, 2),
            BonusItem("minecraft:glow_berries", 6, 16),
            BonusItem("minecraft:rabbit_foot", 1, 2),
            BonusItem("minecraft:phantom_membrane", 1, 2)
        )

        // 경험사탕 전용 테이블 (OP 완화: 낮은 등급 위주, 높은 등급은 희귀)
        private val candyPool = listOf(
            CandyDrop("cobblemon:exp_candy_xs", 0.40, 2, 6),
            CandyDrop("cobblemon:exp_candy_s", 0.30, 2, 4),
            CandyDrop("cobblemon:exp_candy_m", 0.18, 1, 3),
            CandyDrop("cobblemon:exp_candy_l", 0.09, 1, 2),
            CandyDrop("cobblemon:exp_candy_xl", 0.03, 1, 1)
        )

        private const val CANDY_REWARD_CHANCE = 0.45

        // 이상한 보너스: 낮은 확률로 추가 지급
        private val strangePool = listOf(
            BonusItem("minecraft:poisonous_potato", 1, 4),
            BonusItem("minecraft:pufferfish", 1, 3),
            BonusItem("minecraft:fermented_spider_eye", 1, 3),
            BonusItem("minecraft:goat_horn", 1, 1),
            BonusItem("minecraft:name_tag", 1, 1),
            BonusItem("minecraft:music_disc_pigstep", 1, 1)
        )
    }

    override fun onStart(event: ActiveEvent, server: MinecraftServer) {
        val config = event.definition.explorerConfig ?: return
        val stops = mutableListOf<StopData>()
        val claimedStops = ConcurrentHashMap<Int, MutableSet<UUID>>()

        val players = server.playerManager.playerList
        if (players.isEmpty()) return

        var generated = 0
        for (i in 0 until config.stopCount) {
            val player = players.random()
            val pos = SpawnHelper.findSafeLocationNearPlayer(player, config.searchRadius)
            if (pos != null) {
                stops.add(StopData(generated, pos))
                claimedStops[generated] = mutableSetOf()
                generated++
            }
        }

        event.setData(DATA_STOPS, stops)
        event.setData(DATA_CLAIMED_STOPS, claimedStops)

        BroadcastUtil.announceEventStart(
            server,
            event.definition.displayName,
            event.definition.description,
            event.definition.durationMinutes,
            listOf(
                "포켓스탑 / Pokéstops: §e${stops.size}개 생성됨 (generated)",
                "포켓스탑 근처로 가면 보상을 받을 수 있습니다. / Visit Pokéstops for rewards.",
                "가장 가까운 포켓스탑까지 거리가 표시됩니다. / Nearest Pokéstop distance shown."
            )
        )
    }

    override fun onTick(event: ActiveEvent, server: MinecraftServer) {
        val config = event.definition.explorerConfig ?: return
        val stops = event.getData<List<StopData>>(DATA_STOPS) ?: return
        val claimedStops = event.getData<ConcurrentHashMap<Int, MutableSet<UUID>>>(DATA_CLAIMED_STOPS) ?: return
        val world = server.overworld

        for (player in server.playerManager.playerList) {
            for (stop in stops) {
                val claimed = claimedStops[stop.id] ?: continue
                if (claimed.contains(player.uuid)) continue

                val dist = player.blockPos.getSquaredDistance(stop.pos)
                val actualDist = kotlin.math.sqrt(dist.toDouble())

                if (actualDist <= config.interactRadius) {
                    claimStop(event, player, stop, claimedStops, server)
                }
            }
        }

        if (event.ticksRemaining % 40 == 0L) {
            for (stop in stops) {
                val anyUnclaimed = claimedStops[stop.id]?.let { claimed ->
                    server.playerManager.playerList.any { !claimed.contains(it.uuid) }
                } ?: false

                if (anyUnclaimed) {
                    spawnStopParticles(world, stop.pos)
                }
            }
        }
    }

    override fun onEnd(event: ActiveEvent, server: MinecraftServer) {
        val totalClaimed = event.participants.values.sum()
        BroadcastUtil.announceEventEnd(
            server,
            event.definition.displayName,
            listOf(
                "참가자 / Participants: §e${event.participants.size}명",
                "총 발견 포켓스탑 / Total Pokéstops Found: §e${totalClaimed}"
            )
        )
    }

    private fun claimStop(
        event: ActiveEvent,
        player: ServerPlayerEntity,
        stop: StopData,
        claimedStops: ConcurrentHashMap<Int, MutableSet<UUID>>,
        server: MinecraftServer
    ) {
        val claimed = claimedStops[stop.id] ?: return
        if (!claimed.add(player.uuid)) return

        val config = event.definition.explorerConfig ?: return
        event.addProgress(player.uuid)

        val override = ExplorerOverrideConfig.current()
        val stopRewardPool = if (override.enabled) override.stopRewards else config.stopRewards

        // 기존 보상
        RewardManager.giveRewards(player, stopRewardPool, event.definition)

        // 신규: 보너스 랜덤 아이템 지급(다양성 강화)
        giveStopBonusItems(player)

        if (Random.nextDouble() < config.legendFragmentChance) {
            BroadcastUtil.sendPersonal(
                player,
                "${CobblemonEventsMod.config.prefix}§d희귀한 조각을 발견했습니다! / Rare fragment found!"
            )
            RewardManager.giveItemDirect(player, "cobblemon:rare_candy", 3)
        }

        val count = event.getProgress(player.uuid)
        BroadcastUtil.sendPersonal(
            player,
            "${CobblemonEventsMod.config.prefix}§a포켓스탑 발견! / Pokéstop found! §7(${count}회 / times)"
        )

        BroadcastUtil.broadcast(
            server,
            "${CobblemonEventsMod.config.prefix}§e${player.name.string}§f님이 포켓스탑을 발견했습니다! / found a Pokéstop! §7(${count}회)"
        )

        player.serverWorld.spawnParticles(
            ParticleTypes.TOTEM_OF_UNDYING,
            stop.pos.x.toDouble(),
            stop.pos.y.toDouble() + 1.0,
            stop.pos.z.toDouble(),
            30,
            0.5,
            1.0,
            0.5,
            0.1
        )

        // 플레이어 기준으로 더 이상 찾을 스탑이 없으면 거리 보스바를 즉시 제거한다.
        val stops = event.getData<List<StopData>>(DATA_STOPS).orEmpty()
        val hasRemainingStops = stops.any { stopData ->
            claimedStops[stopData.id]?.contains(player.uuid) != true
        }
        if (!hasRemainingStops) {
            EventProgressHud.clearTrackingBarFor(player.uuid)
        }
    }

    private fun giveStopBonusItems(player: ServerPlayerEntity) {
        if (Random.nextDouble() < CANDY_REWARD_CHANCE) {
            val candy = rollCandyDrop()
            val candyCount = rollCount(candy.minCount, candy.maxCount)
            RewardManager.giveItemDirect(player, candy.itemId, candyCount)

            BroadcastUtil.sendPersonal(
                player,
                "${CobblemonEventsMod.config.prefix}§b보너스 경험사탕 / Bonus Exp Candy: §f${candy.itemId} §7x${candyCount}"
            )
        } else {
            val bonus = bonusPool.random()
            val bonusCount = rollCount(bonus.minCount, bonus.maxCount)
            RewardManager.giveItemDirect(player, bonus.itemId, bonusCount)

            BroadcastUtil.sendPersonal(
                player,
                "${CobblemonEventsMod.config.prefix}§a보너스 아이템 / Bonus Item: §f${bonus.itemId} §7x${bonusCount}"
            )
        }

        // 20% 확률로 이상한 아이템 추가 지급 / 20% chance for a strange bonus item
        if (Random.nextDouble() < 0.20) {
            val strange = strangePool.random()
            val strangeCount = rollCount(strange.minCount, strange.maxCount)
            RewardManager.giveItemDirect(player, strange.itemId, strangeCount)

            BroadcastUtil.sendPersonal(
                player,
                "${CobblemonEventsMod.config.prefix}§e수상한 보너스 발견 / Strange Bonus: §f${strange.itemId} §7x${strangeCount}"
            )
        }
    }

    private fun rollCandyDrop(): CandyDrop {
        val roll = Random.nextDouble()
        var cumulative = 0.0
        for (drop in candyPool) {
            cumulative += drop.chance
            if (roll < cumulative) {
                return drop
            }
        }
        return candyPool.last()
    }

    private fun rollCount(min: Int, max: Int): Int {
        return if (min >= max) {
            min
        } else {
            Random.nextInt(min, max + 1)
        }
    }

    private fun spawnStopParticles(world: ServerWorld, pos: BlockPos) {
        try {
            world.spawnParticles(
                ParticleTypes.END_ROD,
                pos.x.toDouble() + 0.5,
                pos.y.toDouble() + 2.0,
                pos.z.toDouble() + 0.5,
                5,
                0.2,
                0.5,
                0.2,
                0.02
            )
            world.spawnParticles(
                ParticleTypes.ENCHANT,
                pos.x.toDouble() + 0.5,
                pos.y.toDouble() + 1.0,
                pos.z.toDouble() + 0.5,
                8,
                0.5,
                0.5,
                0.5,
                0.05
            )
        } catch (_: Exception) {
        }
    }

    data class StopData(val id: Int, val pos: BlockPos)
}
