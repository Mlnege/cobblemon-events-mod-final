package com.cobblemonevents.rewards

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemonevents.CobblemonEventsMod
import com.cobblemonevents.config.*
import com.cobblemonevents.util.BroadcastUtil
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import kotlin.random.Random

object RewardManager {

    private val prefix get() = CobblemonEventsMod.config.prefix
    private const val MASTER_BALL_SPECIAL_MIN_CHANCE = 0.005   // 0.5%
    private const val MASTER_BALL_SPECIAL_MAX_CHANCE = 0.01    // 1.0%
    private const val MASTER_BALL_SPECIAL_BASE_CHANCE = 0.005  // 이벤트 배수 적용 전 기본 0.5%

    // ========================================================
    // 메인 보상 지급 (기본 보상 + 레어 드롭 롤)
    // ========================================================

    fun giveRewards(player: ServerPlayerEntity, rewards: RewardPool, eventDef: EventDefinition) {
        // 1) 기본 보상 지급
        when (rewards.rewardMode.uppercase()) {
            "ALL" -> {
                rewards.pokemon.forEach { givePokemonReward(player, it, rewards.broadcastReward) }
                rewards.items.forEach { giveItemReward(player, it, rewards.broadcastReward) }
            }
            "RANDOM_ONE" -> {
                val all = mutableListOf<Any>()
                all.addAll(rewards.pokemon)
                all.addAll(rewards.items)
                if (all.isNotEmpty()) {
                    when (val chosen = all.random()) {
                        is PokemonRewardEntry -> givePokemonReward(player, chosen, rewards.broadcastReward)
                        is ItemRewardEntry -> giveItemReward(player, chosen, rewards.broadcastReward)
                    }
                }
            }
            "RANDOM_MULTI" -> {
                val all = mutableListOf<Any>()
                all.addAll(rewards.pokemon)
                all.addAll(rewards.items)
                if (all.isNotEmpty()) {
                    val count = rewards.randomCount.coerceAtMost(all.size)
                    all.shuffled().take(count).forEach { item ->
                        when (item) {
                            is PokemonRewardEntry -> givePokemonReward(player, item, rewards.broadcastReward)
                            is ItemRewardEntry -> giveItemReward(player, item, rewards.broadcastReward)
                        }
                    }
                }
            }
        }

        // 2) 레어 드롭 롤! (Mega Showdown + Legendary Monuments)
        rollRareDrops(player, eventDef.eventType)

        BroadcastUtil.sendPersonal(player, "${prefix}§a§l★ 보상이 지급되었습니다!")
    }

    // ========================================================
    // 레어 드롭 시스템 (0.25% 확률)
    // Mega Showdown / Legendary Monuments 아이템
    // ========================================================

    fun rollRareDrops(player: ServerPlayerEntity, eventType: String) {
        val rareConfig = CobblemonEventsMod.config.rareDrops
        if (!rareConfig.enabled) return

        val multiplier = rareConfig.eventTypeMultipliers[eventType] ?: 1.0

        // Mega Showdown 아이템
        for (drop in rareConfig.megaShowdownDrops) {
            val chance = drop.dropChance * multiplier
            if (Random.nextDouble() < chance) {
                if (tryGiveModItem(player, drop)) {
                    announceRareDrop(player, drop, "§d[Mega Showdown]")
                }
            }
        }

        // Legendary Monuments 아이템
        for (drop in rareConfig.legendaryMonumentsDrops) {
            val chance = drop.dropChance * multiplier
            if (Random.nextDouble() < chance) {
                if (tryGiveModItem(player, drop)) {
                    announceRareDrop(player, drop, "§6[Legendary Monuments]")
                }
            }
        }

        // 모든 이벤트 공통: 마스터볼 특별 드롭 (0.5% ~ 1.0%)
        rollSpecialMasterBallDrop(player, multiplier)
    }

    private fun rollSpecialMasterBallDrop(player: ServerPlayerEntity, multiplier: Double) {
        val masterBallChance = (MASTER_BALL_SPECIAL_BASE_CHANCE * multiplier)
            .coerceIn(MASTER_BALL_SPECIAL_MIN_CHANCE, MASTER_BALL_SPECIAL_MAX_CHANCE)
        if (Random.nextDouble() >= masterBallChance) return

        val displayPercent = String.format("%.2f", masterBallChance * 100.0)
        val drop = RareDropEntry(
            itemId = "cobblemon:master_ball",
            displayName = "§d마스터볼",
            dropChance = masterBallChance,
            description = "이벤트 특별 드롭 (${displayPercent}%)"
        )

        if (tryGiveModItem(player, drop)) {
            announceRareDrop(player, drop, "§5[Special]")
        }
    }

    private fun tryGiveModItem(player: ServerPlayerEntity, drop: RareDropEntry): Boolean {
        return try {
            val identifier = Identifier.tryParse(drop.itemId) ?: return false

            val item = Registries.ITEM.get(identifier)

            // air = 등록되지 않은 아이템 (모드 미설치)
            val airId = Identifier.tryParse("minecraft:air")
            if (airId != null) {
                val airItem = Registries.ITEM.get(airId)
                if (item == airItem) {
                    CobblemonEventsMod.LOGGER.debug(
                        "[레어드롭] 모드 아이템 없음: ${drop.itemId} (모드 미설치)"
                    )
                    return false
                }
            }

            val stack = ItemStack(item, 1)
            if (!player.inventory.insertStack(stack)) {
                player.dropItem(stack, false)
                BroadcastUtil.sendPersonal(player, "${prefix}§7(인벤토리 가득 → 바닥 드롭)")
            }

            CobblemonEventsMod.LOGGER.info(
                "[레어드롭] ${player.name.string} ← ${drop.itemId} (${drop.displayName})"
            )
            true
        } catch (e: Exception) {
            CobblemonEventsMod.LOGGER.debug("[레어드롭] 지급 실패: ${drop.itemId} - ${e.message}")
            false
        }
    }

