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
        private const val KEY_PARTICIPANTS = "ai_dynamic_participants"
        private const val KEY_REWARDED_PLAYERS = "ai_dynamic_rewarded_players"
    }

    override fun onStart(event: ActiveEvent, server: MinecraftServer) {
        val mode = normalizeMode(event.getData<String>(KEY_MODE))
        val target = event.getData<Int>(KEY_TARGET) ?: 8
        val template = event.getData<String>(KEY_TEMPLATE) ?: "unknown"
        val profile = event.getData<String>(KEY_PROFILE) ?: "none"

        event.setData(KEY_PARTICIPANTS, ConcurrentHashMap.newKeySet<UUID>())
        event.setData(KEY_REWARDED_PLAYERS, ConcurrentHashMap.newKeySet<UUID>())

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
            val mode = normalizeMode(event.getData<String>(KEY_MODE))
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
        val participantSet = event.getData<MutableSet<UUID>>(KEY_PARTICIPANTS) ?: mutableSetOf()
        val allParticipants = linkedSetOf<UUID>().apply {
            addAll(participantSet)
            addAll(event.participants.keys)
            addAll(event.completedPlayers)
        }

        for (uuid in allParticipants) {
            val progress = event.participants.getOrDefault(uuid, 0)
            if (progress <= 0) continue
            participant++

            val player = server.playerManager.getPlayer(uuid) ?: continue

            val rewarded = tryGrantCompletionReward(event, player, target, progress)
            if (rewarded) {
                completed++
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
        val mode = normalizeMode(event.getData<String>(KEY_MODE))
        if (mode != "catch" && mode != "variety" && mode != "hybrid") return
        handleProgress(event, player, species, fromCatch = true)
    }

    override fun onBattleWon(event: ActiveEvent, player: ServerPlayerEntity, defeatedSpecies: String) {
        val mode = normalizeMode(event.getData<String>(KEY_MODE))
        if (mode != "battle" && mode != "hybrid") return
        handleProgress(event, player, defeatedSpecies, fromCatch = false)
    }

    private fun handleProgress(
        event: ActiveEvent,
        player: ServerPlayerEntity,
        species: String,
        fromCatch: Boolean
    ) {
        val mode = normalizeMode(event.getData<String>(KEY_MODE))
        val target = event.getData<Int>(KEY_TARGET) ?: 8
        val participantSet = event.getData<MutableSet<UUID>>(KEY_PARTICIPANTS)
            ?: ConcurrentHashMap.newKeySet<UUID>().also { event.setData(KEY_PARTICIPANTS, it) }
        participantSet.add(player.uuid)

        val progress = when (mode) {
            "variety" -> {
                val varietyMap = event.getData<ConcurrentHashMap<UUID, MutableSet<String>>>(KEY_VARIETY_MAP)
                    ?: ConcurrentHashMap<UUID, MutableSet<String>>().also { event.setData(KEY_VARIETY_MAP, it) }
                val set = varietyMap.computeIfAbsent(player.uuid) { ConcurrentHashMap.newKeySet<String>() }
                val normalizedSpecies = species.trim().lowercase()
                if (normalizedSpecies.isNotBlank()) {
                    set.add(normalizedSpecies)
                }
                event.participants[player.uuid] = set.size
                set.size
            }
            else -> event.addProgress(player.uuid)
        }

        BroadcastUtil.sendProgress(
            player,
            "[AI Dynamic] ${modeToDisplay(mode)} ${progress}/${target}" + if (fromCatch) " (포획)" else " (배틀)"
        )

        val rewarded = tryGrantCompletionReward(event, player, target, progress)
        if (rewarded && progress >= target) {
            BroadcastUtil.broadcast(
                player.server,
                "${CobblemonEventsMod.config.prefix}[AI Dynamic] ${player.name.string} 님이 목표를 달성했습니다!"
            )
        }
    }

    private fun tryGrantCompletionReward(
        event: ActiveEvent,
        player: ServerPlayerEntity,
        target: Int,
        progress: Int
    ): Boolean {
        val hasCompleted = event.completedPlayers.contains(player.uuid) || progress >= target
        if (!hasCompleted) return false

        event.completedPlayers.add(player.uuid)
        val rewardedPlayers = event.getData<MutableSet<UUID>>(KEY_REWARDED_PLAYERS)
            ?: ConcurrentHashMap.newKeySet<UUID>().also { event.setData(KEY_REWARDED_PLAYERS, it) }

        if (!rewardedPlayers.add(player.uuid)) {
            return true
        }

        return try {
            RewardManager.giveRewards(player, event.definition.rewards, event.definition)
            true
        } catch (e: Exception) {
            rewardedPlayers.remove(player.uuid)
            CobblemonEventsMod.LOGGER.error(
                "[AI Dynamic] 완료 보상 지급 실패: player=${player.name.string}, event=${event.definition.id}",
                e
            )
            false
        }
    }

    private fun normalizeMode(rawMode: String?): String {
        val mode = rawMode?.trim()?.lowercase().orEmpty()
        return when (mode) {
            "catch", "battle", "variety", "hybrid" -> mode
            else -> {
                when {
                    mode.contains("variety") || mode.contains("도감") || mode.contains("diversity") -> "variety"
                    mode.contains("battle") || mode.contains("raid") || mode.contains("전투") -> "battle"
                    mode.contains("hybrid") || mode.contains("combo") || mode.contains("복합") -> "hybrid"
                    else -> "catch"
                }
            }
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
