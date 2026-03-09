package com.cobblemonevents.config

import com.cobblemonevents.CobblemonEventsMod
import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.io.File

// ============================================================
// 보상 관련 데이터 클래스
// ============================================================

data class PokemonRewardEntry(
    val species: String = "bulbasaur",
    val level: Int = 10,
    val shinyChance: Double = 0.0,
    val ivMin: Int = 0,
    val ivMax: Int = 31,
    val formAspects: List<String> = emptyList(),
    val moves: List<String> = emptyList(),        // 기술 지정 (선택)
    val ability: String = ""                       // 특성 지정 (선택)
)

data class ItemRewardEntry(
    val itemId: String = "minecraft:diamond",
    val count: Int = 1,
    val nbt: String? = null
)

data class RewardPool(
    val pokemon: List<PokemonRewardEntry> = emptyList(),
    val items: List<ItemRewardEntry> = emptyList(),
    val rewardMode: String = "ALL",           // ALL, RANDOM_ONE, RANDOM_MULTI
    val randomCount: Int = 1,                 // RANDOM_MULTI일 때 지급 수
    val broadcastReward: Boolean = true
)

// ============================================================
// 이벤트 유형별 세부 설정
// ============================================================

/** 시공 균열 설정 */
data class RiftConfig(
    val riftTypes: List<RiftTypeEntry> = listOf(
        RiftTypeEntry("fire", "§c🔥 불 타입 균열", listOf("fire"), listOf(
            "charizard", "arcanine", "magmortar", "blaziken", "infernape",
            "typhlosion", "volcarona", "cinderace", "heatran"
        )),
        RiftTypeEntry("ice", "§b❄ 얼음 타입 균열", listOf("ice"), listOf(
            "lapras", "glaceon", "weavile", "mamoswine", "froslass",
            "articuno", "walrein", "beartic", "avalugg"
        )),
        RiftTypeEntry("dragon", "§5🐉 드래곤 타입 균열", listOf("dragon"), listOf(
            "dragonite", "salamence", "garchomp", "hydreigon", "haxorus",
            "goodra", "kommo-o", "dragapult", "baxcalibur"
        )),
        RiftTypeEntry("legendary", "§6🌌 전설의 균열", listOf("legendary"), listOf(
            "dragonite", "tyranitar", "salamence", "garchomp", "hydreigon",
            "goodra", "kommo-o", "dragapult", "baxcalibur"
        )),
        RiftTypeEntry("ghost", "§8👻 고스트 타입 균열", listOf("ghost"), listOf(
            "gengar", "mismagius", "chandelure", "dragapult", "spectrier",
            "polteageist", "cursola", "spiritomb"
        )),
        RiftTypeEntry("electric", "§e⚡ 전기 타입 균열", listOf("electric"), listOf(
            "pikachu", "raichu", "jolteon", "electivire", "luxray",
            "magnezone", "rotom", "zeraora", "regieleki"
        ))
    ),
    val spawnRadius: Int = 50,                   // 균열 주변 스폰 반경
    val spawnCount: Int = 8,                     // 균열당 스폰 수
    val pokemonLevelMin: Int = 30,
    val pokemonLevelMax: Int = 60,
    val shinyBoost: Double = 0.05,               // 균열 내 이로치 확률 부스트
    val riftSearchRadius: Int = 500,             // 균열 생성 범위 (플레이어 기준)
    val dropRewards: RewardPool = RewardPool(
        items = listOf(
            ItemRewardEntry("cobblemon:rare_candy", 2),
            ItemRewardEntry("cobblemon:ability_patch", 1),
            ItemRewardEntry("cobblemon:master_ball", 1)
        ),
        rewardMode = "RANDOM_ONE"
    )
)

data class RiftTypeEntry(
    val id: String = "fire",
    val displayName: String = "§c🔥 불 타입 균열",
    val types: List<String> = listOf("fire"),
    val pokemonPool: List<String> = listOf("charizard")
)

