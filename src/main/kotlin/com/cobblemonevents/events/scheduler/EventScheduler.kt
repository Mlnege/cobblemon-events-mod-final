package com.cobblemonevents.events.scheduler

import com.cobblemonevents.CobblemonEventsMod
import com.cobblemonevents.ai.AiGeneratedContentPlanner
import com.cobblemonevents.config.EventDefinition
import com.cobblemonevents.config.ExplorerOverrideConfig
import com.cobblemonevents.events.ActiveEvent
import com.cobblemonevents.events.EventHandler
import com.cobblemonevents.events.EventState
import com.cobblemonevents.events.types.*
import com.cobblemonevents.util.BroadcastUtil
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import java.util.concurrent.ConcurrentHashMap

class EventScheduler {
    data class ContinueStartResult(
        val success: Boolean,
        val reason: String,
        val nextDelayMinutes: Int = 0
    )

    companion object {
        private const val DATA_NEXT_REPEAT_DELAY_TICKS = "ce_next_repeat_delay_ticks"
        private const val TICKS_PER_MINUTE = 20L * 60L
    }

    private val activeEvents = ConcurrentHashMap<String, ActiveEvent>()
    private val handlers = mutableMapOf<String, EventHandler>()
    private var server: MinecraftServer? = null

    @Volatile
    private var started = false

    init {
        handlers["TEMPORAL_RIFT"] = TemporalRiftEvent()
        handlers["EXPLORER"] = ExplorerEvent()
        handlers["HUNTING_SEASON"] = HuntingSeasonEvent()
        handlers["LEGENDARY_RAID"] = LegendaryRaidEvent()
        handlers["LUCKY_EVENT"] = LuckyEvent()
        handlers["ULTRA_WORMHOLE"] = UltraWormholeEvent()
        handlers["GYM_CHALLENGE"] = GymChallengeEvent()
        handlers["AI_DYNAMIC"] = AiDynamicEvent()
    }

    fun onServerStarted(server: MinecraftServer) {
        if (started && this.server === server) {
            CobblemonEventsMod.LOGGER.info("[스케줄러] 이미 시작된 서버라 초기화를 건너뜁니다.")
            return
        }

        if (started) {
            cleanupActiveEvents(this.server)
        }

        this.server = server
        started = true
        initializeSchedules(skipInitialRound = false)

        CobblemonEventsMod.LOGGER.info("[스케줄러] 서버 시작과 함께 이벤트 스케줄 초기화 완료")
    }

    fun onServerStopping() {
        cleanupActiveEvents(server)
        activeEvents.clear()
        server = null
        started = false

        CobblemonEventsMod.LOGGER.info("[스케줄러] 서버 종료로 인해 모든 이벤트 정리 완료")
    }

    private fun cleanupActiveEvents(server: MinecraftServer?) {
        if (server == null) return

        for ((_, event) in activeEvents) {
            if (event.state == EventState.ACTIVE) {
                try {
                    endEvent(event, server, reschedule = false)
                } catch (e: Exception) {
                    CobblemonEventsMod.LOGGER.error(
                        "[스케줄러] '${event.definition.id}' 종료 정리 중 오류",
                        e
                    )
                }
            }
        }
    }

    private fun initializeSchedules(skipInitialRound: Boolean = false) {
        activeEvents.clear()
        val config = CobblemonEventsMod.config

        for (eventDef in config.events) {
            if (!eventDef.enabled) continue

            if (!handlers.containsKey(eventDef.eventType)) {
                CobblemonEventsMod.LOGGER.warn(
                    "[스케줄러] 알 수 없는 이벤트 유형: ${eventDef.eventType} (${eventDef.id})"
                )
                continue
            }

            scheduleEvent(eventDef, isRepeat = skipInitialRound)
        }
    }

