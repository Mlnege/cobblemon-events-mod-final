package com.cobblemonevents.events.types

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemonevents.CobblemonEventsMod
import com.cobblemonevents.events.ActiveEvent
import com.cobblemonevents.events.EventHandler
import com.cobblemonevents.integration.ExternalModApiRegistry
import com.cobblemonevents.rewards.RewardManager
import com.cobblemonevents.util.BroadcastUtil
import com.cobblemonevents.util.SpawnHelper
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import java.util.UUID

class UltraWormholeEvent : EventHandler {

    companion object {
        private const val DATA_SELECTED_BEASTS = "selectedBeasts"
        private const val DATA_CAUGHT_BEASTS = "caughtBeasts"
        private const val DATA_WORMHOLE_TARGET = "wormholeTargetPlayer"
        private const val DATA_WORMHOLE_SPAWNED_BY_COMMAND = "wormholeSpawnedByCommand"
        private const val DATA_WORMHOLE_RESPAWN_LOCKED = "wormholeRespawnLocked"
        private const val DATA_ULTRA_SPACE_DIMENSION_IDS = "ultraSpaceDimensionIds"
        private const val DATA_TRACKED_BEAST_ENTITY_UUIDS = "trackedWormholeBeastUuids"

        private const val WORMHOLE_HEIGHT_OFFSET = 12
        private const val WORMHOLE_KEEPALIVE_INTERVAL_TICKS = 20L * 30L
        private const val WORMHOLE_BEAST_TRACK_RADIUS = 64.0

        private val DEFAULT_ULTRA_SPACE_DIMENSION_IDS = setOf("ultrabeasts:ultra_space")
        private val SAFE_PLAYER_NAME = Regex("^[A-Za-z0-9_]{1,16}$")

        /** Canonical mod ID for Cobblemon Ultra Beasts, plus known aliases for multi-variant detection. */
        private val ULTRA_BEASTS_MOD_IDS = listOf("cobblemon_ultrabeast", "cobblemon-ultra-beasts", "ultrabeasts")
    }

    override fun onStart(event: ActiveEvent, server: MinecraftServer) {
        val config = event.definition.wormholeConfig ?: return

        val ultraSpaceDimensionIds = resolveUltraSpaceDimensionIds(server, config)
        event.setData(DATA_ULTRA_SPACE_DIMENSION_IDS, ultraSpaceDimensionIds.toList())

        val location = SpawnHelper.findRandomEventLocation(server, config.wormholeSearchRadius)
        if (location == null) {
            CobblemonEventsMod.LOGGER.warn("[UltraWormhole] 이벤트 위치를 찾지 못해 시작을 취소합니다.")
            return
        }

        val (_, fallbackPos) = location
        event.eventLocation = fallbackPos

        val selectedBeasts = config.ultraBeastPool
            .shuffled()
            .take(config.spawnCount.coerceAtMost(config.ultraBeastPool.size))

        event.setData(DATA_SELECTED_BEASTS, selectedBeasts)
        event.setData(DATA_CAUGHT_BEASTS, mutableMapOf<String, Int>())

        val spawnedByCommand = spawnWormholeForRandomPlayer(server, event)
        event.setData(DATA_WORMHOLE_SPAWNED_BY_COMMAND, spawnedByCommand)
        event.setData(DATA_WORMHOLE_RESPAWN_LOCKED, false)

        if (!spawnedByCommand) {
            val spawned = SpawnHelper.spawnMultiplePokemon(
                world = server.overworld,
                speciesList = selectedBeasts,
                centerPos = fallbackPos,
                radius = 40,
                count = config.spawnCount,
                levelMin = config.wormholeLevel - 5,
                levelMax = config.wormholeLevel + 5,
                shinyChance = config.wormholeShinyChance
            )
            rememberSpawnedBeasts(event, spawned)
        }

        val pos = event.eventLocation ?: fallbackPos
        BroadcastUtil.announceWormhole(
            server,
            selectedBeasts.joinToString(", "),
            pos.x,
            pos.y,
            pos.z,
            event.definition.durationMinutes
        )

        BroadcastUtil.broadcast(server, "${CobblemonEventsMod.config.prefix}울트라 워프홀 이벤트가 시작되었습니다! / Ultra Wormhole Event has begun!")
        selectedBeasts.forEach { beast ->
            BroadcastUtil.broadcast(server, "  - $beast")
        }
        BroadcastUtil.broadcastBlank(server)
    }

