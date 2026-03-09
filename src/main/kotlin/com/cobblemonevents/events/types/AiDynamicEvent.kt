package com.cobblemonevents.events.types

import com.cobblemonevents.CobblemonEventsMod
import com.cobblemonevents.ai.AiGeneratedContentPlanner
import com.cobblemonevents.events.ActiveEvent
import com.cobblemonevents.events.EventHandler
import com.cobblemonevents.rewards.RewardManager
import com.cobblemonevents.util.BroadcastUtil
import com.cobblemonevents.util.SpawnHelper
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AiDynamicEvent : EventHandler {

    companion object {
        private const val TRACKING_SEARCH_RADIUS = 320
        private const val SPEED_BONUS_TIER_S_MAX_RATIO = 0.20
        private const val SPEED_BONUS_TIER_A_MAX_RATIO = 0.45
        private const val SPEED_BONUS_TIER_B_MAX_RATIO = 0.70
        private const val KEY_MODE = "ai_dynamic_mode"
        private const val KEY_TARGET = "ai_dynamic_target"
        private const val KEY_TEMPLATE = "ai_dynamic_template_id"
        private const val KEY_PROFILE = "ai_dynamic_profile"
        private const val KEY_SOURCE = "ai_dynamic_source"
        private const val KEY_CATEGORY = "ai_dynamic_category"
        private const val KEY_THEME_TYPE = "ai_dynamic_theme_type"
        private const val KEY_TARGET_BIOME = "ai_dynamic_target_biome"
        private const val KEY_CORE_MECHANISM = "ai_dynamic_core_mechanism"
        private const val KEY_SPECIAL_ENCOUNTER = "ai_dynamic_special_encounter"
        private const val KEY_VARIETY_MAP = "ai_dynamic_variety_map"
        private const val KEY_PARTICIPANTS = "ai_dynamic_participants"
        private const val KEY_REWARDED_PLAYERS = "ai_dynamic_rewarded_players"
    }

    override fun onStart(event: ActiveEvent, server: MinecraftServer) {
        val mode = normalizeMode(event.getData<String>(KEY_MODE))
        val target = event.getData<Int>(KEY_TARGET) ?: 8
        val template = event.getData<String>(KEY_TEMPLATE) ?: "unknown"
        val profile = event.getData<String>(KEY_PROFILE) ?: "none"
        val source = event.getData<String>(KEY_SOURCE) ?: "internal"
        val category = event.getData<String>(KEY_CATEGORY)?.trim()?.lowercase().orEmpty()
        val themeType = event.getData<String>(KEY_THEME_TYPE)?.takeIf { it.isNotBlank() }
        val targetBiome = event.getData<String>(KEY_TARGET_BIOME)?.takeIf { it.isNotBlank() }
        val coreMechanism = event.getData<String>(KEY_CORE_MECHANISM)?.takeIf { it.isNotBlank() }
        val specialEncounter = event.getData<String>(KEY_SPECIAL_ENCOUNTER)?.takeIf { it.isNotBlank() }
        val trackingEnabled = shouldEnableTracking(category, coreMechanism)

        event.setData(KEY_PARTICIPANTS, ConcurrentHashMap.newKeySet<UUID>())
        event.setData(KEY_REWARDED_PLAYERS, ConcurrentHashMap.newKeySet<UUID>())

        if (mode == "variety") {
            event.setData(KEY_VARIETY_MAP, ConcurrentHashMap<UUID, MutableSet<String>>())
        }
        if (trackingEnabled && event.eventLocation == null) {
            val location = SpawnHelper.findRandomEventLocation(server, TRACKING_SEARCH_RADIUS)
            if (location != null) {
                event.eventLocation = location.second
            }
        }

        val lines = mutableListOf(
            "미션: ${modeToDisplay(mode)}",
            "목표: ${target}회",
            "완료 보상: AI 밸런스 보상 지급",
            "템플릿: $template / 프로필: $profile / 소스: $source"
        )
        if (mode == "variety") {
            lines.add("규칙: 서로 다른 종만 카운트 (중복 종 미반영)")
        }
        if (trackingEnabled) {
            val pos = event.eventLocation
            if (pos != null) {
                lines.add("추적 지점: X:${pos.x} Z:${pos.z}")
                lines.add("화살표: 화면 상단 보스바(↖ ↑ ↗)에서 방향 표시")
            } else {
                lines.add("화살표: 위치 탐색 실패로 이번 회차는 비활성화")
            }
        }
        if (!themeType.isNullOrBlank() || !targetBiome.isNullOrBlank()) {
            lines.add("테마: ${themeType ?: "-"} / 바이옴: ${targetBiome ?: "-"}")
        }
        if (!specialEncounter.isNullOrBlank()) {
            lines.add("특수 조우: $specialEncounter")
        }
        if (!coreMechanism.isNullOrBlank()) {
            lines.add("메커니즘: ${coreMechanism.take(64)}")
        }

        BroadcastUtil.announceEventStart(
            server,
            event.definition.displayName,
            event.definition.description,
            event.definition.durationMinutes,
            lines
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
        val mode = normalizeMode(event.getData<String>(KEY_MODE))
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

        AiGeneratedContentPlanner.recordDynamicOutcome(
            mode = mode,
            participants = participant,
            completed = completed,
            durationMinutes = event.definition.durationMinutes
        )

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
            grantSpeedBonus(event, player)
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

    private fun grantSpeedBonus(event: ActiveEvent, player: ServerPlayerEntity) {
        val totalTicks = (event.definition.durationMinutes.coerceAtLeast(1) * 20L * 60L).toLong()
        if (totalTicks <= 0L) return

        val elapsedTicks = (totalTicks - event.ticksRemaining).coerceIn(0L, totalTicks)
        val elapsedRatio = elapsedTicks.toDouble() / totalTicks.toDouble()

        val (tier, superBallCount, hyperBallCount) = when {
            elapsedRatio <= SPEED_BONUS_TIER_S_MAX_RATIO -> Triple("S", 8, 4)
            elapsedRatio <= SPEED_BONUS_TIER_A_MAX_RATIO -> Triple("A", 6, 3)
            elapsedRatio <= SPEED_BONUS_TIER_B_MAX_RATIO -> Triple("B", 4, 2)
            else -> Triple("C", 3, 0)
        }

        if (superBallCount > 0) {
            RewardManager.giveItemDirect(player, "cobblemon:great_ball", superBallCount)
        }
        if (hyperBallCount > 0) {
            RewardManager.giveItemDirect(player, "cobblemon:ultra_ball", hyperBallCount)
        }

        val elapsedSeconds = (elapsedTicks / 20L).toInt()
        val rewardText = buildString {
            append("슈퍼볼 x$superBallCount")
            if (hyperBallCount > 0) append(" + 하이퍼볼 x$hyperBallCount")
        }
        BroadcastUtil.sendPersonal(
            player,
            "${CobblemonEventsMod.config.prefix}[AI Dynamic] 속도 보너스[$tier] 지급: $rewardText (완료 ${elapsedSeconds}초)"
        )
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

    private fun shouldEnableTracking(category: String, coreMechanism: String?): Boolean {
        if (category == "legendary_tracking") return true
        val mechanism = coreMechanism?.trim()?.lowercase().orEmpty()
        return mechanism.contains("tracking") ||
            mechanism.contains("clue") ||
            mechanism.contains("추적")
    }
}
