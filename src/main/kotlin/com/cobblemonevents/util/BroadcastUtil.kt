package com.cobblemonevents.util

import com.cobblemonevents.CobblemonEventsMod
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text

object BroadcastUtil {

    private val prefix get() = CobblemonEventsMod.config.prefix

    /** 전체 서버 방송 */
    fun broadcast(server: MinecraftServer, message: String) {
        server.playerManager.playerList.forEach { player ->
            player.sendMessage(Text.literal(message), false)
        }
    }

    /** 빈 줄 */
    fun broadcastBlank(server: MinecraftServer) = broadcast(server, "")

    /** 구분선 */
    fun broadcastSeparator(server: MinecraftServer, color: String = "§6") {
        broadcast(server, "${color}§l═══════════════════════════════════")
    }

    /** 이벤트 시작 공지 */
    fun announceEventStart(
        server: MinecraftServer,
        eventName: String,
        description: String,
        duration: Int,
        extraLines: List<String> = emptyList()
    ) {
        broadcastBlank(server)
        broadcastSeparator(server, "§a")
        broadcast(server, "${prefix}§a§l✦ 이벤트 시작!")
        broadcast(server, "  $eventName")
        broadcast(server, "  $description")
        broadcast(server, "  §7제한시간: §f${duration}분")
        extraLines.forEach { broadcast(server, "  $it") }
        broadcastSeparator(server, "§a")
        broadcastBlank(server)

        // 사운드 효과
        playEventSound(server)
    }

    /** 이벤트 사전 공지 */
    fun announceUpcoming(server: MinecraftServer, eventName: String, description: String, minutesUntil: Int) {
        broadcastBlank(server)
        broadcast(server, "${prefix}§b§l⏰ 이벤트 사전 공지!")
        broadcast(server, "  $eventName §7이(가) §e${minutesUntil}분 후§7에 시작됩니다!")
        broadcast(server, "  $description")
        broadcastBlank(server)
    }

    /** 이벤트 종료 공지 */
    fun announceEventEnd(
        server: MinecraftServer,
        eventName: String,
        stats: List<String> = emptyList()
    ) {
        broadcastBlank(server)
        broadcastSeparator(server, "§c")
        broadcast(server, "${prefix}§c§l✦ 이벤트 종료!")
        broadcast(server, "  $eventName §7이벤트가 종료되었습니다.")
        stats.forEach { broadcast(server, "  $it") }
        broadcastSeparator(server, "§c")
        broadcastBlank(server)
    }

    /** 시공 균열 스타일 공지 */
    fun announceRift(server: MinecraftServer, riftName: String, x: Int, y: Int, z: Int, duration: Int) {
        broadcastBlank(server)
        broadcast(server, "§d§l╔══════════════════════════════╗")
        broadcast(server, "§d§l║  ${prefix}§d§l⚠ 시공 균열이 열렸습니다!")
        broadcast(server, "§d§l║  §f$riftName")
        broadcast(server, "§d§l║  §7좌표: §eX:$x §7Y:§e$y §7Z:§e$z")
        broadcast(server, "§d§l║  §7${duration}분 후 닫힙니다!")
        broadcast(server, "§d§l╚══════════════════════════════╝")
        broadcastBlank(server)
        playWormholeSound(server)
    }

    /** 울트라 워프홀 공지 */
    fun announceWormhole(server: MinecraftServer, beastName: String, x: Int, y: Int, z: Int, duration: Int) {
        broadcastBlank(server)
        broadcast(server, "§5§l╔══════════════════════════════╗")
        broadcast(server, "§5§l║  §d§l🌌 울트라 워프홀 출현!")
        broadcast(server, "§5§l║  §f울트라비스트가 나타났습니다!")
        broadcast(server, "§5§l║  §7좌표: §eX:$x §7Y:§e$y §7Z:§e$z")
        broadcast(server, "§5§l║  §7${duration}분 후 닫힙니다!")
        broadcast(server, "§5§l╚══════════════════════════════╝")
        broadcastBlank(server)
        playWormholeSound(server)
    }

