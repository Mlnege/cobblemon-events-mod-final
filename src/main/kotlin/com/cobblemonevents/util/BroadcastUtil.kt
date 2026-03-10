package com.cobblemonevents.util

import com.cobblemonevents.CobblemonEventsMod
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text

object BroadcastUtil {

    private const val SEPARATOR = "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    private val prefix get() = CobblemonEventsMod.config.prefix

    private data class Theme(
        val icon: String,
        val title: String,
        val titleEn: String,
        val startSound: SoundEvent,
        val endSound: SoundEvent
    )

    fun broadcast(server: MinecraftServer, message: String) {
        server.playerManager.playerList.forEach { it.sendMessage(Text.literal(message), false) }
    }

    fun broadcastBlank(server: MinecraftServer) = broadcast(server, "")

    fun broadcastSeparator(server: MinecraftServer, marker: String = "◆") {
        broadcast(server, "$marker $SEPARATOR")
    }

    fun announceEventStart(
        server: MinecraftServer,
        eventName: String,
        description: String,
        duration: Int,
        extraLines: List<String> = emptyList()
    ) {
        val theme = resolveTheme(eventName)
        broadcastBlank(server)
        broadcastSeparator(server, theme.icon)
        broadcast(server, "${prefix}${theme.icon} ${theme.title} 시작 / ${theme.titleEn} Start")
        broadcast(server, "  • 이벤트 / Event: $eventName")
        broadcast(server, "  • 설명 / Description: $description")
        broadcast(server, "  • 지속시간 / Duration: ${duration}분 / min")
        extraLines.forEach { line -> broadcast(server, "  • $line") }
        broadcastSeparator(server, theme.icon)
        broadcastBlank(server)
        playSound(server, theme.startSound, 1.0f, 1.0f)
    }

    fun announceUpcoming(server: MinecraftServer, eventName: String, description: String, minutesUntil: Int) {
        val theme = resolveTheme(eventName)
        broadcastBlank(server)
        broadcast(server, "${prefix}${theme.icon} ${theme.title} 예고 / ${theme.titleEn} Incoming")
        broadcast(server, "  • 시작까지 / Starts in: ${minutesUntil}분 / min")
        broadcast(server, "  • 내용 / Details: $description")
        broadcastBlank(server)
        playSound(server, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 0.8f, 1.3f)
    }

    fun announceEventEnd(server: MinecraftServer, eventName: String, stats: List<String> = emptyList()) {
        val theme = resolveTheme(eventName)
        broadcastBlank(server)
        broadcastSeparator(server, theme.icon)
        broadcast(server, "${prefix}${theme.icon} ${theme.title} 종료 / ${theme.titleEn} Ended")
        stats.forEach { s -> broadcast(server, "  • $s") }
        broadcastSeparator(server, theme.icon)
        broadcastBlank(server)
        playSound(server, theme.endSound, 0.9f, 1.1f)
    }

    fun announceRift(server: MinecraftServer, riftName: String, x: Int, y: Int, z: Int, duration: Int) {
        broadcastBlank(server)
        broadcastSeparator(server, "◆")
        broadcast(server, "${prefix}◆ 시공의 균열 발생 / Temporal Rift Opened")
        broadcast(server, "  • 타입 / Type: $riftName")
        broadcast(server, "  • 좌표 / Coords: X:$x Y:$y Z:$z")
        broadcast(server, "  • 유지시간 / Duration: ${duration}분 / min")
        broadcast(server, "  • 균열 포켓몬 포획으로 보상 획득 / Catch Rift Pokémon for rewards")
        broadcastSeparator(server, "◆")
        broadcastBlank(server)
        playSound(server, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 1.0f)
    }

    fun announceWormhole(server: MinecraftServer, beastName: String, x: Int, y: Int, z: Int, duration: Int) {
        broadcastBlank(server)
        broadcastSeparator(server, "◈")
        broadcast(server, "${prefix}◈ 울트라 워프홀 개방 / Ultra Wormhole Opened")
        broadcast(server, "  • 주요 대상 / Featured: $beastName")
        broadcast(server, "  • 좌표 / Coords: X:$x Y:$y Z:$z")
        broadcast(server, "  • 유지시간 / Duration: ${duration}분 / min")
        broadcast(server, "  • 워프홀 근처에서 울트라비스트 탐색 가능 / Ultra Beasts appear near the wormhole")
        broadcastSeparator(server, "◈")
        broadcastBlank(server)
        playSound(server, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.85f)
    }

    fun announceRaid(server: MinecraftServer, bossName: String, x: Int, y: Int, z: Int) {
        broadcastBlank(server)
        broadcastSeparator(server, "⚔")
        broadcast(server, "${prefix}⚔ 전설 레이드 개시 / Legendary Raid Begin")
        broadcast(server, "  • 보스 / Boss: $bossName")
        broadcast(server, "  • 좌표 / Coords: X:$x Y:$y Z:$z")
        broadcast(server, "  • 동시 공격 및 동시 포획 시도 가능 / Simultaneous attacks and catch attempts allowed")
        broadcastSeparator(server, "⚔")
        broadcastBlank(server)
        playSound(server, SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f)
    }

