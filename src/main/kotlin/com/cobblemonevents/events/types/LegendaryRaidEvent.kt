package com.cobblemonevents.events.types

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemonevents.CobblemonEventsMod
import com.cobblemonevents.config.RaidBossEntry
import com.cobblemonevents.events.ActiveEvent
import com.cobblemonevents.events.EventHandler
import com.cobblemonevents.rewards.RewardManager
import com.cobblemonevents.util.BroadcastUtil
import com.cobblemonevents.util.SpawnHelper
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.world.World
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import java.util.UUID
import kotlin.random.Random

class LegendaryRaidEvent : EventHandler {
    companion object {
        private const val DATA_RAID_BOSS = "raidBoss"
        private const val DATA_BOSS_DEFEATED = "bossDefeated"
        private const val DATA_RAID_BATTLE_MODE = "raidBattleMode"
        private const val DATA_RAID_BOSS_ENTITY_UUID = "raidBossEntityUuid"
        private const val DATA_RAID_DIMENSION_ID = "raidDimensionId"

        private const val RAID_BOSS_SCALE = 5.0f
        private const val RAID_BOSS_SEARCH_RADIUS = 32.0

        private const val RAID_PARTICIPANT_SCAN_INTERVAL_TICKS = 20L
        private const val RAID_PARTICIPANT_RADIUS = 96.0
        private const val RAID_PARTICIPANT_RADIUS_SQ = RAID_PARTICIPANT_RADIUS * RAID_PARTICIPANT_RADIUS
        private const val RAID_DIMENSION_ID = "minecraft:the_nether"
        private const val RAID_NETHER_SPAWN_Y = 210
        private const val RAID_NETHER_PLATFORM_RADIUS = 18
        private const val RAID_NETHER_CLEAR_HEIGHT = 18
    }

    override fun onStart(event: ActiveEvent, server: MinecraftServer) {
        val config = event.definition.raidConfig ?: return
        val bossPool = config.bossPool
        if (bossPool.isEmpty()) return

        val world = resolveRaidWorldForStart(server)
        val pos = if (world.registryKey.value.toString() == RAID_DIMENSION_ID) {
            createNetherRaidArenaCenter(server, world)
        } else {
            val location = SpawnHelper.findRandomEventLocation(server, 300) ?: return
            location.second
        }
        event.eventLocation = pos
        event.setData(DATA_RAID_DIMENSION_ID, world.registryKey.value.toString())
        val shuffledBosses = bossPool.shuffled()

        var selectedBoss: RaidBossEntry? = null
        var spawnedBossEntity: PokemonEntity? = null
        var raidBattleMode = false

        for (candidate in shuffledBosses) {
            val spawnedBoss = trySpawnRaidBattleBoss(server, world, pos, candidate.species)
            if (spawnedBoss != null) {
                selectedBoss = candidate
                spawnedBossEntity = spawnedBoss
                raidBattleMode = true
                break
            }
        }

        if (!raidBattleMode) {
            for (candidate in shuffledBosses) {
                val spawned = SpawnHelper.spawnPokemon(
                    world = world,
                    species = candidate.species,
                    pos = pos,
                    level = candidate.level,
                    shiny = Math.random() < config.bossShinyChance
                )
                if (spawned != null) {
                    applyRaidBossScale(spawned)
                    selectedBoss = candidate
                    spawnedBossEntity = spawned
                    break
                }
            }
        }

        if (selectedBoss == null) {
            CobblemonEventsMod.LOGGER.warn("[LegendaryRaid] 스폰 가능한 보스를 찾지 못해 이벤트 시작을 취소합니다.")
            BroadcastUtil.broadcast(
                server,
                "${CobblemonEventsMod.config.prefix}§c전설 레이드 보스 스폰에 실패해 이번 이벤트를 취소했습니다."
            )
            return
        }

        event.setData(DATA_RAID_BOSS, selectedBoss)
        event.setData(DATA_BOSS_DEFEATED, false)
        event.setData(DATA_RAID_BATTLE_MODE, raidBattleMode)
        if (spawnedBossEntity != null) {
            event.setData(DATA_RAID_BOSS_ENTITY_UUID, spawnedBossEntity.uuid.toString())
        }

        BroadcastUtil.announceRaid(server, selectedBoss.displayName, pos.x, pos.y, pos.z)
        if (world.registryKey.value.toString() == RAID_DIMENSION_ID) {
            BroadcastUtil.broadcast(
                server,
                "${CobblemonEventsMod.config.prefix}§5레이드 돔이 네더 상공(Y:${pos.y})에 생성되었습니다."
            )
        }

        if (raidBattleMode) {
            BroadcastUtil.broadcast(
                server,
                "${CobblemonEventsMod.config.prefix}§bRaidDens 레이드 보스가 생성되었습니다. 동시 공격/동시 포획 시도가 가능합니다."
            )
        }
    }

