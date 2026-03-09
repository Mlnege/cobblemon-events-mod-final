package com.cobblemonevents.integration

import com.cobblemonevents.CobblemonEventsMod
import com.cobblemonevents.config.EventDefinition
import com.cobblemonevents.config.LeagueTierEntry
import com.cobblemonevents.rewards.RewardManager
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.Heightmap
import java.util.Locale

object LeagueChallengeService {
    data class LeagueActionResult(
        val success: Boolean,
        val message: String
    )

    private const val LEAGUE_DIMENSION_ID = "minecraft:overworld"
    private const val BADGES_PER_100_LEVEL = 6
    private const val MAX_BADGE_REQUIREMENT = 18
    private val SAFE_PLAYER_NAME = Regex("^[A-Za-z0-9_]{1,16}$")

    fun getTierLevels(): List<Int> {
        val tiers = resolveGymEventDefinition()?.gymConfig?.leagueTiers.orEmpty()
        return tiers.map { it.tierLevel }.sorted()
    }

    fun getPlayerGymLevel(player: ServerPlayerEntity): Int {
        return CobblemonEventsMod.rankingManager.getGymProgressLevel(player.uuid)
    }

    fun getRequiredBadgeCountForTier(tierLevel: Int): Int {
        val step = (tierLevel / 100).coerceAtLeast(1)
        return (step * BADGES_PER_100_LEVEL).coerceAtMost(MAX_BADGE_REQUIREMENT)
    }

    fun enterLeague(player: ServerPlayerEntity, tierLevel: Int): LeagueActionResult {
        val eventDef = resolveGymEventDefinition()
            ?: return LeagueActionResult(false, "GYM_CHALLENGE 설정이 없습니다.")
        val tier = findTier(eventDef, tierLevel)
            ?: return LeagueActionResult(false, "해당 리그 티어(${tierLevel})가 없습니다.")
        val server = player.server ?: return LeagueActionResult(false, "서버 참조 없음")

        val gymLevel = CobblemonEventsMod.rankingManager.getGymProgressLevel(player.uuid)
        if (gymLevel < tier.tierLevel) {
            return LeagueActionResult(false, "리그 입장 조건 미달 (현재 레벨: $gymLevel, 필요: ${tier.tierLevel})")
        }

        val ownedBadges = CobblemonEventsMod.rankingManager.getGymBadgeCount(player.uuid)
        val requiredBadges = getRequiredBadgeCountForTier(tier.tierLevel)
        val leagueCenter = resolveLeagueCenter(server, player, tier.tierLevel)
        spawnLeagueGateNpc(server, leagueCenter, requiredBadges, ownedBadges)
        if (ownedBadges < requiredBadges) {
            return LeagueActionResult(
                false,
                "리그 심사관: 배지 부족 (${ownedBadges}/${requiredBadges}). 해당 레벨 구간 체육관 배지를 먼저 모아야 합니다."
            )
        }

        val world = server.overworld
        val datapackBuilt = DatapackStructureService.placeHub(server, LEAGUE_DIMENSION_ID, leagueCenter)
        if (!datapackBuilt) {
            buildLeagueArena(world, leagueCenter, tier.tierLevel)
        }

        teleportPlayer(
            server = server,
            player = player,
            dimensionId = LEAGUE_DIMENSION_ID,
            x = leagueCenter.x + 0.5,
            y = leagueCenter.y + 2.0,
            z = leagueCenter.z + 0.5,
            yaw = player.yaw.toDouble(),
            pitch = player.pitch.toDouble()
        )

        val executed = ExternalModApiRegistry.runGymIntegrationCommands(
            server = server,
            commands = tier.entryCommands,
            variables = mapOf(
                "player_name" to player.gameProfile.name,
                "tier_level" to tier.tierLevel.toString(),
                "tier_name" to tier.displayName,
                "gym_level" to gymLevel.toString(),
                "badge_count" to ownedBadges.toString(),
                "required_badges" to requiredBadges.toString(),
                "x" to leagueCenter.x.toString(),
                "y" to leagueCenter.y.toString(),
                "z" to leagueCenter.z.toString()
            )
        )

        return if (executed > 0) {
            LeagueActionResult(
                true,
                "${tier.displayName} 입장 완료 (배지 ${ownedBadges}/${requiredBadges}, 연동 명령 ${executed}개 실행)"
            )
        } else {
            LeagueActionResult(
                true,
                "${tier.displayName} 입장 완료 (배지 ${ownedBadges}/${requiredBadges})"
            )
        }
    }

