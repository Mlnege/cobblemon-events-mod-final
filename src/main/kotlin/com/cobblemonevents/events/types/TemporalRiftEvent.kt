package com.cobblemonevents.events.types

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemonevents.CobblemonEventsMod
import com.cobblemonevents.config.RiftTypeEntry
import com.cobblemonevents.events.ActiveEvent
import com.cobblemonevents.events.EventHandler
import com.cobblemonevents.integration.DatapackStructureService
import com.cobblemonevents.rewards.RewardManager
import com.cobblemonevents.util.BroadcastUtil
import com.cobblemonevents.util.SpawnHelper
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.Heightmap
import java.util.UUID
import java.util.Locale
import kotlin.random.Random

class TemporalRiftEvent : EventHandler {
    private data class RiftArenaTheme(
        val id: String,
        val floor: BlockState,
        val ring: BlockState,
        val outer: BlockState,
        val dome: BlockState,
        val pillar: BlockState,
        val core: BlockState
    )

    private data class RiftReturnSnapshot(
        val dimensionId: String,
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float,
        val pitch: Float
    )

    private val riftThemes: Map<String, RiftArenaTheme> by lazy {
        mapOf(
            "normal" to RiftArenaTheme("normal", Blocks.SMOOTH_STONE.defaultState, Blocks.CALCITE.defaultState, Blocks.DIORITE.defaultState, Blocks.WHITE_STAINED_GLASS.defaultState, Blocks.QUARTZ_PILLAR.defaultState, Blocks.GLOWSTONE.defaultState),
            "fire" to RiftArenaTheme("fire", Blocks.BLACKSTONE.defaultState, Blocks.MAGMA_BLOCK.defaultState, Blocks.NETHER_BRICKS.defaultState, Blocks.RED_STAINED_GLASS.defaultState, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultState, Blocks.SHROOMLIGHT.defaultState),
            "water" to RiftArenaTheme("water", Blocks.PRISMARINE.defaultState, Blocks.DARK_PRISMARINE.defaultState, Blocks.PRISMARINE_BRICKS.defaultState, Blocks.LIGHT_BLUE_STAINED_GLASS.defaultState, Blocks.WARPED_PLANKS.defaultState, Blocks.SEA_LANTERN.defaultState),
            "electric" to RiftArenaTheme("electric", Blocks.YELLOW_CONCRETE.defaultState, Blocks.YELLOW_GLAZED_TERRACOTTA.defaultState, Blocks.BLACK_CONCRETE.defaultState, Blocks.YELLOW_STAINED_GLASS.defaultState, Blocks.LIGHTNING_ROD.defaultState, Blocks.REDSTONE_LAMP.defaultState),
            "grass" to RiftArenaTheme("grass", Blocks.MOSS_BLOCK.defaultState, Blocks.MOSSY_COBBLESTONE.defaultState, Blocks.ROOTED_DIRT.defaultState, Blocks.LIME_STAINED_GLASS.defaultState, Blocks.OAK_LOG.defaultState, Blocks.VERDANT_FROGLIGHT.defaultState),
            "ice" to RiftArenaTheme("ice", Blocks.PACKED_ICE.defaultState, Blocks.BLUE_ICE.defaultState, Blocks.SNOW_BLOCK.defaultState, Blocks.CYAN_STAINED_GLASS.defaultState, Blocks.POLISHED_DIORITE.defaultState, Blocks.PEARLESCENT_FROGLIGHT.defaultState),
            "fighting" to RiftArenaTheme("fighting", Blocks.POLISHED_ANDESITE.defaultState, Blocks.TUFF_BRICKS.defaultState, Blocks.STONE_BRICKS.defaultState, Blocks.GRAY_STAINED_GLASS.defaultState, Blocks.CHISELED_STONE_BRICKS.defaultState, Blocks.LANTERN.defaultState),
            "poison" to RiftArenaTheme("poison", Blocks.PURPLE_CONCRETE.defaultState, Blocks.AMETHYST_BLOCK.defaultState, Blocks.SLIME_BLOCK.defaultState, Blocks.PURPLE_STAINED_GLASS.defaultState, Blocks.PURPUR_PILLAR.defaultState, Blocks.OCHRE_FROGLIGHT.defaultState),
            "ground" to RiftArenaTheme("ground", Blocks.BROWN_TERRACOTTA.defaultState, Blocks.PACKED_MUD.defaultState, Blocks.MUD_BRICKS.defaultState, Blocks.ORANGE_STAINED_GLASS.defaultState, Blocks.DRIPSTONE_BLOCK.defaultState, Blocks.SHROOMLIGHT.defaultState),
            "flying" to RiftArenaTheme("flying", Blocks.QUARTZ_BLOCK.defaultState, Blocks.SMOOTH_QUARTZ.defaultState, Blocks.WHITE_CONCRETE.defaultState, Blocks.WHITE_STAINED_GLASS.defaultState, Blocks.CHISELED_QUARTZ_BLOCK.defaultState, Blocks.END_ROD.defaultState),
            "psychic" to RiftArenaTheme("psychic", Blocks.PURPUR_BLOCK.defaultState, Blocks.AMETHYST_BLOCK.defaultState, Blocks.PINK_CONCRETE.defaultState, Blocks.PINK_STAINED_GLASS.defaultState, Blocks.PURPUR_PILLAR.defaultState, Blocks.END_ROD.defaultState),
            "bug" to RiftArenaTheme("bug", Blocks.LIME_TERRACOTTA.defaultState, Blocks.MOSS_BLOCK.defaultState, Blocks.GREEN_CONCRETE.defaultState, Blocks.GREEN_STAINED_GLASS.defaultState, Blocks.BAMBOO_BLOCK.defaultState, Blocks.VERDANT_FROGLIGHT.defaultState),
            "rock" to RiftArenaTheme("rock", Blocks.STONE.defaultState, Blocks.COBBLESTONE.defaultState, Blocks.DEEPSLATE.defaultState, Blocks.GRAY_STAINED_GLASS.defaultState, Blocks.POLISHED_ANDESITE.defaultState, Blocks.LANTERN.defaultState),
            "ghost" to RiftArenaTheme("ghost", Blocks.SOUL_SOIL.defaultState, Blocks.POLISHED_BLACKSTONE.defaultState, Blocks.CRYING_OBSIDIAN.defaultState, Blocks.PURPLE_STAINED_GLASS.defaultState, Blocks.DEEPSLATE_BRICKS.defaultState, Blocks.SOUL_LANTERN.defaultState),
            "dragon" to RiftArenaTheme("dragon", Blocks.OBSIDIAN.defaultState, Blocks.PURPUR_BLOCK.defaultState, Blocks.END_STONE_BRICKS.defaultState, Blocks.MAGENTA_STAINED_GLASS.defaultState, Blocks.PURPUR_PILLAR.defaultState, Blocks.END_ROD.defaultState),
            "dark" to RiftArenaTheme("dark", Blocks.POLISHED_BLACKSTONE.defaultState, Blocks.DEEPSLATE_BRICKS.defaultState, Blocks.BLACKSTONE.defaultState, Blocks.BLACK_STAINED_GLASS.defaultState, Blocks.CHISELED_DEEPSLATE.defaultState, Blocks.SOUL_LANTERN.defaultState),
            "steel" to RiftArenaTheme("steel", Blocks.IRON_BLOCK.defaultState, Blocks.POLISHED_TUFF.defaultState, Blocks.HEAVY_CORE.defaultState, Blocks.LIGHT_GRAY_STAINED_GLASS.defaultState, Blocks.CHAIN.defaultState, Blocks.REDSTONE_LAMP.defaultState),
            "fairy" to RiftArenaTheme("fairy", Blocks.PINK_CONCRETE.defaultState, Blocks.CHERRY_PLANKS.defaultState, Blocks.PEARLESCENT_FROGLIGHT.defaultState, Blocks.PINK_STAINED_GLASS.defaultState, Blocks.QUARTZ_PILLAR.defaultState, Blocks.SEA_LANTERN.defaultState),
            "legendary" to RiftArenaTheme("legendary", Blocks.OBSIDIAN.defaultState, Blocks.GILDED_BLACKSTONE.defaultState, Blocks.END_STONE.defaultState, Blocks.MAGENTA_STAINED_GLASS.defaultState, Blocks.CRYING_OBSIDIAN.defaultState, Blocks.BEACON.defaultState)
        )
    }

