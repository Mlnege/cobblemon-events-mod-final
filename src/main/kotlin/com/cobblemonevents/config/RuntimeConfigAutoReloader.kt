package com.cobblemonevents.config

import com.cobblemonevents.CobblemonEventsMod
import com.cobblemonevents.ai.AiProfileRegistry
import net.minecraft.server.MinecraftServer
import java.io.File

/**
 * 서버 실행 중 설정 파일 변경을 감지해 안전하게 자동 리로드한다.
 * 저장 중간 상태(임시 파일/부분 쓰기) 오탐을 줄이기 위해 같은 변경을 2회 연속 관측했을 때만 적용한다.
 */
object RuntimeConfigAutoReloader {
    private const val CHECK_INTERVAL_TICKS = 20L * 2L
    private const val STABLE_HITS_REQUIRED = 2

    @Volatile
    private var running = false
    private var ticksUntilCheck = CHECK_INTERVAL_TICKS

    private data class TrackedFileState(
        var lastAppliedPath: String = "",
        var lastAppliedModified: Long = Long.MIN_VALUE,
        var pendingPath: String = "",
        var pendingModified: Long = Long.MIN_VALUE,
        var pendingHits: Int = 0
    )

    private val eventConfigState = TrackedFileState()
    private val aiConfigState = TrackedFileState()
    private val explorerOverrideState = TrackedFileState()

    fun onServerStarted() {
        running = true
        ticksUntilCheck = CHECK_INTERVAL_TICKS
        prime(eventConfigState, EventConfig.configFile())
        prime(aiConfigState, AiProfileRegistry.configPathFile())
        prime(explorerOverrideState, ExplorerOverrideConfig.activeConfigFile())
    }

    fun onServerStopping() {
        running = false
        ticksUntilCheck = CHECK_INTERVAL_TICKS
    }

    fun tick(server: MinecraftServer) {
        if (!running) return

        ticksUntilCheck--
        if (ticksUntilCheck > 0L) return
        ticksUntilCheck = CHECK_INTERVAL_TICKS

        val eventChanged = detectStableChange(eventConfigState, EventConfig.configFile())
        val explorerChanged = detectStableChange(explorerOverrideState, ExplorerOverrideConfig.activeConfigFile())
        val aiChanged = detectStableChange(aiConfigState, AiProfileRegistry.configPathFile())

        if (eventChanged || explorerChanged) {
            runCatching {
                CobblemonEventsMod.scheduler.reload()
            }.onSuccess {
                CobblemonEventsMod.LOGGER.info(
                    "[AutoReload] 이벤트 설정 자동 반영 완료 (events=$eventChanged, explorer=$explorerChanged)"
                )
            }.onFailure { e ->
                CobblemonEventsMod.LOGGER.error("[AutoReload] 이벤트 설정 자동 반영 실패", e)
            }
        }

        if (aiChanged) {
            val ok = AiProfileRegistry.reloadFromDisk()
            if (ok) {
                CobblemonEventsMod.LOGGER.info("[AutoReload] AI 설정 자동 반영 완료")
            } else {
                CobblemonEventsMod.LOGGER.warn("[AutoReload] AI 설정 자동 반영 실패 (기존 값 유지)")
            }
        }
    }

    private fun prime(state: TrackedFileState, file: File) {
        state.lastAppliedPath = file.absolutePath
        state.lastAppliedModified = if (file.exists()) file.lastModified() else -1L
        state.pendingPath = ""
        state.pendingModified = Long.MIN_VALUE
        state.pendingHits = 0
    }

    private fun detectStableChange(state: TrackedFileState, file: File): Boolean {
        val path = file.absolutePath
        val modified = if (file.exists()) file.lastModified() else -1L

        if (path == state.lastAppliedPath && state.lastAppliedModified == -1L && modified > 0L) {
            state.lastAppliedModified = modified
            state.pendingPath = ""
            state.pendingModified = Long.MIN_VALUE
            state.pendingHits = 0
            return false
        }

        if (path == state.lastAppliedPath && modified == state.lastAppliedModified) {
            state.pendingPath = ""
            state.pendingModified = Long.MIN_VALUE
            state.pendingHits = 0
            return false
        }

        if (path == state.pendingPath && modified == state.pendingModified) {
            state.pendingHits++
        } else {
            state.pendingPath = path
            state.pendingModified = modified
            state.pendingHits = 1
        }

        if (state.pendingHits < STABLE_HITS_REQUIRED) {
            return false
        }

        state.lastAppliedPath = path
        state.lastAppliedModified = modified
        state.pendingPath = ""
        state.pendingModified = Long.MIN_VALUE
        state.pendingHits = 0
        return true
    }
}
