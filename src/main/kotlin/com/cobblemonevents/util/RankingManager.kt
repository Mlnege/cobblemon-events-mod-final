package com.cobblemonevents.util

import com.cobblemonevents.CobblemonEventsMod
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.fabricmc.loader.api.FabricLoader
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class PlayerStats(
    val uuid: String = "",
    val name: String = "",
    var totalCatches: Int = 0,
    var totalBattlesWon: Int = 0,
    var eventsCompleted: Int = 0,
    var legendsDefeated: Int = 0,
    val catchHistory: MutableMap<String, Int> = mutableMapOf() // eventId -> count
)

class RankingManager {

    private val GSON = GsonBuilder().setPrettyPrinting().create()
    private val DATA_FILE: File
        get() = FabricLoader.getInstance().configDir.resolve("cobblemon-events-stats.json").toFile()

    // 현재 이벤트별 임시 랭킹
    private val eventRankings = ConcurrentHashMap<String, ConcurrentHashMap<UUID, Int>>()

    // 영구 플레이어 통계
    private val playerStats = ConcurrentHashMap<UUID, PlayerStats>()

    // ========== 이벤트별 랭킹 ==========

    fun initEventRanking(eventId: String) {
        eventRankings[eventId] = ConcurrentHashMap()
    }

    fun addCatch(eventId: String, playerUUID: UUID, playerName: String, amount: Int = 1): Int {
        val ranking = eventRankings.getOrPut(eventId) { ConcurrentHashMap() }
        val newCount = ranking.merge(playerUUID, amount) { old, new -> old + new } ?: amount

        // 영구 통계 업데이트
        val stats = playerStats.getOrPut(playerUUID) {
            PlayerStats(playerUUID.toString(), playerName)
        }
        stats.totalCatches += amount
        stats.catchHistory[eventId] = newCount

        return newCount
    }

    fun getEventRanking(eventId: String): List<Triple<String, Int, UUID>> {
        val ranking = eventRankings[eventId] ?: return emptyList()
        return ranking.entries
            .sortedByDescending { it.value }
            .map { (uuid, count) ->
                val name = playerStats[uuid]?.name ?: uuid.toString().substring(0, 8)
                Triple(name, count, uuid)
            }
    }

    fun getTopN(eventId: String, n: Int): List<Triple<String, Int, UUID>> {
        return getEventRanking(eventId).take(n)
    }

    fun clearEventRanking(eventId: String) {
        eventRankings.remove(eventId)
    }

    fun getPlayerRank(eventId: String, playerUUID: UUID): Pair<Int, Int> {
        val ranking = getEventRanking(eventId)
        val index = ranking.indexOfFirst { it.third == playerUUID }
        val count = eventRankings[eventId]?.get(playerUUID) ?: 0
        return Pair(if (index >= 0) index + 1 else -1, count)
    }

    // ========== 영구 통계 ==========

    fun recordBattleWin(playerUUID: UUID, playerName: String) {
        val stats = playerStats.getOrPut(playerUUID) {
            PlayerStats(playerUUID.toString(), playerName)
        }
        stats.totalBattlesWon++
    }

    fun recordEventComplete(playerUUID: UUID, playerName: String) {
        val stats = playerStats.getOrPut(playerUUID) {
            PlayerStats(playerUUID.toString(), playerName)
        }
        stats.eventsCompleted++
    }

    fun recordLegendDefeat(playerUUID: UUID, playerName: String) {
        val stats = playerStats.getOrPut(playerUUID) {
            PlayerStats(playerUUID.toString(), playerName)
        }
        stats.legendsDefeated++
    }

    fun getPlayerStats(playerUUID: UUID): PlayerStats? = playerStats[playerUUID]

    // ========== 저장/로드 ==========

    fun save() {
        try {
            DATA_FILE.parentFile.mkdirs()
            val data = playerStats.values.toList()
            DATA_FILE.writeText(GSON.toJson(data))
        } catch (e: Exception) {
            CobblemonEventsMod.LOGGER.error("통계 저장 실패", e)
        }
    }

    fun load() {
        try {
            if (!DATA_FILE.exists()) return
            val json = DATA_FILE.readText()
            val type = object : TypeToken<List<PlayerStats>>() {}.type
            val data: List<PlayerStats> = GSON.fromJson(json, type) ?: return
            playerStats.clear()
            data.forEach { stats ->
                try {
                    playerStats[UUID.fromString(stats.uuid)] = stats
                } catch (_: Exception) {}
            }
            CobblemonEventsMod.LOGGER.info("[코블몬 이벤트] 플레이어 통계 ${playerStats.size}명 로드 완료")
        } catch (e: Exception) {
            CobblemonEventsMod.LOGGER.error("통계 로드 실패", e)
        }
    }
}
