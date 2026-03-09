package com.cobblemonevents.util

import com.cobblemonevents.CobblemonEventsMod
import com.cobblemonevents.events.ActiveEvent
import com.cobblemonevents.events.types.ExplorerEvent
import net.minecraft.entity.boss.BossBar
import net.minecraft.entity.boss.ServerBossBar
import net.minecraft.server.MinecraftServer
import net.minecraft.text.Text
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2
import kotlin.math.sqrt

object EventProgressHud {
    private const val UPDATE_INTERVAL_TICKS = 20L
    private const val ARROW_BAR_PERCENT = 1.0f
    private const val EXPLORER_STOPS_KEY = "stops"
    private const val EXPLORER_CLAIMED_STOPS_KEY = "claimedStops"
    private const val GYM_TARGET_KEY = "gym_target"

    @Volatile
    private var running = false

    private var tickCounter = 0L
    private var hadActiveEvent = false
    private val trackingBars = mutableMapOf<UUID, ServerBossBar>()

    fun onServerStarted() {
        running = true
        tickCounter = 0L
        hadActiveEvent = false
        clearAllTrackingBars()
    }

    fun onServerStopping(server: MinecraftServer?) {
        if (server != null) {
            clearActionbar(server)
        }
        clearAllTrackingBars()
        running = false
        tickCounter = 0L
        hadActiveEvent = false
    }

    fun tick(server: MinecraftServer) {
        if (!running) return

        tickCounter++
        if (tickCounter % UPDATE_INTERVAL_TICKS != 0L) return

        val active = CobblemonEventsMod.scheduler.getActiveEvents().firstOrNull()
        if (active == null) {
            if (hadActiveEvent) {
                clearActionbar(server)
                clearAllTrackingBars()
                hadActiveEvent = false
            }
            return
        }

        hadActiveEvent = true
        if (active.definition.eventType == "EXPLORER") {
            clearActionbar(server)
            renderExplorerDistanceBars(server, active)
            return
        }

        renderActionbar(server, active)
        if (isTrackingEvent(active)) {
            renderTrackingArrowBars(server, active)
        } else {
            clearAllTrackingBars()
        }
    }

    private fun renderActionbar(server: MinecraftServer, event: ActiveEvent) {
        val eventName = resolveEventName(event)
        val target = resolveTarget(event)
        val joined = event.participants.values.count { it > 0 }
        val done = event.completedPlayers.size
        val remain = formatRemain(event)

        for (player in server.playerManager.playerList) {
            val personal = event.getProgress(player.uuid)
            val personalPart = when {
                target != null && target > 0 -> " | 내진행:$personal/$target"
                personal > 0 -> " | 내진행:$personal"
                else -> ""
            }

            val text =
                "${CobblemonEventsMod.config.prefix}[진행] $eventName | 남은:$remain | 참가:$joined | 완료:$done$personalPart"
            player.sendMessage(Text.literal(text), true)
        }
    }

    private fun renderTrackingArrowBars(server: MinecraftServer, event: ActiveEvent) {
        val pos = event.eventLocation ?: run {
            clearAllTrackingBars()
            return
        }

        val onlineIds = mutableSetOf<UUID>()
        for (player in server.playerManager.playerList) {
            onlineIds += player.uuid
            val dx = (pos.x + 0.5) - player.x
            val dz = (pos.z + 0.5) - player.z
            val arrow = resolveArrow(player.yaw.toDouble(), dx, dz)

            val bar = trackingBars.getOrPut(player.uuid) {
                createTrackingBar(resolveTrackingColor(event))
            }
            bar.addPlayer(player)
            bar.color = resolveTrackingColor(event)
            bar.percent = ARROW_BAR_PERCENT
            bar.name = Text.literal(arrow)
            bar.isVisible = true
        }

        val stale = trackingBars.keys.filter { it !in onlineIds }
        for (uuid in stale) {
            trackingBars.remove(uuid)?.clearPlayers()
        }
    }

    private fun renderExplorerDistanceBars(server: MinecraftServer, event: ActiveEvent) {
        val stops = event.getData<List<ExplorerEvent.StopData>>(EXPLORER_STOPS_KEY) ?: run {
            clearAllTrackingBars()
            return
        }
        val claimedStops = event.getData<ConcurrentHashMap<Int, MutableSet<UUID>>>(EXPLORER_CLAIMED_STOPS_KEY) ?: run {
            clearAllTrackingBars()
            return
        }

        val onlineIds = mutableSetOf<UUID>()
        for (player in server.playerManager.playerList) {
            onlineIds += player.uuid

            var nearestDistance = Double.MAX_VALUE
            for (stop in stops) {
                val claimed = claimedStops[stop.id]
                if (claimed?.contains(player.uuid) == true) continue

                val dx = (stop.pos.x + 0.5) - player.x
                val dz = (stop.pos.z + 0.5) - player.z
                val distance = sqrt(dx * dx + dz * dz)
                if (distance < nearestDistance) {
                    nearestDistance = distance
                }
            }

            val bar = trackingBars.getOrPut(player.uuid) {
                createTrackingBar(BossBar.Color.PINK)
            }
            bar.addPlayer(player)
            bar.color = BossBar.Color.PINK
            bar.percent = ARROW_BAR_PERCENT
            bar.name = if (nearestDistance.isFinite() && nearestDistance < Double.MAX_VALUE) {
                Text.literal("✦ 거리:${nearestDistance.toInt()}m")
            } else {
                Text.literal("✦ 남은 포켓스탑 없음")
            }
            bar.isVisible = true
        }

        val stale = trackingBars.keys.filter { it !in onlineIds }
        for (uuid in stale) {
            trackingBars.remove(uuid)?.clearPlayers()
        }
    }

