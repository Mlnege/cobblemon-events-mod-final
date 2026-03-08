package com.cobblemonevents.events.types

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemonevents.CobblemonEventsMod
import com.cobblemonevents.config.RiftTypeEntry
import com.cobblemonevents.events.ActiveEvent
import com.cobblemonevents.events.EventHandler
import com.cobblemonevents.rewards.RewardManager
import com.cobblemonevents.util.BroadcastUtil
import com.cobblemonevents.util.SpawnHelper
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import java.util.UUID
import kotlin.random.Random

class TemporalRiftEvent : EventHandler {
    companion object {
        private const val DATA_SELECTED_RIFT = "selectedRift"
        private const val DATA_SPAWNED_ENTITY_UUIDS = "riftSpawnedEntityUuids"

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
                "dratini", "dragonair", "dragonite", "bagon", "shelgon", "salamence", "gible", "gabite", "garchomp", "deino", "zweilous", "hydreigon", "goomy", "sliggoo", "goodra", "jangmoo", "hakamoo", "kommoo", "frigibax", "arctibax", "baxcalibur"
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
        if (riftTypes.isEmpty()) return

        val selectedRift = riftTypes.random()
        event.setData(DATA_SELECTED_RIFT, selectedRift)

        val location = SpawnHelper.findRandomEventLocation(server, riftConfig.riftSearchRadius)
        if (location == null) {
            CobblemonEventsMod.LOGGER.warn("[TemporalRift] 이벤트 위치를 찾지 못해 시작을 취소합니다.")
            return
        }

        val (_, pos) = location
        event.eventLocation = pos

        val world = server.overworld
        val spawned = SpawnHelper.spawnMultiplePokemon(
            world = world,
            speciesList = selectedRift.pokemonPool,
            centerPos = pos,
            radius = riftConfig.spawnRadius,
            count = riftConfig.spawnCount,
            levelMin = riftConfig.pokemonLevelMin,
            levelMax = riftConfig.pokemonLevelMax,
            shinyChance = riftConfig.shinyBoost
        )
        rememberSpawnedEntities(event, spawned)

        BroadcastUtil.announceRift(
            server,
            selectedRift.displayName,
            pos.x,
            pos.y,
            pos.z,
            event.definition.durationMinutes
        )

        CobblemonEventsMod.LOGGER.info(
            "[TemporalRift] '${selectedRift.id}' 균열 생성 - 좌표: ${pos.x}, ${pos.y}, ${pos.z}, 스폰: ${spawned.size}"
        )
    }

    override fun onTick(event: ActiveEvent, server: MinecraftServer) {
        val pos = event.eventLocation ?: return
        val world = server.overworld

        if (event.ticksRemaining % 60 == 0L) {
            spawnRiftParticles(world, pos)
        }

        val riftConfig = event.definition.riftConfig ?: return
        val selectedRift = event.getData<RiftTypeEntry>(DATA_SELECTED_RIFT) ?: return

        if (event.ticksRemaining > 0 && event.ticksRemaining % (20 * 120) == 0L) {
            val additional = SpawnHelper.spawnMultiplePokemon(
                world = world,
                speciesList = selectedRift.pokemonPool,
                centerPos = pos,
                radius = riftConfig.spawnRadius,
                count = (riftConfig.spawnCount / 2).coerceAtLeast(1),
                levelMin = riftConfig.pokemonLevelMin,
                levelMax = riftConfig.pokemonLevelMax,
                shinyChance = riftConfig.shinyBoost
            )
            rememberSpawnedEntities(event, additional)

            BroadcastUtil.broadcast(
                server,
                "${CobblemonEventsMod.config.prefix}§d균열에서 추가 포켓몬이 출현했습니다! §7(남은 시간: ${event.getRemainingMinutes()}분)"
            )
        }
    }

    override fun onEnd(event: ActiveEvent, server: MinecraftServer) {
        val selectedRift = event.getData<RiftTypeEntry>(DATA_SELECTED_RIFT)
        val riftName = selectedRift?.displayName ?: "시공의 균열"

        val despawned = despawnTrackedEntities(event, server.overworld)

        val riftConfig = event.definition.riftConfig ?: return
        for (playerUUID in event.participants.keys) {
            val player = server.playerManager.getPlayer(playerUUID) ?: continue
            val catchCount = event.getProgress(playerUUID)
            if (catchCount > 0) {
                RewardManager.giveRewards(player, riftConfig.dropRewards, event.definition)
                CobblemonEventsMod.rankingManager.recordEventComplete(playerUUID, player.name.string)
            }
        }

        BroadcastUtil.announceEventEnd(
            server,
            riftName,
            listOf(
                "§7참가자: §f${event.participants.size}명",
                "§7총 포획: §e${event.participants.values.sum()}마리",
                "§7이벤트 스폰 디스폰: §c${despawned}마리"
            )
        )
    }

    override fun onPokemonCaught(event: ActiveEvent, player: ServerPlayerEntity, species: String) {
        val selectedRift = event.getData<RiftTypeEntry>(DATA_SELECTED_RIFT) ?: return

        val isRiftPokemon = selectedRift.pokemonPool.any { it.equals(species, ignoreCase = true) }
        if (!isRiftPokemon) return

        val count = event.addProgress(player.uuid)
        BroadcastUtil.sendProgress(player, "§d시공의 균열 포획: §f${count}마리 §7(${selectedRift.displayName}§7)")

        val riftConfig = event.definition.riftConfig ?: return
        if (Random.nextDouble() < 0.3) {
            RewardManager.giveRewards(player, riftConfig.dropRewards, event.definition)
        }
    }

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

    private fun despawnTrackedEntities(event: ActiveEvent, world: ServerWorld): Int {
        val tracked = event.getData<MutableSet<String>>(DATA_SPAWNED_ENTITY_UUIDS) ?: return 0
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
        event.setData(DATA_SPAWNED_ENTITY_UUIDS, tracked)
        return removed
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