    override fun onTick(event: ActiveEvent, server: MinecraftServer) {
        val pos = event.eventLocation ?: return
        val bossDefeated = event.getData<Boolean>(DATA_BOSS_DEFEATED) ?: false
        val raidBattleMode = event.getData<Boolean>(DATA_RAID_BATTLE_MODE) ?: false
        val world = resolveRaidWorld(event, server)

        if (raidBattleMode && event.ticksRemaining % RAID_PARTICIPANT_SCAN_INTERVAL_TICKS == 0L) {
            trackNearbyRaidParticipants(event, server, world, pos)
        }

        if (bossDefeated) return

        if (event.ticksRemaining % 20 == 0L) {
            try {
                world.spawnParticles(
                    ParticleTypes.FLAME,
                    pos.x.toDouble(), pos.y.toDouble() + 3.0, pos.z.toDouble(),
                    10, 1.0, 1.0, 1.0, 0.05
                )
                world.spawnParticles(
                    ParticleTypes.SOUL_FIRE_FLAME,
                    pos.x.toDouble(), pos.y.toDouble() + 2.0, pos.z.toDouble(),
                    5, 0.5, 0.5, 0.5, 0.02
                )
            } catch (_: Exception) {
            }
        }

        if (event.ticksRemaining % (20 * 180) == 0L && event.ticksRemaining > 0) {
            val boss = event.getData<RaidBossEntry>(DATA_RAID_BOSS) ?: return
            BroadcastUtil.broadcast(
                server,
                "${CobblemonEventsMod.config.prefix}§6전설 ${boss.displayName}§7가 " +
                    "§eX:${pos.x} Y:${pos.y} Z:${pos.z}§7에서 기다리고 있습니다! " +
                    "§7(남은 시간: §f${event.getRemainingMinutes()}분§7)"
            )
        }
    }

    override fun onEnd(event: ActiveEvent, server: MinecraftServer) {
        val boss = event.getData<RaidBossEntry>(DATA_RAID_BOSS) ?: return
        val bossDefeated = event.getData<Boolean>(DATA_BOSS_DEFEATED) ?: false
        val raidBattleMode = event.getData<Boolean>(DATA_RAID_BATTLE_MODE) ?: false
        val config = event.definition.raidConfig ?: return
        val world = resolveRaidWorld(event, server)

        if (raidBattleMode && bossDefeated) {
            event.eventLocation?.let { pos ->
                trackNearbyRaidParticipants(event, server, world, pos)
            }
        }

        val despawned = despawnRaidBoss(event, world)

        if (bossDefeated) {
            BroadcastUtil.announceEventEnd(
                server,
                event.definition.displayName,
                listOf(
                    "§a§l보스를 처치했습니다!",
                    "§7보스: ${boss.displayName}",
                    "참가자: ${event.participants.size}명",
                    "이벤트 보스 디스폰: ${despawned}마리"
                )
            )

            for (playerUUID in event.participants.keys) {
                val player = server.playerManager.getPlayer(playerUUID) ?: continue

                RewardManager.giveRewards(player, config.raidRewards, event.definition)
                CobblemonEventsMod.rankingManager.recordLegendDefeat(playerUUID, player.name.string)

                if (Math.random() < config.catchChance) {
                    RewardManager.givePokemonDirect(
                        player,
                        boss.species,
                        boss.level,
                        shinyChance = config.bossShinyChance
                    )
                    BroadcastUtil.broadcast(
                        server,
                        "${CobblemonEventsMod.config.prefix}§6§l${player.name.string}§7님이 ${boss.displayName}§7을 포획했습니다!"
                    )
                }
            }
        } else {
            BroadcastUtil.announceEventEnd(
                server,
                event.definition.displayName,
                listOf(
                    "§c§l시간 초과! 보스가 도주했습니다...",
                    "§7보스: ${boss.displayName}",
                    "참가자: ${event.participants.size}명",
                    "이벤트 보스 디스폰: ${despawned}마리"
                )
            )
        }
    }