    private fun createTrackingBar(color: BossBar.Color): ServerBossBar {
        return ServerBossBar(Text.literal("추적 방향"), color, BossBar.Style.PROGRESS).apply {
            percent = ARROW_BAR_PERCENT
            isVisible = true
        }
    }

    private fun resolveTrackingColor(event: ActiveEvent): BossBar.Color {
        return when (event.definition.eventType) {
            "TEMPORAL_RIFT" -> BossBar.Color.PINK
            "LEGENDARY_RAID" -> BossBar.Color.RED
            "ULTRA_WORMHOLE" -> BossBar.Color.PURPLE
            "AI_DYNAMIC" -> {
                when (event.getData<String>("ai_dynamic_mode")?.lowercase()) {
                    "battle" -> BossBar.Color.RED
                    "hybrid" -> BossBar.Color.YELLOW
                    "catch" -> BossBar.Color.GREEN
                    else -> BossBar.Color.BLUE
                }
            }
            else -> BossBar.Color.WHITE
        }
    }

    private fun clearAllTrackingBars() {
        trackingBars.values.forEach { it.clearPlayers() }
        trackingBars.clear()
    }

    private fun clearActionbar(server: MinecraftServer) {
        for (player in server.playerManager.playerList) {
            player.sendMessage(Text.literal(""), true)
        }
    }

    private fun resolveEventName(event: ActiveEvent): String {
        return when (event.definition.eventType) {
            "TEMPORAL_RIFT" -> "시공의 균열"
            "EXPLORER" -> "대탐험 포켓스탑"
            "HUNTING_SEASON" -> "사냥 시즌"
            "LEGENDARY_RAID" -> "전설 레이드"
            "LUCKY_EVENT" -> "럭키 이벤트"
            "ULTRA_WORMHOLE" -> "울트라 워프홀"
            "GYM_CHALLENGE" -> "커스텀 체육관"
            "AI_DYNAMIC" -> {
                val category = event.getData<String>("ai_dynamic_category")
                val mode = event.getData<String>("ai_dynamic_mode")
                when {
                    category == "legendary_tracking" -> "AI 전설 추적"
                    mode == "variety" -> "AI 도감 다양성"
                    mode == "battle" -> "AI 배틀 작전"
                    mode == "catch" -> "AI 포획 작전"
                    mode == "hybrid" -> "AI 하이브리드"
                    else -> "AI 생성 이벤트"
                }
            }
            else -> event.definition.displayName.ifBlank { event.definition.id }
        }
    }

    private fun resolveTarget(event: ActiveEvent): Int? {
        val aiTarget = event.getData<Int>("ai_dynamic_target")
        if (aiTarget != null && aiTarget > 0) return aiTarget
        val gymTarget = event.getData<Int>(GYM_TARGET_KEY)
        if (gymTarget != null && gymTarget > 0) return gymTarget

        return when (event.definition.eventType) {
            "EXPLORER" -> event.definition.explorerConfig?.stopCount
            "ULTRA_WORMHOLE" -> event.definition.wormholeConfig?.spawnCount
            "TEMPORAL_RIFT" -> 2
            "LEGENDARY_RAID" -> 1
            "GYM_CHALLENGE" -> event.definition.gymConfig?.randomTargetMax
            else -> null
        }?.takeIf { it > 0 }
    }

    private fun isTrackingEvent(event: ActiveEvent): Boolean {
        return when (event.definition.eventType) {
            "TEMPORAL_RIFT", "LEGENDARY_RAID", "ULTRA_WORMHOLE" -> true
            "AI_DYNAMIC" -> {
                val category = event.getData<String>("ai_dynamic_category")?.lowercase().orEmpty()
                val mechanism = event.getData<String>("ai_dynamic_core_mechanism")?.lowercase().orEmpty()
                category == "legendary_tracking" ||
                    mechanism.contains("tracking") ||
                    mechanism.contains("clue") ||
                    mechanism.contains("추적")
            }
            else -> false
        }
    }

    private fun resolveArrow(playerYaw: Double, dx: Double, dz: Double): String {
        val distance = sqrt(dx * dx + dz * dz)
        if (distance < 2.0) return "◎ 목표 근처"

        val targetYaw = Math.toDegrees(atan2(-dx, dz))
        val delta = wrapDegrees(targetYaw - playerYaw)

        val arrow = when {
            delta >= -22.5 && delta < 22.5 -> "↑"
            delta >= 22.5 && delta < 67.5 -> "↗"
            delta >= 67.5 && delta < 112.5 -> "→"
            delta >= 112.5 && delta < 157.5 -> "↘"
            delta >= 157.5 || delta < -157.5 -> "↓"
            delta >= -157.5 && delta < -112.5 -> "↙"
            delta >= -112.5 && delta < -67.5 -> "←"
            else -> "↖"
        }

        return "$arrow 거리:${distance.toInt()}m"
    }

    private fun wrapDegrees(angle: Double): Double {
        var wrapped = angle % 360.0
        if (wrapped >= 180.0) wrapped -= 360.0
        if (wrapped < -180.0) wrapped += 360.0
        return wrapped
    }

    private fun formatRemain(event: ActiveEvent): String {
        val min = event.getRemainingMinutes().coerceAtLeast(0)
        val sec = event.getRemainingSeconds().coerceIn(0, 59)
        return "${min}m${sec}s"
    }
}