/** 대탐험(포켓스탑) 설정 */
data class ExplorerConfig(
    val stopCount: Int = 10,                     // 생성할 포켓스탑 수
    val searchRadius: Int = 300,                 // 플레이어 기준 생성 범위
    val interactRadius: Double = 3.0,            // 상호작용 거리
    val stopRewards: RewardPool = RewardPool(
        items = listOf(
            ItemRewardEntry("cobblemon:ultra_ball", 5),
            ItemRewardEntry("cobblemon:rare_candy", 1),
            ItemRewardEntry("cobblemon:exp_candy_l", 3)
        ),
        pokemon = listOf(
            PokemonRewardEntry("eevee", 15, shinyChance = 0.08)
        ),
        rewardMode = "RANDOM_MULTI",
        randomCount = 2
    ),
    val legendFragmentChance: Double = 0.05,     // 전설 조각 드롭 확률
    val shinyBoostMinutes: Int = 5               // 이로치 확률 증가 지속시간
)

/** 사냥 시즌(랭킹) 설정 */
data class HuntingSeasonConfig(
    val pokemonPool: List<String> = listOf(
        "lucario", "gardevoir", "gengar", "gyarados", "dragonite",
        "tyranitar", "metagross", "salamence", "garchomp", "togekiss",
        "scizor", "blaziken", "swampert", "sceptile", "infernape"
    ),
    val top1Rewards: RewardPool = RewardPool(
        pokemon = listOf(PokemonRewardEntry("", 50, shinyChance = 1.0)), // species는 사냥 대상으로 자동
        items = listOf(
            ItemRewardEntry("cobblemon:master_ball", 1),
            ItemRewardEntry("minecraft:netherite_ingot", 5)
        ),
        rewardMode = "ALL"
    ),
    val top2Rewards: RewardPool = RewardPool(
        items = listOf(
            ItemRewardEntry("cobblemon:rare_candy", 10),
            ItemRewardEntry("minecraft:diamond", 16)
        ),
        rewardMode = "ALL"
    ),
    val top3Rewards: RewardPool = RewardPool(
        items = listOf(
            ItemRewardEntry("cobblemon:ability_capsule", 1),
            ItemRewardEntry("cobblemon:rare_candy", 5)
        ),
        rewardMode = "ALL"
    ),
    val participationRewards: RewardPool = RewardPool(
        items = listOf(
            ItemRewardEntry("cobblemon:poke_ball", 10)
        ),
        rewardMode = "ALL"
    )
)

/** 전설 레이드 설정 */
data class LegendaryRaidConfig(
    val bossPool: List<RaidBossEntry> = listOf(
        // Cobblemon 1.7.3 구현 기준 전설 + 환상 전체
        RaidBossEntry("articuno", "§b❄ 프리져", 100, 10.0),
        RaidBossEntry("zapdos", "§e⚡ 썬더", 100, 10.0),
        RaidBossEntry("moltres", "§c🔥 파이어", 100, 10.0),
        RaidBossEntry("mewtwo", "§d🧬 뮤츠", 100, 10.0),
        RaidBossEntry("mew", "§d✨ 뮤", 100, 10.0),
        RaidBossEntry("lugia", "§f🌊 루기아", 100, 10.0),
        RaidBossEntry("hooh", "§6🔥 칠색조", 100, 10.0),
        RaidBossEntry("latias", "§c💨 라티아스", 100, 10.0),
        RaidBossEntry("latios", "§9💨 라티오스", 100, 10.0),
        RaidBossEntry("rayquaza", "§a🐉 레쿠쟈", 100, 10.0),
        RaidBossEntry("regirock", "§6🗿 레지락", 100, 10.0),
        RaidBossEntry("regice", "§b🧊 레지아이스", 100, 10.0),
        RaidBossEntry("registeel", "§7⚙ 레지스틸", 100, 10.0),
        RaidBossEntry("regigigas", "§e🛡 레지기가스", 100, 10.0),
        RaidBossEntry("xerneas", "§d🌈 제르네아스", 100, 10.0),
        RaidBossEntry("regieleki", "§e⚡ 레지에레키", 100, 10.0),
        RaidBossEntry("regidrago", "§5🐲 레지드래고", 100, 10.0),
        RaidBossEntry("zarude", "§2🌿 자루도", 100, 10.0)
    ),
    val requiredPlayers: Int = 3,                // 최소 참여 인원
    val bossShinyChance: Double = 0.03,
    val raidRewards: RewardPool = RewardPool(
        items = listOf(
            ItemRewardEntry("cobblemon:master_ball", 1),
            ItemRewardEntry("minecraft:diamond_block", 4),
            ItemRewardEntry("cobblemon:rare_candy", 5)
        ),
        rewardMode = "RANDOM_MULTI",
        randomCount = 2
    ),
    val catchChance: Double = 0.15               // 레이드 보스 포획 확률
)