    fun clearLeague(player: ServerPlayerEntity, tierLevel: Int): LeagueActionResult {
        val eventDef = resolveGymEventDefinition()
            ?: return LeagueActionResult(false, "GYM_CHALLENGE 설정이 없습니다.")
        val tier = findTier(eventDef, tierLevel)
            ?: return LeagueActionResult(false, "해당 리그 티어(${tierLevel})가 없습니다.")
        val server = player.server ?: return LeagueActionResult(false, "서버 참조 없음")

        val gymLevel = CobblemonEventsMod.rankingManager.getGymProgressLevel(player.uuid)
        if (gymLevel < tier.tierLevel) {
            return LeagueActionResult(false, "리그 클리어 조건 미달 (현재 레벨: $gymLevel, 필요: ${tier.tierLevel})")
        }

        val ownedBadges = CobblemonEventsMod.rankingManager.getGymBadgeCount(player.uuid)
        val requiredBadges = getRequiredBadgeCountForTier(tier.tierLevel)
        if (ownedBadges < requiredBadges) {
            return LeagueActionResult(false, "리그 보상 수령 조건 미달 (배지 ${ownedBadges}/${requiredBadges})")
        }

        RewardManager.giveRewards(player, tier.rewards, eventDef)
        if (tier.trophyItemId.isNotBlank() && tier.trophyCount > 0) {
            RewardManager.giveItemDirect(player, tier.trophyItemId, tier.trophyCount)
        }

        val completionExecuted = ExternalModApiRegistry.runGymIntegrationCommands(
            server = server,
            commands = tier.completionCommands,
            variables = mapOf(
                "player_name" to player.gameProfile.name,
                "tier_level" to tier.tierLevel.toString(),
                "tier_name" to tier.displayName,
                "gym_level" to gymLevel.toString(),
                "badge_count" to ownedBadges.toString(),
                "required_badges" to requiredBadges.toString()
            )
        )

        val highestTier = CobblemonEventsMod.rankingManager.recordLeagueTierClear(
            playerUUID = player.uuid,
            playerName = player.name.string,
            tierLevel = tier.tierLevel
        )

        val hallCenter = resolveHallOfFameCenter(server, player, tier.tierLevel)
        buildHallOfFameRoom(server.overworld, hallCenter)
        teleportPlayer(
            server = server,
            player = player,
            dimensionId = LEAGUE_DIMENSION_ID,
            x = hallCenter.x + 0.5,
            y = hallCenter.y + 1.0,
            z = hallCenter.z + 0.5,
            yaw = player.yaw.toDouble(),
            pitch = player.pitch.toDouble()
        )
        val titleApplied = applyHallOfFameTitle(server, player, tier.tierLevel)

        return LeagueActionResult(
            true,
            "${tier.displayName} 클리어 완료 (최고 티어: $highestTier, 트로피: ${tier.trophyItemId} x${tier.trophyCount}, 전당 칭호: ${if (titleApplied) "적용" else "미적용"}, 연동 명령: ${completionExecuted}개)"
        )
    }

    private fun resolveGymEventDefinition(): EventDefinition? {
        return CobblemonEventsMod.config.events.firstOrNull {
            it.eventType == "GYM_CHALLENGE" && it.gymConfig != null
        }
    }

    private fun findTier(eventDef: EventDefinition, tierLevel: Int): LeagueTierEntry? {
        return eventDef.gymConfig?.leagueTiers?.firstOrNull { it.tierLevel == tierLevel }
    }

