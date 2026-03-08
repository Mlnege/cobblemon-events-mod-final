package com.cobblemonevents.events.types

import com.cobblemonevents.CobblemonEventsMod
import com.cobblemonevents.events.ActiveEvent
import com.cobblemonevents.events.EventHandler
import com.cobblemonevents.rewards.RewardManager
import com.cobblemonevents.util.BroadcastUtil
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AiDynamicEvent : EventHandler {

    companion object {
        private const val KEY_MODE = "ai_dynamic_mode"
        private const val KEY_TARGET = "ai_dynamic_target"
        private const val KEY_TEMPLATE = "ai_dynamic_template_id"
        private const val KEY_PROFILE = "ai_dynamic_profile"
        private const val KEY_VARIETY_MAP = "ai_dynamic_variety_map"
    }

    override fun onStart(event: ActiveEvent, server: MinecraftServer) {
        val mode = event.getData<String>(KEY_MODE) ?: "catch"
        val target = event.getData<Int>(KEY_TARGET) ?: 8
        val template = event.getData<String>(KEY_TEMPLATE) ?: "unknown"
        val profile = event.getData<String>(KEY_PROFILE) ?: "none"

        if (mode == "variety") {
            event.setData(KEY_VARIETY_MAP, ConcurrentHashMap<UUID, MutableSet<String>>())
        }

        BroadcastUtil.announceEventStart(
            server,
            event.definition.displayName,
            event.definition.description,
            event.definition.durationMinutes,
            listOf(
                "미션: ${modeToDisplay(mode)}",
                "목표: ${target}회",
                "완료 보상: AI 밸런스 보상 지급",
                "템플릿: $template / 프로필: $profile"
            )
        )
    }

    override fun onTick(event: ActiveEvent, server: MinecraftServer) {
        if (event.ticksRemaining > 0 && event.ticksRemaining % (20L * 60L) == 0L) {
            val mode = event.getData<String>(KEY_MODE) ?: "catch"
            val target = event.getData<Int>(KEY_TARGET) ?: 8
            BroadcastUtil.broadcast(
                server,
                "${CobblemonEventsMod.config.prefix}[AI Dynamic] ${modeToDisplay(mode)} 진행 중 (목표 ${target}, 남은 ${event.getRemainingMinutes()}분)"
            )
        }
    }

    override fun onEnd(event: ActiveEvent, server: MinecraftServer) {
        val target = event.getData<Int>(KEY_TARGET) ?: 8
        var completed = 0
        var participant = 0

        for ((uuid, progress) in event.participants) {
            if (progress <= 0) continue
            participant++

            val player = server.playerManager.getPlayer(uuid) ?: continue
            if (event.completedPlayers.contains(uuid) || progress >= target) {
                completed++
                if (!event.completedPlayers.contains(uuid)) {
                    event.completedPlayers.add(uuid)
                    RewardManager.giveRewards(player, event.definition.rewards, event.definition)
                }
            } else {
                // 미완료 참가 보상 (과하지 않게 고정)
                RewardManager.giveItemDirect(player, "cobblemon:poke_ball", 4)
                RewardManager.giveItemDirect(player, "cobblemon:exp_candy_xs", 1)
                BroadcastUtil.sendPersonal(
                    player,
                    "${CobblemonEventsMod.config.prefix}[AI Dynamic] 참가 보상이 지급되었습니다."
                )
            }
        }

        BroadcastUtil.announceEventEnd(
            server,
            event.definition.displayName,
            listOf(
                "참가자: ${participant}명",
                "목표 달성: ${completed}명",
                "목표 기준: ${target}회"
            )
        )
    }

    override fun onPokemonCaught(event: ActiveEvent, player: ServerPlayerEntity, species: String) {
        val mode = event.getData<String>(KEY_MODE) ?: return
        if (mode != "catch" && mode != "variety" && mode != "hybrid") return
        handleProgress(event, player, species, fromCatch = true)
    }

    override fun onBattleWon(event: ActiveEvent, player: ServerPlayerEntity, defeatedSpecies: String) {
        val mode = event.getData<String>(KEY_MODE) ?: return
        if (mode != "battle" && mode != "hybrid") return
        handleProgress(event, player, defeatedSpecies, fromCatch = false)
    }

    private fun handleProgress(
        event: ActiveEvent,
        player: ServerPlayerEntity,
        species: String,
        fromCatch: Boolean
    ) {
        val mode = event.getData<String>(KEY_MODE) ?: "catch"
        val target = event.getData<Int>(KEY_TARGET) ?: 8

        val progress = when (mode) {
            "variety" -> {
                val varietyMap = event.getData<ConcurrentHashMap<UUID, MutableSet<String>>>(KEY_VARIETY_MAP)
                    ?: ConcurrentHashMap<UUID, MutableSet<String>>().also { event.setData(KEY_VARIETY_MAP, it) }
                val set = varietyMap.computeIfAbsent(player.uuid) { mutableSetOf() }
                if (!set.add(species.lowercase())) {
                    set.size
                } else {
                    event.participants[player.uuid] = set.size
                    set.size
                }
            }
            else -> event.addProgress(player.uuid)
        }

        BroadcastUtil.sendProgress(
            player,
            "[AI Dynamic] ${modeToDisplay(mode)} ${progress}/${target}" + if (fromCatch) " (포획)" else " (배틀)"
        )

        if (progress >= target && event.completedPlayers.add(player.uuid)) {
            RewardManager.giveRewards(player, event.definition.rewards, event.definition)
            BroadcastUtil.broadcast(
                player.server,
                "${CobblemonEventsMod.config.prefix}[AI Dynamic] ${player.name.string} 님이 목표를 달성했습니다!"
            )
        }
    }

    private fun modeToDisplay(mode: String): String {
        return when (mode) {
            "catch" -> "포획 러시"
            "battle" -> "배틀 체인"
            "variety" -> "도감 다양성 수집"
            "hybrid" -> "하이브리드 미션"
            else -> "AI 미션"
        }
    }
}