data class RaidBossEntry(
    val species: String = "rayquaza",
    val displayName: String = "§a🐉 레쿠쟈",
    val level: Int = 100,
    val healthMultiplier: Double = 10.0          // 체력 배수
)

/** 럭키 이벤트 설정 */
data class LuckyEventConfig(
    val effects: List<LuckyEffectEntry> = listOf(
        LuckyEffectEntry("shiny_boost", "§6✨ 서버 전체 이로치 확률 3배!", "shiny_boost", 0.3),
        LuckyEffectEntry("exp_boost", "§b✨ 서버 전체 경험치 2배!", "exp_boost", 2.0),
        LuckyEffectEntry("catch_boost", "§a✨ 포획률 1.5배 증가!", "catch_boost", 1.5),
        LuckyEffectEntry("random_shiny", "§e✨ 랜덤 플레이어 1명 이로치 포켓몬 지급!", "random_shiny", 1.0),
        LuckyEffectEntry("random_legendary", "§d✨ 랜덤 플레이어 주변 전설 포켓몬 출현!", "random_legendary", 1.0),
        LuckyEffectEntry("level_boost", "§f✨ 모든 플레이어 선두 포켓몬 레벨 +5!", "level_boost", 5.0),
        LuckyEffectEntry("item_rain", "§6✨ 아이템 비! 모든 플레이어에게 랜덤 아이템!", "item_rain", 1.0),
        LuckyEffectEntry("spawn_boost", "§a✨ 포켓몬 출현률 3배 증가!", "spawn_boost", 3.0)
    ),
    val effectDurationMinutes: Int = 10
)

data class LuckyEffectEntry(
    val id: String = "shiny_boost",
    val displayName: String = "§6✨ 이로치 확률 증가!",
    val effectType: String = "shiny_boost",
    val value: Double = 2.0
)

/** 울트라 워프홀 설정 */
data class UltraWormholeConfig(
    val ultraBeastPool: List<String> = listOf(
        "nihilego", "buzzwole", "pheromosa", "xurkitree",
        "celesteela", "kartana", "guzzlord", "poipole",
        "naganadel", "stakataka", "blacephalon"
    ),
    val wormholeLevel: Int = 60,
    val wormholeShinyChance: Double = 0.08,      // 울트라비스트 이로치 확률
    val spawnCount: Int = 3,                     // 워프홀당 울트라비스트 수
    val wormholeSearchRadius: Int = 400,
    val wormholeRewards: RewardPool = RewardPool(
        items = listOf(
            ItemRewardEntry("cobblemon:beast_ball", 3),
            ItemRewardEntry("cobblemon:rare_candy", 5),
            ItemRewardEntry("cobblemon:ability_patch", 1)
        ),
        rewardMode = "RANDOM_MULTI",
        randomCount = 2
    ),
    val ultraSpaceDimensionIds: List<String>? = listOf(
        "ultrabeasts:ultra_space"
    ),                                          // 울트라 차원 ID 정확 매칭(서버 차원 ID에 맞춰 수정 가능)
    val requireUltraBeastsMod: Boolean = false   // true면 Ultra Beasts 모드 필수
)