    private fun resolveLeagueCenter(server: MinecraftServer, player: ServerPlayerEntity, tierLevel: Int): BlockPos {
        val world = server.overworld
        val spawn = world.spawnPos
        val tierIndex = (tierLevel / 100).coerceAtLeast(1)
        val hash = player.uuid.hashCode()
        val x = spawn.x + (tierIndex * 220) + ((hash and 31) - 15)
        val z = spawn.z + (tierIndex * 220) + (((hash ushr 5) and 31) - 15)
        val y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z).coerceAtLeast(spawn.y + 10)
        return BlockPos(x, y, z)
    }

    private fun resolveHallOfFameCenter(server: MinecraftServer, player: ServerPlayerEntity, tierLevel: Int): BlockPos {
        val leagueCenter = resolveLeagueCenter(server, player, tierLevel)
        return BlockPos(leagueCenter.x, leagueCenter.y, leagueCenter.z + 56)
    }

    private fun spawnLeagueGateNpc(
        server: MinecraftServer,
        center: BlockPos,
        requiredBadges: Int,
        ownedBadges: Int
    ) {
        val color = if (ownedBadges >= requiredBadges) "green" else "red"
        val nameJson = "{\"text\":\"리그 심사관\",\"color\":\"$color\"}"
        executeServerCommand(
            server,
            "execute in $LEAGUE_DIMENSION_ID run kill @e[type=minecraft:armor_stand,tag=ce_league_gate_npc,x=${center.x},y=${center.y},z=${center.z},distance=..18]"
        )
        executeServerCommand(
            server,
            "execute in $LEAGUE_DIMENSION_ID run summon minecraft:armor_stand ${center.x + 0.5} ${center.y + 1.0} ${center.z + 8.5} " +
                "{NoGravity:1b,Invulnerable:1b,CustomNameVisible:1b,CustomName:'$nameJson',Tags:[\"ce_league_gate_npc\"],ArmorItems:[{},{},{},{id:\"minecraft:golden_helmet\",count:1}],HandItems:[{id:\"minecraft:book\",count:1},{}]}"
        )
        executeServerCommand(
            server,
            "execute in $LEAGUE_DIMENSION_ID run title @a[x=${center.x},y=${center.y},z=${center.z},distance=..24] actionbar " +
                "{\"text\":\"리그 입장 조건: 배지 ${ownedBadges}/${requiredBadges}\",\"color\":\"gold\"}"
        )
    }

    private fun buildLeagueArena(world: ServerWorld, center: BlockPos, tierLevel: Int) {
        val radius = 18
        val floor = when {
            tierLevel >= 300 -> Blocks.OBSIDIAN.defaultState
            tierLevel >= 200 -> Blocks.POLISHED_BLACKSTONE.defaultState
            else -> Blocks.SMOOTH_QUARTZ.defaultState
        }
        val ring = when {
            tierLevel >= 300 -> Blocks.END_STONE_BRICKS.defaultState
            tierLevel >= 200 -> Blocks.AMETHYST_BLOCK.defaultState
            else -> Blocks.LIGHT_BLUE_CONCRETE.defaultState
        }

        for (x in center.x - radius..center.x + radius) {
            for (z in center.z - radius..center.z + radius) {
                val dx = x - center.x
                val dz = z - center.z
                val dist = dx * dx + dz * dz
                for (y in center.y + 1..center.y + 12) {
                    world.setBlockState(BlockPos(x, y, z), Blocks.AIR.defaultState, Block.NOTIFY_ALL)
                }

                val state = when {
                    dist <= 11 * 11 -> floor
                    dist <= 15 * 15 -> ring
                    dist <= 18 * 18 -> Blocks.DEEPSLATE_TILES.defaultState
                    else -> null
                }
                if (state != null) {
                    world.setBlockState(BlockPos(x, center.y, z), state, Block.NOTIFY_ALL)
                }
            }
        }

        repeat(8) { i ->
            val angle = (Math.PI * 2.0) * (i / 8.0)
            val px = center.x + (kotlin.math.cos(angle) * 15.0).toInt()
            val pz = center.z + (kotlin.math.sin(angle) * 15.0).toInt()
            for (h in 1..7) {
                world.setBlockState(BlockPos(px, center.y + h, pz), Blocks.QUARTZ_PILLAR.defaultState, Block.NOTIFY_ALL)
            }
            world.setBlockState(BlockPos(px, center.y + 8, pz), Blocks.SEA_LANTERN.defaultState, Block.NOTIFY_ALL)
        }
    }

    private fun buildHallOfFameRoom(world: ServerWorld, center: BlockPos) {
        val half = 7
        for (x in center.x - half..center.x + half) {
            for (z in center.z - half..center.z + half) {
                for (y in center.y..center.y + 8) {
                    val isWall = x == center.x - half || x == center.x + half || z == center.z - half || z == center.z + half
                    val state = when {
                        y == center.y -> Blocks.POLISHED_BLACKSTONE_BRICKS.defaultState
                        y == center.y + 8 -> Blocks.BLACKSTONE.defaultState
                        isWall -> Blocks.TINTED_GLASS.defaultState
                        else -> Blocks.AIR.defaultState
                    }
                    world.setBlockState(BlockPos(x, y, z), state, Block.NOTIFY_ALL)
                }
            }
        }

        world.setBlockState(center, Blocks.GOLD_BLOCK.defaultState, Block.NOTIFY_ALL)
        world.setBlockState(center.up(), Blocks.BEACON.defaultState, Block.NOTIFY_ALL)
        world.setBlockState(center.up(2), Blocks.END_ROD.defaultState, Block.NOTIFY_ALL)
        world.setBlockState(center.north(2), Blocks.CHISELED_QUARTZ_BLOCK.defaultState, Block.NOTIFY_ALL)
        world.setBlockState(center.south(2), Blocks.CHISELED_QUARTZ_BLOCK.defaultState, Block.NOTIFY_ALL)
        world.setBlockState(center.east(2), Blocks.CHISELED_QUARTZ_BLOCK.defaultState, Block.NOTIFY_ALL)
        world.setBlockState(center.west(2), Blocks.CHISELED_QUARTZ_BLOCK.defaultState, Block.NOTIFY_ALL)
    }

    private fun applyHallOfFameTitle(server: MinecraftServer, player: ServerPlayerEntity, tierLevel: Int): Boolean {
        val playerName = player.gameProfile.name
        if (!SAFE_PLAYER_NAME.matches(playerName)) return false

        val teamName = "cehof${player.uuid.toString().replace("-", "").take(10)}"
        val escapedPrefix = "§6[전당 Lv.$tierLevel] §r"
        executeServerCommand(server, "team add $teamName")
        executeServerCommand(server, "team leave $playerName")
        val prefixOk = executeServerCommand(server, "team modify $teamName prefix \"$escapedPrefix\"")
        val colorOk = executeServerCommand(server, "team modify $teamName color gold")
        val joinOk = executeServerCommand(server, "team join $teamName $playerName")
        return prefixOk && colorOk && joinOk
    }

    private fun teleportPlayer(
        server: MinecraftServer,
        player: ServerPlayerEntity,
        dimensionId: String,
        x: Double,
        y: Double,
        z: Double,
        yaw: Double,
        pitch: Double
    ): Boolean {
        val playerName = player.gameProfile.name
        if (!SAFE_PLAYER_NAME.matches(playerName)) return false
        return executeServerCommand(
            server,
            "execute in $dimensionId run tp $playerName ${fmt(x)} ${fmt(y)} ${fmt(z)} ${fmt(yaw)} ${fmt(pitch)}"
        )
    }

    private fun executeServerCommand(server: MinecraftServer, rawCommand: String): Boolean {
        val command = rawCommand.trim().removePrefix("/")
        return try {
            server.commandManager.executeWithPrefix(server.commandSource, command)
            true
        } catch (e: Exception) {
            CobblemonEventsMod.LOGGER.warn("[LeagueChallenge] command failed: $command", e)
            false
        }
    }

    private fun fmt(value: Double): String = String.format(Locale.US, "%.3f", value)
}