    companion object {
        private const val DATA_SELECTED_RIFT = "selectedRift"
        private const val DATA_SPAWNED_ENTITY_UUIDS = "riftSpawnedEntityUuids"
        private const val DATA_RIFT_REALM_CENTER = "riftRealmCenter"
        private const val DATA_RIFT_REALM_ENTRY = "riftRealmEntry"
        private const val DATA_RIFT_SPECIES_POOL = "riftSpeciesPool"
        private const val DATA_RIFT_THEME_ID = "riftThemeId"
        private const val DATA_RIFT_LAST_OUTSIDE_POINTS = "riftLastOutsidePoints"
        private const val DATA_RIFT_RETURN_POINTS = "riftReturnPoints"
        private const val DATA_RIFT_SYNTHETIC_PORTAL_ACTIVE = "riftSyntheticPortalActive"
        private const val DATA_RIFT_SYNTHETIC_PORTAL_EXIT = "riftSyntheticPortalExit"
        private const val DATA_RIFT_SYNTHETIC_PORTAL_COOLDOWN = "riftSyntheticPortalCooldown"
        private const val MISSION_CLEAR_CATCH_COUNT = 2
        private const val OVERWORLD_DIMENSION_ID = "minecraft:overworld"
        private const val RIFT_REALM_DIMENSION_ID = "minecraft:the_end"
        private const val RIFT_ARENA_Y = 96
        private const val RIFT_ARENA_RADIUS = 22
        private const val RIFT_DOME_HEIGHT = 14
        private const val RIFT_CAPTURE_RADIUS_SQ = 60.0 * 60.0
        private const val RIFT_SYNTHETIC_TRIGGER_RADIUS_SQ = 2.8 * 2.8
        private const val RIFT_LOCK_RADIUS = RIFT_ARENA_RADIUS - 2
        private const val RIFT_LOCK_RADIUS_SQ = RIFT_LOCK_RADIUS.toDouble() * RIFT_LOCK_RADIUS.toDouble()
        private const val RIFT_SYNTHETIC_COOLDOWN_TICKS = 40L
        private const val RIFT_SHINY_CHANCE = 0.05
        private const val RIFT_DITTO_CHANCE = 0.05
        private const val ADDITIONAL_SPAWN_INTERVAL_TICKS = 20L * 120L
        private const val RIFT_CLEANUP_RADIUS = RIFT_ARENA_RADIUS + 24
        private const val RIFT_CLEANUP_HEIGHT = RIFT_DOME_HEIGHT + 14
        private const val TEMPLATE_NAMESPACE = "cobblemon-events"
        private const val TEMPLATE_PREFIX = "rift"
        private val SAFE_PLAYER_NAME = Regex("^[A-Za-z0-9_]{1,16}$")
        private val BANNED_RIFT_SPAWN_SURFACES = setOf(
            Blocks.GLASS,
            Blocks.TINTED_GLASS,
            Blocks.WHITE_STAINED_GLASS,
            Blocks.ORANGE_STAINED_GLASS,
            Blocks.MAGENTA_STAINED_GLASS,
            Blocks.LIGHT_BLUE_STAINED_GLASS,
            Blocks.YELLOW_STAINED_GLASS,
            Blocks.LIME_STAINED_GLASS,
            Blocks.PINK_STAINED_GLASS,
            Blocks.GRAY_STAINED_GLASS,
            Blocks.LIGHT_GRAY_STAINED_GLASS,
            Blocks.CYAN_STAINED_GLASS,
            Blocks.PURPLE_STAINED_GLASS,
            Blocks.BLUE_STAINED_GLASS,
            Blocks.BROWN_STAINED_GLASS,
            Blocks.GREEN_STAINED_GLASS,
            Blocks.RED_STAINED_GLASS,
            Blocks.BLACK_STAINED_GLASS
        )

        // 요청사항: 전 타입 + 하위 진화체/다양성 강화
        private val DEFAULT_RIFT_TYPES = listOf(
            RiftTypeEntry("normal", "§f일반 타입 균열", listOf("normal"), listOf(
                "eevee", "porygon", "snorlax", "munchlax", "buneary", "lopunny", "skwovet", "greedent", "teddiursa", "ursaluna", "zigzagoon", "linoone"
            )),
            RiftTypeEntry("fire", "§c불 타입 균열", listOf("fire"), listOf(
                "charmander", "charmeleon", "charizard", "vulpix", "ninetales", "growlithe", "arcanine", "torchic", "combusken", "blaziken", "litten", "torracat", "incineroar"
            )),
            RiftTypeEntry("water", "§9물 타입 균열", listOf("water"), listOf(
                "squirtle", "wartortle", "blastoise", "magikarp", "gyarados", "mudkip", "marshtomp", "swampert", "popplio", "brionne", "primarina", "finizen", "palafin"
            )),
            RiftTypeEntry("electric", "§e전기 타입 균열", listOf("electric"), listOf(
                "pichu", "pikachu", "raichu", "mareep", "flaaffy", "ampharos", "shinx", "luxio", "luxray", "magnemite", "magneton", "magnezone", "toxel", "toxtricity"
            )),
            RiftTypeEntry("grass", "§a풀 타입 균열", listOf("grass"), listOf(
                "bulbasaur", "ivysaur", "venusaur", "oddish", "gloom", "vileplume", "bellsprout", "weepinbell", "victreebel", "treecko", "grovyle", "sceptile", "sprigatito", "floragato", "meowscarada"
            )),
            RiftTypeEntry("ice", "§b얼음 타입 균열", listOf("ice"), listOf(
                "snorunt", "glalie", "froslass", "swinub", "piloswine", "mamoswine", "spheal", "sealeo", "walrein", "snom", "frosmoth", "frigibax", "arctibax", "baxcalibur"
            )),
            RiftTypeEntry("fighting", "§6격투 타입 균열", listOf("fighting"), listOf(
                "riolu", "lucario", "machop", "machoke", "machamp", "mankey", "primeape", "annihilape", "tyrogue", "hitmonlee", "hitmonchan", "meditite", "medicham"
            )),
            RiftTypeEntry("poison", "§5독 타입 균열", listOf("poison"), listOf(
                "zubat", "golbat", "crobat", "grimer", "muk", "gastly", "haunter", "gengar", "venipede", "whirlipede", "scolipede"
            )),
            RiftTypeEntry("ground", "§6땅 타입 균열", listOf("ground"), listOf(
                "sandshrew", "sandslash", "swinub", "piloswine", "mamoswine", "phanpy", "donphan", "trapinch", "vibrava", "flygon", "gible", "gabite", "garchomp"
            )),
            RiftTypeEntry("flying", "§7비행 타입 균열", listOf("flying"), listOf(
                "pidgey", "pidgeotto", "pidgeot", "starly", "staravia", "staraptor", "rufflet", "braviary", "fletchling", "fletchinder", "talonflame", "rookidee", "corvisquire", "corviknight"
            )),
            RiftTypeEntry("psychic", "§d에스퍼 타입 균열", listOf("psychic"), listOf(
                "abra", "kadabra", "alakazam", "ralts", "kirlia", "gardevoir", "gallade", "drowzee", "hypno", "espurr", "meowstic", "indeedee"
            )),
            RiftTypeEntry("bug", "§2벌레 타입 균열", listOf("bug"), listOf(
                "caterpie", "metapod", "butterfree", "weedle", "kakuna", "beedrill", "scyther", "scizor", "larvesta", "volcarona", "grubbin", "charjabug", "vikavolt"
            )),
            RiftTypeEntry("rock", "§8바위 타입 균열", listOf("rock"), listOf(
                "geodude", "graveler", "golem", "onix", "rhyhorn", "rhydon", "rhyperior", "rockruff", "lycanroc", "anorith", "armaldo", "cranidos", "rampardos"
            )),
            RiftTypeEntry("ghost", "§8고스트 타입 균열", listOf("ghost"), listOf(
                "gastly", "haunter", "gengar", "misdreavus", "mismagius", "litwick", "lampent", "chandelure", "dreepy", "drakloak", "dragapult", "greavard", "houndstone"
            )),
            RiftTypeEntry("dragon", "§5드래곤 타입 균열", listOf("dragon"), listOf(
                "dratini", "dragonair", "dragonite", "bagon", "shelgon", "salamence", "gible", "gabite", "garchomp", "deino", "zweilous", "hydreigon", "goomy", "sliggoo", "goodra", "jangmo-o", "hakamo-o", "kommo-o", "frigibax", "arctibax", "baxcalibur"
            )),
            RiftTypeEntry("dark", "§1악 타입 균열", listOf("dark"), listOf(
                "murkrow", "honchkrow", "houndour", "houndoom", "pawniard", "bisharp", "kingambit", "deino", "zweilous", "hydreigon", "sneasel", "weavile", "maschiff", "mabosstiff"
            )),
            RiftTypeEntry("steel", "§7강철 타입 균열", listOf("steel"), listOf(
                "magnemite", "magneton", "magnezone", "aron", "lairon", "aggron", "beldum", "metang", "metagross", "riolu", "lucario", "honedge", "doublade", "aegislash"
            )),
            RiftTypeEntry("fairy", "§d페어리 타입 균열", listOf("fairy"), listOf(
                "cleffa", "clefairy", "clefable", "igglybuff", "jigglypuff", "wigglytuff", "ralts", "kirlia", "gardevoir", "sylveon", "flabebe", "floette", "florges", "tinkatink", "tinkatuff", "tinkaton"
            ))
        )
    }

