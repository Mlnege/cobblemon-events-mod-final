package com.cobblemonevents.rewards

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemonevents.CobblemonEventsMod
import com.cobblemonevents.config.*
import com.cobblemonevents.events.ActiveEvent
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
    private const val MASTER_BALL_SPECIAL_BASE_CHANCE = 0.005

    // ========================================================
    // 시간 기반 보상 배수 계산 / Time-based reward multiplier
    // 빠른 완료 → 더 많은 보상 / Faster completion → more rewards
    // 공식: 1.0 + (남은 시간 비율) × 1.5
    // ========================================================

    fun calcTimeMultiplier(event: ActiveEvent): Double {
        val totalTicks = event.definition.durationMinutes * 60L * 20L
        if (totalTicks <= 0) return 1.0
        val fraction = event.ticksRemaining.toDouble() / totalTicks.toDouble()
        return (1.0 + fraction * 1.5).coerceIn(1.0, 2.5)
    }

    // ========================================================
    // 메인 보상 지급 / Main reward distribution
    // ========================================================

    fun giveRewards(player: ServerPlayerEntity, rewards: RewardPool, eventDef: EventDefinition, timeMultiplier: Double = 1.0) {
        when (rewards.rewardMode.uppercase()) {
            "ALL" -> {
                rewards.pokemon.forEach { givePokemonReward(player, it, rewards.broadcastReward) }
                rewards.items.forEach { giveItemReward(player, it, rewards.broadcastReward, timeMultiplier) }
            }
            "RANDOM_ONE" -> {
                val all = mutableListOf<Any>()
                all.addAll(rewards.pokemon)
                all.addAll(rewards.items)
                if (all.isNotEmpty()) {
                    when (val chosen = all.random()) {
                        is PokemonRewardEntry -> givePokemonReward(player, chosen, rewards.broadcastReward)
                        is ItemRewardEntry -> giveItemReward(player, chosen, rewards.broadcastReward, timeMultiplier)
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
                            is ItemRewardEntry -> giveItemReward(player, item, rewards.broadcastReward, timeMultiplier)
                        }
                    }
                }
            }
        }

        rollRareDrops(player, eventDef.eventType)

        if (timeMultiplier > 1.05) {
            val bonusPct = ((timeMultiplier - 1.0) * 100).toInt()
            BroadcastUtil.sendPersonal(player, "${prefix}§e⚡ 빠른 완료 보너스 +${bonusPct}%! / Speed Bonus +${bonusPct}%!")
        }

        BroadcastUtil.sendPersonal(player, "${prefix}§a§l★ 보상이 지급되었습니다! / Rewards granted!")
    }

    /** 이벤트 인스턴스로부터 시간 배수를 자동 계산하여 보상 지급 */
    fun giveRewardsWithEvent(player: ServerPlayerEntity, rewards: RewardPool, event: ActiveEvent) {
        giveRewards(player, rewards, event.definition, calcTimeMultiplier(event))
    }

    // ========================================================
    // 레어 드롭 시스템 / Rare drop system (0.25% chance)
    // ========================================================

    fun rollRareDrops(player: ServerPlayerEntity, eventType: String) {
        val rareConfig = CobblemonEventsMod.config.rareDrops
        if (!rareConfig.enabled) return

        val multiplier = rareConfig.eventTypeMultipliers[eventType] ?: 1.0

        for (drop in rareConfig.megaShowdownDrops) {
            val chance = drop.dropChance * multiplier
            if (Random.nextDouble() < chance) {
                if (tryGiveModItem(player, drop)) {
                    announceRareDrop(player, drop, "§d[Mega Showdown]")
                }
            }
        }

        for (drop in rareConfig.legendaryMonumentsDrops) {
            val chance = drop.dropChance * multiplier
            if (Random.nextDouble() < chance) {
                if (tryGiveModItem(player, drop)) {
                    announceRareDrop(player, drop, "§6[Legendary Monuments]")
                }
            }
        }

        rollSpecialMasterBallDrop(player, multiplier)
    }

    private fun rollSpecialMasterBallDrop(player: ServerPlayerEntity, multiplier: Double) {
        val masterBallChance = (MASTER_BALL_SPECIAL_BASE_CHANCE * multiplier)
            .coerceIn(MASTER_BALL_SPECIAL_MIN_CHANCE, MASTER_BALL_SPECIAL_MAX_CHANCE)
        if (Random.nextDouble() >= masterBallChance) return

        val displayPercent = String.format("%.2f", masterBallChance * 100.0)
        val drop = RareDropEntry(
            itemId = "cobblemon:master_ball",
            displayName = "§d마스터볼 / Master Ball",
            dropChance = masterBallChance,
            description = "이벤트 특별 드롭 / Event special drop (${displayPercent}%)"
        )

        if (tryGiveModItem(player, drop)) {
            announceRareDrop(player, drop, "§5[Special]")
        }
    }

    private fun tryGiveModItem(player: ServerPlayerEntity, drop: RareDropEntry): Boolean {
        return try {
            val identifier = Identifier.tryParse(drop.itemId) ?: return false
            val item = Registries.ITEM.get(identifier)

            val airId = Identifier.tryParse("minecraft:air")
            if (airId != null) {
                val airItem = Registries.ITEM.get(airId)
                if (item == airItem) {
                    CobblemonEventsMod.LOGGER.debug(
                        "[레어드롭/RareDrop] 모드 아이템 없음 / Item not found: ${drop.itemId}"
                    )
                    return false
                }
            }

            val stack = ItemStack(item, 1)
            if (!player.inventory.insertStack(stack)) {
                player.dropItem(stack, false)
                BroadcastUtil.sendPersonal(player, "${prefix}§7(인벤토리 가득 → 바닥 드롭 / Inventory full → dropped)")
            }

            CobblemonEventsMod.LOGGER.info(
                "[레어드롭/RareDrop] ${player.name.string} ← ${drop.itemId} (${drop.displayName})"
            )
            true
        } catch (e: Exception) {
            CobblemonEventsMod.LOGGER.debug("[레어드롭/RareDrop] 지급 실패 / Grant failed: ${drop.itemId} - ${e.message}")
            false
        }
    }

    private fun announceRareDrop(player: ServerPlayerEntity, drop: RareDropEntry, modTag: String) {
        val server = player.server ?: return

        BroadcastUtil.sendPersonal(player, "")
        BroadcastUtil.sendPersonal(player, "${prefix}§6§l✦✦✦ 초레어 아이템 획득!! / Ultra Rare Drop!! ✦✦✦")
        BroadcastUtil.sendPersonal(player, "  $modTag ${drop.displayName}")
        if (drop.description.isNotEmpty()) {
            BroadcastUtil.sendPersonal(player, "  §7${drop.description}")
        }
        BroadcastUtil.sendPersonal(player, "")

        BroadcastUtil.broadcast(server, "")
        BroadcastUtil.broadcast(server,
            "${prefix}§6§l✦ RARE DROP! §e${player.name.string}§f님이 / obtained"
        )
        BroadcastUtil.broadcast(server,
            "  $modTag ${drop.displayName} §f을(를) 획득했습니다! / acquired!"
        )
        BroadcastUtil.broadcast(server, "")

        player.playSoundToPlayer(
            SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
            SoundCategory.MASTER, 1.0f, 1.5f
        )
    }

    // ========================================================
    // 직접 지급 헬퍼 / Direct grant helpers
    // ========================================================

    fun givePokemonDirect(player: ServerPlayerEntity, species: String, level: Int, shinyChance: Double = 0.0) {
        givePokemonReward(player, PokemonRewardEntry(species, level, shinyChance), true)
    }

    fun giveItemDirect(player: ServerPlayerEntity, itemId: String, count: Int) {
        giveItemReward(player, ItemRewardEntry(itemId, count), false)
    }

    // ========================================================
    // 내부 보상 로직 / Internal reward logic
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
            val hasSpace = party.occupied() < party.size()
            if (hasSpace) {
                party.add(pokemon)
            } else {
                Cobblemon.storage.getPC(player).add(pokemon)
                BroadcastUtil.sendPersonal(player, "${prefix}§7(파티 만석 → PC 전송 / Party full → sent to PC)")
            }

            val shinyText = if (isShiny) "§6✦이로치 / Shiny✦ " else ""
            BroadcastUtil.sendPersonal(player,
                "${prefix}§b포켓몬 획득 / Pokémon received: ${shinyText}§f${reward.species} §7(Lv.${reward.level})")

            if (broadcast) {
                broadcastToOthers(player,
                    "${prefix}§e${player.name.string}§f님이 ${shinyText}§b${reward.species} §7(Lv.${reward.level})§f 획득!")
            }
        } catch (e: Exception) {
            CobblemonEventsMod.LOGGER.error("[보상/Reward] 포켓몬 지급 실패 / Pokémon grant failed: ${reward.species}", e)
            BroadcastUtil.sendPersonal(player, "${prefix}§c포켓몬 보상 오류 / Pokémon reward error.")
        }
    }

    private fun giveItemReward(player: ServerPlayerEntity, reward: ItemRewardEntry, broadcast: Boolean, timeMultiplier: Double = 1.0) {
        try {
            val identifier = Identifier.tryParse(reward.itemId) ?: return
            val scaledCount = (reward.count * timeMultiplier).toInt().coerceAtLeast(reward.count)
            val stack = ItemStack(Registries.ITEM.get(identifier), scaledCount)

            if (!player.inventory.insertStack(stack)) {
                player.dropItem(stack, false)
                BroadcastUtil.sendPersonal(player, "${prefix}§7(인벤토리 가득 → 드롭 / Inventory full → dropped)")
            }

            BroadcastUtil.sendPersonal(player,
                "${prefix}§a아이템 획득 / Item received: §f${reward.itemId} §7x${scaledCount}")

            if (broadcast) {
                broadcastToOthers(player,
                    "${prefix}§e${player.name.string}§f님이 §a${reward.itemId} §7x${scaledCount}§f 획득!")
            }
        } catch (e: Exception) {
            CobblemonEventsMod.LOGGER.error("[보상/Reward] 아이템 지급 실패 / Item grant failed: ${reward.itemId}", e)
        }
    }

    private fun broadcastToOthers(player: ServerPlayerEntity, message: String) {
        player.server?.playerManager?.playerList?.forEach { other ->
            if (other.uuid != player.uuid) other.sendMessage(Text.literal(message), false)
        }
    }
}
