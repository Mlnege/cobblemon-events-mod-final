package com.cobblemonevents.commands

import com.cobblemonevents.CobblemonEventsMod
import com.cobblemonevents.ai.AiGeneratedContentPlanner
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

object AiGeneratedContentCommand {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            literal("aigeneratedcontent")
                .requires { it.hasPermissionLevel(2) }
                .executes { runGenerated(it, execute = true, ignoreCooldown = false, trigger = "command") }
                .then(literal("preview").executes {
                    runGenerated(it, execute = false, ignoreCooldown = false, trigger = "preview")
                })
                .then(literal("run").executes {
                    runGenerated(it, execute = true, ignoreCooldown = false, trigger = "command_run")
                })
                .then(literal("force").executes {
                    runGenerated(it, execute = true, ignoreCooldown = true, trigger = "command_force")
                })
                .then(literal("enable").executes { setAutoMode(it, true) })
                .then(literal("disable").executes { setAutoMode(it, false) })
                .then(literal("status").executes { status(it) })
        )
    }

    private fun runGenerated(
        ctx: CommandContext<ServerCommandSource>,
        execute: Boolean,
        ignoreCooldown: Boolean,
        trigger: String
    ): Int {
        val source = ctx.source
        val decision = AiGeneratedContentPlanner.generate(
            server = source.server,
            execute = execute,
            ignoreCooldown = ignoreCooldown,
            trigger = trigger
        )

        val prefix = CobblemonEventsMod.config.prefix
        val summary = buildString {
            append("${prefix}[AI] result=${decision.reason}")
            if (decision.selectedEventId != null) {
                append(", event=${decision.selectedEventId}")
            }
            append(", players=${decision.playerCount}")
            append(", avgLvl=${"%.1f".format(decision.averagePartyLevel)}")
            append(", timCoreTagged=${decision.timCoreTaggedNearby}")
            append(", trigger=${decision.trigger}")
        }

        if (decision.executed) {
            source.sendFeedback({ Text.literal(summary) }, true)
            return 1
        }

        source.sendFeedback({ Text.literal(summary) }, false)
        return 0
    }

    private fun status(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        val prefix = CobblemonEventsMod.config.prefix
        val line = AiGeneratedContentPlanner.getStatusLine()
        source.sendFeedback({ Text.literal("${prefix}[AI] $line") }, false)
        return 1
    }

    private fun setAutoMode(ctx: CommandContext<ServerCommandSource>, enabled: Boolean): Int {
        val source = ctx.source
        val prefix = CobblemonEventsMod.config.prefix
        AiGeneratedContentPlanner.setAutoPlannerEnabled(enabled)
        val statusText = if (enabled) "enabled" else "disabled"
        source.sendFeedback({ Text.literal("${prefix}[AI] auto planner $statusText") }, true)
        return 1
    }
}
