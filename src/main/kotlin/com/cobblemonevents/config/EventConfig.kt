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
        val newConfig = load()
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
