package com.cobblemonevents.ai

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemonevents.CobblemonEventsMod
import com.cobblemonevents.config.EventDefinition
import com.cobblemonevents.config.ItemRewardEntry
import com.cobblemonevents.config.RewardPool
import com.cobblemonevents.events.EventState
import com.cobblemonevents.util.BroadcastUtil
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.Box
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

data class AiGeneratedDecision(
    val executed: Boolean,
    val reason: String,
    val selectedEventId: String? = null,
    val selectedEventType: String? = null,
    val selectedProfileId: String? = null,
    val playerCount: Int = 0,
    val averagePartyLevel: Double = 0.0,
    val timCoreTaggedNearby: Int = 0,
    val trigger: String = "unknown"
)

private data class AiDynamicTemplate(
    val id: String,
    val displayName: String,
    val description: String,
    val mode: String,
    val targetLevel: Double,
    val baseTarget: Int,
    val rewardTier: Int,
    val weight: Double,
    val cooldownGroup: String
)

object AiGeneratedContentPlanner {
    @Volatile
    private var autoPlannerEnabled = false

    private const val AUTO_PLAN_INTERVAL_TICKS = 20L * 60L * 12L
    private const val RETRY_INTERVAL_TICKS = 20L * 60L * 3L
    private const val MIN_EVENT_TYPE_COOLDOWN_TICKS = 20L * 60L * 25L
    private const val MIN_EVENT_ID_COOLDOWN_TICKS = 20L * 60L * 20L
    private const val TIM_CORE_SCAN_RADIUS = 48.0
    private const val ADDON_DURATION_MINUTES = 3
    private const val UPCOMING_BASE_EVENT_GUARD_TICKS = 20L * 60L * 6L

    private val eventTypeLastExecutedTick = mutableMapOf<String, Long>()
    private val eventIdLastExecutedTick = mutableMapOf<String, Long>()
    private val cycleRemainingTemplateIds = linkedSetOf<String>()

    private val dataSources: List<PlannerDataSource> = listOf(
        ServerCoreDataSource,
        TimCoreDataSource
    )

    @Volatile
    private var initialized = false
    private var ticksUntilNextAutoPlan = AUTO_PLAN_INTERVAL_TICKS
    private var serverTickCounter = 0L
    private var lastDecision = AiGeneratedDecision(
        executed = false,
        reason = "not_generated_yet"
    )

    fun onServerStarted() {
        initialized = true
        ticksUntilNextAutoPlan = AUTO_PLAN_INTERVAL_TICKS
        serverTickCounter = 0L
        eventTypeLastExecutedTick.clear()
        eventIdLastExecutedTick.clear()
        cycleRemainingTemplateIds.clear()
        lastDecision = AiGeneratedDecision(false, "server_started")
    }

    fun onServerStopping() {
        initialized = false
        ticksUntilNextAutoPlan = AUTO_PLAN_INTERVAL_TICKS
        serverTickCounter = 0L
        eventTypeLastExecutedTick.clear()
        eventIdLastExecutedTick.clear()
        cycleRemainingTemplateIds.clear()
    }

    fun tick(server: MinecraftServer) {
        if (!initialized) return

        serverTickCounter++
        if (!autoPlannerEnabled) return

        ticksUntilNextAutoPlan--
        if (ticksUntilNextAutoPlan > 0) return

        val decision = generate(server, execute = true, ignoreCooldown = false, trigger = "auto")
        ticksUntilNextAutoPlan = if (decision.executed) {
            computeNextAutoIntervalTicks(decision.playerCount)
        } else {
            RETRY_INTERVAL_TICKS
        }
    }

