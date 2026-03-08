package com.cobblemonevents

import com.cobblemonevents.commands.EventCommands
import com.cobblemonevents.config.EventConfig
import com.cobblemonevents.events.scheduler.EventScheduler
import com.cobblemonevents.integration.CobblemonHooks
import com.cobblemonevents.util.RankingManager
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory

object CobblemonEventsMod : ModInitializer {

    const val MOD_ID = "cobblemon-events"
    const val VERSION = "2.1.0"
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
            LOGGER.warn("§e[코블몬 이벤트] §f이미 초기화되어 중복 등록을 건너뜁니다.")
            return
        }
        initialized = true

        LOGGER.info("§a[코블몬 이벤트] §fv$VERSION 초기화 중...")

        config = EventConfig.load()
        scheduler = EventScheduler()
        rankingManager = RankingManager()

        CobblemonHooks.register()

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            EventCommands.register(dispatcher)
        }

        ServerLifecycleEvents.SERVER_STARTED.register { srv ->
            server = srv
            scheduler.onServerStarted(srv)
            rankingManager.load()
            LOGGER.info("§a[코블몬 이벤트] §f이벤트 스케줄러 시작! 등록된 이벤트: ${config.events.size}개")
        }

        ServerLifecycleEvents.SERVER_STOPPING.register { _ ->
            scheduler.onServerStopping()
            rankingManager.save()
            config.save()
            server = null
            LOGGER.info("§a[코블몬 이벤트] §f모드 종료.")
        }

        ServerTickEvents.END_SERVER_TICK.register { srv ->
            scheduler.tick(srv)
        }

        LOGGER.info("§a[코블몬 이벤트] §fv$VERSION 초기화 완료!")
        LOGGER.info("§a[코블몬 이벤트] §f지원 이벤트: 시공균열, 대탐험, 사냥시즌, 전설레이드, 럭키, 울트라워프홀")
    }
}