    override fun onStart(event: ActiveEvent, server: MinecraftServer) {
        val riftConfig = event.definition.riftConfig ?: return
        val riftTypes = resolveRiftTypes(riftConfig.riftTypes)
        if (riftTypes.isEmpty()) {
            CobblemonEventsMod.LOGGER.warn("[TemporalRift] 타입 정의가 비어 있어 시작을 취소합니다.")
            return
        }
        val selectedRift = riftTypes.random()
        val selectedTheme = resolveThemeForRift(selectedRift)
        event.setData(DATA_SELECTED_RIFT, selectedRift)
        event.setData(DATA_RIFT_THEME_ID, selectedTheme.id)

        val speciesPool = buildUnifiedSpeciesPool(riftTypes)
        if (speciesPool.isEmpty()) {
            CobblemonEventsMod.LOGGER.warn("[TemporalRift] 포켓몬 풀이 비어 있어 시작을 취소합니다.")
            return
        }
        event.setData(DATA_RIFT_SPECIES_POOL, speciesPool)

        val location = SpawnHelper.findRandomEventLocation(server, riftConfig.riftSearchRadius)
        if (location == null) {
            CobblemonEventsMod.LOGGER.warn("[TemporalRift] 이벤트 위치를 찾지 못해 시작을 취소합니다.")
            return
        }

        val (_, pos) = location
        event.eventLocation = pos
        event.setData(DATA_RIFT_LAST_OUTSIDE_POINTS, mutableMapOf<String, RiftReturnSnapshot>())
        event.setData(DATA_RIFT_RETURN_POINTS, mutableMapOf<String, RiftReturnSnapshot>())

        val riftWorld = findWorldById(server, RIFT_REALM_DIMENSION_ID)

        val (spawnWorld, spawnCenter) = if (riftWorld != null) {
            val realmCenter = createRealmCenter()
            event.setData(DATA_RIFT_REALM_CENTER, realmCenter)
            event.setData(DATA_RIFT_SYNTHETIC_PORTAL_COOLDOWN, mutableMapOf<String, Int>())
            val datapackPlaced = DatapackStructureService.placeTypeDome(
                server = server,
                dimensionId = RIFT_REALM_DIMENSION_ID,
                center = realmCenter,
                typeId = selectedRift.id
            )
            val imported = if (!datapackPlaced) tryImportArenaTemplate(server, realmCenter, selectedTheme.id) else false
            if (!datapackPlaced && !imported) {
                buildRiftArena(riftWorld, realmCenter, selectedTheme)
            }
            if (!isRiftArenaReady(riftWorld, realmCenter)) {
                buildRiftArena(riftWorld, realmCenter, selectedTheme)
            }

            val realmEntry = resolveRiftEntryPos(riftWorld, realmCenter)
            val syntheticExit = BlockPos(realmCenter.x, realmEntry.y, realmCenter.z + RIFT_ARENA_RADIUS - 4)
            event.setData(DATA_RIFT_REALM_ENTRY, realmEntry)
            event.setData(DATA_RIFT_SYNTHETIC_PORTAL_EXIT, syntheticExit)

            event.setData(DATA_RIFT_SYNTHETIC_PORTAL_ACTIVE, true)
            BroadcastUtil.broadcast(
                server,
                "${CobblemonEventsMod.config.prefix}§d${selectedRift.displayName} §f차원의 틈이 열렸습니다. / The dimensional rift has opened."
            )
            Pair(riftWorld, realmEntry)
        } else {
            event.setData(DATA_RIFT_SYNTHETIC_PORTAL_ACTIVE, false)
            event.setData(DATA_RIFT_SYNTHETIC_PORTAL_COOLDOWN, mutableMapOf<String, Int>())
            BroadcastUtil.broadcast(
                server,
                "${CobblemonEventsMod.config.prefix}§7Rift realm missing; running in overworld mode."
            )
            Pair(server.overworld, pos)
        }

        val spawned = spawnRiftWave(
            event = event,
            world = spawnWorld,
            centerPos = spawnCenter,
            speciesPool = speciesPool,
            count = riftConfig.spawnCount,
            levelMin = riftConfig.pokemonLevelMin,
            levelMax = riftConfig.pokemonLevelMax
        )

        BroadcastUtil.announceRift(
            server,
            selectedRift.displayName,
            pos.x,
            pos.y,
            pos.z,
            event.definition.durationMinutes
        )

        CobblemonEventsMod.LOGGER.info(
            "[TemporalRift] rift created - theme:${selectedTheme.id}, entrance:${pos.x},${pos.y},${pos.z}, spawned:$spawned, synthetic:true"
        )
    }