    private fun announceRareDrop(player: ServerPlayerEntity, drop: RareDropEntry, modTag: String) {
        val server = player.server ?: return

        BroadcastUtil.sendPersonal(player, "")
        BroadcastUtil.sendPersonal(player, "${prefix}§6§l✦✦✦ 초레어 아이템 획득!! ✦✦✦")
        BroadcastUtil.sendPersonal(player, "  $modTag ${drop.displayName}")
        if (drop.description.isNotEmpty()) {
            BroadcastUtil.sendPersonal(player, "  §7${drop.description}")
        }
        BroadcastUtil.sendPersonal(player, "")

        BroadcastUtil.broadcast(server, "")
        BroadcastUtil.broadcast(server,
            "${prefix}§6§l✦ RARE DROP! §e${player.name.string}§f님이"
        )
        BroadcastUtil.broadcast(server,
            "  $modTag ${drop.displayName} §f을(를) 획득했습니다!"
        )
        BroadcastUtil.broadcast(server, "")

        player.playSoundToPlayer(
            SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
            SoundCategory.MASTER, 1.0f, 1.5f
        )
    }

    // ========================================================
    // 직접 지급 헬퍼
    // ========================================================

    fun givePokemonDirect(player: ServerPlayerEntity, species: String, level: Int, shinyChance: Double = 0.0) {
        givePokemonReward(player, PokemonRewardEntry(species, level, shinyChance), true)
    }

    fun giveItemDirect(player: ServerPlayerEntity, itemId: String, count: Int) {
        giveItemReward(player, ItemRewardEntry(itemId, count), false)
    }

    // ========================================================
    // 내부 보상 로직
    // ========================================================

    private fun givePokemonReward(player: ServerPlayerEntity, reward: PokemonRewardEntry, broadcast: Boolean) {
        try {
            val properties = PokemonProperties.parse("${reward.species} level=${reward.level}")
            val pokemon = properties.create()

            if (reward.ivMin > 0 || reward.ivMax < 31) {
                pokemon.ivs.forEach { (stat, _) ->
                    pokemon.ivs[stat] = Random.nextInt(reward.ivMin, reward.ivMax + 1)
                }
            }

            val isShiny = Random.nextDouble() < reward.shinyChance
            if (isShiny) pokemon.shiny = true

            if (reward.formAspects.isNotEmpty()) {
                pokemon.forcedAspects = pokemon.forcedAspects + reward.formAspects
            }

            val party = Cobblemon.storage.getParty(player)
            // toGappyList() 대체 코드
            val hasSpace = party.occupied() < party.size()
            if (hasSpace) {
                party.add(pokemon)
            } else {
                Cobblemon.storage.getPC(player).add(pokemon)
                BroadcastUtil.sendPersonal(player, "${prefix}§7(파티 만석 → PC 전송)")
            }

            val shinyText = if (isShiny) "§6✦이로치✦ " else ""
            BroadcastUtil.sendPersonal(player,
                "${prefix}§b포켓몬 획득: ${shinyText}§f${reward.species} §7(Lv.${reward.level})")

            if (broadcast) {
                broadcastToOthers(player,
                    "${prefix}§e${player.name.string}§f님이 ${shinyText}§b${reward.species} §7(Lv.${reward.level})§f 획득!")
            }
        } catch (e: Exception) {
            CobblemonEventsMod.LOGGER.error("[보상] 포켓몬 지급 실패: ${reward.species}", e)
            BroadcastUtil.sendPersonal(player, "${prefix}§c포켓몬 보상 오류.")
        }
    }

    private fun giveItemReward(player: ServerPlayerEntity, reward: ItemRewardEntry, broadcast: Boolean) {
        try {
            val identifier = Identifier.tryParse(reward.itemId) ?: return
            val stack = ItemStack(Registries.ITEM.get(identifier), reward.count)

            if (!player.inventory.insertStack(stack)) {
                player.dropItem(stack, false)
                BroadcastUtil.sendPersonal(player, "${prefix}§7(인벤토리 가득 → 드롭)")
            }

            BroadcastUtil.sendPersonal(player,
                "${prefix}§a아이템 획득: §f${reward.itemId} §7x${reward.count}")

            if (broadcast) {
                broadcastToOthers(player,
                    "${prefix}§e${player.name.string}§f님이 §a${reward.itemId} §7x${reward.count}§f 획득!")
            }
        } catch (e: Exception) {
            CobblemonEventsMod.LOGGER.error("[보상] 아이템 지급 실패: ${reward.itemId}", e)
        }
    }

    private fun broadcastToOthers(player: ServerPlayerEntity, message: String) {
        player.server?.playerManager?.playerList?.forEach { other ->
            if (other.uuid != player.uuid) other.sendMessage(Text.literal(message), false)
        }
    }
}