    private fun scheduleEvent(def: EventDefinition, isRepeat: Boolean = false) {
        val delayMinutes = effectiveDelayMinutes(def, isRepeat)
        val durationMinutes = effectiveDurationMinutes(def)
        val delayTicks = delayMinutes.toLong() * TICKS_PER_MINUTE

        val announceTicks = if (def.announceBeforeMinutes > 0) {
            delayTicks - (def.announceBeforeMinutes.toLong() * TICKS_PER_MINUTE)
        } else {
            delayTicks
        }

        val event = ActiveEvent(
            definition = def,
            state = EventState.WAITING,
            ticksUntilStart = delayTicks,
            ticksRemaining = durationMinutes.toLong() * TICKS_PER_MINUTE,
            ticksUntilAnnounce = maxOf(announceTicks, 0L)
        )

        activeEvents[def.id] = event

        val logPrefix = if (isRepeat) "다음" else "초기"
        CobblemonEventsMod.LOGGER.info(
            "[스케줄러] '${def.id}' $logPrefix 스케줄 등록 " +
                    "(${if (isRepeat) def.intervalMinutes else def.startDelayMinutes}분 후 시작)"
        )
    }

    private fun scheduleEventWithDelayTicks(def: EventDefinition, delayTicksInput: Long) {
        val durationMinutes = effectiveDurationMinutes(def)
        val delayTicks = delayTicksInput.coerceAtLeast(20L)
        val announceLeadTicks = def.announceBeforeMinutes.toLong() * TICKS_PER_MINUTE
        val announceTicks = if (def.announceBeforeMinutes > 0 && delayTicks > announceLeadTicks) {
            delayTicks - announceLeadTicks
        } else {
            delayTicks
        }

        val event = ActiveEvent(
            definition = def,
            state = EventState.WAITING,
            ticksUntilStart = delayTicks,
            ticksRemaining = durationMinutes.toLong() * TICKS_PER_MINUTE,
            ticksUntilAnnounce = maxOf(announceTicks, 0L)
        )

        activeEvents[def.id] = event
        val delayMinutesCeil = ((delayTicks + TICKS_PER_MINUTE - 1L) / TICKS_PER_MINUTE).toInt()
        CobblemonEventsMod.LOGGER.info(
            "[Scheduler] '${def.id}' custom schedule registered (${delayMinutesCeil}m later)"
        )
    }

    private fun effectiveDelayMinutes(def: EventDefinition, isRepeat: Boolean): Int {
        val base = if (isRepeat) def.intervalMinutes else def.startDelayMinutes
        if (def.eventType != "EXPLORER") return base

        val override = ExplorerOverrideConfig.current()
        if (!override.enabled) return base

        return if (isRepeat) {
            (override.intervalMinutes ?: base).coerceAtLeast(1)
        } else {
            base
        }
    }

    private fun effectiveDurationMinutes(def: EventDefinition): Int {
        val base = def.durationMinutes
        if (def.eventType != "EXPLORER") return base

        val override = ExplorerOverrideConfig.current()
        if (!override.enabled) return base

        return (override.durationMinutes ?: base).coerceAtLeast(1)
    }

    fun getEffectiveIntervalMinutesForDisplay(def: EventDefinition): Int =
        effectiveDelayMinutes(def, isRepeat = true)

    fun getEffectiveDurationMinutesForDisplay(def: EventDefinition): Int =
        effectiveDurationMinutes(def)

    fun tick(server: MinecraftServer) {
        if (!started) return
        this.server = server

        for ((_, event) in activeEvents) {
            when (event.state) {
                EventState.WAITING -> tickWaiting(event, server)
                EventState.ANNOUNCED -> tickAnnounced(event, server)
                EventState.ACTIVE -> tickActive(event, server)
                EventState.ENDED -> {}
            }
        }
    }

    private fun tickWaiting(event: ActiveEvent, server: MinecraftServer) {
        if (event.definition.announceBeforeMinutes > 0) {
            event.ticksUntilAnnounce--
            if (event.ticksUntilAnnounce <= 0 && event.state == EventState.WAITING) {
                event.state = EventState.ANNOUNCED
                BroadcastUtil.announceUpcoming(
                    server,
                    event.definition.displayName,
                    event.definition.description,
                    event.definition.announceBeforeMinutes
                )
            }
        }

        event.ticksUntilStart--
        if (event.ticksUntilStart <= 0) {
            tryStartEvent(event, server)
        }
    }

    private fun tickAnnounced(event: ActiveEvent, server: MinecraftServer) {
        event.ticksUntilStart--
        if (event.ticksUntilStart <= 0) {
            tryStartEvent(event, server)
        }
    }