    override fun onTick(event: ActiveEvent, server: MinecraftServer) {
        val entrancePos = event.eventLocation ?: return
        if (isRealmModeEnabled(event, server)) {
            if (event.ticksRemaining % 20L == 0L) {
                updatePortalReturnSnapshots(event, server)
            }
            handleSyntheticPortalTransitions(event, server)
            enforceRiftBoundary(event, server)
        }

        if (event.ticksRemaining % 60 == 0L) {
            spawnRiftParticles(server.overworld, entrancePos)
            val realmCenter = event.getData<BlockPos>(DATA_RIFT_REALM_CENTER)
            val realmWorld = findWorldById(server, RIFT_REALM_DIMENSION_ID)
            if (realmCenter != null && realmWorld != null) {
                spawnRiftParticles(realmWorld, realmCenter)
            }
        }
        if (event.getData<Boolean>(DATA_RIFT_SYNTHETIC_PORTAL_ACTIVE) == true && event.ticksRemaining % 10L == 0L) {
            spawnSyntheticPortalParticles(server.overworld, entrancePos, scale = 2.6)
            val realmWorld = findWorldById(server, RIFT_REALM_DIMENSION_ID)
            val syntheticExit = event.getData<BlockPos>(DATA_RIFT_SYNTHETIC_PORTAL_EXIT)
            if (realmWorld != null && syntheticExit != null) {
                spawnSyntheticPortalParticles(realmWorld, syntheticExit, scale = 1.6)
            }
        }

        val riftConfig = event.definition.riftConfig ?: return
        val speciesPool = event.getData<List<String>>(DATA_RIFT_SPECIES_POOL).orEmpty()
        if (speciesPool.isEmpty()) return

        if (event.ticksRemaining > 0 && event.ticksRemaining % ADDITIONAL_SPAWN_INTERVAL_TICKS == 0L) {
            val usePortalRealm = isRealmModeEnabled(event, server)
            val realmEntry = event.getData<BlockPos>(DATA_RIFT_REALM_ENTRY)
            val realmWorld = findWorldById(server, RIFT_REALM_DIMENSION_ID)

            val (spawnWorld, spawnCenter) = if (usePortalRealm && realmEntry != null && realmWorld != null) {
                Pair(realmWorld, realmEntry)
            } else {
                Pair(server.overworld, entrancePos)
            }

            val additional = spawnRiftWave(
                event = event,
                world = spawnWorld,
                centerPos = spawnCenter,
                speciesPool = speciesPool,
                count = (riftConfig.spawnCount / 2).coerceAtLeast(1),
                levelMin = riftConfig.pokemonLevelMin,
                levelMax = riftConfig.pokemonLevelMax
            )

            BroadcastUtil.broadcast(
                server,
                "${CobblemonEventsMod.config.prefix}§d균열 내부에 추가 포켓몬이 출현했습니다! / More Pokémon appeared in the rift! §7(+${additional}마리, 남은 시간 / Time left: ${event.getRemainingMinutes()}분)"
            )
        }
    }

    override fun onEnd(event: ActiveEvent, server: MinecraftServer) {
        val selectedRift = event.getData<RiftTypeEntry>(DATA_SELECTED_RIFT)
        val riftName = selectedRift?.displayName ?: "시공의 균열"

        val returned = teleportPlayersOutOfRift(event, server)
        removeSyntheticPortalFrames(event, server)
        val arenaCleared = clearRiftArena(event, server)
        event.setData(DATA_RIFT_SYNTHETIC_PORTAL_ACTIVE, false)
        val despawned = despawnTrackedEntities(event, server)

        val riftConfig = event.definition.riftConfig ?: return
        for (playerUUID in event.participants.keys) {
            val player = server.playerManager.getPlayer(playerUUID) ?: continue
            val catchCount = event.getProgress(playerUUID)
            if (catchCount >= MISSION_CLEAR_CATCH_COUNT) {
                RewardManager.giveRewardsWithEvent(player, riftConfig.dropRewards, event)
                CobblemonEventsMod.rankingManager.recordEventComplete(playerUUID, player.name.string)
            }
        }

        BroadcastUtil.announceEventEnd(
            server,
            riftName,
            listOf(
                "Participants: ${event.participants.size}",
                "Total catches: ${event.participants.values.sum()}",
                "Returned players: $returned",
                "Rift arena cleared: $arenaCleared",
                "Rift despawned: $despawned"
            )
        )
    }

    override fun onPokemonCaught(event: ActiveEvent, player: ServerPlayerEntity, species: String) {
        val selectedRift = event.getData<RiftTypeEntry>(DATA_SELECTED_RIFT)
        val usePortalRealm = isRealmModeConfigured(event)
        val insideRealm = isInsideRiftRealm(event, player)
        if (usePortalRealm && !insideRealm) return
        if (!usePortalRealm) {
            val speciesPool = event.getData<List<String>>(DATA_RIFT_SPECIES_POOL).orEmpty()
            if (speciesPool.none { it.equals(species, ignoreCase = true) }) return
        }

        val count = event.addProgress(player.uuid)
        BroadcastUtil.sendProgress(
            player,
            "§d시공의 균열 포획 / Rift Catch: §f${count}/$MISSION_CLEAR_CATCH_COUNT §7(${selectedRift?.displayName ?: "전타입 균열"}§7)"
        )
        if (count >= MISSION_CLEAR_CATCH_COUNT && event.completedPlayers.add(player.uuid)) {
            BroadcastUtil.sendProgress(player, "§d시공의 균열 미션 완료! / Temporal Rift mission complete! §f($MISSION_CLEAR_CATCH_COUNT 마리 포획 / caught)")
        }

        val riftConfig = event.definition.riftConfig ?: return
        if (Random.nextDouble() < 0.3) {
            RewardManager.giveRewards(player, riftConfig.dropRewards, event.definition)
        }
    }