/** 체육관 커스텀 이벤트 설정 */
data class GymIntegrationCommand(
    val id: String = "spawn_gym_leader",
    val requiredMods: List<String> = listOf("cobblemontrainerbattle"),
    val command: String = "say [체육관 연동] {type_name} 리더 소환 커맨드를 설정하세요."
)

data class GymTrainerProfile(
    val generation: Int = 1,
    val role: String = "LEADER", // LEADER, ELITE, CHAMPION, VILLAIN
    val name: String = "Brock",
    val title: String = "관장",
    val levelMin: Int = 1,
    val levelMax: Int = 40,
    val typeHints: List<String> = emptyList()
)

data class GymTypeEntry(
    val id: String = "electric",
    val displayName: String = "§e⚡ 전기 체육관",
    val targetWins: Int = 3,
    val rewardMultiplier: Double = 1.0
)

data class LeagueTierEntry(
    val tierLevel: Int = 100,
    val displayName: String = "§e리그 1부",
    val trophyItemId: String = "aps_trophies:bronze_trophy",
    val trophyCount: Int = 1,
    val rewards: RewardPool = RewardPool(
        items = listOf(
            ItemRewardEntry("cobblemon:ultra_ball", 16),
            ItemRewardEntry("cobblemon:rare_candy", 6)
        ),
        rewardMode = "RANDOM_MULTI",
        randomCount = 2
    ),
    val entryCommands: List<GymIntegrationCommand> = emptyList(),
    val completionCommands: List<GymIntegrationCommand> = emptyList()
)

data class GymConfig(
    val gymTypes: List<GymTypeEntry> = listOf(
        GymTypeEntry("electric", "§e⚡ 번개 배지 체육관", 3, 1.0),
        GymTypeEntry("water", "§9🌊 파도 배지 체육관", 3, 1.0),
        GymTypeEntry("fire", "§c🔥 화염 배지 체육관", 4, 1.1),
        GymTypeEntry("grass", "§a🌿 숲의 배지 체육관", 4, 1.1),
        GymTypeEntry("psychic", "§d🔮 정신 배지 체육관", 5, 1.2),
        GymTypeEntry("dragon", "§5🐉 용의 배지 체육관", 5, 1.25)
    ),
    val randomTargetMin: Int = 3,
    val randomTargetMax: Int = 6,
    val gymSearchRadius: Int = 450,
    val leaderLevelMin: Int = 40,
    val leaderLevelMax: Int = 80,
    val levelCap: Int = 300,
    val levelGainOnClear: Int = 5,
    val legendaryRewardStartLevel: Int = 150,
    val completionRewards: RewardPool = RewardPool(
        items = listOf(
            ItemRewardEntry("cobblemon:ultra_ball", 8),
            ItemRewardEntry("cobblemon:rare_candy", 3),
            ItemRewardEntry("cobblemon:exp_candy_l", 4)
        ),
        rewardMode = "RANDOM_MULTI",
        randomCount = 2
    ),
    val participationRewards: RewardPool = RewardPool(
        items = listOf(
            ItemRewardEntry("cobblemon:great_ball", 6)
        ),
        rewardMode = "ALL"
    ),
    val legendaryMonumentRewards: RewardPool = RewardPool(
        items = listOf(
            ItemRewardEntry("legendarymonuments:red_chain", 1),
            ItemRewardEntry("legendarymonuments:star_core", 1),
            ItemRewardEntry("legendarymonuments:azure_flute", 1)
        ),
        rewardMode = "RANDOM_ONE"
    ),
    val trainerCatalog: List<GymTrainerProfile> = defaultGymTrainerCatalog(),
    val leagueTiers: List<LeagueTierEntry> = defaultLeagueTiers(),
    val integrationCommands: List<GymIntegrationCommand> = listOf(
        GymIntegrationCommand()
    )
)

