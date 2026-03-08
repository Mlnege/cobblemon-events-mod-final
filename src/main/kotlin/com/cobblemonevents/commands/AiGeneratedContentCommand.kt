package com.cobblemonevents.commands

import com.cobblemonevents.CobblemonEventsMod
import com.cobblemonevents.ai.AiGeneratedContentPlanner
import com.cobblemonevents.ai.AiProfileRegistry
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.CommandManager.argument
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
                .then(literal("concept")
                    .executes { showConcept(it) }
                    .then(argument("prompt", StringArgumentType.greedyString()).executes { setConcept(it) })
                )
                .then(literal("profile")
                    .then(literal("window").executes { profileWindow(it) })
                    .then(literal("list").executes { listProfiles(it) })
                    .then(literal("register")
                        .then(argument("id", StringArgumentType.word())
                            .then(argument("prompt", StringArgumentType.greedyString()).executes { registerProfile(it) })
                        )
                    )
                    .then(literal("remove")
                        .then(argument("id", StringArgumentType.word()).executes { removeProfile(it) })
                    )
                    .then(literal("enable")
                        .then(argument("id", StringArgumentType.word()).executes { setProfileEnabled(it, true) })
                    )
                    .then(literal("disable")
                        .then(argument("id", StringArgumentType.word()).executes { setProfileEnabled(it, false) })
                    )
                )
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
            if (decision.selectedProfileId != null) {
                append(", profile=${decision.selectedProfileId}")
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

    private fun showConcept(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        val prefix = CobblemonEventsMod.config.prefix
        val concept = AiProfileRegistry.getConceptPrompt()
        source.sendFeedback({ Text.literal("${prefix}[AI] concept: $concept") }, false)
        return 1
    }

    private fun setConcept(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        val prefix = CobblemonEventsMod.config.prefix
        val prompt = StringArgumentType.getString(ctx, "prompt")
        AiProfileRegistry.setConceptPrompt(prompt)
        source.sendFeedback({ Text.literal("${prefix}[AI] concept updated.") }, true)
        return 1
    }

    private fun profileWindow(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        val prefix = CobblemonEventsMod.config.prefix
        val concept = AiProfileRegistry.getConceptPrompt()
        val profiles = AiProfileRegistry.listProfiles()

        source.sendFeedback({ Text.literal("${prefix}[AI Window] ===== AI 등록 창 =====") }, false)
        source.sendFeedback({ Text.literal("${prefix}[AI Window] concept: $concept") }, false)
        source.sendFeedback({ Text.literal("${prefix}[AI Window] profiles: ${profiles.size}개") }, false)

        if (profiles.isEmpty()) {
            source.sendFeedback({ Text.literal("${prefix}[AI Window] 등록된 프로필이 없습니다.") }, false)
        } else {
            profiles.forEach { profile ->
                val state = if (profile.enabled) "ON" else "OFF"
                source.sendFeedback(
                    { Text.literal("${prefix}[AI Window] - ${profile.id} [$state] :: ${profile.prompt}") },
                    false
                )
            }
        }

        source.sendFeedback(
            { Text.literal("${prefix}[AI Window] 사용법: /aigeneratedcontent profile register <id> <prompt>") },
            false
        )
        source.sendFeedback(
            { Text.literal("${prefix}[AI Window] 사용법: /aigeneratedcontent profile enable|disable <id>") },
            false
        )
        source.sendFeedback(
            { Text.literal("${prefix}[AI Window] 사용법: /aigeneratedcontent concept <prompt>") },
            false
        )
        return 1
    }

    private fun listProfiles(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        val prefix = CobblemonEventsMod.config.prefix
        val profiles = AiProfileRegistry.listProfiles()

        if (profiles.isEmpty()) {
            source.sendFeedback({ Text.literal("${prefix}[AI] profiles: none") }, false)
            return 1
        }

        source.sendFeedback({ Text.literal("${prefix}[AI] profiles:") }, false)
        profiles.forEach { profile ->
            val state = if (profile.enabled) "ON" else "OFF"
            source.sendFeedback({ Text.literal("${prefix}- ${profile.id} [$state]") }, false)
        }
        return 1
    }

    private fun registerProfile(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        val prefix = CobblemonEventsMod.config.prefix
        val id = StringArgumentType.getString(ctx, "id")
        val prompt = StringArgumentType.getString(ctx, "prompt")
        val created = AiProfileRegistry.registerProfile(id, prompt)

        if (!created) {
            source.sendFeedback({ Text.literal("${prefix}[AI] profile register failed. (중복/잘못된 값)") }, false)
            return 0
        }

        source.sendFeedback({ Text.literal("${prefix}[AI] profile '${id.lowercase()}' registered.") }, true)
        return 1
    }

    private fun removeProfile(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        val prefix = CobblemonEventsMod.config.prefix
        val id = StringArgumentType.getString(ctx, "id")
        val removed = AiProfileRegistry.removeProfile(id)

        if (!removed) {
            source.sendFeedback({ Text.literal("${prefix}[AI] profile not found: $id") }, false)
            return 0
        }

        source.sendFeedback({ Text.literal("${prefix}[AI] profile removed: $id") }, true)
        return 1
    }

    private fun setProfileEnabled(
        ctx: CommandContext<ServerCommandSource>,
        enabled: Boolean
    ): Int {
        val source = ctx.source
        val prefix = CobblemonEventsMod.config.prefix
        val id = StringArgumentType.getString(ctx, "id")
        val updated = AiProfileRegistry.setEnabled(id, enabled)

        if (!updated) {
            source.sendFeedback({ Text.literal("${prefix}[AI] profile not found: $id") }, false)
            return 0
        }

        val state = if (enabled) "enabled" else "disabled"
        source.sendFeedback({ Text.literal("${prefix}[AI] profile $state: $id") }, true)
        return 1
    }
}
