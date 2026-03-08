package com.cobblemonevents.config

/**
 * 레어 드롭 설정
 *
 * Mega Showdown & Legendary Monuments 모드의 핵심 아이템을
 * 이벤트 보상에서 0.25% 확률로 추가 드롭하는 시스템
 *
 * ※ 해당 모드가 설치되지 않은 경우 아이템이 자동으로 무시됩니다 (give 실패)
 */
data class RareDropConfig(
    val enabled: Boolean = true,
    val globalDropChance: Double = 0.0025,       // 0.25% 기본 확률

    // ========================================================
    // Mega Showdown 아이템 (namespace: mega_showdown)
    // ========================================================
    val megaShowdownDrops: List<RareDropEntry> = listOf(
        // --- 메가 진화 관련 ---
        RareDropEntry("mega_showdown:key_stone", "§d키스톤", 0.0025,
            "메가 진화에 필수. 메가 팔찌에 장착"),
        RareDropEntry("mega_showdown:mega_bracelet", "§d메가 팔찌", 0.0025,
            "메가 진화를 발동시키는 핵심 장비"),

        // --- 메가스톤 (대표) ---
        RareDropEntry("mega_showdown:charizardite_x", "§6리자몽나이트 X", 0.0025),
        RareDropEntry("mega_showdown:charizardite_y", "§6리자몽나이트 Y", 0.0025),
        RareDropEntry("mega_showdown:gengarite", "§5팬텀나이트", 0.0025),
        RareDropEntry("mega_showdown:lucarionite", "§b루카리오나이트", 0.0025),
        RareDropEntry("mega_showdown:gardevoirite", "§d가디나이트", 0.0025),
        RareDropEntry("mega_showdown:blazikenite", "§c번치코나이트", 0.0025),
        RareDropEntry("mega_showdown:gyaradosite", "§9갸라도스나이트", 0.0025),
        RareDropEntry("mega_showdown:mewtwoite_x", "§5뮤츠나이트 X", 0.0025),
        RareDropEntry("mega_showdown:mewtwoite_y", "§5뮤츠나이트 Y", 0.0025),
        RareDropEntry("mega_showdown:tyranitarite", "§8마기라스나이트", 0.0025),
        RareDropEntry("mega_showdown:salamencite", "§c보만다나이트", 0.0025),
        RareDropEntry("mega_showdown:metagrossite", "§9메타그로스나이트", 0.0025),
        RareDropEntry("mega_showdown:garchompite", "§6한카리아스나이트", 0.0025),
        RareDropEntry("mega_showdown:scizorite", "§c핫삼나이트", 0.0025),
        RareDropEntry("mega_showdown:absolite", "§8앱솔나이트", 0.0025),
        RareDropEntry("mega_showdown:raw_mega_stone", "§7원석 메가스톤", 0.005,
            "가공하면 메가스톤 제작 가능"),

        // --- 테라스탈 관련 ---
        RareDropEntry("mega_showdown:tera_orb", "§e테라 오브", 0.0025,
            "테라스탈 변환에 필요한 핵심 아이템"),
        RareDropEntry("mega_showdown:tera_shard_fire", "§c불꽃 테라 조각", 0.005),
        RareDropEntry("mega_showdown:tera_shard_water", "§9물 테라 조각", 0.005),
        RareDropEntry("mega_showdown:tera_shard_electric", "§e전기 테라 조각", 0.005),
        RareDropEntry("mega_showdown:tera_shard_dragon", "§5드래곤 테라 조각", 0.005),
        RareDropEntry("mega_showdown:tera_shard_stellar", "§6스텔라 테라 조각", 0.0025),

        // --- Z기술 관련 ---
        RareDropEntry("mega_showdown:z_ring", "§eZ링", 0.0025,
            "Z기술 발동에 필요한 핵심 장비"),
        RareDropEntry("mega_showdown:sparkling_stone", "§e빛나는 돌", 0.0025,
            "Z링 제작 재료"),
        RareDropEntry("mega_showdown:normalium_z", "§fZ크리스탈 (노말)", 0.005),
        RareDropEntry("mega_showdown:firium_z", "§cZ크리스탈 (불꽃)", 0.005),
        RareDropEntry("mega_showdown:waterium_z", "§9Z크리스탈 (물)", 0.005),
        RareDropEntry("mega_showdown:electrium_z", "§eZ크리스탈 (전기)", 0.005),
        RareDropEntry("mega_showdown:dragonium_z", "§5Z크리스탈 (드래곤)", 0.005),

        // --- 다이맥스 관련 ---
        RareDropEntry("mega_showdown:dynamax_band", "§cD맥스 밴드", 0.0025,
            "다이맥스 발동에 필요한 핵심 장비"),
        RareDropEntry("mega_showdown:wishing_star", "§e소원의 별", 0.0025,
            "다이맥스 밴드 제작 재료"),
        RareDropEntry("mega_showdown:max_mushroom", "§cM맥스 버섯", 0.005,
            "다이맥스 수프 재료"),

        // --- 원시회귀 관련 ---
        RareDropEntry("mega_showdown:red_orb", "§c붉은 구슬", 0.0025,
            "그란돈 원시회귀"),
        RareDropEntry("mega_showdown:blue_orb", "§9쪽빛 구슬", 0.0025,
            "가이오가 원시회귀"),

        // --- 기타 특수 ---
        RareDropEntry("mega_showdown:adamant_crystal", "§b금강옥", 0.0025,
            "디아루가 오리진폼"),
        RareDropEntry("mega_showdown:lustrous_globe", "§d백옥", 0.0025,
            "펄기아 오리진폼"),
        RareDropEntry("mega_showdown:griseous_core", "§8백금옥", 0.0025,
            "기라티나 오리진폼"),
        RareDropEntry("mega_showdown:omni_ring", "§6오므니 링", 0.001,
            "모든 기믹을 하나로! 최상급 레어")
    ),

    // ========================================================
    // Legendary Monuments 아이템 (namespace: legendarymonuments)
    // ========================================================
    val legendaryMonumentsDrops: List<RareDropEntry> = listOf(
        // --- 핵심 소환 아이템 ---
        RareDropEntry("legendarymonuments:arc_phone", "§6아크 폰", 0.001,
            "8가지 기능이 탑재된 만능 폰. 구조물 탐색 등"),
        RareDropEntry("legendarymonuments:azure_flute", "§b하늘 피리", 0.001,
            "아르세우스 소환에 필요. 천계의 문을 여는 악기"),
        RareDropEntry("legendarymonuments:red_chain", "§c붉은 사슬", 0.0025,
            "디아루가/펄기아/기라티나 소환에 필요"),

        // --- 전설 소환 코어 ---
        RareDropEntry("legendarymonuments:titan_core", "§6타이탄 코어", 0.0025,
            "타이탄 해머/검 제작의 핵심 재료"),
        RareDropEntry("legendarymonuments:ocean_core", "§9오션 코어", 0.0025,
            "가이오가 소환에 필요한 바다의 코어"),
        RareDropEntry("legendarymonuments:earth_core", "§c어스 코어", 0.0025,
            "그란돈 소환에 필요한 대지의 코어"),
        RareDropEntry("legendarymonuments:star_core", "§e스타 코어", 0.0025,
            "레쿠쟈 소환에 필요한 별의 코어"),

        // --- 특수 아이템 ---
        RareDropEntry("legendarymonuments:gs_ball", "§6GS 볼", 0.0025,
            "세레비 소환에 필요한 신비의 볼"),
        RareDropEntry("legendarymonuments:silver_wing", "§f은빛 깃털", 0.0025,
            "루기아 소환에 필요한 신성한 깃털"),
        RareDropEntry("legendarymonuments:rainbow_wing", "§6무지개 깃털", 0.0025,
            "호오 소환에 필요한 찬란한 깃털"),
        RareDropEntry("legendarymonuments:clear_bell", "§e투명 방울", 0.0025,
            "에크루티크 마을 탐색에 필요"),
        RareDropEntry("legendarymonuments:liberty_pass", "§f리버티 패스", 0.0025,
            "비크티니 소환에 필요한 통행증"),
        RareDropEntry("legendarymonuments:emerald_emblem", "§a에메랄드 엠블럼", 0.0025,
            "레쿠쟈 소환 아이템 제작에 필요"),
        RareDropEntry("legendarymonuments:shadow_soul_stone", "§8그림자 영혼석", 0.0025,
            "다크 루기아 변환에 필요"),

        // --- 장비 ---
        RareDropEntry("legendarymonuments:titan_hammer", "§6타이탄 해머", 0.001,
            "타이탄 코어로 제작. 3x3 채굴 + 강력한 차지 공격"),
        RareDropEntry("legendarymonuments:ancient_origin_ball", "§5고대 오리진 볼", 0.0025,
            "왜곡 세계에서 제작 가능한 특수 몬스터볼"),
        RareDropEntry("legendarymonuments:meltan_box", "§7멜탄 상자", 0.0025,
            "멜탄을 유인하는 신비의 상자"),
        RareDropEntry("legendarymonuments:sanctuary_block", "§a성역 블록", 0.001,
            "반경 100블록 폭발 방지 블록")
    ),

    // ========================================================
    // 이벤트별 드롭 배수 설정
    // ========================================================
    val eventTypeMultipliers: Map<String, Double> = mapOf(
        "TEMPORAL_RIFT" to 1.5,          // 시공 균열: 1.5배
        "EXPLORER" to 1.0,               // 대탐험: 기본
        "HUNTING_SEASON" to 2.0,         // 사냥 시즌: 2배 (1등)
        "LEGENDARY_RAID" to 3.0,         // 전설 레이드: 3배
        "LUCKY_EVENT" to 1.0,            // 럭키: 기본
        "ULTRA_WORMHOLE" to 2.5          // 울트라 워프홀: 2.5배
    )
)

data class RareDropEntry(
    val itemId: String,                  // 모드:아이템ID
    val displayName: String,             // 한글 표시명
    val dropChance: Double = 0.0025,     // 드롭 확률 (0.0025 = 0.25%)
    val description: String = ""         // 아이템 설명
)