    private fun spawnWormholeForRandomPlayer(server: MinecraftServer, event: ActiveEvent): Boolean {
        if (!ExternalModApiRegistry.isAnyLoaded(ULTRA_BEASTS_MOD_IDS)) {
            CobblemonEventsMod.LOGGER.info(
                "[UltraWormhole] Ultra Beasts mod not present (checked: ${ULTRA_BEASTS_MOD_IDS.joinToString()}). " +
                    "Skipping wormhole command — Cobblemon native spawns will be used instead. " +
                    "울트라비스트 모드 미감지: 워프홀 명령 생략, 기본 스폰으로 대체합니다."
            )
            return false
        }

        val players = server.playerManager.playerList
        if (players.isEmpty()) {
            CobblemonEventsMod.LOGGER.warn("[UltraWormhole] 온라인 플레이어가 없어 웜홀 명령을 실행하지 못했습니다.")
            return false
        }

        val target = players.random()
        val playerName = target.gameProfile.name
        if (!SAFE_PLAYER_NAME.matches(playerName)) {
            CobblemonEventsMod.LOGGER.warn("[UltraWormhole] 플레이어 이름이 명령 실행에 안전하지 않아 취소: $playerName")
            return false
        }

        val spawnPos = target.blockPos.up(WORMHOLE_HEIGHT_OFFSET)
        event.eventLocation = spawnPos
        event.setData(DATA_WORMHOLE_TARGET, playerName)

        val command = "execute as $playerName at $playerName positioned ~ ~${WORMHOLE_HEIGHT_OFFSET} ~ run ultrabeasts wormhole spawn"
        val ok = executeServerCommand(server, command)

        if (ok) {
            CobblemonEventsMod.LOGGER.info("[UltraWormhole] 웜홀 생성 명령 실행 완료: $command")
        } else {
            CobblemonEventsMod.LOGGER.warn("[UltraWormhole] 웜홀 생성 명령 실행 실패: $command")
        }

        return ok
    }