    fun announceHunting(server: MinecraftServer, targetPokemon: String, duration: Int) {
        broadcastBlank(server)
        broadcastSeparator(server, "🏹")
        broadcast(server, "${prefix}🏹 사냥 시즌 시작 / Hunting Season Start")
        broadcast(server, "  • 목표 포켓몬 / Target: $targetPokemon")
        broadcast(server, "  • 지속시간 / Duration: ${duration}분 / min")
        broadcast(server, "  • 포획 순위에 따라 보상 지급 / Rewards based on catch ranking")
        broadcastSeparator(server, "🏹")
        broadcastBlank(server)
        playSound(server, SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 1.05f)
    }

    fun announceLucky(server: MinecraftServer, effectName: String, duration: Int) {
        broadcastBlank(server)
        broadcastSeparator(server, "✦")
        broadcast(server, "${prefix}✦ 럭키 이벤트 발동 / Lucky Event Activated")
        broadcast(server, "  • 효과 / Effect: $effectName")
        broadcast(server, "  • 지속시간 / Duration: ${duration}분 / min")
        broadcast(server, "  • 서버 전체에 확률 보정이 적용됩니다 / Server-wide rate bonus applied")
        broadcastSeparator(server, "✦")
        broadcastBlank(server)
        playSound(server, SoundEvents.BLOCK_BEACON_ACTIVATE, 1.0f, 1.15f)
    }

    fun announceRanking(server: MinecraftServer, rankings: List<Triple<String, Int, String>>) {
        broadcastBlank(server)
        broadcast(server, "${prefix}🏆 이벤트 최종 순위 / Final Rankings")
        rankings.forEachIndexed { idx, entry ->
            val rank = idx + 1
            val (playerName, count, _) = entry
            broadcast(server, "  #$rank $playerName - $count")
        }
        broadcastBlank(server)
    }

    fun sendProgress(player: ServerPlayerEntity, message: String) {
        player.sendMessage(Text.literal(message), true)
    }

    fun sendPersonal(player: ServerPlayerEntity, message: String) {
        player.sendMessage(Text.literal(message), false)
    }

    private fun resolveTheme(eventName: String): Theme {
        val lower = eventName.lowercase()
        return when {
            "시공" in eventName || "rift" in lower -> Theme(
                icon = "◆",
                title = "시공의 균열",
                titleEn = "Temporal Rift",
                startSound = SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE,
                endSound = SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value()
            )
            "웜홀" in eventName || "워프홀" in eventName || "wormhole" in lower -> Theme(
                icon = "◈",
                title = "울트라 워프홀",
                titleEn = "Ultra Wormhole",
                startSound = SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                endSound = SoundEvents.BLOCK_PORTAL_TRAVEL
            )
            "레이드" in eventName || "raid" in lower -> Theme(
                icon = "⚔",
                title = "전설 레이드",
                titleEn = "Legendary Raid",
                startSound = SoundEvents.ENTITY_ENDER_DRAGON_GROWL,
                endSound = SoundEvents.ENTITY_ENDER_DRAGON_DEATH
            )
            "사냥" in eventName || "hunt" in lower -> Theme(
                icon = "🏹",
                title = "사냥 시즌",
                titleEn = "Hunting Season",
                startSound = SoundEvents.ENTITY_PLAYER_LEVELUP,
                endSound = SoundEvents.BLOCK_NOTE_BLOCK_BELL.value()
            )
            "럭키" in eventName || "lucky" in lower -> Theme(
                icon = "✦",
                title = "럭키 이벤트",
                titleEn = "Lucky Event",
                startSound = SoundEvents.BLOCK_BEACON_ACTIVATE,
                endSound = SoundEvents.BLOCK_BEACON_DEACTIVATE
            )
            "탐험" in eventName || "explorer" in lower -> Theme(
                icon = "🧭",
                title = "대탐험",
                titleEn = "Explorer",
                startSound = SoundEvents.ITEM_LODESTONE_COMPASS_LOCK,
                endSound = SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value()
            )
            "체육관" in eventName || "gym" in lower -> Theme(
                icon = "🏟",
                title = "커스텀 체육관",
                titleEn = "Gym Challenge",
                startSound = SoundEvents.BLOCK_BEACON_ACTIVATE,
                endSound = SoundEvents.BLOCK_BEACON_DEACTIVATE
            )
            else -> Theme(
                icon = "★",
                title = "월드 이벤트",
                titleEn = "World Event",
                startSound = SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                endSound = SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value()
            )
        }
    }

    private fun playSound(server: MinecraftServer, sound: SoundEvent, volume: Float, pitch: Float) {
        server.playerManager.playerList.forEach { player ->
            player.playSoundToPlayer(sound, SoundCategory.MASTER, volume, pitch)
        }
    }
}