    /** 전설 레이드 공지 */
    fun announceRaid(server: MinecraftServer, bossName: String, x: Int, y: Int, z: Int) {
        broadcastBlank(server)
        broadcast(server, "§6§l╔══════════════════════════════╗")
        broadcast(server, "§6§l║  §e§l🌠 전설 레이드 등장!")
        broadcast(server, "§6§l║  §f$bossName §7이(가) 나타났습니다!")
        broadcast(server, "§6§l║  §7좌표: §eX:$x §7Y:§e$y §7Z:§e$z")
        broadcast(server, "§6§l║  §c힘을 모아 쓰러뜨리세요!")
        broadcast(server, "§6§l╚══════════════════════════════╝")
        broadcastBlank(server)
        playBossSound(server)
    }

    /** 사냥 시즌 공지 */
    fun announceHunting(server: MinecraftServer, targetPokemon: String, duration: Int) {
        broadcastBlank(server)
        broadcast(server, "§c§l╔══════════════════════════════╗")
        broadcast(server, "§c§l║  §f§l🏹 포켓몬 사냥 시즌 개막!")
        broadcast(server, "§c§l║  §7오늘의 사냥 포켓몬:")
        broadcast(server, "§c§l║  §e§l  $targetPokemon")
        broadcast(server, "§c§l║  §7가장 많이 잡은 사람이 보상!")
        broadcast(server, "§c§l║  §7제한시간: §f${duration}분")
        broadcast(server, "§c§l╚══════════════════════════════╝")
        broadcastBlank(server)
        playEventSound(server)
    }

    /** 럭키 이벤트 공지 */
    fun announceLucky(server: MinecraftServer, effectName: String, duration: Int) {
        broadcastBlank(server)
        broadcast(server, "§e§l★═══════════════════════════★")
        broadcast(server, "  §e§l🎰 럭키 이벤트 발동!")
        broadcast(server, "  $effectName")
        broadcast(server, "  §7지속시간: §f${duration}분")
        broadcast(server, "§e§l★═══════════════════════════★")
        broadcastBlank(server)
        playLuckySound(server)
    }

    /** 랭킹 공지 */
    fun announceRanking(server: MinecraftServer, rankings: List<Triple<String, Int, String>>) {
        broadcast(server, "${prefix}§e§l🏆 사냥 시즌 최종 결과!")
        broadcastBlank(server)
        val medals = listOf("§6🥇", "§f🥈", "§c🥉")
        for ((index, entry) in rankings.withIndex()) {
            val (playerName, count, _) = entry
            val medal = if (index < 3) medals[index] else "§7  ${index + 1}위"
            broadcast(server, "  $medal §f$playerName §7- §e${count}마리")
        }
        broadcastBlank(server)
    }

    /** 진행도 메시지 (액션바) */
    fun sendProgress(player: ServerPlayerEntity, message: String) {
        player.sendMessage(Text.literal(message), true)
    }

    /** 개인 메시지 */
    fun sendPersonal(player: ServerPlayerEntity, message: String) {
        player.sendMessage(Text.literal(message), false)
    }

    // ========== 사운드 효과 ==========

    private fun playEventSound(server: MinecraftServer) {
        server.playerManager.playerList.forEach { player ->
            player.playSoundToPlayer(
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundCategory.MASTER, 1.0f, 1.0f
            )
        }
    }

    private fun playWormholeSound(server: MinecraftServer) {
        server.playerManager.playerList.forEach { player ->
            player.playSoundToPlayer(
                SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                SoundCategory.MASTER, 1.0f, 0.5f
            )
        }
    }

    private fun playBossSound(server: MinecraftServer) {
        server.playerManager.playerList.forEach { player ->
            player.playSoundToPlayer(
                SoundEvents.ENTITY_ENDER_DRAGON_GROWL,
                SoundCategory.MASTER, 1.0f, 1.0f
            )
        }
    }

    private fun playLuckySound(server: MinecraftServer) {
        server.playerManager.playerList.forEach { player ->
            player.playSoundToPlayer(
                SoundEvents.ENTITY_PLAYER_LEVELUP,
                SoundCategory.MASTER, 1.0f, 1.2f
            )
        }
    }
}