    override fun onTick(event: ActiveEvent, server: MinecraftServer) {
        val pos = event.eventLocation ?: return
        val world = server.overworld
        val spawnedByCommand = event.getData<Boolean>(DATA_WORMHOLE_SPAWNED_BY_COMMAND) == true

        if (spawnedByCommand && hasPlayersInUltraSpace(event, server)) {
            event.setData(DATA_WORMHOLE_RESPAWN_LOCKED, true)
        }

        val respawnLocked = event.getData<Boolean>(DATA_WORMHOLE_RESPAWN_LOCKED) == true
        if (spawnedByCommand && !respawnLocked && event.ticksRemaining > 0 && event.ticksRemaining % WORMHOLE_KEEPALIVE_INTERVAL_TICKS == 0L) {
            keepWormholeAlive(server, pos)
        }

        if (event.ticksRemaining % 10 == 0L) {
            spawnWormholeParticles(world, pos)
        }

        if (event.ticksRemaining > 0 && event.ticksRemaining % (20 * 180) == 0L) {
            BroadcastUtil.broadcast(
                server,
                "${CobblemonEventsMod.config.prefix}워프홀이 열려 있습니다 / Wormhole is open — X:${pos.x} Y:${pos.y} Z:${pos.z} (남은 / Remaining: ${event.getRemainingMinutes()}분 / min)"
            )
        }

        val config = event.definition.wormholeConfig ?: return
        val selectedBeasts = event.getData<List<String>>(DATA_SELECTED_BEASTS) ?: return

        if (event.ticksRemaining > 0 && event.ticksRemaining % (20 * 240) == 0L) {
            val beforeSpawnIds = findNearbyBeasts(world, pos, selectedBeasts)
                .map { it.uuid.toString() }
                .toSet()

            val ultraBeastsModPresent = ExternalModApiRegistry.isAnyLoaded(ULTRA_BEASTS_MOD_IDS)
            val addSpawnCommand = "ultrabeasts wormhole addspawn ${pos.x} ${pos.y} ${pos.z} 2"
            val addSpawnSuccess = ultraBeastsModPresent && executeServerCommand(server, addSpawnCommand)

            if (!addSpawnSuccess) {
                val spawned = SpawnHelper.spawnMultiplePokemon(
                    world = world,
                    speciesList = selectedBeasts,
                    centerPos = pos,
                    radius = 40,
                    count = 2,
                    levelMin = config.wormholeLevel - 5,
                    levelMax = config.wormholeLevel + 5,
                    shinyChance = config.wormholeShinyChance
                )
                rememberSpawnedBeasts(event, spawned)
            } else {
                rememberNewNearbyBeasts(event, world, pos, selectedBeasts, beforeSpawnIds)
            }

            BroadcastUtil.broadcast(
                server,
                "${CobblemonEventsMod.config.prefix}워프홀에서 추가 울트라비스트가 출현했습니다! / More Ultra Beasts have appeared from the wormhole!"
            )
        }
    }

    override fun onEnd(event: ActiveEvent, server: MinecraftServer) {
        val config = event.definition.wormholeConfig ?: return

        val clearSuccess = clearWormhole(server, event)
        if (!clearSuccess) {
            CobblemonEventsMod.LOGGER.warn("[UltraWormhole] 웜홀 정리 명령이 모두 실패했습니다.")
        }

        val despawned = despawnTrackedBeasts(event, server.overworld)

        for (playerUUID in event.participants.keys) {
            val player = server.playerManager.getPlayer(playerUUID) ?: continue
            val catches = event.getProgress(playerUUID)
            if (catches > 0) {
                RewardManager.giveRewards(player, config.wormholeRewards, event.definition)
                CobblemonEventsMod.rankingManager.recordEventComplete(playerUUID, player.name.string)
            }
        }

        val caughtBeasts = event.getData<MutableMap<String, Int>>(DATA_CAUGHT_BEASTS) ?: mutableMapOf()
        val totalCaught = caughtBeasts.values.sum()

        BroadcastUtil.announceEventEnd(
            server,
            event.definition.displayName,
            listOf(
                "참가자 / Participants: ${event.participants.size}명",
                "포획한 울트라비스트 / Ultra Beasts Caught: ${totalCaught}마리",
                "이벤트 스폰 디스폰 / Event Spawns Despawned: ${despawned}마리",
                "웜홀이 닫혔습니다. / Wormhole has closed."
            )
        )
    }

    override fun onPokemonCaught(event: ActiveEvent, player: ServerPlayerEntity, species: String) {
        val config = event.definition.wormholeConfig ?: return

        val isUltraBeast = config.ultraBeastPool.any { it.equals(species, ignoreCase = true) }
        if (!isUltraBeast) return

        event.addProgress(player.uuid)

        val caughtBeasts = event.getData<MutableMap<String, Int>>(DATA_CAUGHT_BEASTS) ?: mutableMapOf()
        caughtBeasts[species] = (caughtBeasts[species] ?: 0) + 1
        event.setData(DATA_CAUGHT_BEASTS, caughtBeasts)

        val count = event.getProgress(player.uuid)

        BroadcastUtil.broadcast(
            player.server!!,
            "${CobblemonEventsMod.config.prefix}${player.name.string} 님이 울트라비스트 $species 를 포획했습니다! / caught Ultra Beast $species! (${count}회 / times)"
        )

        BroadcastUtil.sendProgress(player, "울트라비스트 포획 / Ultra Beast caught: ${count}마리 / total")
        RewardManager.giveRewards(player, config.wormholeRewards, event.definition)
    }

