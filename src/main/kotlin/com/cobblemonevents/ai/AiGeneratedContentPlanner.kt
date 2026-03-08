package com.cobblemonevents.ai

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemonevents.CobblemonEventsMod
import com.cobblemonevents.config.EventDefinition
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

    private val addonSupportedTypes = setOf(
        "EXPLORER",
        "LUCKY_EVENT"
    )

    private val eventTypeLastExecutedTick = mutableMapOf<String, Long>()
    private val eventIdLastExecutedTick = mutableMapOf<String, Long>()

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
        lastDecision = AiGeneratedDecision(false, "server_started")
    }

    fun onServerStopping() {
        initialized = false
        ticksUntilNextAutoPlan = AUTO_PLAN_INTERVAL_TICKS
        serverTickCounter = 0L
        eventTypeLastExecutedTick.clear()
        eventIdLastExecutedTick.clear()
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

        val playerProfiles = buildPlayerProfiles(server, players)
        val averagePartyLevel = if (playerProfiles.isNotEmpty()) {
            playerProfiles.map { it.averagePartyLevel }.average()
        } else {
            1.0
        }
        val dataContext = PlannerDataContext()
        for (source in dataSources) {
            source.enrich(server, players, dataContext)
        }
        val selectedProfile = AiProfileRegistry.pickEnabledProfile()

        val candidates = CobblemonEventsMod.config.events
            .asSequence()
            .filter { it.enabled && addonSupportedTypes.contains(it.eventType) }
            .filter { it.requiredPlayerCount <= players.size }
            .filter { ignoreCooldown || !isOnCooldown(it) }
            .map { def -> def to score(def, players.size, averagePartyLevel, dataContext, selectedProfile) }
            .sortedByDescending { it.second }
            .toList()

        if (candidates.isEmpty()) {
            val decision = AiGeneratedDecision(
                executed = false,
                reason = if (ignoreCooldown) "no_supported_addon_candidates" else "all_addon_candidates_on_cooldown",
                selectedProfileId = selectedProfile?.id,
                playerCount = players.size,
                averagePartyLevel = averagePartyLevel,
                timCoreTaggedNearby = dataContext.timCoreTaggedNearby,
                trigger = trigger
            )
            lastDecision = decision
            return decision
        }

        val topCandidates = candidates.take(3)
        val selected = weightedPick(topCandidates) ?: topCandidates.first().first

        if (!execute) {
            val decision = AiGeneratedDecision(
                executed = false,
                reason = "preview",
                selectedEventId = selected.id,
                selectedEventType = selected.eventType,
                selectedProfileId = selectedProfile?.id,
                playerCount = players.size,
                averagePartyLevel = averagePartyLevel,
                timCoreTaggedNearby = dataContext.timCoreTaggedNearby,
                trigger = trigger
            )
            lastDecision = decision
            return decision
        }

        val started = CobblemonEventsMod.scheduler.forceStartAddonFromBase(
            baseEventId = selected.id,
            server = server,
            durationMinutes = ADDON_DURATION_MINUTES
        )
        val decision = if (started) {
            markExecuted(selected)
            BroadcastUtil.broadcast(
                server,
                "${CobblemonEventsMod.config.prefix}[AI Addon] '${selected.displayName}' 보조 월드 이벤트를 시작했습니다. " +
                    "(지속 ${ADDON_DURATION_MINUTES}분, 프로필:${selectedProfile?.id ?: "none"}, 플레이어:${players.size}, 평균 레벨:${averagePartyLevel.roundToInt()}, tim_core 태그:${dataContext.timCoreTaggedNearby})"
            )
            AiGeneratedDecision(
                executed = true,
                reason = "started",
                selectedEventId = selected.id,
                selectedEventType = selected.eventType,
                selectedProfileId = selectedProfile?.id,
                playerCount = players.size,
                averagePartyLevel = averagePartyLevel,
                timCoreTaggedNearby = dataContext.timCoreTaggedNearby,
                trigger = trigger
            )
        } else {
            AiGeneratedDecision(
                executed = false,
                reason = "scheduler_rejected_start",
                selectedEventId = selected.id,
                selectedEventType = selected.eventType,
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
        return "auto_enabled=$autoPlannerEnabled, next_auto_plan=${minutesUntil}m, last=${lastDecision.reason}, last_event=${lastDecision.selectedEventId ?: "-"}"
    }

    fun getLastDecision(): AiGeneratedDecision = lastDecision

    fun setAutoPlannerEnabled(enabled: Boolean) {
        autoPlannerEnabled = enabled
        if (enabled && ticksUntilNextAutoPlan <= 0L) {
            ticksUntilNextAutoPlan = AUTO_PLAN_INTERVAL_TICKS
        }
    }

    fun isAutoPlannerEnabled(): Boolean = autoPlannerEnabled

    private fun isOnCooldown(def: EventDefinition): Boolean {
        val typeLast = eventTypeLastExecutedTick[def.eventType]
        if (typeLast != null) {
            val typeCooldown = maxOf(
                MIN_EVENT_TYPE_COOLDOWN_TICKS,
                def.durationMinutes.toLong() * 20L * 60L * 2L
            )
            if (serverTickCounter - typeLast < typeCooldown) {
                return true
            }
        }

        val idLast = eventIdLastExecutedTick[def.id]
        if (idLast != null) {
            val idCooldown = maxOf(
                MIN_EVENT_ID_COOLDOWN_TICKS,
                def.durationMinutes.toLong() * 20L * 60L
            )
            if (serverTickCounter - idLast < idCooldown) {
                return true
            }
        }

        return false
    }

    private fun markExecuted(def: EventDefinition) {
        eventTypeLastExecutedTick[def.eventType] = serverTickCounter
        eventIdLastExecutedTick[def.id] = serverTickCounter
    }

    private fun score(
        def: EventDefinition,
        playerCount: Int,
        averagePartyLevel: Double,
        context: PlannerDataContext,
        selectedProfile: AiProfileEntry?
    ): Double {
        val targetLevel = when (def.eventType) {
            "LEGENDARY_RAID" -> 70.0
            "ULTRA_WORMHOLE" -> 58.0
            "TEMPORAL_RIFT" -> 46.0
            "HUNTING_SEASON" -> 40.0
            "EXPLORER" -> 26.0
            "LUCKY_EVENT" -> 20.0
            else -> 35.0
        }

        var score = 100.0
        score -= abs(averagePartyLevel - targetLevel) * 1.25
        score -= abs(playerCount - def.requiredPlayerCount) * 3.0

        when (def.eventType) {
            "LEGENDARY_RAID" -> {
                if (playerCount >= 3) score += 10.0 else score -= 35.0
                if (context.raidDensLoaded) score += 8.0
            }
            "EXPLORER" -> {
                if (playerCount <= 3) score += 8.0
                score += 3.0
            }
            "LUCKY_EVENT" -> {
                score += 6.0
            }
        }

        if (context.timCoreLoaded) {
            // tim_core ?쒓렇 ?ㅽ룿??留롮쑝硫?怨쇰???諛⑹?瑜??꾪빐 ?쒖씠???믪? ?대깽??媛以묒튂瑜???텣??
            if (context.timCoreTaggedNearby > 20) {
                if (def.eventType == "LEGENDARY_RAID" || def.eventType == "ULTRA_WORMHOLE") {
                    score -= 18.0
                }
                if (def.eventType == "EXPLORER" || def.eventType == "LUCKY_EVENT") {
                    score += 6.0
                }
            } else {
                score += 2.0
            }
        }

        if (selectedProfile != null) {
            val prompt = selectedProfile.prompt.lowercase()
            if ((prompt.contains("explorer") || prompt.contains("탐험")) && def.eventType == "EXPLORER") {
                score += 8.0
            }
            if ((prompt.contains("lucky") || prompt.contains("행운")) && def.eventType == "LUCKY_EVENT") {
                score += 8.0
            }
        }

        return score
    }

    private fun weightedPick(candidates: List<Pair<EventDefinition, Double>>): EventDefinition? {
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

    private fun buildPlayerProfiles(
        server: MinecraftServer,
        players: List<ServerPlayerEntity>
    ): List<PlannerPlayerProfile> {
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