    fun generate(
        server: MinecraftServer,
        execute: Boolean,
        ignoreCooldown: Boolean,
        trigger: String
    ): AiGeneratedDecision {
        val players = server.playerManager.playerList
        if (players.isEmpty()) {
            val decision = AiGeneratedDecision(
                executed = false,
                reason = "no_online_players",
                playerCount = 0,
                trigger = trigger
            )
            lastDecision = decision
            return decision
        }

        if (execute && !ignoreCooldown) {
            if (CobblemonEventsMod.scheduler.getActiveEvents().isNotEmpty()) {
                val decision = AiGeneratedDecision(
                    executed = false,
                    reason = "active_event_exists",
                    playerCount = players.size,
                    trigger = trigger
                )
                lastDecision = decision
                return decision
            }

            val nextBaseStartTicks = CobblemonEventsMod.scheduler.getAllEvents().values
                .asSequence()
                .filter { !it.definition.id.startsWith("aiaddon_") }
                .filter { it.state == EventState.WAITING || it.state == EventState.ANNOUNCED }
                .map { it.ticksUntilStart }
                .filter { it >= 0L }
                .minOrNull()

            if (nextBaseStartTicks != null && nextBaseStartTicks <= UPCOMING_BASE_EVENT_GUARD_TICKS) {
                val decision = AiGeneratedDecision(
                    executed = false,
                    reason = "base_event_soon",
                    playerCount = players.size,
                    trigger = trigger
                )
                lastDecision = decision
                return decision
            }
        }

        val playerProfiles = buildPlayerProfiles(players)
        val averagePartyLevel = if (playerProfiles.isNotEmpty()) {
            playerProfiles.map { it.averagePartyLevel }.average()
        } else {
            1.0
        }

        val dataContext = PlannerDataContext()
        for (source in dataSources) {
            source.enrich(server, players, dataContext)
        }

        val conceptPrompt = AiProfileRegistry.getConceptPrompt()
        val selectedProfile = AiProfileRegistry.pickEnabledProfile()
        val templates = buildTemplates(conceptPrompt, selectedProfile)
        syncTemplateRotation(templates)
        val rotationPreferredIds = cycleRemainingTemplateIds.toSet()

        val candidates = templates
            .asSequence()
            .filter { ignoreCooldown || !isOnCooldown(it) }
            .map { template ->
                template to scoreTemplate(
                    template = template,
                    playerCount = players.size,
                    averagePartyLevel = averagePartyLevel,
                    context = dataContext,
                    conceptPrompt = conceptPrompt,
                    selectedProfile = selectedProfile
                )
            }
            .sortedByDescending { it.second }
                .toList()

        if (candidates.isEmpty()) {
            val decision = AiGeneratedDecision(
                executed = false,
                reason = if (ignoreCooldown) "no_dynamic_templates" else "all_dynamic_templates_on_cooldown",
                selectedProfileId = selectedProfile?.id,
                playerCount = players.size,
                averagePartyLevel = averagePartyLevel,
                timCoreTaggedNearby = dataContext.timCoreTaggedNearby,
                trigger = trigger
            )
            lastDecision = decision
            return decision
        }

        val rotationCandidates = candidates.filter { (template, _) -> template.id in rotationPreferredIds }
        val effectiveCandidates = if (rotationCandidates.isNotEmpty()) rotationCandidates else candidates

        val topCandidates = effectiveCandidates.take(4)
        var selected = weightedPick(topCandidates) ?: topCandidates.first().first
        var target = computeTarget(selected, players.size, averagePartyLevel)
        var advisorUsed = false
        var advisorReason: String? = null

        val advice = ExternalAiAdvisor.requestTemplateAdvice(
            ExternalAdviceInput(
                conceptPrompt = conceptPrompt,
                profileId = selectedProfile?.id,
                profilePrompt = selectedProfile?.prompt,
                playerCount = players.size,
                averagePartyLevel = averagePartyLevel,
                timCoreTaggedNearby = dataContext.timCoreTaggedNearby,
                candidates = topCandidates.map { (template, score) ->
                    ExternalCandidate(
                        id = template.id,
                        mode = template.mode,
                        displayName = template.displayName,
                        localScore = score,
                        suggestedTarget = computeTarget(template, players.size, averagePartyLevel)
                    )
                }
            )
        )

        if (advice != null) {
            advisorUsed = true
            val advisedTemplate = topCandidates.firstOrNull { it.first.id == advice.preferredTemplateId }?.first
            if (advisedTemplate != null) {
                selected = advisedTemplate
            }
            target = (computeTarget(selected, players.size, averagePartyLevel) + advice.targetDelta).coerceIn(3, 28)
            advisorReason = advice.reason
        }

        val runtimeDef = buildRuntimeDefinition(selected)

        if (!execute) {
            val decision = AiGeneratedDecision(
                executed = false,
                reason = if (advisorUsed) "preview_advised" else "preview",
                selectedEventId = selected.id,
                selectedEventType = "AI_DYNAMIC",
                selectedProfileId = selectedProfile?.id,
                playerCount = players.size,
                averagePartyLevel = averagePartyLevel,
                timCoreTaggedNearby = dataContext.timCoreTaggedNearby,
                trigger = trigger
            )
            lastDecision = decision
            return decision
        }

        val started = CobblemonEventsMod.scheduler.forceStartDynamic(
            definition = runtimeDef,
            server = server,
            initialData = mapOf(
                "ai_dynamic_mode" to selected.mode,
                "ai_dynamic_target" to target,
                "ai_dynamic_template_id" to selected.id,
                "ai_dynamic_profile" to (selectedProfile?.id ?: "none")
            )
        )

        val decision = if (started) {
            markExecuted(selected)
            markTemplateConsumed(selected.id, templates)
            BroadcastUtil.broadcast(
                server,
                "${CobblemonEventsMod.config.prefix}[AI Dynamic] 신규 창의 이벤트 '${selected.displayName}' 시작 " +
                    "(모드:${selected.mode}, 목표:${target}, ${ADDON_DURATION_MINUTES}분, 프로필:${selectedProfile?.id ?: "none"}" +
                    (if (advisorUsed) ", 외부AI보정:on" else ", 외부AI보정:off") + ")"
            )
            if (!advisorReason.isNullOrBlank()) {
                CobblemonEventsMod.LOGGER.info("[AI Dynamic] advisor note: ${advisorReason.take(180)}")
            }
            AiGeneratedDecision(
                executed = true,
                reason = if (advisorUsed) "started_advised" else "started",
                selectedEventId = selected.id,
                selectedEventType = "AI_DYNAMIC",
                selectedProfileId = selectedProfile?.id,
                playerCount = players.size,
                averagePartyLevel = averagePartyLevel,
                timCoreTaggedNearby = dataContext.timCoreTaggedNearby,
                trigger = trigger
            )
        } else {
            AiGeneratedDecision(
                executed = false,
                reason = if (advisorUsed) "scheduler_rejected_start_advised" else "scheduler_rejected_start",
                selectedEventId = selected.id,
                selectedEventType = "AI_DYNAMIC",
                selectedProfileId = selectedProfile?.id,
                playerCount = players.size,
                averagePartyLevel = averagePartyLevel,
                timCoreTaggedNearby = dataContext.timCoreTaggedNearby,
                trigger = trigger
            )
        }

        lastDecision = decision
        return decision
    }

