package com.cobblemonevents.commands

import com.cobblemonevents.CobblemonEventsMod
import com.cobblemonevents.ai.AiGeneratedContentPlanner
import com.cobblemonevents.ai.ExternalAiAdvisor
import com.cobblemonevents.ai.AiProfileRegistry
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
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
                .then(
                    literal("interval")
                        .executes { showInterval(it) }
                        .then(argument("minutes", IntegerArgumentType.integer(1, 180)).executes { setIntervalFixed(it) })
                        .then(
                            literal("range")
                                .then(argument("min", IntegerArgumentType.integer(1, 180))
                                    .then(argument("max", IntegerArgumentType.integer(1, 180)).executes {
                                        setIntervalRange(it)
                                    })
                                )
                        )
                )
                .then(literal("nextgen")
                    .then(literal("status").executes { nextGenStatus(it) })
                )
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
                .then(literal("advisor")
                    .then(literal("status").executes { advisorStatus(it) })
                    .then(literal("enable").executes { setAdvisorEnabled(it, true) })
                    .then(literal("disable").executes { setAdvisorEnabled(it, false) })
                    .then(literal("endpoint")
                        .then(argument("url", StringArgumentType.greedyString()).executes { setAdvisorEndpoint(it) })
                    )
                    .then(literal("model")
                        .then(argument("name", StringArgumentType.greedyString()).executes { setAdvisorModel(it) })
                    )
                    .then(literal("token")
                        .then(argument("value", StringArgumentType.greedyString()).executes { setAdvisorToken(it) })
                    )
                    .then(literal("timeout")
                        .then(argument("ms", IntegerArgumentType.integer(500, 15000)).executes { setAdvisorTimeout(it) })
                    )
                    .then(literal("test").executes { testAdvisor(it) })
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

    private fun nextGenStatus(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        val prefix = CobblemonEventsMod.config.prefix
        val line = AiGeneratedContentPlanner.getNextGenReservationLine()
        source.sendFeedback({ Text.literal("${prefix}[AI NextGen] $line") }, false)
        return 1
    }

    private fun showInterval(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        val prefix = CobblemonEventsMod.config.prefix
        val (min, max) = AiGeneratedContentPlanner.getAutoPlanIntervalRangeMinutes()
        val text = if (min == max) "${min}m (fixed)" else "${min}-${max}m (random)"
        source.sendFeedback({ Text.literal("${prefix}[AI] auto interval: $text") }, false)
        return 1
    }

    private fun setIntervalFixed(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        val prefix = CobblemonEventsMod.config.prefix
        val minutes = IntegerArgumentType.getInteger(ctx, "minutes")
        AiGeneratedContentPlanner.setAutoPlanIntervalFixed(minutes)
        source.sendFeedback({ Text.literal("${prefix}[AI] auto interval set to ${minutes}m (fixed)") }, true)
        return 1
    }

    private fun setIntervalRange(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        val prefix = CobblemonEventsMod.config.prefix
        val min = IntegerArgumentType.getInteger(ctx, "min")
        val max = IntegerArgumentType.getInteger(ctx, "max")
        AiGeneratedContentPlanner.setAutoPlanIntervalRange(min, max)
        val (safeMin, safeMax) = AiGeneratedContentPlanner.getAutoPlanIntervalRangeMinutes()
        source.sendFeedback({ Text.literal("${prefix}[AI] auto interval set to ${safeMin}-${safeMax}m") }, true)
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

    private fun advisorStatus(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        val prefix = CobblemonEventsMod.config.prefix
        val cfg = AiProfileRegistry.getAdvisorConfig()
        val hasToken = if (cfg.bearerToken.isBlank()) "no" else "yes"
        source.sendFeedback(
            {
                Text.literal(
                    "${prefix}[AI Advisor] enabled=${cfg.enabled}, endpoint=${cfg.endpoint.ifBlank { "-" }}, " +
                        "model=${cfg.model.ifBlank { "-" }}, timeout=${cfg.timeoutMs}ms, token=$hasToken"
                )
            },
            false
        )
        return 1
    }

    private fun setAdvisorEnabled(ctx: CommandContext<ServerCommandSource>, enabled: Boolean): Int {
        val source = ctx.source
        val prefix = CobblemonEventsMod.config.prefix
        AiProfileRegistry.setAdvisorEnabled(enabled)
        source.sendFeedback({ Text.literal("${prefix}[AI Advisor] ${if (enabled) "enabled" else "disabled"}") }, true)
        return 1
    }

    private fun setAdvisorEndpoint(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        val prefix = CobblemonEventsMod.config.prefix
        val url = StringArgumentType.getString(ctx, "url")
        AiProfileRegistry.setAdvisorEndpoint(url)
        source.sendFeedback({ Text.literal("${prefix}[AI Advisor] endpoint updated.") }, true)
        return 1
    }

    private fun setAdvisorModel(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        val prefix = CobblemonEventsMod.config.prefix
        val model = StringArgumentType.getString(ctx, "name")
        AiProfileRegistry.setAdvisorModel(model)
        source.sendFeedback({ Text.literal("${prefix}[AI Advisor] model updated.") }, true)
        return 1
    }

    private fun setAdvisorToken(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        val prefix = CobblemonEventsMod.config.prefix
        val token = StringArgumentType.getString(ctx, "value")
        AiProfileRegistry.setAdvisorToken(token)
        source.sendFeedback({ Text.literal("${prefix}[AI Advisor] token updated.") }, true)
        return 1
    }

    private fun setAdvisorTimeout(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        val prefix = CobblemonEventsMod.config.prefix
        val ms = IntegerArgumentType.getInteger(ctx, "ms")
        AiProfileRegistry.setAdvisorTimeoutMs(ms)
        source.sendFeedback({ Text.literal("${prefix}[AI Advisor] timeout set to ${ms}ms.") }, true)
        return 1
    }

    private fun testAdvisor(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        val prefix = CobblemonEventsMod.config.prefix
        val (ok, message) = ExternalAiAdvisor.testConnection()
        source.sendFeedback(
            { Text.literal("${prefix}[AI Advisor] test=${if (ok) "ok" else "fail"} ($message)") },
            false
        )
        return if (ok) 1 else 0
    }
}
