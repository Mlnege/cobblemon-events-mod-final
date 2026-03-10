package com.cobblemonevents.events.types

import com.cobblemon.mod.common.Cobblemon
import com.cobblemonevents.CobblemonEventsMod
import com.cobblemonevents.config.LuckyEffectEntry
import com.cobblemonevents.events.ActiveEvent
import com.cobblemonevents.events.EventHandler
import com.cobblemonevents.rewards.RewardManager
import com.cobblemonevents.util.BroadcastUtil
import com.cobblemonevents.util.SpawnHelper
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import kotlin.random.Random

/**
 * 🎰 럭키 이벤트
 */
class LuckyEvent : EventHandler {

    override fun onStart(event: ActiveEvent, server: MinecraftServer) {
        val config = event.definition.luckyConfig ?: return
        val effects = config.effects
        if (effects.isEmpty()) return

        val selectedEffect = effects.random()
        event.setData("selectedEffect", selectedEffect)
        applyEffect(selectedEffect, event, server)

        BroadcastUtil.announceLucky(
            server,
            selectedEffect.displayName,
            event.definition.durationMinutes
        )
    }

    override fun onTick(event: ActiveEvent, server: MinecraftServer) {
        val selectedEffect = event.getData<LuckyEffectEntry>("selectedEffect") ?: return

        if (event.ticksRemaining > 0 && event.ticksRemaining % (20 * 60) == 0L) {
            BroadcastUtil.broadcast(server,
                "${CobblemonEventsMod.config.prefix}§e🎰 럭키 효과 지속 중 / Lucky effect active: ${selectedEffect.displayName} " +
                        "§7(남은 / Remaining: §f${event.getRemainingMinutes()}분 / min§7)"
            )
        }
    }

    override fun onEnd(event: ActiveEvent, server: MinecraftServer) {
        val selectedEffect = event.getData<LuckyEffectEntry>("selectedEffect") ?: return
        removeEffect(selectedEffect)

        BroadcastUtil.announceEventEnd(server, event.definition.displayName, listOf(
            "§7효과 / Effect: ${selectedEffect.displayName}",
            "§7럭키 효과가 종료되었습니다. / Lucky effect has ended."
        ))
    }

    private fun applyEffect(effect: LuckyEffectEntry, event: ActiveEvent, server: MinecraftServer) {
        val config = CobblemonEventsMod.config

        when (effect.effectType) {
            "shiny_boost" -> {
                config.globalShinyBoost = effect.value
                CobblemonEventsMod.LOGGER.info("[럭키] 이로치 확률 ${effect.value}배 적용")
            }
            "exp_boost" -> {
                config.globalExpBoost = effect.value
                CobblemonEventsMod.LOGGER.info("[럭키] 경험치 ${effect.value}배 적용")
            }
            "catch_boost" -> {
                config.globalCatchBoost = effect.value
                CobblemonEventsMod.LOGGER.info("[럭키] 포획률 ${effect.value}배 적용")
            }
            "random_shiny" -> {
                val players = server.playerManager.playerList
                if (players.isNotEmpty()) {
                    val luckyPlayer = players.random()
                    giveRandomShiny(luckyPlayer)
                    BroadcastUtil.broadcast(server,
                        "${CobblemonEventsMod.config.prefix}§6✨ §e${luckyPlayer.name.string}§f님에게 이로치 포켓몬이 지급되었습니다! / A Shiny Pokémon was given to ${luckyPlayer.name.string}!"
                    )
                }
            }
            "random_legendary" -> {
                val players = server.playerManager.playerList
                if (players.isNotEmpty()) {
                    val luckyPlayer = players.random()
                    val legends = listOf("moltres", "zapdos", "articuno", "raikou", "entei", "suicune", "latias", "latios")
                    val species = legends.random()
                    val level = Random.nextInt(50, 80)

                    val pos = SpawnHelper.findSafeLocationNearPlayer(luckyPlayer, 30)
                    if (pos != null) {
                        SpawnHelper.spawnPokemon(
                            luckyPlayer.serverWorld, species, pos, level
                        )
                        BroadcastUtil.broadcast(server,
                            "${CobblemonEventsMod.config.prefix}§d✨ §e${luckyPlayer.name.string}§f님 주변에 전설의 포켓몬이 나타났습니다! / A Legendary Pokémon appeared near ${luckyPlayer.name.string}!"
                        )
                    }
                }
            }
            "level_boost" -> {
                val boost = effect.value.toInt()
                for (player in server.playerManager.playerList) {
                    try {
                        val party = Cobblemon.storage.getParty(player)
                        val lead = party.firstOrNull()
                        if (lead != null) {
                            val newLevel = (lead.level + boost).coerceAtMost(100)
                            lead.level = newLevel
                            BroadcastUtil.sendPersonal(player,
                                "${CobblemonEventsMod.config.prefix}§b✨ 선두 포켓몬 레벨이 +${boost}되었습니다! / Lead Pokémon level +${boost}! §7(Lv.$newLevel)"
                            )
                        }
                    } catch (e: Exception) {
                        CobblemonEventsMod.LOGGER.error("[럭키] 레벨 부스트 실패: ${player.name.string}", e)
                    }
                }
            }
            "item_rain" -> {
                val randomItems = listOf(
                    "cobblemon:ultra_ball" to 5,
                    "cobblemon:rare_candy" to 2,
                    "cobblemon:exp_candy_l" to 3,
                    "minecraft:diamond" to 3,
                    "minecraft:golden_apple" to 2,
                    "cobblemon:poke_ball" to 20,
                    "cobblemon:great_ball" to 10
                )
                for (player in server.playerManager.playerList) {
                    val (itemId, count) = randomItems.random()
                    RewardManager.giveItemDirect(player, itemId, count)
                    BroadcastUtil.sendPersonal(player,
                        "${CobblemonEventsMod.config.prefix}§6✨ 하늘에서 아이템이 떨어졌습니다! / Items fell from the sky!"
                    )
                }
            }
            "spawn_boost" -> {
                CobblemonEventsMod.LOGGER.info("[럭키] 스폰율 ${effect.value}배 적용")
            }
        }
    }

    private fun removeEffect(effect: LuckyEffectEntry) {
        val config = CobblemonEventsMod.config
        when (effect.effectType) {
            "shiny_boost" -> config.globalShinyBoost = 1.0
            "exp_boost" -> config.globalExpBoost = 1.0
            "catch_boost" -> config.globalCatchBoost = 1.0
            "spawn_boost" -> { /* 리셋 */ }
        }
    }

    private fun giveRandomShiny(player: ServerPlayerEntity) {
        val starters = listOf(
            "pikachu", "eevee", "charmander", "bulbasaur", "squirtle",
            "cyndaquil", "totodile", "chikorita", "mudkip", "treecko",
            "torchic", "piplup", "turtwig", "chimchar", "froakie"
        )
        val species = starters.random()
        val level = Random.nextInt(15, 35)

        RewardManager.givePokemonDirect(player, species, level, shinyChance = 1.0)
    }
}