    override fun canBreakBlock(event: ActiveEvent, player: ServerPlayerEntity, world: ServerWorld, pos: BlockPos): Boolean {
        if (!isRealmModeConfigured(event)) return true
        if (world.registryKey.value.toString() != RIFT_REALM_DIMENSION_ID) return true

        val center = event.getData<BlockPos>(DATA_RIFT_REALM_CENTER) ?: return true
        val dx = (pos.x + 0.5) - (center.x + 0.5)
        val dz = (pos.z + 0.5) - (center.z + 0.5)
        val inRadius = (dx * dx + dz * dz) <= ((RIFT_ARENA_RADIUS + 6).toDouble() * (RIFT_ARENA_RADIUS + 6).toDouble())
        val inHeight = pos.y in (center.y - 2)..(center.y + RIFT_DOME_HEIGHT + 8)
        if (!inRadius || !inHeight) return true

        BroadcastUtil.sendPersonal(player, "${CobblemonEventsMod.config.prefix}§c시공의 균열에서는 블럭을 부술 수 없습니다. / Block breaking is not allowed inside the Temporal Rift.")
        return false
    }

    private fun buildUnifiedSpeciesPool(riftTypes: List<RiftTypeEntry>): List<String> {
        return riftTypes
            .flatMap { it.pokemonPool }
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun resolveThemeForRift(rift: RiftTypeEntry): RiftArenaTheme {
        val keys = mutableListOf<String>()
        keys += rift.id.trim().lowercase()
        keys += rift.types.map { it.trim().lowercase() }
        for (key in keys) {
            val theme = riftThemes[key]
            if (theme != null) return theme
        }
        return riftThemes.getValue("normal")
    }

    private fun spawnRiftWave(
        event: ActiveEvent,
        world: ServerWorld,
        centerPos: BlockPos,
        speciesPool: List<String>,
        count: Int,
        levelMin: Int,
        levelMax: Int
    ): Int {
        val safeMin = levelMin.coerceAtLeast(1)
        val safeMax = levelMax.coerceAtLeast(safeMin)
        val spawned = mutableListOf<PokemonEntity>()

        repeat(count.coerceAtLeast(0)) {
            val species = selectRiftSpecies(speciesPool)
            val spawnPos = randomSpawnPos(world, centerPos, RIFT_ARENA_RADIUS)
            val level = Random.nextInt(safeMin, safeMax + 1)
            val shiny = Random.nextDouble() < RIFT_SHINY_CHANCE
            val entity = SpawnHelper.spawnPokemon(world, species, spawnPos, level, shiny) ?: return@repeat
            spawned += entity
        }

        rememberSpawnedEntities(event, spawned)
        return spawned.size
    }

    private fun selectRiftSpecies(speciesPool: List<String>): String {
        return if (Random.nextDouble() < RIFT_DITTO_CHANCE) {
            "ditto"
        } else {
            speciesPool.random()
        }
    }

    private fun randomSpawnPos(world: ServerWorld, center: BlockPos, radius: Int): BlockPos {
        val safeRadius = (radius - 5).coerceAtLeast(4)
        repeat(24) {
            val angle = Random.nextDouble() * Math.PI * 2.0
            val distance = kotlin.math.sqrt(Random.nextDouble()) * safeRadius
            val x = center.x + kotlin.math.round(kotlin.math.cos(angle) * distance).toInt()
            val z = center.z + kotlin.math.round(kotlin.math.sin(angle) * distance).toInt()
            val y = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z).coerceAtLeast(center.y + 1)
            val spawnPos = BlockPos(x, y, z)
            val surfaceBlock = world.getBlockState(spawnPos.down()).block
            if (surfaceBlock in BANNED_RIFT_SPAWN_SURFACES) return@repeat
            if (!world.getBlockState(spawnPos).isAir) return@repeat
            if (!world.getBlockState(spawnPos.up()).isAir) return@repeat
            return spawnPos
        }
        return center.up()
    }

    private fun createRealmCenter(): BlockPos {
        val base = Random.nextInt(6000, 12001)
        val signedX = if (Random.nextBoolean()) base else -base
        val signedZ = if (Random.nextBoolean()) base else -base
        val alignedX = (signedX / 16) * 16
        val alignedZ = (signedZ / 16) * 16
        return BlockPos(alignedX, RIFT_ARENA_Y, alignedZ)
    }

    private fun tryImportArenaTemplate(server: MinecraftServer, center: BlockPos, themeId: String): Boolean {
        val candidates = listOf(
            "$TEMPLATE_PREFIX/$themeId",
            "$TEMPLATE_PREFIX/default"
        )
        for (templatePath in candidates) {
            if (!hasTemplateResource(server, templatePath)) continue
            val templateId = "$TEMPLATE_NAMESPACE:$templatePath"
            val placed = executeServerCommand(
                server,
                "execute in $RIFT_REALM_DIMENSION_ID positioned ${center.x} ${center.y} ${center.z} run place template $templateId"
            )
            if (placed) {
                CobblemonEventsMod.LOGGER.info("[TemporalRift] 구조물 템플릿 적용 성공: $templateId")
                return true
            }
        }
        return false
    }

    private fun hasTemplateResource(server: MinecraftServer, templatePath: String): Boolean {
        return try {
            val resourceId = Identifier.of(TEMPLATE_NAMESPACE, "structure/$templatePath.nbt")
            server.resourceManager.getResource(resourceId).isPresent
        } catch (_: Exception) {
            false
        }
    }

    private fun buildRiftArena(world: ServerWorld, center: BlockPos, theme: RiftArenaTheme) {
        val clearRadius = RIFT_ARENA_RADIUS + 10
        val floorY = center.y
        for (x in center.x - clearRadius..center.x + clearRadius) {
            for (z in center.z - clearRadius..center.z + clearRadius) {
                for (y in floorY + 1..floorY + RIFT_DOME_HEIGHT + 6) {
                    world.setBlockState(BlockPos(x, y, z), Blocks.AIR.defaultState, Block.NOTIFY_ALL)
                }

                val dx = x - center.x
                val dz = z - center.z
                val distanceSq = dx * dx + dz * dz
                val state = when {
                    distanceSq <= (RIFT_ARENA_RADIUS * RIFT_ARENA_RADIUS) -> theme.floor
                    distanceSq <= ((RIFT_ARENA_RADIUS + 2) * (RIFT_ARENA_RADIUS + 2)) -> theme.ring
                    distanceSq <= ((RIFT_ARENA_RADIUS + 4) * (RIFT_ARENA_RADIUS + 4)) -> theme.outer
                    else -> null
                }
                if (state != null) {
                    world.setBlockState(BlockPos(x, floorY, z), state, Block.NOTIFY_ALL)
                }
            }
        }

        buildRiftDome(world, center, theme)
        buildRiftPillars(world, center, theme)

        val core = center.up(1)
        world.setBlockState(core, theme.core, Block.NOTIFY_ALL)
        world.setBlockState(core.up(), Blocks.END_ROD.defaultState, Block.NOTIFY_ALL)
    }

