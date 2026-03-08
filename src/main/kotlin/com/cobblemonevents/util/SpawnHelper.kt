package com.cobblemonevents.util

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemonevents.CobblemonEventsMod
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.Heightmap
import kotlin.random.Random

object SpawnHelper {

    /**
     * 랜덤 플레이어 근처 안전한 좌표 찾기
     */
    fun findSafeLocationNearPlayer(player: ServerPlayerEntity, radius: Int): BlockPos? {
        val world = player.serverWorld
        val playerPos = player.blockPos

        for (attempt in 0..20) {
            val offsetX = Random.nextInt(-radius, radius + 1)
            val offsetZ = Random.nextInt(-radius, radius + 1)
            val x = playerPos.x + offsetX
            val z = playerPos.z + offsetZ

            val pos = BlockPos(x, 0, z)
            val topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z)
            val safePos = BlockPos(x, topY, z)

            // 물 위나 용암 위가 아닌지 확인
            val belowBlock = world.getBlockState(safePos.down())
            if (!belowBlock.isAir && !belowBlock.isLiquid) {
                return safePos
            }
        }
        return null
    }

    /**
     * 서버 내 랜덤 온라인 플레이어 근처에 좌표 생성
     */
    fun findRandomEventLocation(server: MinecraftServer, radius: Int): Pair<ServerPlayerEntity, BlockPos>? {
        val players = server.playerManager.playerList
        if (players.isEmpty()) return null

        val randomPlayer = players.random()
        val pos = findSafeLocationNearPlayer(randomPlayer, radius)
        return if (pos != null) Pair(randomPlayer, pos) else null
    }

    /**
     * 지정 좌표에 포켓몬 스폰
     */
    fun spawnPokemon(
        world: ServerWorld,
        species: String,
        pos: BlockPos,
        level: Int,
        shiny: Boolean = false,
        properties: String = ""
    ): PokemonEntity? {
        return try {
            val propString = buildString {
                append("$species level=$level")
                if (shiny) append(" shiny=yes")
                if (properties.isNotEmpty()) append(" $properties")
            }

            val pokemonProps = PokemonProperties.parse(propString)
            val pokemon = pokemonProps.create()
            val entity = pokemon.sendOut(
                world,
                pos.toCenterPos(),
                null
            )
            entity
        } catch (e: Exception) {
            CobblemonEventsMod.LOGGER.error("[코블몬 이벤트] 포켓몬 스폰 실패: $species", e)
            null
        }
    }

    /**
     * 포켓몬 다수 스폰 (균열/워프홀용)
     */
    fun spawnMultiplePokemon(
        world: ServerWorld,
        speciesList: List<String>,
        centerPos: BlockPos,
        radius: Int,
        count: Int,
        levelMin: Int,
        levelMax: Int,
        shinyChance: Double = 0.0
    ): List<PokemonEntity> {
        val spawned = mutableListOf<PokemonEntity>()

        for (i in 0 until count) {
            val species = speciesList.random()
            val level = Random.nextInt(levelMin, levelMax + 1)
            val isShiny = Random.nextDouble() < shinyChance

            // 스폰 위치를 중심으로 분산
            val offsetX = Random.nextInt(-radius, radius + 1)
            val offsetZ = Random.nextInt(-radius, radius + 1)
            val x = centerPos.x + offsetX
            val z = centerPos.z + offsetZ
            val topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z)
            val spawnPos = BlockPos(x, topY, z)

            val entity = spawnPokemon(world, species, spawnPos, level, isShiny)
            if (entity != null) {
                spawned.add(entity)
            }
        }

        CobblemonEventsMod.LOGGER.info(
            "[코블몬 이벤트] ${spawned.size}/${count} 포켓몬 스폰 완료 " +
            "(중심: ${centerPos.x}, ${centerPos.z})"
        )
        return spawned
    }

    /**
     * 파티클 좌표 생성 (원형 배치용)
     */
    fun getCirclePositions(center: BlockPos, radius: Int, count: Int): List<BlockPos> {
        val positions = mutableListOf<BlockPos>()
        val angleStep = 2 * Math.PI / count
        for (i in 0 until count) {
            val angle = angleStep * i
            val x = center.x + (radius * Math.cos(angle)).toInt()
            val z = center.z + (radius * Math.sin(angle)).toInt()
            positions.add(BlockPos(x, center.y, z))
        }
        return positions
    }
}