    private fun tryStartEvent(
        event: ActiveEvent,
        server: MinecraftServer,
        ignorePlayerRequirement: Boolean = false
    ) {
        val def = event.definition
        val onlinePlayers = server.playerManager.playerList.size

        if (!ignorePlayerRequirement && onlinePlayers < def.requiredPlayerCount) {
            if (onlinePlayers == 0) {
                val shouldRepeat = shouldRescheduleOnEnd(event)
                if (shouldRepeat) {
                    activeEvents.remove(def.id)
                    scheduleEvent(def, isRepeat = true)
                    CobblemonEventsMod.LOGGER.info(
                        "[Scheduler] '${def.id}' skipped (no players online). Next fixed cycle preserved."
                    )
                } else {
                    activeEvents.remove(def.id)
                    CobblemonEventsMod.LOGGER.info(
                        "[Scheduler] '${def.id}' skipped (no players online). No reschedule for one-shot/AI event."
                    )
                }
                return
            }

            event.ticksUntilStart = 20L * 60L
            event.state = EventState.WAITING

            BroadcastUtil.broadcast(
                server,
                "${CobblemonEventsMod.config.prefix}§c'${def.displayName}§c' " +
                        "인원 부족으로 1분 후 재시도합니다. / Not enough players — retrying in 1 min. §7(필요 / Required: ${def.requiredPlayerCount}명)"
            )
            return
        }

        event.state = EventState.ACTIVE
        event.ticksRemaining = effectiveDurationMinutes(def).toLong() * 20L * 60L
        event.participants.clear()

        val handler = handlers[def.eventType]
        if (handler != null) {
            try {
                handler.onStart(event, server)
                CobblemonEventsMod.LOGGER.info("[스케줄러] '${def.id}' 이벤트 시작!")
            } catch (e: Exception) {
                CobblemonEventsMod.LOGGER.error("[스케줄러] '${def.id}' 이벤트 시작 실패!", e)
                endEvent(event, server, reschedule = false)
            }
        } else {
            CobblemonEventsMod.LOGGER.warn("[스케줄러] '${def.id}' 핸들러가 없어 이벤트를 종료합니다.")
            endEvent(event, server, reschedule = false)
        }
    }

    private fun tickActive(event: ActiveEvent, server: MinecraftServer) {
        event.ticksRemaining--

        val handler = handlers[event.definition.eventType]
        try {
            handler?.onTick(event, server)
        } catch (e: Exception) {
            CobblemonEventsMod.LOGGER.error("[스케줄러] '${event.definition.id}' tick 오류", e)
        }

        if (event.ticksRemaining <= 0) {
            endEvent(event, server, reschedule = shouldRescheduleOnEnd(event))
        }
    }

    private fun endEvent(
        event: ActiveEvent,
        server: MinecraftServer,
        reschedule: Boolean = true
    ) {
        event.state = EventState.ENDED

        val handler = handlers[event.definition.eventType]
        try {
            handler?.onEnd(event, server)
        } catch (e: Exception) {
            CobblemonEventsMod.LOGGER.error(
                "[스케줄러] '${event.definition.id}' 종료 처리 오류",
                e
            )
        }

        CobblemonEventsMod.LOGGER.info(
            "[스케줄러] '${event.definition.id}' 이벤트 종료 " +
                    "(참가자: ${event.participants.size}명, 재예약: $reschedule)"
        )

        if (reschedule) {
            val nextRepeatDelayTicks = event.getData<Long>(DATA_NEXT_REPEAT_DELAY_TICKS)
            if (nextRepeatDelayTicks != null && nextRepeatDelayTicks > 0L) {
                scheduleEventWithDelayTicks(event.definition, nextRepeatDelayTicks)
            } else {
                scheduleEvent(event.definition, isRepeat = true)
            }
        } else {
            activeEvents.remove(event.definition.id)
        }
    }

    fun onPokemonCaught(player: ServerPlayerEntity, species: String) {
        AiGeneratedContentPlanner.recordPlayerCatch(player, species)

        for ((_, event) in activeEvents) {
            if (event.state != EventState.ACTIVE) continue
            val handler = handlers[event.definition.eventType] ?: continue

            try {
                handler.onPokemonCaught(event, player, species)
            } catch (e: Exception) {
                CobblemonEventsMod.LOGGER.error("[스케줄러] 포획 이벤트 처리 오류", e)
            }
        }
    }