    private fun isRiftArenaReady(world: ServerWorld, center: BlockPos): Boolean {
        val floor = world.getBlockState(center).block
        val core = world.getBlockState(center.up()).block
        if (floor != Blocks.AIR && core != Blocks.AIR) {
            return true
        }
        val topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, center.x, center.z)
        return topY >= center.y
    }

    private fun resolveRiftEntryPos(world: ServerWorld, center: BlockPos): BlockPos {
        val topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, center.x, center.z)
        val entryY = maxOf(center.y + 1, topY + 1)
        return BlockPos(center.x, entryY, center.z)
    }

    private fun buildRiftDome(world: ServerWorld, center: BlockPos, theme: RiftArenaTheme) {
        for (y in 1..RIFT_DOME_HEIGHT) {
            val radiusAtHeight = kotlin.math.sqrt(
                (RIFT_ARENA_RADIUS.toDouble() * RIFT_ARENA_RADIUS.toDouble()) - (y.toDouble() * y.toDouble())
            ).toInt().coerceAtLeast(3)
            val shellMin = (radiusAtHeight - 1) * (radiusAtHeight - 1)
            val shellMax = radiusAtHeight * radiusAtHeight
            val currentY = center.y + y
            for (x in center.x - radiusAtHeight..center.x + radiusAtHeight) {
                for (z in center.z - radiusAtHeight..center.z + radiusAtHeight) {
                    val dx = x - center.x
                    val dz = z - center.z
                    val distanceSq = dx * dx + dz * dz
                    if (distanceSq in shellMin..shellMax) {
                        world.setBlockState(BlockPos(x, currentY, z), theme.dome, Block.NOTIFY_ALL)
                    }
                }
            }
        }
    }

    private fun buildRiftPillars(world: ServerWorld, center: BlockPos, theme: RiftArenaTheme) {
        val points = listOf(
            BlockPos(center.x + 9, center.y + 1, center.z + 9),
            BlockPos(center.x - 9, center.y + 1, center.z + 9),
            BlockPos(center.x + 9, center.y + 1, center.z - 9),
            BlockPos(center.x - 9, center.y + 1, center.z - 9)
        )
        for (point in points) {
            for (dy in 0..8) {
                world.setBlockState(point.up(dy), theme.pillar, Block.NOTIFY_ALL)
            }
            world.setBlockState(point.up(9), theme.core, Block.NOTIFY_ALL)
        }
    }


    private fun clearSyntheticPortalFrame(world: ServerWorld, center: BlockPos) {
        val base = center.up(1)
        for (x in -4..4) {
            for (y in 0..9) {
                for (z in -2..2) {
                    world.setBlockState(base.add(x, y, z), Blocks.AIR.defaultState, Block.NOTIFY_ALL)
                }
            }
        }
    }

    private fun clearRiftEntranceStructure(world: ServerWorld, center: BlockPos) {
        val removable = mutableSetOf(
            Blocks.CRYING_OBSIDIAN,
            Blocks.SCULK,
            Blocks.SOUL_LANTERN,
            Blocks.END_ROD,
            Blocks.LIGHT_BLUE_STAINED_GLASS,
            Blocks.CYAN_STAINED_GLASS
        )
        for (theme in riftThemes.values) {
            removable.add(theme.floor.block)
            removable.add(theme.ring.block)
            removable.add(theme.outer.block)
            removable.add(theme.dome.block)
            removable.add(theme.pillar.block)
            removable.add(theme.core.block)
        }

        for (x in -8..8) {
            for (z in -8..8) {
                for (y in 0..11) {
                    val target = center.add(x, y, z)
                    val block = world.getBlockState(target).block
                    if (block in removable) {
                        world.setBlockState(target, Blocks.AIR.defaultState, Block.NOTIFY_ALL)
                    }
                }
            }
        }
    }

    private fun removeSyntheticPortalFrames(event: ActiveEvent, server: MinecraftServer) {
        val entrancePos = event.eventLocation
        if (entrancePos != null) {
            clearSyntheticPortalFrame(server.overworld, entrancePos)
            clearRiftEntranceStructure(server.overworld, entrancePos)
        }

        val realmWorld = findWorldById(server, RIFT_REALM_DIMENSION_ID)
        val syntheticExit = event.getData<BlockPos>(DATA_RIFT_SYNTHETIC_PORTAL_EXIT)
        if (realmWorld != null && syntheticExit != null) {
            clearSyntheticPortalFrame(realmWorld, syntheticExit)
        }
    }

    private fun clearRiftArena(event: ActiveEvent, server: MinecraftServer): Boolean {
        val realmWorld = findWorldById(server, RIFT_REALM_DIMENSION_ID) ?: return false
        val center = event.getData<BlockPos>(DATA_RIFT_REALM_CENTER) ?: return false
        for (x in center.x - RIFT_CLEANUP_RADIUS..center.x + RIFT_CLEANUP_RADIUS) {
            for (z in center.z - RIFT_CLEANUP_RADIUS..center.z + RIFT_CLEANUP_RADIUS) {
                for (y in center.y - 2..center.y + RIFT_CLEANUP_HEIGHT) {
                    realmWorld.setBlockState(BlockPos(x, y, z), Blocks.AIR.defaultState, Block.NOTIFY_ALL)
                }
            }
        }
        return true
    }

    private fun enforceRiftBoundary(event: ActiveEvent, server: MinecraftServer) {
        val realmCenter = event.getData<BlockPos>(DATA_RIFT_REALM_CENTER) ?: return
        val realmEntry = event.getData<BlockPos>(DATA_RIFT_REALM_ENTRY) ?: realmCenter.up()
        val minY = realmCenter.y - 1
        val maxY = realmCenter.y + RIFT_DOME_HEIGHT + 2

        for (player in server.playerManager.playerList) {
            if (player.serverWorld.registryKey.value.toString() != RIFT_REALM_DIMENSION_ID) continue
            val dx = player.x - (realmCenter.x + 0.5)
            val dz = player.z - (realmCenter.z + 0.5)
            val outOfRadius = (dx * dx + dz * dz) > RIFT_LOCK_RADIUS_SQ
            val outOfHeight = player.y < minY || player.y > maxY
            if (!outOfRadius && !outOfHeight) continue

            val playerName = player.gameProfile.name
            if (!SAFE_PLAYER_NAME.matches(playerName)) continue
            executeServerCommand(
                server,
                "execute in $RIFT_REALM_DIMENSION_ID run tp $playerName " +
                    "${fmt(realmEntry.x + 0.5)} ${fmt(realmEntry.y.toDouble())} ${fmt(realmEntry.z + 0.5)} " +
                    "${fmt(player.yaw.toDouble())} ${fmt(player.pitch.toDouble())}"
            )
        }
    }

    private fun handleSyntheticPortalTransitions(event: ActiveEvent, server: MinecraftServer) {
        if (event.getData<Boolean>(DATA_RIFT_SYNTHETIC_PORTAL_ACTIVE) != true) return
        if (!isRealmModeEnabled(event, server)) return

        val entrancePos = event.eventLocation ?: return
        val realmCenter = event.getData<BlockPos>(DATA_RIFT_REALM_CENTER) ?: return
        val realmEntry = event.getData<BlockPos>(DATA_RIFT_REALM_ENTRY) ?: realmCenter.up()
        val syntheticExit = event.getData<BlockPos>(DATA_RIFT_SYNTHETIC_PORTAL_EXIT)
            ?: BlockPos(realmCenter.x, realmEntry.y, realmCenter.z + RIFT_ARENA_RADIUS - 4)

        val outsideSnapshots =
            event.getData<MutableMap<String, RiftReturnSnapshot>>(DATA_RIFT_LAST_OUTSIDE_POINTS) ?: mutableMapOf()
        val returnSnapshots =
            event.getData<MutableMap<String, RiftReturnSnapshot>>(DATA_RIFT_RETURN_POINTS) ?: mutableMapOf()
        val cooldownMap =
            event.getData<MutableMap<String, Int>>(DATA_RIFT_SYNTHETIC_PORTAL_COOLDOWN) ?: mutableMapOf()

        val keys = cooldownMap.keys.toList()
        for (key in keys) {
            val remaining = (cooldownMap[key] ?: 0) - 1
            if (remaining <= 0) {
                cooldownMap.remove(key)
            } else {
                cooldownMap[key] = remaining
            }
        }

        for (player in server.playerManager.playerList) {
            val key = player.uuid.toString()
            if (cooldownMap.containsKey(key)) continue

            val playerName = player.gameProfile.name
            if (!SAFE_PLAYER_NAME.matches(playerName)) continue

            if (isInsideRiftRealm(event, player)) {
                val distanceToExit = squaredDistanceXZ(player.x, player.z, syntheticExit)
                if (distanceToExit > RIFT_SYNTHETIC_TRIGGER_RADIUS_SQ) continue

                val fallback = fallbackReturnSnapshot(server, entrancePos, player)
                val snapshot = returnSnapshots[key] ?: outsideSnapshots[key] ?: fallback
                val resolved = if (findWorldById(server, snapshot.dimensionId) != null) snapshot else fallback
                val ok = executeServerCommand(
                    server,
                    "execute in ${resolved.dimensionId} run tp $playerName " +
                        "${fmt(resolved.x)} ${fmt(resolved.y)} ${fmt(resolved.z)} " +
                        "${fmt(resolved.yaw.toDouble())} ${fmt(resolved.pitch.toDouble())}"
                )
                if (ok) {
                    cooldownMap[key] = RIFT_SYNTHETIC_COOLDOWN_TICKS.toInt()
                }
                continue
            }

            if (player.serverWorld.registryKey.value.toString() != OVERWORLD_DIMENSION_ID) continue
            val distanceToEntry = squaredDistanceXZ(player.x, player.z, entrancePos)
            if (distanceToEntry > RIFT_SYNTHETIC_TRIGGER_RADIUS_SQ) continue

            val outside = RiftReturnSnapshot(
                dimensionId = player.serverWorld.registryKey.value.toString(),
                x = player.x,
                y = player.y,
                z = player.z,
                yaw = player.yaw,
                pitch = player.pitch
            )
            outsideSnapshots[key] = outside
            returnSnapshots[key] = outside

            val ok = executeServerCommand(
                server,
                "execute in $RIFT_REALM_DIMENSION_ID run tp $playerName " +
                    "${fmt(realmEntry.x + 0.5)} ${fmt(realmEntry.y.toDouble())} ${fmt(realmEntry.z + 0.5)} " +
                    "${fmt(player.yaw.toDouble())} ${fmt(player.pitch.toDouble())}"
            )
            if (ok) {
                cooldownMap[key] = RIFT_SYNTHETIC_COOLDOWN_TICKS.toInt()
            }
        }

        event.setData(DATA_RIFT_LAST_OUTSIDE_POINTS, outsideSnapshots)
        event.setData(DATA_RIFT_RETURN_POINTS, returnSnapshots)
        event.setData(DATA_RIFT_SYNTHETIC_PORTAL_COOLDOWN, cooldownMap)
    }

    private fun updatePortalReturnSnapshots(event: ActiveEvent, server: MinecraftServer) {
        if (!isRealmModeEnabled(event, server)) return
        val entrancePos = event.eventLocation ?: return

        val outsideSnapshots =
            event.getData<MutableMap<String, RiftReturnSnapshot>>(DATA_RIFT_LAST_OUTSIDE_POINTS) ?: mutableMapOf()
        val returnSnapshots =
            event.getData<MutableMap<String, RiftReturnSnapshot>>(DATA_RIFT_RETURN_POINTS) ?: mutableMapOf()

        for (player in server.playerManager.playerList) {
            val key = player.uuid.toString()
            if (isInsideRiftRealm(event, player)) {
                if (!returnSnapshots.containsKey(key)) {
                    returnSnapshots[key] = outsideSnapshots[key]
                        ?: RiftReturnSnapshot(
                            dimensionId = "minecraft:overworld",
                            x = entrancePos.x + 0.5,
                            y = entrancePos.y + 1.0,
                            z = entrancePos.z + 0.5,
                            yaw = player.yaw,
                            pitch = player.pitch
                        )
                }
            } else {
                outsideSnapshots[key] = RiftReturnSnapshot(
                    dimensionId = player.serverWorld.registryKey.value.toString(),
                    x = player.x,
                    y = player.y,
                    z = player.z,
                    yaw = player.yaw,
                    pitch = player.pitch
                )
            }
        }

        event.setData(DATA_RIFT_LAST_OUTSIDE_POINTS, outsideSnapshots)
        event.setData(DATA_RIFT_RETURN_POINTS, returnSnapshots)
    }

    private fun teleportPlayersOutOfRift(event: ActiveEvent, server: MinecraftServer): Int {
        if (!isRealmModeEnabled(event, server)) return 0

        val entrancePos = event.eventLocation
        val outsideSnapshots =
            event.getData<MutableMap<String, RiftReturnSnapshot>>(DATA_RIFT_LAST_OUTSIDE_POINTS) ?: mutableMapOf()
        val returnSnapshots =
            event.getData<MutableMap<String, RiftReturnSnapshot>>(DATA_RIFT_RETURN_POINTS) ?: mutableMapOf()
        var moved = 0

        for (player in server.playerManager.playerList) {
            if (!isInsideRiftRealm(event, player)) continue
            val playerName = player.gameProfile.name
            if (!SAFE_PLAYER_NAME.matches(playerName)) continue

            val fallback = fallbackReturnSnapshot(server, entrancePos, player)
            val snapshot = returnSnapshots[player.uuid.toString()] ?: outsideSnapshots[player.uuid.toString()] ?: fallback
            val resolved = if (findWorldById(server, snapshot.dimensionId) != null) snapshot else fallback

            val ok = executeServerCommand(
                server,
                "execute in ${resolved.dimensionId} run tp $playerName " +
                    "${fmt(resolved.x)} ${fmt(resolved.y)} ${fmt(resolved.z)} " +
                    "${fmt(resolved.yaw.toDouble())} ${fmt(resolved.pitch.toDouble())}"
            )
            if (ok) {
                moved++
                BroadcastUtil.sendPersonal(player, "${CobblemonEventsMod.config.prefix}§7균열이 닫혀 입장 전 위치로 복귀했습니다. / The rift closed — returned to your entry position.")
            }
        }

        return moved
    }

    private fun fallbackReturnSnapshot(
        server: MinecraftServer,
        entrancePos: BlockPos?,
        sourcePlayer: ServerPlayerEntity
    ): RiftReturnSnapshot {
        val target = entrancePos ?: server.overworld.spawnPos
        return RiftReturnSnapshot(
            dimensionId = "minecraft:overworld",
            x = target.x + 0.5,
            y = target.y + 1.0,
            z = target.z + 0.5,
            yaw = sourcePlayer.yaw,
            pitch = sourcePlayer.pitch
        )
    }

    private fun isInsideRiftRealm(event: ActiveEvent, player: ServerPlayerEntity): Boolean {
        if (player.serverWorld.registryKey.value.toString() != RIFT_REALM_DIMENSION_ID) {
            return false
        }
        val center = event.getData<BlockPos>(DATA_RIFT_REALM_CENTER) ?: return false
        val dx = player.x - (center.x + 0.5)
        val dz = player.z - (center.z + 0.5)
        return (dx * dx + dz * dz) <= RIFT_CAPTURE_RADIUS_SQ
    }

    private fun isRealmModeConfigured(event: ActiveEvent): Boolean {
        return event.getData<BlockPos>(DATA_RIFT_REALM_CENTER) != null
    }

    private fun isRealmModeEnabled(event: ActiveEvent, server: MinecraftServer): Boolean {
        return isRealmModeConfigured(event) && findWorldById(server, RIFT_REALM_DIMENSION_ID) != null
    }

    private fun squaredDistanceXZ(x: Double, z: Double, pos: BlockPos): Double {
        val dx = x - (pos.x + 0.5)
        val dz = z - (pos.z + 0.5)
        return (dx * dx) + (dz * dz)
    }

    private fun findWorldById(server: MinecraftServer, worldId: String): ServerWorld? {
        return server.worldRegistryKeys
            .asSequence()
            .mapNotNull { key -> server.getWorld(key) }
            .firstOrNull { world -> world.registryKey.value.toString().equals(worldId, ignoreCase = true) }
    }

    private fun executeServerCommand(server: MinecraftServer, rawCommand: String): Boolean {
        val command = rawCommand.trim().removePrefix("/")
        return try {
            val result = server.commandManager.dispatcher.execute(command, server.commandSource)
            result > 0
        } catch (e: Exception) {
            CobblemonEventsMod.LOGGER.warn("[TemporalRift] 명령 실행 실패: $command", e)
            false
        }
    }

    private fun fmt(value: Double): String = String.format(Locale.US, "%.3f", value)

    private fun resolveRiftTypes(configured: List<RiftTypeEntry>): List<RiftTypeEntry> {
        val merged = linkedMapOf<String, RiftTypeEntry>()
        for (entry in DEFAULT_RIFT_TYPES) {
            merged[entry.id.lowercase()] = sanitizeEntry(entry)
        }

        for (entry in configured) {
            val key = entry.id.lowercase()
            val normalizedIncoming = sanitizeEntry(entry)
            val base = merged[key]
            merged[key] = if (base == null) {
                normalizedIncoming
            } else {
                mergeEntry(base, normalizedIncoming)
            }
        }

        return merged.values.toList()
    }

    private fun sanitizeEntry(entry: RiftTypeEntry): RiftTypeEntry {
        val normalizedTypes = entry.types.map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct()
        val normalizedPool = entry.pokemonPool.map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct()
        return entry.copy(
            id = entry.id.trim().lowercase(),
            types = if (normalizedTypes.isEmpty()) listOf(entry.id.trim().lowercase()) else normalizedTypes,
            pokemonPool = normalizedPool
        )
    }

    private fun mergeEntry(base: RiftTypeEntry, incoming: RiftTypeEntry): RiftTypeEntry {
        val displayName = if (incoming.displayName.isNotBlank()) incoming.displayName else base.displayName
        val mergedTypes = (base.types + incoming.types).map { it.lowercase() }.distinct()
        val mergedPool = (base.pokemonPool + incoming.pokemonPool).map { it.lowercase() }.distinct()
        return base.copy(displayName = displayName, types = mergedTypes, pokemonPool = mergedPool)
    }

    private fun rememberSpawnedEntities(event: ActiveEvent, spawned: List<PokemonEntity>) {
        if (spawned.isEmpty()) return

        val tracked = event.getData<MutableSet<String>>(DATA_SPAWNED_ENTITY_UUIDS) ?: mutableSetOf()
        tracked.addAll(spawned.map { it.uuid.toString() })
        event.setData(DATA_SPAWNED_ENTITY_UUIDS, tracked)
    }

    private fun despawnTrackedEntities(event: ActiveEvent, server: MinecraftServer): Int {
        val tracked = event.getData<MutableSet<String>>(DATA_SPAWNED_ENTITY_UUIDS) ?: return 0
        var removed = 0
        val worlds = server.worldRegistryKeys.mapNotNull { key -> server.getWorld(key) }

        for (id in tracked) {
            val uuid = try {
                UUID.fromString(id)
            } catch (_: IllegalArgumentException) {
                null
            } ?: continue

            for (world in worlds) {
                val entity = world.getEntity(uuid)
                if (entity is PokemonEntity && entity.isAlive) {
                    entity.discard()
                    removed++
                    break
                }
            }
        }

        tracked.clear()
        event.setData(DATA_SPAWNED_ENTITY_UUIDS, tracked)
        return removed
    }

    private fun spawnSyntheticPortalParticles(world: ServerWorld, center: BlockPos, scale: Double) {
        try {
            val x = center.x + 0.5
            val y = center.y + 2.2
            val z = center.z + 0.5
            world.spawnParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, (20 * scale).toInt().coerceAtLeast(12), 0.9 * scale, 1.0 * scale, 0.9 * scale, 0.02)
            world.spawnParticles(ParticleTypes.PORTAL, x, y, z, (16 * scale).toInt().coerceAtLeast(10), 0.75 * scale, 0.9 * scale, 0.75 * scale, 0.01)
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, (10 * scale).toInt().coerceAtLeast(6), 0.55 * scale, 0.7 * scale, 0.55 * scale, 0.0)
            world.spawnParticles(ParticleTypes.END_ROD, x, y + 1.1, z, (8 * scale).toInt().coerceAtLeast(4), 0.45 * scale, 0.5 * scale, 0.45 * scale, 0.0)
        } catch (_: Exception) {
        }
    }

    private fun spawnRiftParticles(world: ServerWorld, center: BlockPos) {
        try {
            val positions = SpawnHelper.getCirclePositions(center, 5, 16)
            for (pos in positions) {
                world.spawnParticles(
                    ParticleTypes.PORTAL,
                    pos.x.toDouble(), pos.y.toDouble() + 1.5, pos.z.toDouble(),
                    5, 0.5, 1.0, 0.5, 0.02
                )
                world.spawnParticles(
                    ParticleTypes.REVERSE_PORTAL,
                    pos.x.toDouble(), pos.y.toDouble() + 2.0, pos.z.toDouble(),
                    3, 0.3, 0.5, 0.3, 0.01
                )
            }
        } catch (_: Exception) {
        }
    }
}
