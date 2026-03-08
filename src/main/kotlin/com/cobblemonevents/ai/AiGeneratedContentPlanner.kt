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
    private const val DEFAULT_ADDON_DURATION_MINUTES = 5
    private const val UPCOMING_BASE_EVENT_GUARD_TICKS = 20L * 60L * 6L
    private const val PROCEDURAL_TEMPLATE_BATCH_SIZE = 24
    private const val EXTERNAL_TEMPLATE_BATCH_SIZE = 24
    private const val MAX_TEMPLATE_POOL_SIZE = 96
    private const val CANDIDATE_WINDOW_SIZE = 12

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
        val addonDurationMinutes = AiProfileRegistry.getDynamicEventDurationMinutes()
        val selectedProfile = AiProfileRegistry.pickEnabledProfile()
        val seedTemplates = buildSeedTemplates(conceptPrompt, selectedProfile)
        val proceduralTemplates = buildProceduralTemplates(
            seedTemplates = seedTemplates,
            conceptPrompt = conceptPrompt,
            selectedProfile = selectedProfile,
            playerCount = players.size,
            averagePartyLevel = averagePartyLevel
        )
        val externalTemplates = buildExternalTemplates(
            conceptPrompt = conceptPrompt,
            selectedProfile = selectedProfile,
            playerCount = players.size,
            averagePartyLevel = averagePartyLevel,
            dataContext = dataContext,
            seedTemplates = seedTemplates
        )
        val templates = mergeTemplatePool(seedTemplates, proceduralTemplates, externalTemplates)
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

        val topCandidates = effectiveCandidates.take(CANDIDATE_WINDOW_SIZE)
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
        return merged
    }

    private fun buildProceduralTemplates(
        seedTemplates: List<AiDynamicTemplate>,
        conceptPrompt: String,
        selectedProfile: AiProfileEntry?,
        playerCount: Int,
        averagePartyLevel: Double
    ): List<AiDynamicTemplate> {
        if (seedTemplates.isEmpty()) return emptyList()

        val prompt = (conceptPrompt + " " + (selectedProfile?.prompt ?: "")).lowercase()
        val now = System.currentTimeMillis()
        val modifiers = listOf("혜성", "혼돈", "연쇄", "극한", "미로", "폭주", "역전", "광란", "초월", "기습")
        val results = mutableListOf<AiDynamicTemplate>()

        for (idx in 0 until PROCEDURAL_TEMPLATE_BATCH_SIZE) {
            val mode = pickMode(prompt)
            val seed = seedTemplates[Random.nextInt(seedTemplates.size)]
            val flavor = modifiers[Random.nextInt(modifiers.size)]
            val targetHint = (baseTargetForMode(mode) + playerCount + Random.nextInt(-2, 4)).coerceIn(3, 28)
            val rewardTier = (
                seed.rewardTier + (if (mode == "hybrid") 1 else 0) + Random.nextInt(-1, 2)
                ).coerceIn(1, 3)
            val weight = (seed.weight + Random.nextDouble(-0.35, 0.85)).coerceIn(0.35, 3.0)
            val id = "proc_${mode}_${now}_${idx}_${Random.nextInt(1000, 9999)}"
            val displayName = "$flavor ${modeDisplay(mode)} 미션"
            val description = "AI 절차 생성 이벤트: ${modeDisplay(mode)} 중심 목표 $targetHint 달성"
            val targetLevel = (averagePartyLevel + Random.nextDouble(-12.0, 16.0)).coerceIn(8.0, 95.0)

            results.add(
                AiDynamicTemplate(
                    id = id,
                    displayName = displayName.take(40),
                    description = description.take(180),
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
        seedTemplates: List<AiDynamicTemplate>
    ): List<AiDynamicTemplate> {
        if (seedTemplates.isEmpty()) return emptyList()

        val seedCandidates = seedTemplates.take(8).map { template ->
            ExternalCandidate(
                id = template.id,
                mode = template.mode,
                displayName = template.displayName,
                localScore = template.weight * 10.0,
                suggestedTarget = template.baseTarget.coerceIn(3, 28)
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
                desiredCount = EXTERNAL_TEMPLATE_BATCH_SIZE,
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
        val safeIdFromAi = sanitizeIdPart(raw.id)
        val id = if (safeIdFromAi.isBlank()) {
            "ext_${mode}_${now}_${index}_${Random.nextInt(1000, 9999)}"
        } else {
            "ext_${safeIdFromAi}_${now}_${index}"
        }.take(80)

        val displayName = raw.displayName?.trim().orEmpty().ifBlank {
            "외부 AI ${modeDisplay(mode)}"
        }.take(40)
        val description = raw.description?.trim().orEmpty().ifBlank {
            "외부 AI가 생성한 창의형 ${modeDisplay(mode)} 이벤트"
        }.take(180)
        val targetHint = raw.targetHint?.coerceIn(3, 28)
            ?: (baseTargetForMode(mode) + playerCount).coerceIn(3, 28)
        val rewardTier = raw.rewardTier?.coerceIn(1, 3) ?: rewardTierForMode(mode, targetHint)
        val weight = (raw.weight ?: (1.0 + rewardTier * 0.12)).coerceIn(0.3, 3.5)
        val cooldownGroup = sanitizeIdPart(raw.cooldownGroup).ifBlank { mode }.take(24)
        val targetLevel = (averagePartyLevel + Random.nextDouble(-10.0, 14.0)).coerceIn(8.0, 95.0)

        if (displayName.isBlank() || description.isBlank()) return null

        return AiDynamicTemplate(
            id = id,
            displayName = displayName,
            description = description,
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
        repeat(2) { weightedModes.add("variety") }
        repeat(2) { weightedModes.add("hybrid") }

        if (containsAny(prompt, "포획", "catch", "collector", "수집")) {
            repeat(3) { weightedModes.add("catch") }
            repeat(2) { weightedModes.add("variety") }
        }
        if (containsAny(prompt, "배틀", "전투", "raid", "battle")) {
            repeat(3) { weightedModes.add("battle") }
            repeat(2) { weightedModes.add("hybrid") }
        }
        if (containsAny(prompt, "도감", "variety", "다양")) {
            repeat(3) { weightedModes.add("variety") }
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
        return (byMode + if (targetHint >= 12) 1 else 0).coerceIn(1, 3)
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

    private fun buildRuntimeDefinition(template: AiDynamicTemplate, addonDurationMinutes: Int): EventDefinition {
        val safeDurationMinutes = addonDurationMinutes.takeIf { it > 0 } ?: DEFAULT_ADDON_DURATION_MINUTES
        return EventDefinition(
            id = "aidyn_${template.id}_${System.currentTimeMillis()}",
            displayName = "AI ${template.displayName}",
            description = template.description,
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