    fun onBattleWon(player: ServerPlayerEntity, defeatedSpecies: String) {
        AiGeneratedContentPlanner.recordPlayerBattle(player, defeatedSpecies)

        for ((_, event) in activeEvents) {
            if (event.state != EventState.ACTIVE) continue
            val handler = handlers[event.definition.eventType] ?: continue

            try {
                handler.onBattleWon(event, player, defeatedSpecies)
            } catch (e: Exception) {
                CobblemonEventsMod.LOGGER.error("[스케줄러] 배틀 이벤트 처리 오류", e)
            }
        }
    }

    fun onPlayerJoin(player: ServerPlayerEntity) {
        return
    }

    fun canPlayerBreakBlock(player: ServerPlayerEntity, world: ServerWorld, pos: BlockPos): Boolean {
        for ((_, event) in activeEvents) {
            if (event.state != EventState.ACTIVE) continue
            val handler = handlers[event.definition.eventType] ?: continue
            val allowed = try {
                handler.canBreakBlock(event, player, world, pos)
            } catch (e: Exception) {
                CobblemonEventsMod.LOGGER.error("[Scheduler] block-break guard error", e)
                true
            }
            if (!allowed) return false
        }
        return true
    }

    fun forceStart(eventId: String, server: MinecraftServer): Boolean {
        val existing = activeEvents[eventId]
        if (existing != null && existing.state != EventState.ENDED) {
            return false
        }

        val def = CobblemonEventsMod.config.events.find { it.id == eventId } ?: return false

        val event = ActiveEvent(
            definition = def,
            state = EventState.WAITING,
            ticksUntilStart = 1L,
            ticksRemaining = effectiveDurationMinutes(def).toLong() * 20L * 60L,
            ticksUntilAnnounce = 0L
        )

        activeEvents[eventId] = event
        tryStartEvent(event, server, ignorePlayerRequirement = true)
        return true
    }

    /**
     * AI 부가 컨텐츠용 임시 이벤트 시작.
     * 기본 스케줄 이벤트를 건드리지 않기 위해 고유 ID로 1회성 인스턴스를 생성한다.
     */
    fun forceStartAddonFromBase(
        baseEventId: String,
        server: MinecraftServer,
        durationMinutes: Int
    ): Boolean {
        val baseDef = CobblemonEventsMod.config.events.find { it.id == baseEventId } ?: return false
        if (!handlers.containsKey(baseDef.eventType)) return false

        val safeDuration = durationMinutes.coerceAtLeast(1)
        val addonId = "aiaddon_${baseDef.id}_${System.currentTimeMillis()}"
        val addonDef = baseDef.copy(
            id = addonId,
            displayName = "${baseDef.displayName} [AI Addon]",
            description = "${baseDef.description} (AI Addon)",
            intervalMinutes = Int.MAX_VALUE,
            durationMinutes = safeDuration,
            startDelayMinutes = 0,
            announceBeforeMinutes = 0,
            requiredPlayerCount = 1
        )

        val event = ActiveEvent(
            definition = addonDef,
            state = EventState.WAITING,
            ticksUntilStart = 1L,
            ticksRemaining = safeDuration.toLong() * 20L * 60L,
            ticksUntilAnnounce = 0L
        )

        activeEvents[addonId] = event
        tryStartEvent(event, server, ignorePlayerRequirement = true)
        return event.state == EventState.ACTIVE
    }

    fun forceStartDynamic(
        definition: EventDefinition,
        server: MinecraftServer,
        initialData: Map<String, Any> = emptyMap()
    ): Boolean {
        if (!handlers.containsKey(definition.eventType)) return false
        val existing = activeEvents[definition.id]
        if (existing != null && existing.state != EventState.ENDED) {
            return false
        }

        val event = ActiveEvent(
            definition = definition,
            state = EventState.WAITING,
            ticksUntilStart = 1L,
            ticksRemaining = effectiveDurationMinutes(definition).toLong() * 20L * 60L,
            ticksUntilAnnounce = 0L
        )

        for ((key, value) in initialData) {
            event.setData(key, value)
        }

        activeEvents[definition.id] = event
        tryStartEvent(event, server, ignorePlayerRequirement = true)
        return event.state == EventState.ACTIVE
    }