    fun getStatusLine(): String {
        val minutesUntil = (ticksUntilNextAutoPlan / 20L / 60L).coerceAtLeast(0)
        val rotation = rotationRemainingText()
        return "auto_enabled=$autoPlannerEnabled, next_auto_plan=${minutesUntil}m, last=${lastDecision.reason}, " +
            "last_event=${lastDecision.selectedEventId ?: "-"}, rotation_remaining=$rotation"
    }

    fun getLastDecision(): AiGeneratedDecision = lastDecision

    fun setAutoPlannerEnabled(enabled: Boolean) {
        autoPlannerEnabled = enabled
        if (enabled && ticksUntilNextAutoPlan <= 0L) {
            ticksUntilNextAutoPlan = AUTO_PLAN_INTERVAL_TICKS
        }
    }

    fun isAutoPlannerEnabled(): Boolean = autoPlannerEnabled

    private fun isOnCooldown(template: AiDynamicTemplate): Boolean {
        val typeLast = eventTypeLastExecutedTick[template.cooldownGroup]
        if (typeLast != null) {
            val typeCooldown = maxOf(
                MIN_EVENT_TYPE_COOLDOWN_TICKS,
                ADDON_DURATION_MINUTES.toLong() * 20L * 60L * 2L
            )
            if (serverTickCounter - typeLast < typeCooldown) return true
        }

        val idLast = eventIdLastExecutedTick[template.id]
        if (idLast != null) {
            val idCooldown = maxOf(
                MIN_EVENT_ID_COOLDOWN_TICKS,
                ADDON_DURATION_MINUTES.toLong() * 20L * 60L
            )
            if (serverTickCounter - idLast < idCooldown) return true
        }

        return false
    }

