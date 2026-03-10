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
    val category: String,
    val mode: String,
    val targetLevel: Double,
    val baseTarget: Int,
    val rewardTier: Int,
    val weight: Double,
    val cooldownGroup: String,
    val sourceTag: String = "internal",
    val themeType: String? = null,
    val targetBiome: String? = null,
    val coreMechanism: String? = null,
    val specialEncounter: String? = null
)

object AiGeneratedContentPlanner {
    @Volatile
    private var autoPlannerEnabled = false

    private const val DEFAULT_AUTO_PLAN_INTERVAL_MINUTES_MIN = 15L
    private const val DEFAULT_AUTO_PLAN_INTERVAL_MINUTES_MAX = 30L
    private const val RETRY_INTERVAL_TICKS = 20L * 60L * 15L
    private const val MIN_EVENT_TYPE_COOLDOWN_TICKS = 20L * 60L * 25L
    private const val MIN_EVENT_ID_COOLDOWN_TICKS = 20L * 60L * 20L
    private const val TIM_CORE_SCAN_RADIUS = 48.0
    private const val DEFAULT_ADDON_DURATION_MINUTES = 5
    private const val MAX_ADDON_DURATION_MINUTES = 10
    private const val AI_DYNAMIC_DURATION_MINUTES = 5
    private const val AI_DYNAMIC_DURATION_MAX_MINUTES = 10
    private const val MIN_TARGET_COUNT = 4
    private const val MAX_TARGET_COUNT = 10
    private const val UPCOMING_BASE_EVENT_GUARD_TICKS = 20L * 60L * 6L
    private const val PROCEDURAL_TEMPLATE_BATCH_SIZE = 36
    private const val EXTERNAL_TEMPLATE_BATCH_SIZE = 24
    private const val TEMPLATE_DATABASE_SIZE = 150
    private const val MAX_TEMPLATE_POOL_SIZE = 150
    private const val CANDIDATE_WINDOW_SIZE = 12
    private const val TPS_STOP_THRESHOLD = 17.0
    private const val TPS_WARNING_THRESHOLD = 18.0
    private const val TPS_LOW_LOAD_THRESHOLD = 18.4
    private const val RECENT_OUTCOME_LIMIT = 24
    private const val PLAYER_BEHAVIOR_RETENTION_TICKS = 20L * 60L * 180L
    private const val PLAYER_BEHAVIOR_MAX_SPECIES = 64
    private const val MAX_TIMCORE_SCAN_PLAYERS = 8
    private const val AI_LEGENDARY_EVENT_FIXED_CHANCE = 0.0025 // 0.25%
    private const val NEXT_GEN_VERSION_TAG = "v3.1.0-pro-reserved"

    private val legendaryEncounterHints = setOf(
        "mewtwo", "mew", "lugia", "hooh", "ho-oh",
        "rayquaza", "dialga", "palkia", "giratina",
        "xerneas", "yveltal", "reshiram", "zekrom",
        "kyogre", "groudon", "solgaleo", "lunala"
    )

    private val eventTypeLastExecutedTick = mutableMapOf<String, Long>()
    private val eventIdLastExecutedTick = mutableMapOf<String, Long>()
    private val cycleRemainingTemplateIds = linkedSetOf<String>()
    private val recentOutcomes = mutableListOf<DynamicOutcome>()
    private val playerBehaviorLogs = mutableMapOf<String, PlayerBehaviorLog>()

    private val dataSources: List<PlannerDataSource> = listOf(
        ServerCoreDataSource,
        TimCoreDataSource
    )

    @Volatile
    private var initialized = false
    private var ticksUntilNextAutoPlan = computeNextAutoIntervalTicks()
    private var serverTickCounter = 0L
    private var promptTemplateCursor = 0
    private var lastDecision = AiGeneratedDecision(
        executed = false,
        reason = "not_generated_yet"
    )

    fun onServerStarted() {
        initialized = true
        ticksUntilNextAutoPlan = computeNextAutoIntervalTicks()
        serverTickCounter = 0L
        eventTypeLastExecutedTick.clear()
        eventIdLastExecutedTick.clear()
        cycleRemainingTemplateIds.clear()
        recentOutcomes.clear()
        playerBehaviorLogs.clear()
        promptTemplateCursor = 0
        lastDecision = AiGeneratedDecision(false, "server_started")
    }

    fun onServerStopping() {
        initialized = false
        ticksUntilNextAutoPlan = computeNextAutoIntervalTicks()
        serverTickCounter = 0L
        eventTypeLastExecutedTick.clear()
        eventIdLastExecutedTick.clear()
        cycleRemainingTemplateIds.clear()
        recentOutcomes.clear()
        playerBehaviorLogs.clear()
        promptTemplateCursor = 0
    }