    private fun executeServerCommand(server: MinecraftServer, rawCommand: String): Boolean {
        val command = rawCommand.trim().removePrefix("/")
        return try {
            server.commandManager.executeWithPrefix(server.commandSource, command)
            true
        } catch (e: Exception) {
            CobblemonEventsMod.LOGGER.warn("[UltraWormhole] 명령 실행 중 오류: $command", e)
            false
        }
    }

    private fun clearWormhole(server: MinecraftServer, event: ActiveEvent): Boolean {
        val pos = event.eventLocation
        val commands = mutableListOf("ultrabeasts wormhole clear")

        if (pos != null) {
            commands += "execute in minecraft:overworld positioned ${pos.x + 0.5} ${pos.y.toDouble()} ${pos.z + 0.5} run ultrabeasts wormhole clear"
        }

        val targetPlayer = event.getData<String>(DATA_WORMHOLE_TARGET)
        if (targetPlayer != null && SAFE_PLAYER_NAME.matches(targetPlayer)) {
            commands += "execute as $targetPlayer at $targetPlayer run ultrabeasts wormhole clear"
        }

        var success = false
        for (command in commands) {
            val ok = executeServerCommand(server, command)
            if (ok) {
                success = true
            }
        }
        return success
    }

    private fun keepWormholeAlive(server: MinecraftServer, pos: BlockPos) {
        if (!ExternalModApiRegistry.isAnyLoaded(ULTRA_BEASTS_MOD_IDS)) return
        val command = "execute in minecraft:overworld positioned ${pos.x + 0.5} ${pos.y.toDouble()} ${pos.z + 0.5} run ultrabeasts wormhole spawn"
        val ok = executeServerCommand(server, command)
        if (!ok) {
            CobblemonEventsMod.LOGGER.warn("[UltraWormhole] 웜홀 유지 스폰 명령 실행 실패: $command")
        }
    }

    private fun hasPlayersInUltraSpace(event: ActiveEvent, server: MinecraftServer): Boolean {
        val ultraSpaceIds = event.getData<List<String>>(DATA_ULTRA_SPACE_DIMENSION_IDS)
            ?.map { normalizeDimensionId(it) }
            ?.toSet()
            ?.ifEmpty { DEFAULT_ULTRA_SPACE_DIMENSION_IDS }
            ?: DEFAULT_ULTRA_SPACE_DIMENSION_IDS

        return server.playerManager.playerList.any { player ->
            val playerDimensionId = normalizeDimensionId(player.serverWorld.registryKey.value.toString())
            ultraSpaceIds.contains(playerDimensionId)
        }
    }

    private fun resolveUltraSpaceDimensionIds(
        server: MinecraftServer,
        config: com.cobblemonevents.config.UltraWormholeConfig
    ): Set<String> {
        val configured = config.ultraSpaceDimensionIds
            ?.map { normalizeDimensionId(it) }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()
        if (configured.isNotEmpty()) {
            CobblemonEventsMod.LOGGER.info("[UltraWormhole] 설정된 울트라 차원 ID 사용: ${configured.joinToString()}")
            return configured
        }

        val discovered = server.worldRegistryKeys
            .map { normalizeDimensionId(it.value.toString()) }
            .filter { it.contains("ultra") && (it.contains("space") || it.contains("beast")) }
            .toSet()
        if (discovered.isNotEmpty()) {
            CobblemonEventsMod.LOGGER.info("[UltraWormhole] 서버에서 자동 감지한 울트라 차원 ID: ${discovered.joinToString()}")
            return discovered
        }

        CobblemonEventsMod.LOGGER.warn("[UltraWormhole] 울트라 차원 ID를 찾지 못해 기본값을 사용합니다: ${DEFAULT_ULTRA_SPACE_DIMENSION_IDS.joinToString()}")
        return DEFAULT_ULTRA_SPACE_DIMENSION_IDS
    }

