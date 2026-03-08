package com.cobblemonevents.util

import com.cobblemonevents.CobblemonEventsMod
import com.cobblemonevents.events.ActiveEvent
import net.minecraft.server.MinecraftServer
import net.minecraft.text.Text

object EventProgressHud {
    private const val UPDATE_INTERVAL_TICKS = 20L
    private const val SIDEBAR_UPDATE_INTERVAL_TICKS = 40L
    private const val OBJECTIVE_ID = "ce_evt"
    private const val SIDEBAR_TITLE = "§b✦ 이벤트 진행"

    @Volatile
    private var running = false

    private var tickCounter = 0L
    private var objectivePrepared = false
    private var hadActiveEvent = false
    private val previousSidebarEntries = linkedSetOf<String>()

    fun onServerStarted() {
        running = true
        tickCounter = 0L
        objectivePrepared = false
        hadActiveEvent = false
        previousSidebarEntries.clear()
    }

    fun onServerStopping(server: MinecraftServer?) {
        if (server != null) {
            clearSidebar(server)
            runCommand(server, "scoreboard objectives remove $OBJECTIVE_ID")
        }
        running = false
        tickCounter = 0L
        objectivePrepared = false
        hadActiveEvent = false
        previousSidebarEntries.clear()
    }

    fun tick(server: MinecraftServer) {
        if (!running) return

        tickCounter++
        if (tickCounter % UPDATE_INTERVAL_TICKS != 0L) return

        val active = CobblemonEventsMod.scheduler.getActiveEvents().firstOrNull()
        if (active == null) {
            if (hadActiveEvent) {
                clearSidebar(server)
                clearActionbar(server)
                hadActiveEvent = false
            }
            return
        }

        hadActiveEvent = true
        ensureObjective(server)
        renderActionbar(server, active)
        if (tickCounter % SIDEBAR_UPDATE_INTERVAL_TICKS == 0L) {
            renderSidebar(server, active)
        }
    }

    private fun renderActionbar(server: MinecraftServer, event: ActiveEvent) {
        val target = resolveTarget(event)
        val joined = event.participants.values.count { it > 0 }
        val done = event.completedPlayers.size
        val remain = formatRemain(event)
        val name = event.definition.displayName

        for (player in server.playerManager.playerList) {
            val personal = event.getProgress(player.uuid)
            val personalPart = if (target != null && target > 0) {
                " | Me:$personal/$target"
            } else if (personal > 0) {
                " | Me:$personal"
            } else {
                ""
            }
            val text =
                "${CobblemonEventsMod.config.prefix}[Progress] $name | Left:$remain | Joined:$joined | Done:$done$personalPart"
            player.sendMessage(Text.literal(text), true)
        }
    }

    private fun renderSidebar(server: MinecraftServer, event: ActiveEvent) {
        val target = resolveTarget(event)
        val joined = event.participants.values.count { it > 0 }
        val done = event.completedPlayers.size
        val progressTotal = event.participants.values.sum()
        val remain = formatRemain(event)
        val eventName = trimForSidebar(event.definition.displayName, 18)

        for (oldEntry in previousSidebarEntries) {
            runCommand(server, "scoreboard players reset \"${escapeQuoted(oldEntry)}\" $OBJECTIVE_ID")
        }
        previousSidebarEntries.clear()

        val lines = listOf(
            "§f이벤트 §e$eventName" to 8,
            "§7────────────" to 7,
            "§f남은시간 §b$remain" to 6,
            "§f참가 §a${joined}명" to 5,
            "§f완료 §d${done}명" to 4,
            "§f목표 §e${target ?: "-"}" to 3,
            "§f진행도 §6$progressTotal" to 2,
            "§8${sanitizeToken(event.definition.eventType)}" to 1
        )

        for ((raw, score) in lines) {
            val entry = sanitizeEntry(raw)
            runCommand(server, "scoreboard players set \"${escapeQuoted(entry)}\" $OBJECTIVE_ID $score")
            previousSidebarEntries.add(entry)
        }

        runCommand(server, "scoreboard objectives setdisplay sidebar $OBJECTIVE_ID")
    }

    private fun ensureObjective(server: MinecraftServer) {
        if (objectivePrepared) return
        runCommand(server, "scoreboard objectives add $OBJECTIVE_ID dummy \"$SIDEBAR_TITLE\"")
        runCommand(server, "scoreboard objectives modify $OBJECTIVE_ID displayname \"$SIDEBAR_TITLE\"")
        runCommand(server, "scoreboard objectives setdisplay sidebar $OBJECTIVE_ID")
        objectivePrepared = true
    }

    private fun clearSidebar(server: MinecraftServer) {
        for (oldEntry in previousSidebarEntries) {
            runCommand(server, "scoreboard players reset \"${escapeQuoted(oldEntry)}\" $OBJECTIVE_ID")
        }
        previousSidebarEntries.clear()
        runCommand(server, "scoreboard objectives setdisplay sidebar")
    }

    private fun clearActionbar(server: MinecraftServer) {
        for (player in server.playerManager.playerList) {
            player.sendMessage(Text.literal(""), true)
        }
    }

    private fun resolveTarget(event: ActiveEvent): Int? {
        val aiTarget = event.getData<Int>("ai_dynamic_target")
        if (aiTarget != null && aiTarget > 0) return aiTarget

        return when (event.definition.eventType) {
            "EXPLORER" -> event.definition.explorerConfig?.stopCount
            "ULTRA_WORMHOLE" -> event.definition.wormholeConfig?.spawnCount
            "TEMPORAL_RIFT" -> event.definition.riftConfig?.spawnCount
            "LEGENDARY_RAID" -> 1
            else -> null
        }?.takeIf { it > 0 }
    }

    private fun formatRemain(event: ActiveEvent): String {
        val min = event.getRemainingMinutes().coerceAtLeast(0)
        val sec = event.getRemainingSeconds().coerceAtLeast(0).coerceAtMost(59)
        return "${min}m${sec}s"
    }

    private fun sanitizeEntry(value: String): String {
        val safe = value
            .replace("\"", "")
            .replace("\\", "")
            .take(36)
        return if (safe.isBlank()) "line_${System.nanoTime()}" else safe
    }

    private fun trimForSidebar(value: String, maxLen: Int): String {
        val noQuotes = value.replace("\"", "").trim()
        if (noQuotes.length <= maxLen) return noQuotes
        return noQuotes.take(maxLen - 1) + "…"
    }

    private fun sanitizeToken(value: String): String {
        val safe = value.lowercase()
            .map { ch ->
                when {
                    ch in 'a'..'z' -> ch
                    ch in '0'..'9' -> ch
                    ch == '_' || ch == '-' -> ch
                    else -> '_'
                }
            }
            .joinToString("")
            .trim('_')
            .take(20)
        return if (safe.isBlank()) "event" else safe
    }

    private fun escapeQuoted(value: String): String {
        return value.replace("\"", "")
    }

    private fun runCommand(server: MinecraftServer, rawCommand: String): Boolean {
        val command = rawCommand.trim().removePrefix("/")
        return try {
            val silent = server.commandSource.withSilent()
            server.commandManager.executeWithPrefix(silent, command)
            true
        } catch (_: Exception) {
            false
        }
    }
}
