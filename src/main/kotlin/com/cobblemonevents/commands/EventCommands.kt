package com.cobblemonevents.commands

import com.cobblemonevents.CobblemonEventsMod
import com.cobblemonevents.events.EventState
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

object EventCommands {

    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(literal("이벤트")
            .then(literal("목록").executes { listEvents(it) })
            .then(literal("활성").executes { activeEvents(it) })
            .then(literal("시작").then(
                argument("이벤트ID", StringArgumentType.word())
                    .suggests { _, b ->
                        CobblemonEventsMod.config.events.forEach { b.suggest(it.id) }
                        b.buildFuture()
                    }
                    .requires { it.hasPermissionLevel(2) }
                    .executes { startEvent(it, "이벤트ID") }
            ))
            .then(literal("종료").then(
                argument("이벤트ID", StringArgumentType.word())
                    .suggests { _, b ->
                        CobblemonEventsMod.scheduler.getAllEvents().keys.forEach { b.suggest(it) }
                        b.buildFuture()
                    }
                    .requires { it.hasPermissionLevel(2) }
                    .executes { stopEvent(it, "이벤트ID") }
            ))
            .then(literal("건너뛰기").then(
                argument("이벤트ID", StringArgumentType.word())
                    .suggests { _, b ->
                        CobblemonEventsMod.scheduler.getAllEvents().keys.forEach { b.suggest(it) }
                        b.buildFuture()
                    }
                    .requires { it.hasPermissionLevel(2) }
                    .executes { skipEvent(it, "이벤트ID") }
            ))
            .then(literal("리로드")
                .requires { it.hasPermissionLevel(2) }
                .executes { reloadConfig(it) }
            )
            .then(literal("통계").executes { showStats(it) })
            .then(literal("랭킹").executes { showRanking(it) })
            .then(literal("울트라워프홀")
                .requires { it.hasPermissionLevel(2) }
                .then(literal("생성").executes { spawnUltraWormhole(it) })
                .then(literal("닫기").executes { closeUltraWormhole(it) })
            )
            .then(literal("도움말").executes { showHelp(it) })
            .executes { showHelp(it) }
        )

        dispatcher.register(literal("event")
            .then(literal("list").executes { listEvents(it) })
            .then(literal("active").executes { activeEvents(it) })
            .then(literal("start").then(
                argument("eventId", StringArgumentType.word())
                    .suggests { _, b ->
                        CobblemonEventsMod.config.events.forEach { b.suggest(it.id) }
                        b.buildFuture()
                    }
                    .requires { it.hasPermissionLevel(2) }
                    .executes { startEvent(it, "eventId") }
            ))
            .then(literal("stop").then(
                argument("eventId", StringArgumentType.word())
                    .suggests { _, b ->
                        CobblemonEventsMod.scheduler.getAllEvents().keys.forEach { b.suggest(it) }
                        b.buildFuture()
                    }
                    .requires { it.hasPermissionLevel(2) }
                    .executes { stopEvent(it, "eventId") }
            ))
            .then(literal("skip").then(
                argument("eventId", StringArgumentType.word())
                    .suggests { _, b ->
                        CobblemonEventsMod.scheduler.getAllEvents().keys.forEach { b.suggest(it) }
                        b.buildFuture()
                    }
                    .requires { it.hasPermissionLevel(2) }
                    .executes { skipEvent(it, "eventId") }
            ))
            .then(literal("continue").then(
                argument("eventId", StringArgumentType.word())
                    .suggests { _, b ->
                        CobblemonEventsMod.config.events.forEach { b.suggest(it.id) }
                        b.buildFuture()
                    }
                    .requires { it.hasPermissionLevel(2) }
                    .executes { continueEvent(it, "eventId") }
            ))
            .then(literal("reload").requires { it.hasPermissionLevel(2) }.executes { reloadConfig(it) })
            .then(literal("stats").executes { showStats(it) })
            .then(literal("ranking").executes { showRanking(it) })
            .then(literal("ultrawormhole")
                .requires { it.hasPermissionLevel(2) }
                .then(literal("spawn").executes { spawnUltraWormhole(it) })
                .then(literal("close").executes { closeUltraWormhole(it) })
            )
            .then(literal("help").executes { showHelp(it) })
            .executes { showHelp(it) }
        )