    private fun normalizeDimensionId(raw: String): String {
        val trimmed = raw.trim().lowercase()
        if (trimmed.isEmpty()) return ""
        return if (":" in trimmed) trimmed else "minecraft:$trimmed"
    }

    private fun rememberSpawnedBeasts(event: ActiveEvent, spawned: List<PokemonEntity>) {
        if (spawned.isEmpty()) return

        val tracked = event.getData<MutableSet<String>>(DATA_TRACKED_BEAST_ENTITY_UUIDS) ?: mutableSetOf()
        tracked.addAll(spawned.map { it.uuid.toString() })
        event.setData(DATA_TRACKED_BEAST_ENTITY_UUIDS, tracked)
    }

    private fun rememberNewNearbyBeasts(
        event: ActiveEvent,
        world: ServerWorld,
        pos: BlockPos,
        speciesList: List<String>,
        beforeSpawnIds: Set<String>
    ) {
        val newEntities = findNearbyBeasts(world, pos, speciesList)
            .filter { it.uuid.toString() !in beforeSpawnIds }
        rememberSpawnedBeasts(event, newEntities)
    }

    private fun findNearbyBeasts(world: ServerWorld, pos: BlockPos, speciesList: List<String>): List<PokemonEntity> {
        val speciesSet = speciesList.map { it.lowercase() }.toSet()
        val searchBox = Box(
            pos.x.toDouble() - WORMHOLE_BEAST_TRACK_RADIUS,
            pos.y.toDouble() - 32.0,
            pos.z.toDouble() - WORMHOLE_BEAST_TRACK_RADIUS,
            pos.x.toDouble() + WORMHOLE_BEAST_TRACK_RADIUS,
            pos.y.toDouble() + 48.0,
            pos.z.toDouble() + WORMHOLE_BEAST_TRACK_RADIUS
        )

        return world.getEntitiesByClass(PokemonEntity::class.java, searchBox) { entity ->
            entity.isAlive && speciesSet.contains(entity.pokemon.species.name.lowercase())
        }
    }

    private fun despawnTrackedBeasts(event: ActiveEvent, world: ServerWorld): Int {
        val tracked = event.getData<MutableSet<String>>(DATA_TRACKED_BEAST_ENTITY_UUIDS) ?: return 0
        var removed = 0

        for (id in tracked) {
            val uuid = try {
                UUID.fromString(id)
            } catch (_: IllegalArgumentException) {
                null
            } ?: continue

            val entity = world.getEntity(uuid)
            if (entity is PokemonEntity && entity.isAlive) {
                entity.discard()
                removed++
            }
        }

        tracked.clear()
        event.setData(DATA_TRACKED_BEAST_ENTITY_UUIDS, tracked)
        return removed
    }

    private fun spawnWormholeParticles(world: ServerWorld, center: BlockPos) {
        try {
            world.spawnParticles(
                ParticleTypes.PORTAL,
                center.x.toDouble() + 0.5,
                center.y.toDouble() + 3.0,
                center.z.toDouble() + 0.5,
                20,
                1.5,
                2.0,
                1.5,
                0.05
            )

            val positions = SpawnHelper.getCirclePositions(center, 4, 12)
            for (pos in positions) {
                world.spawnParticles(
                    ParticleTypes.DRAGON_BREATH,
                    pos.x.toDouble(),
                    pos.y.toDouble() + 2.0,
                    pos.z.toDouble(),
                    2,
                    0.1,
                    0.3,
                    0.1,
                    0.01
                )
            }

            world.spawnParticles(
                ParticleTypes.END_ROD,
                center.x.toDouble() + 0.5,
                center.y.toDouble() + 4.0,
                center.z.toDouble() + 0.5,
                5,
                0.3,
                0.5,
                0.3,
                0.02
            )
        } catch (_: Exception) {
        }
    }
}