    fun tick(server: MinecraftServer) {
        if (!initialized) return

        serverTickCounter++
        if (!autoPlannerEnabled) return

        ticksUntilNextAutoPlan--
        if (ticksUntilNextAutoPlan > 0) return

        val decision = generate(server, execute = true, ignoreCooldown = false, trigger = "auto")
        ticksUntilNextAutoPlan = if (decision.executed) {
            computeNextAutoIntervalTicks()
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
        val estimatedTps = estimateServerTps(server)
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

        if (execute && !ignoreCooldown && estimatedTps != null && estimatedTps < TPS_STOP_THRESHOLD) {
            val decision = AiGeneratedDecision(
                executed = false,
                reason = "tps_below_17",
                playerCount = players.size,
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
        dataContext.estimatedTps = estimatedTps
        dataContext.recentSuccessRate = recentSuccessRate()
        dataContext.recentAverageDurationMinutes = recentAverageDurationMinutes()
        for (source in dataSources) {
            source.enrich(server, players, dataContext)
        }
        val behaviorSignal = buildBehaviorSignal(players)

        val conceptPrompt = AiProfileRegistry.getConceptPrompt()
        val addonDurationMinutes = Random.nextInt(
            AI_DYNAMIC_DURATION_MINUTES,
            AI_DYNAMIC_DURATION_MAX_MINUTES + 1
        )
        val durationTargetCap = resolveDurationTargetCap(addonDurationMinutes)
        val selectedProfile = AiProfileRegistry.pickEnabledProfile()
        val seedTemplates = buildSeedTemplates(conceptPrompt, selectedProfile)
        val promptCatalogTemplates = buildPromptCatalogTemplates(
            conceptPrompt = conceptPrompt,
            selectedProfile = selectedProfile,
            averagePartyLevel = averagePartyLevel,
            estimatedTps = estimatedTps,
            playerCount = players.size
        )
        val proceduralTemplates = buildProceduralTemplates(
            seedTemplates = seedTemplates,
            conceptPrompt = conceptPrompt,
            selectedProfile = selectedProfile,
            playerCount = players.size,
            averagePartyLevel = averagePartyLevel,
            estimatedTps = estimatedTps
        )
        val externalTemplates = buildExternalTemplates(
            conceptPrompt = conceptPrompt,
            selectedProfile = selectedProfile,
            playerCount = players.size,
            averagePartyLevel = averagePartyLevel,
            dataContext = dataContext,
            seedTemplates = seedTemplates,
            execute = execute
        )
        val templates = mergeTemplatePool(
            seedTemplates,
            promptCatalogTemplates,
            proceduralTemplates,
            externalTemplates
        )
        syncTemplateRotation(templates)
        val rotationPreferredIds = cycleRemainingTemplateIds.toSet()

        val candidates = templates
            .asSequence()
            .filter { ignoreCooldown || !isOnCooldown(it, addonDurationMinutes) }
            .map { template ->
                template to scoreTemplate(
                    template = template,
                    playerCount = players.size,
                    averagePartyLevel = averagePartyLevel,
                    context = dataContext,
                    conceptPrompt = conceptPrompt,
                    selectedProfile = selectedProfile,
                    behaviorSignal = behaviorSignal
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

        val selectedArchetype = pickEventArchetype()
        val archetypeCandidates = effectiveCandidates.filter { (template, _) ->
            matchesArchetype(template, selectedArchetype)
        }
        val selectionCandidates = if (archetypeCandidates.isNotEmpty()) archetypeCandidates else effectiveCandidates
        val topCandidates = selectionCandidates.take(CANDIDATE_WINDOW_SIZE)
        var selected = weightedPick(topCandidates) ?: topCandidates.first().first
        var target = computeTarget(selected, players.size, averagePartyLevel, dataContext)
        var advisorUsed = false
        var advisorReason: String? = null

        val advice = if (execute) {
            ExternalAiAdvisor.requestTemplateAdvice(
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
                            suggestedTarget = computeTarget(template, players.size, averagePartyLevel, dataContext)
                        )
                    }
                )
            )
        } else {
            null
        }

        if (advice != null) {
            advisorUsed = true
            val advisedTemplate = topCandidates.firstOrNull { it.first.id == advice.preferredTemplateId }?.first
            if (advisedTemplate != null) {
                selected = advisedTemplate
            }
            target = (
                computeTarget(selected, players.size, averagePartyLevel, dataContext) + advice.targetDelta
                ).coerceIn(MIN_TARGET_COUNT, durationTargetCap)
            advisorReason = advice.reason
        }

        if (
            execute &&
            !ignoreCooldown &&
            estimatedTps != null &&
            estimatedTps < TPS_WARNING_THRESHOLD &&
            isHighLoadMode(selected.mode)
        ) {
            val fallback = selectionCandidates.firstOrNull { (template, _) -> !isHighLoadMode(template.mode) }?.first
            if (fallback == null) {
                val decision = AiGeneratedDecision(
                    executed = false,
                    reason = "tps_warning_no_low_load_candidate",
                    selectedProfileId = selectedProfile?.id,
                    playerCount = players.size,
                    averagePartyLevel = averagePartyLevel,
                    timCoreTaggedNearby = dataContext.timCoreTaggedNearby,
                    trigger = trigger
                )
                lastDecision = decision
                return decision
            }
            selected = fallback
            target = computeTarget(selected, players.size, averagePartyLevel, dataContext)
            advisorReason = listOfNotNull(advisorReason, "tps_guard_fallback_applied").joinToString(" | ")
        }

        if (!ignoreCooldown && isLegendaryRaidLikeTemplate(selected)) {
            val legendaryChance = computeLegendaryEventChance(
                playerCount = players.size,
                context = dataContext,
                template = selected
            )
            val chanceLabel = toPercentLabel(legendaryChance)
            val allowed = Random.nextDouble() < legendaryChance
            if (!allowed) {
                val nonLegendaryFallback = selectionCandidates
                    .asSequence()
                    .map { it.first }
                    .firstOrNull { !isLegendaryRaidLikeTemplate(it) }

                if (nonLegendaryFallback == null) {
                    val decision = AiGeneratedDecision(
                        executed = false,
                        reason = "legendary_ai_probability_gate_blocked",
                        selectedProfileId = selectedProfile?.id,
                        playerCount = players.size,
                        averagePartyLevel = averagePartyLevel,
                        timCoreTaggedNearby = dataContext.timCoreTaggedNearby,
                        trigger = trigger
                    )
                    lastDecision = decision
                    return decision
                }

                selected = nonLegendaryFallback
                target = computeTarget(selected, players.size, averagePartyLevel, dataContext)
                advisorReason = listOfNotNull(advisorReason, "legendary_ai_gate_fallback($chanceLabel)").joinToString(" | ")
            } else {
                advisorReason = listOfNotNull(advisorReason, "legendary_ai_gate_pass($chanceLabel)").joinToString(" | ")
            }
        }

        val durationScaledTarget = applyDurationTargetScale(
            target = target,
            durationMinutes = addonDurationMinutes,
            durationTargetCap = durationTargetCap
        )
        if (durationScaledTarget != target) {
            advisorReason = listOfNotNull(
                advisorReason,
                "duration_target_scaled:${target}->${durationScaledTarget}(${addonDurationMinutes}m)"
            ).joinToString(" | ")
            target = durationScaledTarget
        } else {
            target = target.coerceIn(MIN_TARGET_COUNT, durationTargetCap)
        }

        val scaledTarget = applyTpsTargetScale(target, estimatedTps)
        val finalScaledTarget = scaledTarget.coerceIn(MIN_TARGET_COUNT, durationTargetCap)
        if (finalScaledTarget != target) {
            advisorReason = listOfNotNull(advisorReason, "tps_target_scaled:${target}->${finalScaledTarget}").joinToString(" | ")
            target = finalScaledTarget
        }
        val runtimeDef = buildRuntimeDefinition(selected, addonDurationMinutes)

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

        val initialData = mutableMapOf<String, Any>(
            "ai_dynamic_mode" to selected.mode,
            "ai_dynamic_target" to target,
            "ai_dynamic_template_id" to selected.id,
            "ai_dynamic_profile" to (selectedProfile?.id ?: "none"),
            "ai_dynamic_source" to selected.sourceTag,
            "ai_dynamic_category" to selected.category
        )
        selected.themeType?.let { initialData["ai_dynamic_theme_type"] = it }
        selected.targetBiome?.let { initialData["ai_dynamic_target_biome"] = it }
        selected.coreMechanism?.let { initialData["ai_dynamic_core_mechanism"] = it }
        selected.specialEncounter?.let { initialData["ai_dynamic_special_encounter"] = it }

        val started = CobblemonEventsMod.scheduler.forceStartDynamic(
            definition = runtimeDef,
            server = server,
            initialData = initialData
        )

        val decision = if (started) {
            markExecuted(selected)
            markTemplateConsumed(selected.id, templates)
            val displayName = resolveKoreanTemplateName(selected)
            BroadcastUtil.broadcast(
                server,
                "${CobblemonEventsMod.config.prefix}[AI Dynamic] 신규 창의 이벤트 '${displayName}' 시작 " +
                    "(모드:${selected.mode}, 목표:${target}, ${addonDurationMinutes}분, 프로필:${selectedProfile?.id ?: "none"}" +
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
        val successRate = recentSuccessRate()?.let { String.format("%.2f", it) } ?: "-"
        val promptCount = AiPromptTemplateCatalog.templateCount()
        val promptSource = AiPromptTemplateCatalog.sourceName()
        val behaviorPlayers = playerBehaviorLogs.size
        val interval = AiProfileRegistry.getAutoPlanIntervalRangeMinutes()
        return "auto_enabled=$autoPlannerEnabled, next_auto_plan=${minutesUntil}m, last=${lastDecision.reason}, " +
            "last_event=${lastDecision.selectedEventId ?: "-"}, rotation_remaining=$rotation, " +
            "success_rate=$successRate, prompt_catalog=${promptCount}@${promptSource}, " +
            "behavior_players=$behaviorPlayers, interval=${interval.first}-${interval.second}m, nextgen=$NEXT_GEN_VERSION_TAG"
    }

    fun getNextGenReservationLine(): String {
        return "reserved_version=$NEXT_GEN_VERSION_TAG, features=species300,behavior_learning,tps_autoscale,ai_scheduler"
    }

    fun getLastDecision(): AiGeneratedDecision = lastDecision

    fun setAutoPlannerEnabled(enabled: Boolean) {
        autoPlannerEnabled = enabled
        if (enabled && ticksUntilNextAutoPlan <= 0L) {
            ticksUntilNextAutoPlan = computeNextAutoIntervalTicks()
        }
    }

    fun setAutoPlanIntervalFixed(minutes: Int) {
        AiProfileRegistry.setAutoPlanIntervalFixed(minutes)
        ticksUntilNextAutoPlan = computeNextAutoIntervalTicks()
    }

    fun setAutoPlanIntervalRange(minMinutes: Int, maxMinutes: Int) {
        AiProfileRegistry.setAutoPlanIntervalRange(minMinutes, maxMinutes)
        ticksUntilNextAutoPlan = computeNextAutoIntervalTicks()
    }

    fun getAutoPlanIntervalRangeMinutes(): Pair<Int, Int> = AiProfileRegistry.getAutoPlanIntervalRangeMinutes()

    fun isAutoPlannerEnabled(): Boolean = autoPlannerEnabled

    private fun isOnCooldown(template: AiDynamicTemplate, addonDurationMinutes: Int): Boolean {
        val typeLast = eventTypeLastExecutedTick[template.cooldownGroup]
        if (typeLast != null) {
            val typeCooldown = maxOf(
                MIN_EVENT_TYPE_COOLDOWN_TICKS,
                addonDurationMinutes.toLong() * 20L * 60L * 2L
            )
            if (serverTickCounter - typeLast < typeCooldown) return true
        }

        val idLast = eventIdLastExecutedTick[template.id]
        if (idLast != null) {
            val idCooldown = maxOf(
                MIN_EVENT_ID_COOLDOWN_TICKS,
                addonDurationMinutes.toLong() * 20L * 60L
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
        val limit = 8
        val head = cycleRemainingTemplateIds.take(limit)
        return if (cycleRemainingTemplateIds.size <= limit) {
            head.joinToString("|")
        } else {
            "${head.joinToString("|")}|...(+${cycleRemainingTemplateIds.size - limit})"
        }
    }

    private fun buildSeedTemplates(conceptPrompt: String, profile: AiProfileEntry?): List<AiDynamicTemplate> {
        val prompt = (conceptPrompt + " " + (profile?.prompt ?: "")).lowercase()

        val base = mutableListOf(
            AiDynamicTemplate(
                id = "catch_rush",
                displayName = "포획 러시",
                description = "지정 시간 동안 포획 수를 달성하는 창의형 AI 이벤트",
                category = "general",
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
                category = "world_raid",
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
                category = "type_surge",
                mode = "variety",
                targetLevel = 32.0,
                baseTarget = 3,
                rewardTier = 2,
                weight = 0.72,
                cooldownGroup = "variety"
            ),
            AiDynamicTemplate(
                id = "hybrid_pressure",
                displayName = "하이브리드 미션",
                description = "포획과 배틀을 동시에 활용하는 고난도 AI 이벤트",
                category = "world_raid",
                mode = "hybrid",
                targetLevel = 52.0,
                baseTarget = 7,
                rewardTier = 3,
                weight = 0.8,
                cooldownGroup = "hybrid"
            ),
            AiDynamicTemplate(
                id = "migration_hunt",
                displayName = "포켓몬 대이동",
                description = "특정 포켓몬 스폰이 활발한 구역에서 포획 목표를 달성하는 이벤트",
                category = "migration",
                mode = "catch",
                targetLevel = 30.0,
                baseTarget = 7,
                rewardTier = 1,
                weight = 1.05,
                cooldownGroup = "catch"
            ),
            AiDynamicTemplate(
                id = "legend_trace",
                displayName = "전설 포켓몬 추적",
                description = "단서를 모아 전설 추적 진행도를 채우는 운영형 이벤트",
                category = "legendary_tracking",
                mode = "variety",
                targetLevel = 45.0,
                baseTarget = 5,
                rewardTier = 2,
                weight = 0.55,
                cooldownGroup = "variety"
            ),
            AiDynamicTemplate(
                id = "type_overdrive",
                displayName = "타입 대폭주",
                description = "특정 타입 중심으로 목표를 채우는 단기 폭주형 이벤트",
                category = "type_surge",
                mode = "variety",
                targetLevel = 36.0,
                baseTarget = 5,
                rewardTier = 2,
                weight = 0.65,
                cooldownGroup = "variety"
            )
        )

        // 프롬프트 방향성 반영
        if (containsAny(prompt, "탐험", "포획", "explorer", "catch")) {
            base.replaceAll {
                when (it.mode) {
                    "catch" -> it.copy(weight = it.weight + 0.35)
                    "variety" -> it.copy(weight = it.weight + 0.08)
                    else -> it
                }
            }
        }
        if (containsAny(prompt, "전투", "배틀", "raid", "battle")) {
            base.replaceAll { if (it.mode == "battle" || it.mode == "hybrid") it.copy(weight = it.weight + 0.5) else it }
        }
        if (containsAny(prompt, "다양성", "도감", "variety")) {
            base.replaceAll { if (it.mode == "variety") it.copy(weight = it.weight + 0.22) else it }
        }

        return base
    }

    private fun buildPromptCatalogTemplates(
        conceptPrompt: String,
        selectedProfile: AiProfileEntry?,
        averagePartyLevel: Double,
        estimatedTps: Double?,
        playerCount: Int
    ): List<AiDynamicTemplate> {
        if (!AiProfileRegistry.isPromptTemplateCatalogEnabled()) {
            return emptyList()
        }

        val catalog = AiPromptTemplateCatalog.getTemplates()
        if (catalog.isEmpty()) return emptyList()

        val configuredBatch = AiProfileRegistry.getPromptTemplateCatalogBatchSize()
        val batchSize = computePromptCatalogBatchSize(configuredBatch, estimatedTps, playerCount)
            .coerceAtMost(catalog.size)
            .coerceAtLeast(1)

        val start = (promptTemplateCursor % catalog.size).coerceAtLeast(0)
        val selectedRaw = ArrayList<PromptTemplateEntry>(batchSize)
        for (idx in 0 until batchSize) {
            selectedRaw.add(catalog[(start + idx) % catalog.size])
        }
        promptTemplateCursor = (start + batchSize) % catalog.size

        val prompt = (conceptPrompt + " " + (selectedProfile?.prompt ?: "")).lowercase()

        return selectedRaw.mapIndexedNotNull { index, raw ->
            val mode = normalizeMode(raw.mode)
            val category = normalizeArchetype(raw.category, mode)
            val sanitizedId = sanitizeIdPart(raw.id).ifBlank {
                "prompt_${System.nanoTime()}_${Random.nextInt(1000, 9999)}"
            }
            val rewardTier = raw.rewardTier.coerceIn(1, 3)
            val baseWeight = raw.weight.coerceIn(0.25, 4.0)
            val weightBoost = promptWeightBoost(
                prompt = prompt,
                mode = mode,
                category = category,
                themeType = raw.themeType
            )
            val targetLevel = (
                averagePartyLevel + Random.nextDouble(-12.0, 14.0)
                ).coerceIn(8.0, 95.0)

            AiDynamicTemplate(
                id = "pc_$sanitizedId",
                displayName = localizePromptCatalogName(raw, category, mode, index).take(40),
                description = raw.description.take(180),
                category = category,
                mode = mode,
                targetLevel = targetLevel,
                baseTarget = raw.targetHint.coerceIn(MIN_TARGET_COUNT, MAX_TARGET_COUNT),
                rewardTier = rewardTier,
                weight = (baseWeight + weightBoost).coerceIn(0.25, 4.2),
                cooldownGroup = sanitizeIdPart(raw.cooldownGroup).ifBlank { mode }.take(24),
                sourceTag = "prompt_catalog",
                themeType = raw.themeType?.trim()?.lowercase()?.ifBlank { null },
                targetBiome = raw.targetBiome?.trim()?.lowercase()?.ifBlank { null },
                coreMechanism = raw.coreMechanism?.trim()?.ifBlank { null },
                specialEncounter = raw.specialEncounter?.trim()?.lowercase()?.ifBlank { null }
            )
        }
    }

    private fun promptWeightBoost(
        prompt: String,
        mode: String,
        category: String,
        themeType: String?
    ): Double {
        var boost = 0.0

        if (containsAny(prompt, "포획", "catch", "capture") && mode == "catch") boost += 0.28
        if (containsAny(prompt, "배틀", "raid", "battle", "전투") && (mode == "battle" || mode == "hybrid")) {
            boost += 0.32
        }
        if (containsAny(prompt, "도감", "variety", "diversity", "탐험") && mode == "variety") boost += 0.10

        if (containsAny(prompt, "world raid", "월드 레이드") && category == "world_raid") boost += 0.22
        if (containsAny(prompt, "migration", "대이동") && category == "migration") boost += 0.22
        if (containsAny(prompt, "legendary", "전설") && category == "legendary_tracking") boost += 0.22
        if (containsAny(prompt, "type outbreak", "타입 대폭주") && category == "type_surge") boost += 0.22

        val normalizedTheme = themeType?.trim()?.lowercase()
        if (!normalizedTheme.isNullOrBlank() && containsAny(prompt, normalizedTheme)) {
            boost += 0.18
        }

        return boost
    }

    private fun computePromptCatalogBatchSize(configuredBatch: Int, estimatedTps: Double?, playerCount: Int): Int {
        var size = configuredBatch.coerceIn(12, 120)
        if (playerCount <= 2) size = (size * 0.75).toInt()
        if (estimatedTps != null && estimatedTps < 19.0) size = (size * 0.8).toInt()
        if (estimatedTps != null && estimatedTps < TPS_LOW_LOAD_THRESHOLD) size = (size * 0.6).toInt()
        return size.coerceAtLeast(10)
    }

    private fun computeProceduralBatchSize(estimatedTps: Double?, playerCount: Int): Int {
        var size = PROCEDURAL_TEMPLATE_BATCH_SIZE
        if (playerCount <= 2) size = (size * 0.7).toInt()
        if (estimatedTps != null && estimatedTps < 19.0) size = (size * 0.8).toInt()
        if (estimatedTps != null && estimatedTps < TPS_LOW_LOAD_THRESHOLD) size = (size * 0.55).toInt()
        return size.coerceIn(10, PROCEDURAL_TEMPLATE_BATCH_SIZE)
    }

    private fun computeExternalTemplateBatchSize(estimatedTps: Double?, playerCount: Int): Int {
        var size = EXTERNAL_TEMPLATE_BATCH_SIZE
        if (playerCount <= 2) size = (size * 0.75).toInt()
        if (estimatedTps != null && estimatedTps < 19.0) size = (size * 0.7).toInt()
        if (estimatedTps != null && estimatedTps < TPS_LOW_LOAD_THRESHOLD) size = (size * 0.5).toInt()
        return size.coerceIn(6, EXTERNAL_TEMPLATE_BATCH_SIZE)
    }

    private fun mergeTemplatePool(vararg groups: List<AiDynamicTemplate>): List<AiDynamicTemplate> {
        val seenIds = mutableSetOf<String>()
        val merged = mutableListOf<AiDynamicTemplate>()
        for (group in groups) {
            for (template in group) {
                val id = sanitizeIdPart(template.id).ifBlank { "template_${System.nanoTime()}" }
                if (!seenIds.add(id)) continue
                merged.add(template.copy(id = id))
                if (merged.size >= MAX_TEMPLATE_POOL_SIZE) {
                    return merged
                }
            }
        }

        if (merged.isNotEmpty() && merged.size < TEMPLATE_DATABASE_SIZE) {
            val seedPool = merged.toList()
            var idx = 0
            while (merged.size < TEMPLATE_DATABASE_SIZE && merged.size < MAX_TEMPLATE_POOL_SIZE) {
                val seed = seedPool[idx % seedPool.size]
                val syntheticId = "${sanitizeIdPart(seed.id)}_db_${idx}_${System.nanoTime()}"
                if (seenIds.add(syntheticId)) {
                    merged.add(
                        seed.copy(
                            id = syntheticId.take(80),
                            displayName = "${seed.displayName} #${idx + 1}".take(40)
                        )
                    )
                }
                idx++
            }
        }

        return merged.take(MAX_TEMPLATE_POOL_SIZE)
    }

    private fun buildProceduralTemplates(
        seedTemplates: List<AiDynamicTemplate>,
        conceptPrompt: String,
        selectedProfile: AiProfileEntry?,
        playerCount: Int,
        averagePartyLevel: Double,
        estimatedTps: Double?
    ): List<AiDynamicTemplate> {
        if (seedTemplates.isEmpty()) return emptyList()

        val prompt = (conceptPrompt + " " + (selectedProfile?.prompt ?: "")).lowercase()
        val now = System.currentTimeMillis()
        val modifiers = listOf("혜성", "혼돈", "연쇄", "극한", "미로", "폭주", "역전", "광란", "초월", "기습")
        val results = mutableListOf<AiDynamicTemplate>()
        val proceduralBatchSize = computeProceduralBatchSize(estimatedTps, playerCount)

        for (idx in 0 until proceduralBatchSize) {
            val mode = pickMode(prompt)
            val category = pickEventArchetype()
            val seed = seedTemplates[Random.nextInt(seedTemplates.size)]
            val flavor = modifiers[Random.nextInt(modifiers.size)]
            val targetHint = (baseTargetForMode(mode) + playerCount + Random.nextInt(-1, 3)).coerceIn(
                MIN_TARGET_COUNT,
                MAX_TARGET_COUNT
            )
            val rewardTier = (
                seed.rewardTier + (if (mode == "hybrid") 1 else 0) + Random.nextInt(-1, 2)
                ).coerceIn(1, 3)
            val weight = (seed.weight + Random.nextDouble(-0.35, 0.85)).coerceIn(0.35, 3.0)
            val id = "proc_${category}_${mode}_${now}_${idx}_${Random.nextInt(1000, 9999)}"
            val displayName = "$flavor ${modeDisplay(mode)} 미션"
            val description = "AI 절차 생성 이벤트: ${modeDisplay(mode)} 중심 목표 $targetHint 달성"
            val targetLevel = (averagePartyLevel + Random.nextDouble(-12.0, 16.0)).coerceIn(8.0, 95.0)

            results.add(
                AiDynamicTemplate(
                    id = id,
                    displayName = displayName.take(40),
                    description = description.take(180),
                    category = category,
                    mode = mode,
                    targetLevel = targetLevel,
                    baseTarget = targetHint,
                    rewardTier = rewardTier,
                    weight = weight,
                    cooldownGroup = mode
                )
            )
        }

        return results
    }

    private fun buildExternalTemplates(
        conceptPrompt: String,
        selectedProfile: AiProfileEntry?,
        playerCount: Int,
        averagePartyLevel: Double,
        dataContext: PlannerDataContext,
        seedTemplates: List<AiDynamicTemplate>,
        execute: Boolean
    ): List<AiDynamicTemplate> {
        if (seedTemplates.isEmpty()) return emptyList()
        if (!execute) return emptyList()
        val estimatedTps = dataContext.estimatedTps
        if (estimatedTps != null && estimatedTps < TPS_LOW_LOAD_THRESHOLD) {
            return emptyList()
        }

        val seedCandidates = seedTemplates.take(8).map { template ->
            ExternalCandidate(
                id = template.id,
                mode = template.mode,
                displayName = template.displayName,
                localScore = template.weight * 10.0,
                suggestedTarget = template.baseTarget.coerceIn(MIN_TARGET_COUNT, MAX_TARGET_COUNT)
            )
        }

        val generated = ExternalAiAdvisor.requestGeneratedTemplates(
            ExternalTemplateGenerationInput(
                conceptPrompt = conceptPrompt,
                profileId = selectedProfile?.id,
                profilePrompt = selectedProfile?.prompt,
                playerCount = playerCount,
                averagePartyLevel = averagePartyLevel,
                timCoreTaggedNearby = dataContext.timCoreTaggedNearby,
                desiredCount = computeExternalTemplateBatchSize(estimatedTps, playerCount),
                seedCandidates = seedCandidates
            )
        )

        if (generated.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()
        return generated.mapIndexedNotNull { index, raw ->
            toDynamicTemplate(raw, now, index, playerCount, averagePartyLevel)
        }
    }

    private fun toDynamicTemplate(
        raw: ExternalGeneratedTemplate,
        now: Long,
        index: Int,
        playerCount: Int,
        averagePartyLevel: Double
    ): AiDynamicTemplate? {
        val mode = normalizeMode(raw.mode)
        val category = normalizeArchetype(raw.category, mode)
        val safeIdFromAi = sanitizeIdPart(raw.id)
        val id = if (safeIdFromAi.isBlank()) {
            "ext_${category}_${mode}_${now}_${index}_${Random.nextInt(1000, 9999)}"
        } else {
            "ext_${safeIdFromAi}_${now}_${index}"
        }.take(80)

        val displayName = raw.displayName?.trim().orEmpty().ifBlank {
            "외부 AI ${modeDisplay(mode)}"
        }.take(40)
        val description = raw.description?.trim().orEmpty().ifBlank {
            "외부 AI가 생성한 창의형 ${modeDisplay(mode)} 이벤트"
        }.take(180)
        val targetHint = raw.targetHint?.coerceIn(MIN_TARGET_COUNT, MAX_TARGET_COUNT)
            ?: (baseTargetForMode(mode) + (playerCount / 2)).coerceIn(MIN_TARGET_COUNT, MAX_TARGET_COUNT)
        val rewardTier = raw.rewardTier?.coerceIn(1, 3) ?: rewardTierForMode(mode, targetHint)
        val weight = (raw.weight ?: (1.0 + rewardTier * 0.12)).coerceIn(0.3, 3.5)
        val cooldownGroup = sanitizeIdPart(raw.cooldownGroup).ifBlank { mode }.take(24)
        val targetLevel = (averagePartyLevel + Random.nextDouble(-10.0, 14.0)).coerceIn(8.0, 95.0)

        if (displayName.isBlank() || description.isBlank()) return null

        return AiDynamicTemplate(
            id = id,
            displayName = displayName,
            description = description,
            category = category,
            mode = mode,
            targetLevel = targetLevel,
            baseTarget = targetHint,
            rewardTier = rewardTier,
            weight = weight,
            cooldownGroup = cooldownGroup
        )
    }

    private fun pickMode(prompt: String): String {
        val weightedModes = mutableListOf<String>()
        repeat(3) { weightedModes.add("catch") }
        repeat(3) { weightedModes.add("battle") }
        repeat(1) { weightedModes.add("variety") }
        repeat(2) { weightedModes.add("hybrid") }

        if (containsAny(prompt, "포획", "catch", "collector", "수집")) {
            repeat(3) { weightedModes.add("catch") }
            repeat(1) { weightedModes.add("variety") }
        }
        if (containsAny(prompt, "배틀", "전투", "raid", "battle")) {
            repeat(3) { weightedModes.add("battle") }
            repeat(2) { weightedModes.add("hybrid") }
        }
        if (containsAny(prompt, "도감", "variety", "다양")) {
            repeat(2) { weightedModes.add("variety") }
        }
        if (containsAny(prompt, "복합", "혼합", "hybrid", "combo")) {
            repeat(3) { weightedModes.add("hybrid") }
        }

        return weightedModes[Random.nextInt(weightedModes.size)]
    }

    private fun normalizeMode(rawMode: String?): String {
        val mode = rawMode?.trim()?.lowercase().orEmpty()
        if (mode.isBlank()) return "catch"

        return when {
            mode in setOf("catch", "capture", "collector", "hunt", "rush", "farm", "loot") -> "catch"
            mode in setOf("battle", "combat", "raid", "boss", "fight", "duel", "slay", "defeat") -> "battle"
            mode in setOf("variety", "dex", "collection", "species", "diversity", "scan", "catalog") -> "variety"
            mode in setOf("hybrid", "mixed", "combo", "chain", "gauntlet", "triathlon", "fusion") -> "hybrid"
            mode.contains("catch") || mode.contains("capture") || mode.contains("수집") || mode.contains("포획") -> "catch"
            mode.contains("battle") || mode.contains("raid") || mode.contains("fight") || mode.contains("전투") -> "battle"
            mode.contains("variety") || mode.contains("species") || mode.contains("도감") -> "variety"
            mode.contains("hybrid") || mode.contains("combo") || mode.contains("혼합") || mode.contains("복합") -> "hybrid"
            else -> "catch"
        }
    }

    private fun normalizeArchetype(rawCategory: String?, mode: String): String {
        val category = rawCategory?.trim()?.lowercase().orEmpty()
        if (category in setOf("general", "migration", "world_raid", "legendary_tracking", "type_surge")) {
            return category
        }
        return when (mode) {
            "battle", "hybrid" -> "world_raid"
            "variety" -> "type_surge"
            else -> "general"
        }
    }

    private fun pickEventArchetype(): String {
        val roll = Random.nextDouble(100.0)
        return when {
            roll < 56.0 -> "general"
            roll < 76.0 -> "migration"
            roll < 91.0 -> "world_raid"
            roll < 92.5 -> "legendary_tracking" // 1.5%
            else -> "type_surge"
        }
    }

    private fun matchesArchetype(template: AiDynamicTemplate, archetype: String): Boolean {
        if (template.category == archetype) return true
        return when (archetype) {
            "general" -> true
            "migration" -> template.mode == "catch"
            "world_raid" -> template.mode == "battle" || template.mode == "hybrid"
            "legendary_tracking" -> template.mode == "variety" || template.mode == "battle"
            "type_surge" -> template.mode == "variety" || template.mode == "catch"
            else -> true
        }
    }

    private fun baseTargetForMode(mode: String): Int {
        return when (mode) {
            "catch" -> 7
            "battle" -> 5
            "variety" -> 4
            "hybrid" -> 7
            else -> 6
        }
    }

    private fun rewardTierForMode(mode: String, targetHint: Int): Int {
        val byMode = when (mode) {
            "hybrid" -> 3
            "battle" -> 2
            "variety" -> 2
            else -> 1
        }
        return (byMode + if (targetHint >= 10) 1 else 0).coerceIn(1, 3)
    }

    private fun modeDisplay(mode: String): String {
        return when (mode) {
            "catch" -> "포획"
            "battle" -> "배틀"
            "variety" -> "다양성"
            "hybrid" -> "하이브리드"
            else -> "미션"
        }
    }

    private fun modeLabelKo(mode: String): String {
        return when (mode) {
            "catch" -> "포획형"
            "battle" -> "전투형"
            "variety" -> "추적형"
            "hybrid" -> "협동형"
            else -> "일반형"
        }
    }

    private fun categoryLabelKo(category: String): String {
        return when (category) {
            "general" -> "일반 이벤트"
            "migration" -> "포켓몬 대이동"
            "world_raid" -> "월드 레이드"
            "legendary_tracking" -> "전설 추적"
            "type_surge" -> "타입 대폭주"
            else -> "AI 이벤트"
        }
    }

    private fun typeLabelKo(themeType: String?): String? {
        return when (themeType?.trim()?.lowercase()) {
            "normal" -> "노말"
            "fire" -> "불꽃"
            "water" -> "물"
            "electric" -> "전기"
            "grass" -> "풀"
            "ice" -> "얼음"
            "fighting" -> "격투"
            "poison" -> "독"
            "ground" -> "땅"
            "flying" -> "비행"
            "psychic" -> "에스퍼"
            "bug" -> "벌레"
            "rock" -> "바위"
            "ghost" -> "고스트"
            "dragon" -> "드래곤"
            "dark" -> "악"
            "steel" -> "강철"
            "fairy" -> "페어리"
            else -> null
        }
    }

    private fun localizePromptCatalogName(
        raw: PromptTemplateEntry,
        category: String,
        mode: String,
        index: Int
    ): String {
        val categoryKo = categoryLabelKo(category)
        val modeKo = modeLabelKo(mode)
        val themeKo = typeLabelKo(raw.themeType)
        val number = Regex("""\d+""")
            .find(raw.sourceEventName ?: raw.id)
            ?.value
            ?: (index + 1).toString()

        val themePart = if (themeKo.isNullOrBlank()) "" else "·$themeKo"
        return "$categoryKo $modeKo $number$themePart"
    }

    private fun resolveKoreanTemplateName(template: AiDynamicTemplate): String {
        val raw = template.displayName.trim()
        val hasHangul = raw.any { it in '가'..'힣' }
        if (hasHangul) return raw

        val categoryKo = categoryLabelKo(template.category)
        val modeKo = modeLabelKo(template.mode)
        val themeKo = typeLabelKo(template.themeType)
        return if (themeKo.isNullOrBlank()) {
            "$categoryKo $modeKo"
        } else {
            "$categoryKo $modeKo·$themeKo"
        }
    }

    private fun isLegendaryRaidLikeTemplate(template: AiDynamicTemplate): Boolean {
        if (template.category == "legendary_tracking") return true
        if (template.mode != "battle" && template.mode != "hybrid") return false

        val special = template.specialEncounter?.trim()?.lowercase().orEmpty()
        if (special in legendaryEncounterHints) return true

        val text = (
            template.displayName + " " +
                template.description + " " +
                (template.specialEncounter ?: "")
            ).lowercase()

        if (containsAny(text, "전설", "legend", "legendary", "raid boss", "레전더리")) {
            return true
        }
        return legendaryEncounterHints.any { text.contains(it) }
    }

    private fun sanitizeIdPart(value: String?): String {
        val src = value?.trim()?.lowercase().orEmpty()
        if (src.isBlank()) return ""

        val out = buildString(src.length) {
            for (ch in src) {
                when {
                    ch in 'a'..'z' -> append(ch)
                    ch in '0'..'9' -> append(ch)
                    ch == '_' || ch == '-' -> append(ch)
                    ch == ' ' -> append('_')
                }
            }
        }
        return out.trim('_', '-').take(64)
    }

    private fun scoreTemplate(
        template: AiDynamicTemplate,
        playerCount: Int,
        averagePartyLevel: Double,
        context: PlannerDataContext,
        conceptPrompt: String,
        selectedProfile: AiProfileEntry?,
        behaviorSignal: BehaviorSignal
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
                if (playerCount <= 4) score += 2.0
            }
        }

        if (context.timCoreLoaded) {
            if (context.timCoreTaggedNearby > 20 && (template.mode == "battle" || template.mode == "hybrid")) {
                score -= 10.0
            }
            if (context.timCoreTaggedNearby <= 20 && template.mode == "variety") {
                score += 1.0
            }
        }

        when (template.mode) {
            "catch" -> score += behaviorSignal.catchBias
            "battle" -> score += behaviorSignal.battleBias
            "hybrid" -> score += (behaviorSignal.catchBias + behaviorSignal.battleBias) * 0.55
            "variety" -> score += behaviorSignal.varietyBias
        }
        if (template.specialEncounter != null && template.specialEncounter in behaviorSignal.recentSpeciesSet) {
            score += 1.8
        }

        val tps = context.estimatedTps
        if (tps != null) {
            if (tps < TPS_WARNING_THRESHOLD && isHighLoadMode(template.mode)) {
                score -= 30.0
            }
            if (tps >= 19.0 && template.mode == "catch") {
                score += 2.0
            }
        }

        when (template.category) {
            "migration" -> if (template.mode == "catch") score += 3.0
            "world_raid" -> if (template.mode == "battle" || template.mode == "hybrid") score += 3.0
            "legendary_tracking" -> if (template.mode == "variety") score += 1.0
            "type_surge" -> if (template.mode == "variety") score += 1.0
        }

        val prompt = (conceptPrompt + " " + (selectedProfile?.prompt ?: "")).lowercase()
        if (containsAny(prompt, "밸런스", "안정", "경제")) {
            if (template.rewardTier >= 3) score -= 6.0
        }

        return score
    }

    private fun computeTarget(
        template: AiDynamicTemplate,
        playerCount: Int,
        averagePartyLevel: Double,
        context: PlannerDataContext
    ): Int {
        val difficulty = resolveDifficultyBand(
            playerCount = playerCount,
            recentSuccessRate = context.recentSuccessRate,
            recentAverageDurationMinutes = context.recentAverageDurationMinutes,
            estimatedTps = context.estimatedTps
        )

        val range = when (difficulty) {
            DifficultyBand.EASY -> 4..5
            DifficultyBand.NORMAL -> 6..7
            DifficultyBand.HARD -> 8..9
            DifficultyBand.VERY_HARD -> 10..10
        }

        val levelAdjust = when {
            averagePartyLevel >= 65.0 -> 1
            averagePartyLevel <= 20.0 -> -1
            else -> 0
        }
        val modeAdjust = when (template.mode) {
            "variety" -> -2
            "hybrid" -> 1
            else -> 0
        }
        val base = template.baseTarget + (playerCount / 4) + levelAdjust + modeAdjust
        val computed = base.coerceIn(range.first, range.last).coerceIn(MIN_TARGET_COUNT, MAX_TARGET_COUNT)
        return if (template.mode == "variety") {
            (computed - 1).coerceAtLeast(MIN_TARGET_COUNT)
        } else {
            computed
        }
    }

    private fun resolveDifficultyBand(
        playerCount: Int,
        recentSuccessRate: Double?,
        recentAverageDurationMinutes: Double?,
        estimatedTps: Double?
    ): DifficultyBand {
        var level = when {
            playerCount <= 3 -> 0
            playerCount <= 7 -> 1
            playerCount <= 12 -> 2
            else -> 3
        }

        if (recentSuccessRate != null) {
            if (recentSuccessRate >= 0.78) level += 1
            if (recentSuccessRate <= 0.42) level -= 1
        }

        if (recentAverageDurationMinutes != null) {
            if (recentAverageDurationMinutes <= 5.0) level += 1
            if (recentAverageDurationMinutes >= 14.0) level -= 1
        }

        if (estimatedTps != null && estimatedTps < TPS_WARNING_THRESHOLD) {
            level -= 1
        }

        return when (level.coerceIn(0, 3)) {
            0 -> DifficultyBand.EASY
            1 -> DifficultyBand.NORMAL
            2 -> DifficultyBand.HARD
            else -> DifficultyBand.VERY_HARD
        }
    }

    private fun buildRuntimeDefinition(template: AiDynamicTemplate, addonDurationMinutes: Int): EventDefinition {
        val safeDurationMinutes = addonDurationMinutes
            .takeIf { it > 0 }
            ?.coerceAtMost(MAX_ADDON_DURATION_MINUTES)
            ?: DEFAULT_ADDON_DURATION_MINUTES
        val displayName = resolveKoreanTemplateName(template)

        val detailParts = mutableListOf<String>()
        if (!template.themeType.isNullOrBlank()) detailParts.add("theme=${template.themeType}")
        if (!template.targetBiome.isNullOrBlank()) detailParts.add("biome=${template.targetBiome}")
        if (!template.specialEncounter.isNullOrBlank()) detailParts.add("special=${template.specialEncounter}")
        if (!template.coreMechanism.isNullOrBlank()) detailParts.add("mechanism=${template.coreMechanism}")
        val description = if (detailParts.isEmpty()) {
            template.description
        } else {
            "${template.description} | ${detailParts.joinToString(", ")}".take(220)
        }

        return EventDefinition(
            id = "aidyn_${template.id}_${System.currentTimeMillis()}",
            displayName = "AI $displayName",
            description = description,
            enabled = true,
            intervalMinutes = Int.MAX_VALUE,
            durationMinutes = safeDurationMinutes,
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
                ItemRewardEntry("cobblemon:exp_candy_s", 3),
                ItemRewardEntry("cobblemon:rare_candy", 1),
                ItemRewardEntry("cobblemon:timer_ball", 3)
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

    private fun computeNextAutoIntervalTicks(): Long {
        val range = runCatching { AiProfileRegistry.getAutoPlanIntervalRangeMinutes() }
            .getOrElse {
                DEFAULT_AUTO_PLAN_INTERVAL_MINUTES_MIN.toInt() to DEFAULT_AUTO_PLAN_INTERVAL_MINUTES_MAX.toInt()
            }
        val minMinutes = range.first.toLong().coerceAtLeast(1L)
        val maxMinutes = range.second.toLong().coerceAtLeast(minMinutes)
        val minutes = Random.nextLong(
            minMinutes,
            maxMinutes + 1L
        )
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

            val sampledPlayers = if (players.size <= MAX_TIMCORE_SCAN_PLAYERS) {
                players
            } else {
                val step = (players.size.toDouble() / MAX_TIMCORE_SCAN_PLAYERS.toDouble()).coerceAtLeast(1.0)
                val out = mutableListOf<ServerPlayerEntity>()
                var index = 0.0
                while (index < players.size && out.size < MAX_TIMCORE_SCAN_PLAYERS) {
                    out.add(players[index.toInt()])
                    index += step
                }
                out
            }

            val seen = mutableSetOf<String>()
            for (player in sampledPlayers) {
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

    fun recordPlayerCatch(player: ServerPlayerEntity, species: String) {
        recordPlayerBehavior(player.uuidAsString, species = species, isCatch = true)
    }

    fun recordPlayerBattle(player: ServerPlayerEntity, species: String) {
        recordPlayerBehavior(player.uuidAsString, species = species, isCatch = false)
    }

    private fun recordPlayerBehavior(playerUuid: String, species: String, isCatch: Boolean) {
        if (playerUuid.isBlank()) return

        val normalizedSpecies = sanitizeIdPart(species).ifBlank { return }
        val log = playerBehaviorLogs.getOrPut(playerUuid) { PlayerBehaviorLog() }
        if (isCatch) {
            log.catches++
        } else {
            log.battles++
        }
        log.lastTick = serverTickCounter
        log.recentSpecies.addLast(normalizedSpecies)
        while (log.recentSpecies.size > PLAYER_BEHAVIOR_MAX_SPECIES) {
            log.recentSpecies.removeFirst()
        }
    }

    private fun buildBehaviorSignal(players: List<ServerPlayerEntity>): BehaviorSignal {
        if (players.isEmpty()) return BehaviorSignal()
        cleanupBehaviorLogs()

        var catches = 0
        var battles = 0
        var trackedPlayers = 0
        val speciesSet = linkedSetOf<String>()

        for (player in players) {
            val log = playerBehaviorLogs[player.uuidAsString] ?: continue
            trackedPlayers++
            catches += log.catches
            battles += log.battles
            speciesSet.addAll(log.recentSpecies.toList().takeLast(18))
        }

        if (trackedPlayers == 0) return BehaviorSignal()

        val totalActions = (catches + battles).coerceAtLeast(1)
        val catchRatio = catches.toDouble() / totalActions.toDouble()
        val battleRatio = battles.toDouble() / totalActions.toDouble()
        val diversity = speciesSet.size

        val catchBias = ((catchRatio - 0.5) * 14.0).coerceIn(-5.0, 5.0)
        val battleBias = ((battleRatio - 0.5) * 14.0).coerceIn(-5.0, 5.0)
        val varietyBias = when {
            diversity >= 20 -> 3.2
            diversity >= 12 -> 2.1
            diversity >= 8 -> 1.1
            diversity >= 4 -> 0.2
            else -> -1.6
        }

        return BehaviorSignal(
            catchBias = catchBias,
            battleBias = battleBias,
            varietyBias = varietyBias,
            recentSpeciesSet = speciesSet
        )
    }

    private fun cleanupBehaviorLogs() {
        if (playerBehaviorLogs.isEmpty()) return
        val it = playerBehaviorLogs.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            if (serverTickCounter - entry.value.lastTick > PLAYER_BEHAVIOR_RETENTION_TICKS) {
                it.remove()
            }
        }
    }

    private fun applyTpsTargetScale(target: Int, estimatedTps: Double?): Int {
        if (estimatedTps == null) return target
        if (estimatedTps >= 19.0) return target

        return when {
            estimatedTps < 17.4 -> (target - 2).coerceAtLeast(MIN_TARGET_COUNT)
            estimatedTps < 18.0 -> (target - 1).coerceAtLeast(MIN_TARGET_COUNT)
            else -> target
        }
    }

    private fun resolveDurationTargetCap(durationMinutes: Int): Int {
        val safeDuration = durationMinutes.coerceIn(
            AI_DYNAMIC_DURATION_MINUTES,
            AI_DYNAMIC_DURATION_MAX_MINUTES
        )
        val extraMinutes = (safeDuration - AI_DYNAMIC_DURATION_MINUTES).coerceAtLeast(0)
        // 상한도 분당 +1.5 스케일과 동일하게 확장해서 캡에 의해 목표가 과도하게 눌리지 않게 한다.
        val scaledBonusCap = (extraMinutes * 1.5).roundToInt()
        val maxExtraMinutes = (AI_DYNAMIC_DURATION_MAX_MINUTES - AI_DYNAMIC_DURATION_MINUTES).coerceAtLeast(0)
        val maxScaledBonusCap = (maxExtraMinutes * 1.5).roundToInt()
        return (MAX_TARGET_COUNT + scaledBonusCap).coerceAtMost(MAX_TARGET_COUNT + maxScaledBonusCap)
    }

    private fun applyDurationTargetScale(
        target: Int,
        durationMinutes: Int,
        durationTargetCap: Int
    ): Int {
        val safeDuration = durationMinutes.coerceIn(
            AI_DYNAMIC_DURATION_MINUTES,
            AI_DYNAMIC_DURATION_MAX_MINUTES
        )
        val extraMinutes = (safeDuration - AI_DYNAMIC_DURATION_MINUTES).coerceAtLeast(0)
        if (extraMinutes <= 0) {
            return target.coerceIn(MIN_TARGET_COUNT, durationTargetCap)
        }

        // 5분 기준에서 1분 증가마다 +1.5 목표를 반영한다.
        val bonus = (extraMinutes * 1.5).roundToInt()
        return (target + bonus).coerceIn(MIN_TARGET_COUNT, durationTargetCap)
    }

    private fun computeLegendaryEventChance(
        playerCount: Int,
        context: PlannerDataContext,
        template: AiDynamicTemplate
    ): Double {
        return AI_LEGENDARY_EVENT_FIXED_CHANCE
    }

    private fun toPercentLabel(chance: Double): String {
        val scaled = (chance * 1000.0).toInt().coerceAtLeast(0) // x10 percent
        val whole = scaled / 10
        val frac = scaled % 10
        return "p=${whole}.${frac}%"
    }

    private fun containsAny(text: String, vararg keywords: String): Boolean {
        return keywords.any { text.contains(it) }
    }

    fun recordDynamicOutcome(mode: String, participants: Int, completed: Int, durationMinutes: Int) {
        val safeParticipants = participants.coerceAtLeast(0)
        val safeCompleted = completed.coerceIn(0, safeParticipants)
        val safeDuration = durationMinutes.coerceIn(1, MAX_ADDON_DURATION_MINUTES)
        val safeMode = normalizeMode(mode)
        recentOutcomes.add(
            DynamicOutcome(
                mode = safeMode,
                participants = safeParticipants,
                completed = safeCompleted,
                durationMinutes = safeDuration
            )
        )
        if (recentOutcomes.size > RECENT_OUTCOME_LIMIT) {
            recentOutcomes.removeAt(0)
        }
    }

    private fun recentSuccessRate(): Double? {
        if (recentOutcomes.isEmpty()) return null
        val values = recentOutcomes.map {
            if (it.participants <= 0) 0.0 else it.completed.toDouble() / it.participants.toDouble()
        }
        return values.average().coerceIn(0.0, 1.0)
    }

    private fun recentAverageDurationMinutes(): Double? {
        if (recentOutcomes.isEmpty()) return null
        return recentOutcomes.map { it.durationMinutes.toDouble() }.average()
    }

    private fun estimateServerTps(server: MinecraftServer): Double? {
        val nanos = runCatching {
            val method = server.javaClass.methods.firstOrNull {
                it.name == "getAverageNanosPerTick" && it.parameterCount == 0
            } ?: return@runCatching null
            val value = method.invoke(server) as? Number ?: return@runCatching null
            value.toDouble()
        }.getOrNull()

        if (nanos != null && nanos > 0.0) {
            return (1_000_000_000.0 / nanos).coerceIn(0.0, 20.0)
        }

        val tickTimesAvg = runCatching {
            val method = server.javaClass.methods.firstOrNull {
                it.name == "getTickTimes" && it.parameterCount == 0
            } ?: return@runCatching null
            val raw = method.invoke(server) as? LongArray ?: return@runCatching null
            if (raw.isEmpty()) return@runCatching null
            raw.average()
        }.getOrNull()

        if (tickTimesAvg != null && tickTimesAvg > 0.0) {
            return (1_000_000_000.0 / tickTimesAvg).coerceIn(0.0, 20.0)
        }
        return null
    }

    private fun isHighLoadMode(mode: String): Boolean = mode == "battle" || mode == "hybrid"

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
        var loadedCoreMods: Int = 0,
        var estimatedTps: Double? = null,
        var recentSuccessRate: Double? = null,
        var recentAverageDurationMinutes: Double? = null
    )

    private data class DynamicOutcome(
        val mode: String,
        val participants: Int,
        val completed: Int,
        val durationMinutes: Int
    )

    private data class PlayerBehaviorLog(
        var catches: Int = 0,
        var battles: Int = 0,
        val recentSpecies: ArrayDeque<String> = ArrayDeque(),
        var lastTick: Long = 0L
    )

    private data class BehaviorSignal(
        val catchBias: Double = 0.0,
        val battleBias: Double = 0.0,
        val varietyBias: Double = 0.0,
        val recentSpeciesSet: Set<String> = emptySet()
    )

    private enum class DifficultyBand {
        EASY,
        NORMAL,
        HARD,
        VERY_HARD
    }
}