private fun defaultLeagueTiers(): List<LeagueTierEntry> {
    return listOf(
        LeagueTierEntry(
            tierLevel = 100,
            displayName = "§e리그 1부",
            trophyItemId = "aps_trophies:bronze_trophy",
            trophyCount = 1,
            rewards = RewardPool(
                items = listOf(
                    ItemRewardEntry("cobblemon:ultra_ball", 20),
                    ItemRewardEntry("cobblemon:rare_candy", 8),
                    ItemRewardEntry("legendarymonuments:red_chain", 1)
                ),
                rewardMode = "RANDOM_MULTI",
                randomCount = 2
            )
        ),
        LeagueTierEntry(
            tierLevel = 200,
            displayName = "§7리그 2부",
            trophyItemId = "aps_trophies:silver_trophy",
            trophyCount = 1,
            rewards = RewardPool(
                items = listOf(
                    ItemRewardEntry("cobblemon:ultra_ball", 32),
                    ItemRewardEntry("cobblemon:master_ball", 1),
                    ItemRewardEntry("legendarymonuments:star_core", 1)
                ),
                rewardMode = "RANDOM_MULTI",
                randomCount = 2
            )
        ),
        LeagueTierEntry(
            tierLevel = 300,
            displayName = "§6리그 챔피언",
            trophyItemId = "aps_trophies:gold_trophy",
            trophyCount = 1,
            rewards = RewardPool(
                items = listOf(
                    ItemRewardEntry("cobblemon:master_ball", 1),
                    ItemRewardEntry("cobblemon:rare_candy", 20),
                    ItemRewardEntry("legendarymonuments:azure_flute", 1)
                ),
                rewardMode = "RANDOM_MULTI",
                randomCount = 3
            )
        )
    )
}