    override fun onBattleWon(event: ActiveEvent, player: ServerPlayerEntity, defeatedSpecies: String) {
        val boss = event.getData<RaidBossEntry>(DATA_RAID_BOSS) ?: return
        val raidBattleMode = event.getData<Boolean>(DATA_RAID_BATTLE_MODE) ?: false

        if (defeatedSpecies.equals(boss.species, ignoreCase = true)) {
            event.setData(DATA_BOSS_DEFEATED, true)
            event.addProgress(player.uuid)

            BroadcastUtil.broadcast(
                player.server!!,
                "${CobblemonEventsMod.config.prefix}§6§l전설 ${player.name.string}§7님이 ${boss.displayName}§7을 쓰러뜨렸습니다!"
            )

            if (!raidBattleMode) {
                event.ticksRemaining = 100
            }
        } else {
            event.participants.putIfAbsent(player.uuid, 0)
        }
    }

    private fun trackNearbyRaidParticipants(event: ActiveEvent, server: MinecraftServer, world: ServerWorld, pos: BlockPos) {
        val centerX = pos.x + 0.5
        val centerY = pos.y + 0.5
        val centerZ = pos.z + 0.5

        for (player in server.playerManager.playerList) {
            if (player.serverWorld.registryKey != world.registryKey) continue
            if (!player.isAlive || player.isSpectator) continue

            val distanceSq = player.squaredDistanceTo(centerX, centerY, centerZ)
            if (distanceSq <= RAID_PARTICIPANT_RADIUS_SQ) {
                event.participants.putIfAbsent(player.uuid, 0)
            }
        }
    }

    private fun trySpawnRaidBattleBoss(server: MinecraftServer, world: ServerWorld, pos: BlockPos, species: String): PokemonEntity? {
        val speciesId = species.lowercase()
        val dimensionId = world.registryKey.value.toString()
        val commands = listOf(
            "crd spawnboss ${pos.x} ${pos.y} ${pos.z} boss cobblemon:${speciesId}",
            "crd spawnboss ${pos.x} ${pos.y} ${pos.z} ${dimensionId} boss cobblemon:${speciesId}"
        )

        for (command in commands) {
            val existingBossIds = findNearbyBosses(world, pos, speciesId).map { it.uuid }.toSet()

            if (executeServerCommand(server, command)) {
                val nearbyBosses = findNearbyBosses(world, pos, speciesId)
                val spawnedBoss = nearbyBosses.firstOrNull { it.uuid !in existingBossIds }
                    ?: nearbyBosses.firstOrNull()

                if (spawnedBoss == null) {
                    CobblemonEventsMod.LOGGER.warn(
                        "[LegendaryRaid] Command succeeded but no boss entity was detected near ${pos.x},${pos.y},${pos.z}: $command"
                    )
                    continue
                }

                applyRaidBossScale(spawnedBoss)
                CobblemonEventsMod.LOGGER.info("[LegendaryRaid] Raid battle boss spawned: $command")
                return spawnedBoss
            }
        }

        return null
    }