    private fun markExecuted(template: AiDynamicTemplate) {
        eventTypeLastExecutedTick[template.cooldownGroup] = serverTickCounter
        eventIdLastExecutedTick[template.id] = serverTickCounter
    }

    private fun syncTemplateRotation(templates: List<AiDynamicTemplate>) {
        if (templates.isEmpty()) {
            cycleRemainingTemplateIds.clear()
            return
        }
        val ids = templates.map { it.id }.toSet()
        cycleRemainingTemplateIds.retainAll(ids)
        if (cycleRemainingTemplateIds.isEmpty()) {
            cycleRemainingTemplateIds.addAll(ids)
        }
    }

    private fun markTemplateConsumed(templateId: String, templates: List<AiDynamicTemplate>) {
        if (templates.isEmpty()) return

        if (cycleRemainingTemplateIds.isEmpty()) {
            cycleRemainingTemplateIds.addAll(templates.map { it.id })
        }

        cycleRemainingTemplateIds.remove(templateId)
        if (cycleRemainingTemplateIds.isEmpty()) {
            cycleRemainingTemplateIds.addAll(templates.map { it.id })
        }
    }

    private fun rotationRemainingText(): String {
        if (cycleRemainingTemplateIds.isEmpty()) return "-"
        return cycleRemainingTemplateIds.joinToString("|")
    }

    private fun buildTemplates(conceptPrompt: String, profile: AiProfileEntry?): List<AiDynamicTemplate> {
        val prompt = (conceptPrompt + " " + (profile?.prompt ?: "")).lowercase()

        val base = mutableListOf(
            AiDynamicTemplate(
                id = "catch_rush",
                displayName = "포획 러시",
                description = "지정 시간 동안 포획 수를 달성하는 창의형 AI 이벤트",
                mode = "catch",
                targetLevel = 28.0,
                baseTarget = 6,
                rewardTier = 1,
                weight = 1.0,
                cooldownGroup = "catch"
            ),
            AiDynamicTemplate(
                id = "battle_chain",
                displayName = "배틀 체인",
                description = "지정 시간 동안 배틀 승리 누적을 달성하는 창의형 AI 이벤트",
                mode = "battle",
                targetLevel = 40.0,
                baseTarget = 5,
                rewardTier = 2,
                weight = 1.0,
                cooldownGroup = "battle"
            ),
            AiDynamicTemplate(
                id = "variety_scan",
                displayName = "도감 다양성 스캔",
                description = "서로 다른 종족 포획으로 목표를 채우는 창의형 AI 이벤트",
                mode = "variety",
                targetLevel = 32.0,
                baseTarget = 4,
                rewardTier = 2,
                weight = 1.1,
                cooldownGroup = "variety"
            ),
            AiDynamicTemplate(
                id = "hybrid_pressure",
                displayName = "하이브리드 미션",
                description = "포획과 배틀을 동시에 활용하는 고난도 AI 이벤트",
                mode = "hybrid",
                targetLevel = 52.0,
                baseTarget = 7,
                rewardTier = 3,
                weight = 0.8,
                cooldownGroup = "hybrid"
            )
        )

        // 프롬프트 방향성 반영
        if (containsAny(prompt, "탐험", "포획", "explorer", "catch")) {
            base.replaceAll { if (it.mode == "catch" || it.mode == "variety") it.copy(weight = it.weight + 0.4) else it }
        }
        if (containsAny(prompt, "전투", "배틀", "raid", "battle")) {
            base.replaceAll { if (it.mode == "battle" || it.mode == "hybrid") it.copy(weight = it.weight + 0.5) else it }
        }
        if (containsAny(prompt, "다양성", "도감", "variety")) {
            base.replaceAll { if (it.mode == "variety") it.copy(weight = it.weight + 0.6) else it }
        }

        return base
    }