        dispatcher.register(literal("ce")
            .then(literal("list").executes { listEvents(it) })
            .then(literal("active").executes { activeEvents(it) })
            .then(literal("start").then(
                argument("id", StringArgumentType.word())
                    .suggests { _, b ->
                        CobblemonEventsMod.config.events.forEach { b.suggest(it.id) }
                        b.buildFuture()
                    }
                    .requires { it.hasPermissionLevel(2) }
                    .executes { startEvent(it, "id") }
            ))
            .then(literal("stop").then(
                argument("id", StringArgumentType.word())
                    .suggests { _, b ->
                        CobblemonEventsMod.scheduler.getAllEvents().keys.forEach { b.suggest(it) }
                        b.buildFuture()
                    }
                    .requires { it.hasPermissionLevel(2) }
                    .executes { stopEvent(it, "id") }
            ))
            .then(literal("skip").then(
                argument("id", StringArgumentType.word())
                    .suggests { _, b ->
                        CobblemonEventsMod.scheduler.getAllEvents().keys.forEach { b.suggest(it) }
                        b.buildFuture()
                    }
                    .requires { it.hasPermissionLevel(2) }
                    .executes { skipEvent(it, "id") }
            ))
            .then(literal("continue").then(
                argument("id", StringArgumentType.word())
                    .suggests { _, b ->
                        CobblemonEventsMod.config.events.forEach { b.suggest(it.id) }
                        b.buildFuture()
                    }
                    .requires { it.hasPermissionLevel(2) }
                    .executes { continueEvent(it, "id") }
            ))
            .then(literal("reload").requires { it.hasPermissionLevel(2) }.executes { reloadConfig(it) })
            .then(literal("uw")
                .requires { it.hasPermissionLevel(2) }
                .then(literal("spawn").executes { spawnUltraWormhole(it) })
                .then(literal("close").executes { closeUltraWormhole(it) })
            )
            .executes { showHelp(it) }
        )

        dispatcher.register(literal("\uC774\uBCA4\uD2B8")
            .then(literal("\uACC4\uC18D").then(
                argument("eventId", StringArgumentType.word())
                    .suggests { _, b ->
                        CobblemonEventsMod.config.events.forEach { b.suggest(it.id) }
                        b.buildFuture()
                    }
                    .requires { it.hasPermissionLevel(2) }
                    .executes { continueEvent(it, "eventId") }
            ))
        )