private fun defaultGymTrainerCatalog(): List<GymTrainerProfile> {
    val trainers = mutableListOf<GymTrainerProfile>()

    fun addGroup(
        generation: Int,
        role: String,
        title: String,
        names: List<String>
    ) {
        names.forEachIndexed { index, name ->
            val (minLevel, maxLevel) = computeTrainerLevelRange(generation, role, index, names.size)
            trainers += GymTrainerProfile(
                generation = generation,
                role = role,
                name = name,
                title = title,
                levelMin = minLevel,
                levelMax = maxLevel
            )
        }
    }

    // Gen 1
    addGroup(1, "LEADER", "관장", listOf("Brock", "Misty", "LtSurge", "Erika", "Koga", "Sabrina", "Blaine", "Giovanni"))
    addGroup(1, "ELITE", "엘리트", listOf("Lorelei", "Bruno", "Agatha", "Lance"))
    addGroup(1, "CHAMPION", "챔피언", listOf("Blue"))
    addGroup(1, "VILLAIN", "빌런", listOf("Giovanni", "Archer", "Ariana"))

    // Gen 2
    addGroup(2, "LEADER", "관장", listOf("Falkner", "Bugsy", "Whitney", "Morty", "Chuck", "Jasmine", "Pryce", "Clair"))
    addGroup(2, "ELITE", "엘리트", listOf("Will", "Koga", "Bruno", "Karen"))
    addGroup(2, "CHAMPION", "챔피언", listOf("Lance"))
    addGroup(2, "VILLAIN", "빌런", listOf("Archer", "Petrel", "Proton"))

    // Gen 3
    addGroup(3, "LEADER", "관장", listOf("Roxanne", "Brawly", "Wattson", "Flannery", "Norman", "Winona", "Tate", "Liza", "Juan"))
    addGroup(3, "ELITE", "엘리트", listOf("Sidney", "Phoebe", "Glacia", "Drake"))
    addGroup(3, "CHAMPION", "챔피언", listOf("Steven", "Wallace"))
    addGroup(3, "VILLAIN", "빌런", listOf("Maxie", "Archie", "Courtney", "Matt"))

    // Gen 4
    addGroup(4, "LEADER", "관장", listOf("Roark", "Gardenia", "Maylene", "CrasherWake", "Fantina", "Byron", "Candice", "Volkner"))
    addGroup(4, "ELITE", "엘리트", listOf("Aaron", "Bertha", "Flint", "Lucian"))
    addGroup(4, "CHAMPION", "챔피언", listOf("Cynthia"))
    addGroup(4, "VILLAIN", "빌런", listOf("Cyrus", "Mars", "Jupiter", "Saturn"))

    // Gen 5
    addGroup(5, "LEADER", "관장", listOf("Cilan", "Chili", "Cress", "Lenora", "Burgh", "Elesa", "Clay", "Skyla", "Brycen", "Drayden"))
    addGroup(5, "ELITE", "엘리트", listOf("Shauntal", "Grimsley", "Caitlin", "Marshal"))
    addGroup(5, "CHAMPION", "챔피언", listOf("Alder", "Iris"))
    addGroup(5, "VILLAIN", "빌런", listOf("Ghetsis", "N", "Colress"))

    // Gen 6
    addGroup(6, "LEADER", "관장", listOf("Viola", "Grant", "Korrina", "Ramos", "Clemont", "Valerie", "Olympia", "Wulfric"))
    addGroup(6, "ELITE", "엘리트", listOf("Malva", "Siebold", "Wikstrom", "Drasna"))
    addGroup(6, "CHAMPION", "챔피언", listOf("Diantha"))
    addGroup(6, "VILLAIN", "빌런", listOf("Lysandre", "Xerosic"))

    // Gen 7
    addGroup(7, "LEADER", "관장", listOf("Ilima", "Lana", "Kiawe", "Mallow", "Sophocles", "Acerola", "Mina", "Hala", "Olivia", "Nanu", "Hapu"))
    addGroup(7, "ELITE", "엘리트", listOf("Hala", "Olivia", "Nanu", "Hapu"))
    addGroup(7, "CHAMPION", "챔피언", listOf("Kukui", "Hau"))
    addGroup(7, "VILLAIN", "빌런", listOf("Lusamine", "Guzma", "Faba"))

    // Gen 8
    addGroup(8, "LEADER", "관장", listOf("Milo", "Nessa", "Kabu", "Bea", "Allister", "Opal", "Gordie", "Melony", "Piers", "Raihan"))
    addGroup(8, "ELITE", "엘리트", listOf("Bede", "Marnie", "Hop", "Raihan"))
    addGroup(8, "CHAMPION", "챔피언", listOf("Leon"))
    addGroup(8, "VILLAIN", "빌런", listOf("Rose", "Oleana"))

    // Gen 9
    addGroup(9, "LEADER", "관장", listOf("Katy", "Brassius", "Iono", "Kofu", "Larry", "Ryme", "Tulip", "Grusha"))
    addGroup(9, "ELITE", "엘리트", listOf("Rika", "Poppy", "Larry", "Hassel"))
    addGroup(9, "CHAMPION", "챔피언", listOf("Geeta", "Nemona"))
    addGroup(9, "VILLAIN", "빌런", listOf("Giacomo", "Mela", "Atticus", "Ortega", "Eri"))

    return trainers
}

private fun computeTrainerLevelRange(
    generation: Int,
    role: String,
    order: Int,
    total: Int
): Pair<Int, Int> {
    val genBase = ((generation - 1) * 30 + 1).coerceIn(1, 300)
    val normalizedRole = role.uppercase()
    val roleOffset = when (normalizedRole) {
        "LEADER" -> 0
        "ELITE" -> 18
        "CHAMPION" -> 35
        "VILLAIN" -> 24
        else -> 0
    }
    val roleSpan = when (normalizedRole) {
        "LEADER" -> 70
        "ELITE" -> 95
        "CHAMPION" -> 120
        "VILLAIN" -> 100
        else -> 70
    }

    val safeTotal = total.coerceAtLeast(1)
    val progress = if (safeTotal == 1) 0.0 else order.toDouble() / (safeTotal - 1).toDouble()
    val minLevel = (genBase + roleOffset + (progress * 25.0).toInt()).coerceIn(1, 300)
    val maxLevel = (minLevel + roleSpan).coerceIn(minLevel, 300)
    return Pair(minLevel, maxLevel)
}

// ============================================================
// 이벤트 정의
// ============================================================