    private fun scoreTemplate(
        template: AiDynamicTemplate,
        playerCount: Int,
        averagePartyLevel: Double,
        context: PlannerDataContext,
        conceptPrompt: String,
        selectedProfile: AiProfileEntry?
    ): Double {
        var score = 100.0 + (template.weight * 10.0)

        score -= abs(averagePartyLevel - template.targetLevel) * 1.2

        when (template.mode) {
            "battle" -> {
                if (playerCount >= 2) score += 8.0 else score -= 18.0
            }
            "hybrid" -> {
                if (playerCount >= 3) score += 10.0 else score -= 20.0
            }
            "catch" -> {
                score += 4.0
            }
            "variety" -> {
                if (playerCount <= 4) score += 6.0
            }
        }

        if (context.timCoreLoaded) {
            if (context.timCoreTaggedNearby > 20 && (template.mode == "battle" || template.mode == "hybrid")) {
                score -= 10.0
            }
            if (context.timCoreTaggedNearby <= 20 && template.mode == "variety") {
                score += 4.0
            }
        }

        val prompt = (conceptPrompt + " " + (selectedProfile?.prompt ?: "")).lowercase()
        if (containsAny(prompt, "밸런스", "안정", "경제")) {
            if (template.rewardTier >= 3) score -= 6.0
        }

        return score
    }

    private fun computeTarget(template: AiDynamicTemplate, playerCount: Int, averagePartyLevel: Double): Int {
        val levelFactor = (averagePartyLevel / 30.0).roundToInt().coerceAtLeast(0)
        val base = template.baseTarget + playerCount + levelFactor

        return when (template.mode) {
            "catch" -> base.coerceIn(6, 24)
            "battle" -> base.coerceIn(4, 18)
            "variety" -> (template.baseTarget + (playerCount / 2) + (levelFactor / 2)).coerceIn(4, 12)
            "hybrid" -> base.coerceIn(6, 20)
            else -> base.coerceIn(5, 16)
        }
    }

    private fun buildRuntimeDefinition(template: AiDynamicTemplate): EventDefinition {
        return EventDefinition(
            id = "aidyn_${template.id}_${System.currentTimeMillis()}",
            displayName = "AI ${template.displayName}",
            description = template.description,
            enabled = true,
            intervalMinutes = Int.MAX_VALUE,
            durationMinutes = ADDON_DURATION_MINUTES,
            startDelayMinutes = 0,
            announceBeforeMinutes = 0,
            requiredPlayerCount = 1,
            eventType = "AI_DYNAMIC",
            rewards = buildRewardPool(template.rewardTier)
        )
    }

    private fun buildRewardPool(tier: Int): RewardPool {
        val items = when (tier) {
            1 -> listOf(
                ItemRewardEntry("cobblemon:poke_ball", 8),
                ItemRewardEntry("cobblemon:great_ball", 2),
                ItemRewardEntry("cobblemon:exp_candy_xs", 2)
            )
            2 -> listOf(
                ItemRewardEntry("cobblemon:great_ball", 6),
                ItemRewardEntry("cobblemon:ultra_ball", 2),
                ItemRewardEntry("cobblemon:exp_candy_s", 2),
                ItemRewardEntry("cobblemon:rare_candy", 1)
            )
            else -> listOf(
                ItemRewardEntry("cobblemon:ultra_ball", 6),
                ItemRewardEntry("cobblemon:exp_candy_m", 2),
                ItemRewardEntry("cobblemon:rare_candy", 2),
                ItemRewardEntry("cobblemon:beast_ball", 1)
            )
        }

        return RewardPool(
            items = items,
            rewardMode = "RANDOM_MULTI",
            randomCount = 2,
            broadcastReward = false
        )
    }

    private fun <T> weightedPick(candidates: List<Pair<T, Double>>): T? {
        if (candidates.isEmpty()) return null
        val minScore = candidates.minOf { it.second }
        val shifted = candidates.map { (def, score) -> def to (score - minScore + 1.0).coerceAtLeast(0.1) }
        val total = shifted.sumOf { it.second }
        if (total <= 0.0) return shifted.first().first

        var point = Random.nextDouble(total)
        for ((def, weight) in shifted) {
            point -= weight
            if (point <= 0.0) return def
        }
        return shifted.last().first
    }