    private fun executeServerCommand(server: MinecraftServer, rawCommand: String): Boolean {
        val command = rawCommand.trim().removePrefix("/")
        return try {
            server.commandManager.executeWithPrefix(server.commandSource, command)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun findNearbyBosses(world: ServerWorld, pos: BlockPos, speciesId: String): List<PokemonEntity> {
        val searchBox = Box(
            pos.x.toDouble() - RAID_BOSS_SEARCH_RADIUS,
            pos.y.toDouble() - 16.0,
            pos.z.toDouble() - RAID_BOSS_SEARCH_RADIUS,
            pos.x.toDouble() + RAID_BOSS_SEARCH_RADIUS,
            pos.y.toDouble() + 24.0,
            pos.z.toDouble() + RAID_BOSS_SEARCH_RADIUS
        )

        return world.getEntitiesByClass(PokemonEntity::class.java, searchBox) { entity ->
            entity.isAlive && entity.pokemon.species.name.equals(speciesId, ignoreCase = true)
        }
    }

    private fun despawnRaidBoss(event: ActiveEvent, world: ServerWorld): Int {
        val id = event.getData<String>(DATA_RAID_BOSS_ENTITY_UUID) ?: return 0
        val uuid = try {
            UUID.fromString(id)
        } catch (_: IllegalArgumentException) {
            null
        } ?: return 0

        val entity = world.getEntity(uuid)
        event.setData(DATA_RAID_BOSS_ENTITY_UUID, "")

        return if (entity is PokemonEntity && entity.isAlive) {
            entity.discard()
            1
        } else {
            0
        }
    }

    private fun applyRaidBossScale(entity: PokemonEntity) {
        try {
            entity.pokemon.scaleModifier = RAID_BOSS_SCALE
            entity.calculateDimensions()
        } catch (e: Exception) {
            CobblemonEventsMod.LOGGER.warn("[LegendaryRaid] Failed to apply raid boss scale ${RAID_BOSS_SCALE}x", e)
        }
    }

    private fun resolveRaidWorldForStart(server: MinecraftServer): ServerWorld {
        return server.getWorld(World.NETHER) ?: server.overworld
    }

    private fun resolveRaidWorld(event: ActiveEvent, server: MinecraftServer): ServerWorld {
        val dimensionId = event.getData<String>(DATA_RAID_DIMENSION_ID)
        if (!dimensionId.isNullOrBlank()) {
            val world = findWorldById(server, dimensionId)
            if (world != null) return world
        }
        return resolveRaidWorldForStart(server)
    }

    private fun findWorldById(server: MinecraftServer, worldId: String): ServerWorld? {
        return server.worldRegistryKeys
            .asSequence()
            .mapNotNull { key -> server.getWorld(key) }
            .firstOrNull { world -> world.registryKey.value.toString().equals(worldId, ignoreCase = true) }
    }

    private fun createNetherRaidArenaCenter(server: MinecraftServer, world: ServerWorld): BlockPos {
        val anchor = server.playerManager.playerList.randomOrNull()
        val baseX = if (anchor == null) {
            Random.nextInt(-1500, 1501)
        } else if (anchor.serverWorld.registryKey == World.NETHER) {
            anchor.blockPos.x
        } else {
            anchor.blockPos.x / 8
        }
        val baseZ = if (anchor == null) {
            Random.nextInt(-1500, 1501)
        } else if (anchor.serverWorld.registryKey == World.NETHER) {
            anchor.blockPos.z
        } else {
            anchor.blockPos.z / 8
        }

        val center = BlockPos(
            baseX + Random.nextInt(-600, 601),
            RAID_NETHER_SPAWN_Y.coerceAtLeast(200),
            baseZ + Random.nextInt(-600, 601)
        )
        buildNetherRaidPlatform(world, center)
        return center
    }

    private fun buildNetherRaidPlatform(world: ServerWorld, center: BlockPos) {
        val radius = RAID_NETHER_PLATFORM_RADIUS
        val floorY = center.y - 1
        for (x in center.x - radius..center.x + radius) {
            for (z in center.z - radius..center.z + radius) {
                val dx = x - center.x
                val dz = z - center.z
                val distanceSq = dx * dx + dz * dz
                if (distanceSq > radius * radius) continue

                for (y in center.y..center.y + RAID_NETHER_CLEAR_HEIGHT) {
                    world.setBlockState(BlockPos(x, y, z), net.minecraft.block.Blocks.AIR.defaultState, net.minecraft.block.Block.NOTIFY_ALL)
                }

                world.setBlockState(
                    BlockPos(x, floorY, z),
                    if (distanceSq >= (radius - 1) * (radius - 1))
                        net.minecraft.block.Blocks.CRYING_OBSIDIAN.defaultState
                    else
                        net.minecraft.block.Blocks.OBSIDIAN.defaultState,
                    net.minecraft.block.Block.NOTIFY_ALL
                )
            }
        }
    }
}