data class EventDefinition(
    val id: String = "default_event",
    val displayName: String = "§6기본 이벤트",
    val description: String = "§f기본 이벤트입니다.",
    val enabled: Boolean = true,
    val intervalMinutes: Int = 60,
    val durationMinutes: Int = 15,
    val startDelayMinutes: Int = 5,
    val announceBeforeMinutes: Int = 2,
    val requiredPlayerCount: Int = 1,
    val eventType: String = "TEMPORAL_RIFT",     // 이벤트 유형

    // 유형별 세부 설정 (해당하는 것만 사용)
    val riftConfig: RiftConfig? = null,
    val explorerConfig: ExplorerConfig? = null,
    val huntingConfig: HuntingSeasonConfig? = null,
    val raidConfig: LegendaryRaidConfig? = null,
    val luckyConfig: LuckyEventConfig? = null,
    val wormholeConfig: UltraWormholeConfig? = null,
    val gymConfig: GymConfig? = null,

    // 범용 보상 (특정 유형이 자체 보상이 없을 때)
    val rewards: RewardPool = RewardPool()
)

// ============================================================
// 메인 설정 클래스
// ============================================================

data class EventConfig(
    var prefix: String = "§6[코블몬 이벤트]§r ",
    var language: String = "ko_kr",
    var globalShinyBoost: Double = 1.0,          // 전역 이로치 배수
    var globalExpBoost: Double = 1.0,            // 전역 경험치 배수
    var globalCatchBoost: Double = 1.0,          // 전역 포획률 배수
    var rareDrops: RareDropConfig = RareDropConfig(), // 레어 드롭 설정
    var events: MutableList<EventDefinition> = mutableListOf()
) {
    companion object {
        private val GSON = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
        private val CONFIG_FILE: File
            get() = FabricLoader.getInstance().configDir.resolve("cobblemon-events.json").toFile()

        fun configFile(): File = CONFIG_FILE

        fun load(): EventConfig {
            return try {
                if (CONFIG_FILE.exists()) {
                    val json = CONFIG_FILE.readText()
                    GSON.fromJson(json, EventConfig::class.java) ?: createDefault()
                } else {
                    createDefault()
                }
            } catch (e: Exception) {
                CobblemonEventsMod.LOGGER.error("설정 파일 로드 실패, 기본값 생성", e)
                createDefault()
            }
        }

        private fun createDefault(): EventConfig {
            val config = EventConfig(
                events = mutableListOf(

                    // ===== 1. 시공 균열 이벤트 =====
                    EventDefinition(
                        id = "temporal_rift",
                        displayName = "§d🌀 시공 균열 이벤트",
                        description = "§f월드에 시공 균열이 열려 특정 타입 포켓몬이 대량 등장합니다!",
                        intervalMinutes = 720,
                        durationMinutes = 15,
                        startDelayMinutes = 180,
                        announceBeforeMinutes = 3,
                        eventType = "TEMPORAL_RIFT",
                        riftConfig = RiftConfig()
                    ),

                    // ===== 2. 포켓몬 대탐험 =====
                    EventDefinition(
                        id = "explorer",
                        displayName = "§a🧭 포켓몬 대탐험",
                        description = "§f월드 곳곳에 포켓스탑이 나타났습니다! 보물을 찾으세요!",
                        intervalMinutes = 720,
                        durationMinutes = 15,
                        startDelayMinutes = 10,
                        announceBeforeMinutes = 1,
                        eventType = "EXPLORER",
                        explorerConfig = ExplorerConfig()
                    ),

                    // ===== 3. 포켓몬 사냥 시즌 =====
                    EventDefinition(
                        id = "hunting_season",
                        displayName = "§c🏹 포켓몬 사냥 시즌",
                        description = "§f지정된 포켓몬을 가장 많이 잡은 플레이어가 보상을 받습니다!",
                        intervalMinutes = 720,
                        durationMinutes = 120,
                        startDelayMinutes = 390,
                        announceBeforeMinutes = 5,
                        eventType = "HUNTING_SEASON",
                        huntingConfig = HuntingSeasonConfig()
                    ),

                    // ===== 4. 전설 레이드 =====
                    EventDefinition(
                        id = "legendary_raid",
                        displayName = "§6🌠 전설 레이드",
                        description = "§f전설의 포켓몬이 나타났습니다! 협동하여 쓰러뜨리세요!",
                        intervalMinutes = 720,
                        durationMinutes = 20,
                        startDelayMinutes = 570,
                        announceBeforeMinutes = 5,
                        requiredPlayerCount = 3,
                        eventType = "LEGENDARY_RAID",
                        raidConfig = LegendaryRaidConfig()
                    ),

                    // ===== 5. 럭키 이벤트 =====
                    EventDefinition(
                        id = "lucky_event",
                        displayName = "§e🎰 럭키 이벤트",
                        description = "§f서버 전체에 랜덤 행운이 찾아옵니다!",
                        intervalMinutes = 720,
                        durationMinutes = 10,
                        startDelayMinutes = 90,
                        announceBeforeMinutes = 0,
                        eventType = "LUCKY_EVENT",
                        luckyConfig = LuckyEventConfig()
                    ),

                    // ===== 6. 울트라 워프홀 =====
                    EventDefinition(
                        id = "ultra_wormhole",
                        displayName = "§5🌌 울트라 워프홀",
                        description = "§f다른 차원에서 울트라비스트가 출현합니다!",
                        intervalMinutes = 720,
                        durationMinutes = 15,
                        startDelayMinutes = 270,
                        announceBeforeMinutes = 3,
                        eventType = "ULTRA_WORMHOLE",
                        wormholeConfig = UltraWormholeConfig()
                    ),

                    // ===== 7. 커스텀 체육관 =====
                    EventDefinition(
                        id = "custom_gym",
                        displayName = "§b🏟 커스텀 체육관 챌린지",
                        description = "§f타입별 체육관을 돌파하고 배지를 획득하세요!",
                        intervalMinutes = 720,
                        durationMinutes = 18,
                        startDelayMinutes = 450,
                        announceBeforeMinutes = 3,
                        eventType = "GYM_CHALLENGE",
                        gymConfig = GymConfig()
                    )
                )
            )
            config.save()
            return config
        }
    }

    fun save() {
        try {
            CONFIG_FILE.parentFile.mkdirs()
            CONFIG_FILE.writeText(GSON.toJson(this))
        } catch (e: Exception) {
            CobblemonEventsMod.LOGGER.error("설정 파일 저장 실패", e)
        }
    }
    fun reload(): EventConfig {
        val newConfig = try {
            if (!CONFIG_FILE.exists()) {
                CobblemonEventsMod.LOGGER.warn("[설정] cobblemon-events.json 파일이 없어 기본 파일을 다시 생성합니다.")
                val recreated = load()
                this.prefix = recreated.prefix
                this.language = recreated.language
                this.events = recreated.events
                this.globalShinyBoost = recreated.globalShinyBoost
                this.globalExpBoost = recreated.globalExpBoost
                this.globalCatchBoost = recreated.globalCatchBoost
                this.rareDrops = recreated.rareDrops
                return this
            }

            val json = CONFIG_FILE.readText()
            GSON.fromJson(json, EventConfig::class.java)
        } catch (e: Exception) {
            CobblemonEventsMod.LOGGER.error("[설정] cobblemon-events.json 리로드 실패, 기존 값을 유지합니다.", e)
            null
        }

        if (newConfig == null) {
            CobblemonEventsMod.LOGGER.warn("[설정] 리로드할 수 없어 기존 설정을 유지합니다.")
            return this
        }

        this.prefix = newConfig.prefix
        this.language = newConfig.language
        this.events = newConfig.events
        this.globalShinyBoost = newConfig.globalShinyBoost
        this.globalExpBoost = newConfig.globalExpBoost
        this.globalCatchBoost = newConfig.globalCatchBoost
        this.rareDrops = newConfig.rareDrops
        return this
    }
}
