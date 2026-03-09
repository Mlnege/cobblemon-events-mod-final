package com.cobblemonevents

import com.cobblemonevents.ai.AiGeneratedContentPlanner
import com.cobblemonevents.ai.AiProfileRegistry
import com.cobblemonevents.commands.AiGeneratedContentCommand
import com.cobblemonevents.commands.EventCommands
import com.cobblemonevents.config.EventConfig
import com.cobblemonevents.config.RuntimeConfigAutoReloader
import com.cobblemonevents.events.scheduler.EventScheduler
import com.cobblemonevents.integration.CobblemonHooks
import com.cobblemonevents.integration.ExternalModApiRegistry
import com.cobblemonevents.util.EventProgressHud
import com.cobblemonevents.util.RankingManager
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory

object CobblemonEventsMod : ModInitializer {

    const val MOD_ID = "cobblemon-events"
    const val VERSION = "2.4.1"
    val LOGGER = LoggerFactory.getLogger(MOD_ID)

    lateinit var config: EventConfig
        private set
    lateinit var scheduler: EventScheduler
        private set
    lateinit var rankingManager: RankingManager
        private set
    var server: MinecraftServer? = null
        private set

    @Volatile
    private var initialized = false

    override fun onInitialize() {
        if (initialized) {
            LOGGER.warn("[CobblemonEvents] 이미 초기화되어 중복 등록을 건너뜁니다.")
            return
        }
        initialized = true

        LOGGER.info("[CobblemonEvents] v$VERSION 초기화 중...")

        config = EventConfig.load()
        AiProfileRegistry.load()
        scheduler = EventScheduler()
        rankingManager = RankingManager()

        CobblemonHooks.register()
        ExternalModApiRegistry.logLoadedIntegrations()

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            EventCommands.register(dispatcher)
            AiGeneratedContentCommand.register(dispatcher)
        }

        ServerLifecycleEvents.SERVER_STARTED.register { srv ->
            server = srv
            AiProfileRegistry.applyStartupProfessionalPrompt()
            scheduler.onServerStarted(srv)
            AiGeneratedContentPlanner.onServerStarted()
            RuntimeConfigAutoReloader.onServerStarted()
            EventProgressHud.onServerStarted()
            rankingManager.load()
            LOGGER.info("[CobblemonEvents] 이벤트 스케줄러 시작. 등록된 이벤트 수: ${config.events.size}")
        }

        ServerLifecycleEvents.SERVER_STOPPING.register { srv ->
            scheduler.onServerStopping()
            AiGeneratedContentPlanner.onServerStopping()
            RuntimeConfigAutoReloader.onServerStopping()
            EventProgressHud.onServerStopping(srv)
            rankingManager.save()
            AiProfileRegistry.save()
            config.save()
            server = null
            LOGGER.info("[CobblemonEvents] 모드 종료.")
        }

        ServerTickEvents.END_SERVER_TICK.register { srv ->
            RuntimeConfigAutoReloader.tick(srv)
            scheduler.tick(srv)
            EventProgressHud.tick(srv)
            AiGeneratedContentPlanner.tick(srv)
        }

        PlayerBlockBreakEvents.BEFORE.register(PlayerBlockBreakEvents.Before { world, player, pos, _, _ ->
            if (world !is ServerWorld || player !is ServerPlayerEntity) {
                return@Before true
            }
            scheduler.canPlayerBreakBlock(player, world, pos)
        })

        LOGGER.info("[CobblemonEvents] v$VERSION 초기화 완료.")
        LOGGER.info("[CobblemonEvents] 지원 이벤트: 시공의 균열, 대탐험, 사냥 시즌, 전설 레이드, 럭키 이벤트, 울트라 웜홀, 커스텀 체육관")
    }
}