    private fun computeNextAutoIntervalTicks(playerCount: Int): Long {
        val minutes = when {
            playerCount >= 8 -> 8L
            playerCount >= 5 -> 10L
            playerCount >= 3 -> 12L
            else -> 16L
        }
        return minutes * 60L * 20L
    }

    private fun buildPlayerProfiles(players: List<ServerPlayerEntity>): List<PlannerPlayerProfile> {
        return players.map { player ->
            val party = runCatching { Cobblemon.storage.getParty(player) }.getOrNull()
            val partyPokemon = mutableListOf<Pokemon>()

            if (party != null) {
                for (slot in 0 until party.size()) {
                    val pokemon: Pokemon? = runCatching { party.get(slot) }.getOrNull()
                    if (pokemon != null) {
                        partyPokemon.add(pokemon)
                    }
                }
            }

            val avgLevel = if (partyPokemon.isNotEmpty()) {
                partyPokemon.map { it.level }.average()
            } else {
                1.0
            }

            PlannerPlayerProfile(
                uuid = player.uuidAsString,
                name = player.gameProfile.name,
                x = player.x,
                y = player.y,
                z = player.z,
                partySize = partyPokemon.size,
                averagePartyLevel = avgLevel
            )
        }
    }

    private interface PlannerDataSource {
        fun enrich(server: MinecraftServer, players: List<ServerPlayerEntity>, context: PlannerDataContext)
    }

    private object ServerCoreDataSource : PlannerDataSource {
        override fun enrich(server: MinecraftServer, players: List<ServerPlayerEntity>, context: PlannerDataContext) {
            val loader = FabricLoader.getInstance()
            context.raidDensLoaded = loader.isModLoaded("cobblemonraiddens") || loader.isModLoaded("raiddens")
            context.loadedCoreMods = listOf(
                "tim_core",
                "cobblemon",
                "fabricloader",
                "luckperms",
                "spark",
                "placeholderapi",
                "placeholder-api"
            ).count { loader.isModLoaded(it) }
        }
    }

    private object TimCoreDataSource : PlannerDataSource {
        override fun enrich(server: MinecraftServer, players: List<ServerPlayerEntity>, context: PlannerDataContext) {
            val loader = FabricLoader.getInstance()
            if (!loader.isModLoaded("tim_core")) return
            context.timCoreLoaded = true

            val seen = mutableSetOf<String>()
            for (player in players) {
                val world = player.serverWorld
                val box = Box(
                    player.x - TIM_CORE_SCAN_RADIUS, player.y - 24.0, player.z - TIM_CORE_SCAN_RADIUS,
                    player.x + TIM_CORE_SCAN_RADIUS, player.y + 24.0, player.z + TIM_CORE_SCAN_RADIUS
                )
                val entities = world.getEntitiesByClass(PokemonEntity::class.java, box) { entity ->
                    entity.isAlive && seen.add(entity.uuidAsString)
                }

                for (entity in entities) {
                    val nbt = entity.pokemon.persistentData
                    val hasBucket = nbt.contains("tim_core:spawned_in_bucket")
                    val hasSpawnCause = nbt.contains("tim_core:spawned_via")
                    if (hasBucket || hasSpawnCause) {
                        context.timCoreTaggedNearby++
                    }
                    if (hasBucket) context.timCoreBucketTaggedNearby++
                    if (hasSpawnCause) context.timCoreSpawnCauseTaggedNearby++
                }
            }
        }
    }

    private fun containsAny(text: String, vararg keywords: String): Boolean {
        return keywords.any { text.contains(it) }
    }

    private data class PlannerPlayerProfile(
        val uuid: String,
        val name: String,
        val x: Double,
        val y: Double,
        val z: Double,
        val partySize: Int,
        val averagePartyLevel: Double
    )

    private data class PlannerDataContext(
        var timCoreLoaded: Boolean = false,
        var timCoreTaggedNearby: Int = 0,
        var timCoreBucketTaggedNearby: Int = 0,
        var timCoreSpawnCauseTaggedNearby: Int = 0,
        var raidDensLoaded: Boolean = false,
        var loadedCoreMods: Int = 0
    )
}