        CobblemonEventsMod.LOGGER.info("[코블몬 이벤트] 커맨드 등록: /이벤트, /event, /ce")
    }

    private fun listEvents(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        val prefix = CobblemonEventsMod.config.prefix
        val allEvents = CobblemonEventsMod.scheduler.getAllEvents()

        send(source, "")
        send(source, "${prefix}§f§l═══ 이벤트 전체 목록 ═══")

        if (allEvents.isEmpty()) {
            send(source, "${prefix}§7등록된 이벤트가 없습니다.")
        } else {
            for ((id, event) in allEvents) {
                val def = event.definition
                val typeIcon = getTypeIcon(def.eventType)
                val stateText = when (event.state) {
                    EventState.WAITING -> {
                        val mins = (event.ticksUntilStart / 20 / 60).toInt()
                        "§e⏳ ${mins}분 후"
                    }
                    EventState.ANNOUNCED -> "§b📢 공지됨"
                    EventState.ACTIVE -> {
                        val mins = event.getRemainingMinutes()
                        "§a▶ ${mins}분 남음"
                    }
                    EventState.ENDED -> "§c■ 종료"
                }

                send(source, "")
                send(source, " $typeIcon ${def.displayName} §7[$id]")
                send(source, "   §7유형: §f${getTypeName(def.eventType)} §7| 상태: $stateText")
                val effectiveInterval = CobblemonEventsMod.scheduler.getEffectiveIntervalMinutesForDisplay(def)
                val effectiveDuration = CobblemonEventsMod.scheduler.getEffectiveDurationMinutesForDisplay(def)
                send(source, "   §7주기: §f${effectiveInterval}분 §7| 지속: §f${effectiveDuration}분")

                if (event.state == EventState.ACTIVE) {
                    send(source, "   §7참가자: §f${event.participants.size}명")
                    val pos = event.eventLocation
                    if (pos != null) {
                        send(source, "   §7좌표: §eX:${pos.x} Z:${pos.z}")
                    }
                }
            }
        }
        send(source, "")
        return 1
    }

    private fun activeEvents(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        val prefix = CobblemonEventsMod.config.prefix
        val active = CobblemonEventsMod.scheduler.getActiveEvents()

        send(source, "")
        send(source, "${prefix}§a§l═══ 현재 진행 중인 이벤트 ═══")

        if (active.isEmpty()) {
            send(source, "${prefix}§7현재 진행 중인 이벤트가 없습니다.")
        } else {
            for (event in active) {
                val def = event.definition
                val icon = getTypeIcon(def.eventType)
                send(source, "")
                send(source, " $icon ${def.displayName}")
                send(source, "   §7남은 시간: §f${event.getRemainingMinutes()}분 ${event.getRemainingSeconds()}초")
                send(source, "   §7참가자: §f${event.participants.size}명")
                val pos = event.eventLocation
                if (pos != null) {
                    send(source, "   §7좌표: §eX:${pos.x} Z:${pos.z}")
                }
            }
        }
        send(source, "")
        return 1
    }

    private fun startEvent(ctx: CommandContext<ServerCommandSource>, argName: String): Int {
        val source = ctx.source
        val prefix = CobblemonEventsMod.config.prefix
        val eventId = StringArgumentType.getString(ctx, argName)

        val success = CobblemonEventsMod.scheduler.forceStart(eventId, source.server)
        if (success) {
            source.sendFeedback({ Text.literal("${prefix}§a'${eventId}' 이벤트 강제 시작!") }, true)
        } else {
            source.sendError(Text.literal("${prefix}§c'${eventId}' 이벤트를 찾을 수 없거나 이미 실행 중입니다."))
        }
        return if (success) 1 else 0
    }

    private fun stopEvent(ctx: CommandContext<ServerCommandSource>, argName: String): Int {
        val source = ctx.source
        val prefix = CobblemonEventsMod.config.prefix
        val eventId = StringArgumentType.getString(ctx, argName)

        val success = CobblemonEventsMod.scheduler.forceStop(eventId, source.server)
        if (success) {
            source.sendFeedback({ Text.literal("${prefix}§c'${eventId}' 이벤트 완전 종료! §7(다음 주기 예약 없음)") }, true)
        } else {
            source.sendError(Text.literal("${prefix}§c'${eventId}' 이벤트를 찾을 수 없거나 종료할 수 없습니다."))
        }
        return if (success) 1 else 0
    }

    private fun skipEvent(ctx: CommandContext<ServerCommandSource>, argName: String): Int {
        val source = ctx.source
        val prefix = CobblemonEventsMod.config.prefix
        val eventId = StringArgumentType.getString(ctx, argName)

        val success = CobblemonEventsMod.scheduler.skipCurrentKeepNext(eventId, source.server)
        if (success) {
            source.sendFeedback({ Text.literal("${prefix}§e'${eventId}' 현재 회차를 건너뛰고 다음 주기를 유지합니다.") }, true)
        } else {
            source.sendError(Text.literal("${prefix}§c'${eventId}' 이벤트를 찾을 수 없거나 건너뛸 수 없습니다."))
        }
        return if (success) 1 else 0
    }

    private fun continueEvent(ctx: CommandContext<ServerCommandSource>, argName: String): Int {
        val source = ctx.source
        val prefix = CobblemonEventsMod.config.prefix
        val eventId = StringArgumentType.getString(ctx, argName)

        val result = CobblemonEventsMod.scheduler.continueNowAndKeepRotation(eventId, source.server)
        return if (result.success) {
            source.sendFeedback(
                { Text.literal("${prefix}[계속] '${eventId}' 즉시 시작, 다음 회차 ${result.nextDelayMinutes}분 후 예약") },
                true
            )
            1
        } else {
            source.sendError(Text.literal("${prefix}[계속] '${eventId}' 실패 (${result.reason})"))
            0
        }
    }

    private fun reloadConfig(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        val prefix = CobblemonEventsMod.config.prefix
        try {
            CobblemonEventsMod.scheduler.reload()
            source.sendFeedback({
                Text.literal("${prefix}§a설정 리로드 완료! 이벤트가 재스케줄링되었습니다.")
            }, true)
            return 1
        } catch (e: Exception) {
            source.sendError(Text.literal("${prefix}§c리로드 실패: ${e.message}"))
            return 0
        }
    }

    private fun showStats(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        val prefix = CobblemonEventsMod.config.prefix
        val player = source.player

        if (player == null) {
            source.sendError(Text.literal("${prefix}§c플레이어만 사용할 수 있습니다."))
            return 0
        }

        val stats = CobblemonEventsMod.rankingManager.getPlayerStats(player.uuid)

        send(source, "")
        send(source, "${prefix}§f§l═══ 내 통계 ═══")
        if (stats != null) {
            send(source, " §7총 포획: §f${stats.totalCatches}마리")
            send(source, " §7배틀 승리: §f${stats.totalBattlesWon}회")
            send(source, " §7이벤트 완료: §f${stats.eventsCompleted}회")
            send(source, " §7전설 처치: §f${stats.legendsDefeated}회")
        } else {
            send(source, " §7아직 기록이 없습니다.")
        }
        send(source, "")
        return 1
    }

    private fun showRanking(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        val prefix = CobblemonEventsMod.config.prefix
        val activeHunts = CobblemonEventsMod.scheduler.getActiveEvents()
            .filter { it.definition.eventType == "HUNTING_SEASON" }

        send(source, "")
        send(source, "${prefix}§e§l═══ 현재 사냥 시즌 랭킹 ═══")

        if (activeHunts.isEmpty()) {
            send(source, "${prefix}§7현재 진행 중인 사냥 시즌이 없습니다.")
        } else {
            for (event in activeHunts) {
                val top = CobblemonEventsMod.rankingManager.getTopN(event.definition.id, 10)
                val target = event.extraData["targetPokemon"] as? String ?: "???"
                send(source, " §7대상: §e$target")

                if (top.isEmpty()) {
                    send(source, " §7아직 포획 기록이 없습니다.")
                } else {
                    val medals = listOf("§6🥇", "§f🥈", "§c🥉")
                    for ((i, entry) in top.withIndex()) {
                        val medal = if (i < 3) medals[i] else "§7  ${i + 1}위"
                        send(source, "  $medal §f${entry.first} §7- §e${entry.second}마리")
                    }
                }
            }
        }
        send(source, "")
        return 1
    }

    private fun spawnUltraWormhole(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        val prefix = CobblemonEventsMod.config.prefix
        if (!source.hasPermissionLevel(2)) {
            source.sendError(Text.literal("${prefix}§c권한이 없습니다. (OP 전용)"))
            return 0
        }

        val success = CobblemonEventsMod.scheduler.forceStart("ultra_wormhole", source.server)
        if (success) {
            source.sendFeedback({ Text.literal("${prefix}§5울트라 워프홀 생성 명령 실행!") }, true)
        } else {
            source.sendError(Text.literal("${prefix}§c울트라 워프홀 생성에 실패했습니다."))
        }
        return if (success) 1 else 0
    }

    private fun closeUltraWormhole(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        val prefix = CobblemonEventsMod.config.prefix
        if (!source.hasPermissionLevel(2)) {
            source.sendError(Text.literal("${prefix}§c권한이 없습니다. (OP 전용)"))
            return 0
        }

        val success = CobblemonEventsMod.scheduler.forceStop("ultra_wormhole", source.server)
        if (success) {
            source.sendFeedback({ Text.literal("${prefix}§5울트라 워프홀 닫기 명령 실행!") }, true)
        } else {
            source.sendError(Text.literal("${prefix}§c울트라 워프홀을 찾을 수 없습니다."))
        }
        return if (success) 1 else 0
    }

    private fun showHelp(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        val prefix = CobblemonEventsMod.config.prefix

        send(source, "")
        send(source, "${prefix}§f§l═══ 코블몬 이벤트 v2.1 도움말 ═══")
        send(source, "")
        send(source, " §e/이벤트 목록 §7- 모든 이벤트 상태")
        send(source, " §e/이벤트 활성 §7- 진행 중인 이벤트만")
        send(source, " §e/이벤트 통계 §7- 내 이벤트 통계")
        send(source, " §e/이벤트 랭킹 §7- 사냥 시즌 랭킹")
        send(source, " §c/이벤트 시작 <ID> §7- 즉시 강제 시작 (OP)")
        send(source, " §c/이벤트 종료 <ID> §7- 완전 종료, 다음 주기 없음 (OP)")
        send(source, " §6/이벤트 건너뛰기 <ID> §7- 이번 회차만 취소, 다음 주기 유지 (OP)")
        send(source, " §c/이벤트 리로드 §7- 설정 리로드 (OP)")
        send(source, " §5/이벤트 울트라워프홀 생성 §7- 울트라 워프홀 즉시 생성 (OP)")
        send(source, " §5/이벤트 울트라워프홀 닫기 §7- 울트라 워프홀 즉시 종료 (OP)")
        send(source, "")
        send(source, " §7영문: §f/event list|active|stats|ranking|start|stop|skip|reload|ultrawormhole spawn|ultrawormhole close")
        send(source, " §7단축: §f/ce list|active|start|stop|skip|reload|uw spawn|uw close")
        send(source, "")
        send(source, " §f§l이벤트 유형:")
        send(source, "  §d🌀 시공 균열 §7- 타입별 포켓몬 대량 출현")
        send(source, "  §a🧭 대탐험 §7- 월드 포켓스탑 탐색")
        send(source, "  §c🏹 사냥 시즌 §7- 포획 경쟁 랭킹전")
        send(source, "  §6🌠 전설 레이드 §7- 협동 보스전")
        send(source, "  §e🎰 럭키 이벤트 §7- 랜덤 행운 효과")
        send(source, "  §5🌌 울트라 워프홀 §7- 울트라비스트 출현")
        send(source, "")
        return 1
    }

    private fun send(source: ServerCommandSource, message: String) {
        source.sendFeedback({ Text.literal(message) }, false)
    }

    private fun getTypeIcon(type: String): String = when (type) {
        "TEMPORAL_RIFT" -> "§d🌀"
        "EXPLORER" -> "§a🧭"
        "HUNTING_SEASON" -> "§c🏹"
        "LEGENDARY_RAID" -> "§6🌠"
        "LUCKY_EVENT" -> "§e🎰"
        "ULTRA_WORMHOLE" -> "§5🌌"
        else -> "§7?"
    }

    private fun getTypeName(type: String): String = when (type) {
        "TEMPORAL_RIFT" -> "시공 균열"
        "EXPLORER" -> "대탐험"
        "HUNTING_SEASON" -> "사냥 시즌"
        "LEGENDARY_RAID" -> "전설 레이드"
        "LUCKY_EVENT" -> "럭키 이벤트"
        "ULTRA_WORMHOLE" -> "울트라 워프홀"
        else -> type
    }
}