    fun forceStop(eventId: String, server: MinecraftServer): Boolean {
        val event = activeEvents[eventId] ?: return false

        return when (event.state) {
            EventState.ACTIVE -> {
                endEvent(event, server, reschedule = false)
                true
            }
            EventState.WAITING, EventState.ANNOUNCED -> {
                activeEvents.remove(eventId)
                CobblemonEventsMod.LOGGER.info(
                    "[스케줄러] '${event.definition.id}' 대기 이벤트 강제 제거"
                )
                true
            }
            EventState.ENDED -> false
        }
    }

    fun skipCurrentKeepNext(eventId: String, server: MinecraftServer): Boolean {
        val event = activeEvents[eventId] ?: return false
        val def = event.definition

        return when (event.state) {
            EventState.WAITING, EventState.ANNOUNCED -> {
                activeEvents.remove(eventId)
                scheduleEvent(def, isRepeat = true)
                CobblemonEventsMod.LOGGER.info(
                    "[스케줄러] '${def.id}' 현재 회차를 취소하고 다음 주기를 유지합니다."
                )
                true
            }
            EventState.ACTIVE -> {
                endEvent(event, server, reschedule = false)
                scheduleEvent(def, isRepeat = true)
                CobblemonEventsMod.LOGGER.info(
                    "[스케줄러] '${def.id}' 진행 중인 회차를 종료하고 다음 주기를 유지합니다."
                )
                true
            }
            EventState.ENDED -> false
        }
    }

    fun continueNowAndKeepRotation(eventId: String, server: MinecraftServer): ContinueStartResult {
        val def = CobblemonEventsMod.config.events.find { it.id == eventId }
            ?: return ContinueStartResult(false, "not_found")
        if (!def.enabled) return ContinueStartResult(false, "disabled")
        if (!handlers.containsKey(def.eventType)) return ContinueStartResult(false, "unsupported_type")

        val existing = activeEvents[eventId]
        if (existing?.state == EventState.ACTIVE) {
            return ContinueStartResult(false, "already_active")
        }

        val intervalMinutes = effectiveDelayMinutes(def, isRepeat = true).coerceAtLeast(1)
        val intervalTicks = intervalMinutes.toLong() * TICKS_PER_MINUTE
        val remainUntilCurrentSlotTicks = when (existing?.state) {
            EventState.WAITING, EventState.ANNOUNCED -> existing.ticksUntilStart.coerceAtLeast(0L)
            else -> intervalTicks
        }
        val nextDelayTicks = (remainUntilCurrentSlotTicks + intervalTicks).coerceAtLeast(intervalTicks)

        if (existing != null) {
            activeEvents.remove(eventId)
        }

        val event = ActiveEvent(
            definition = def,
            state = EventState.WAITING,
            ticksUntilStart = 1L,
            ticksRemaining = effectiveDurationMinutes(def).toLong() * TICKS_PER_MINUTE,
            ticksUntilAnnounce = 0L
        )
        event.setData(DATA_NEXT_REPEAT_DELAY_TICKS, nextDelayTicks)
        activeEvents[eventId] = event

        tryStartEvent(event, server, ignorePlayerRequirement = true)
        if (event.state != EventState.ACTIVE) {
            activeEvents.remove(eventId)
            scheduleEvent(def, isRepeat = true)
            return ContinueStartResult(false, "start_failed")
        }

        val nextDelayMinutes = ((nextDelayTicks + TICKS_PER_MINUTE - 1L) / TICKS_PER_MINUTE).toInt()
        CobblemonEventsMod.LOGGER.info(
            "[Scheduler] continue command started '$eventId' now, next reserved in ${nextDelayMinutes}m"
        )
        return ContinueStartResult(true, "started", nextDelayMinutes)
    }

    fun getActiveEvents(): List<ActiveEvent> =
        activeEvents.values.filter { it.state == EventState.ACTIVE }

    fun getAllEvents(): Map<String, ActiveEvent> = activeEvents.toMap()

    private fun shouldRescheduleOnEnd(event: ActiveEvent): Boolean {
        return !event.definition.id.startsWith("aiaddon_") && event.definition.eventType != "AI_DYNAMIC"
    }

    fun reload() {
        CobblemonEventsMod.config.reload()
        cleanupActiveEvents(server)
        initializeSchedules(skipInitialRound = false)
        CobblemonEventsMod.LOGGER.info("[스케줄러] 설정 리로드 및 이벤트 재초기화 완료")
    }
}
