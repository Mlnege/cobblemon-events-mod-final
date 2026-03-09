package com.cobblemonevents.events

import com.cobblemonevents.config.EventDefinition
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class EventState {
    WAITING, ANNOUNCED, ACTIVE, ENDED
}

/**
 * 활성 이벤트 인스턴스 - 모든 이벤트 유형 공통
 */
data class ActiveEvent(
    val definition: EventDefinition,
    var state: EventState = EventState.WAITING,
    var ticksUntilStart: Long = 0,
    var ticksRemaining: Long = 0,
    var ticksUntilAnnounce: Long = 0,

    // 참가자 관리
    val participants: ConcurrentHashMap<UUID, Int> = ConcurrentHashMap(),
    val completedPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet(),

    // 이벤트별 임시 데이터
    var eventLocation: BlockPos? = null,
    var extraData: MutableMap<String, Any> = ConcurrentHashMap()
) {
    fun isCompleted(playerUUID: UUID): Boolean = completedPlayers.contains(playerUUID)
    fun getProgress(playerUUID: UUID): Int = participants.getOrDefault(playerUUID, 0)

    fun addProgress(playerUUID: UUID, amount: Int = 1): Int {
        return participants.merge(playerUUID, amount) { old, new -> old + new } ?: amount
    }

    fun getRemainingMinutes(): Int = (ticksRemaining / 20 / 60).toInt()
    fun getRemainingSeconds(): Int = ((ticksRemaining / 20) % 60).toInt()

    @Suppress("UNCHECKED_CAST")
    fun <T> getData(key: String): T? = extraData[key] as? T
    fun setData(key: String, value: Any) { extraData[key] = value }
}

/**
 * 이벤트 핸들러 인터페이스 - 각 이벤트 유형이 구현
 */
interface EventHandler {
    /** 이벤트 시작 시 호출 */
    fun onStart(event: ActiveEvent, server: MinecraftServer)

    /** 매 틱 (진행 중일 때) */
    fun onTick(event: ActiveEvent, server: MinecraftServer)

    /** 이벤트 종료 시 호출 */
    fun onEnd(event: ActiveEvent, server: MinecraftServer)

    /** 포켓몬 포획 시 */
    fun onPokemonCaught(event: ActiveEvent, player: ServerPlayerEntity, species: String) {}

    /** 배틀 승리 시 */
    fun onBattleWon(event: ActiveEvent, player: ServerPlayerEntity, defeatedSpecies: String) {}

    fun canBreakBlock(event: ActiveEvent, player: ServerPlayerEntity, world: ServerWorld, pos: BlockPos): Boolean = true
}
