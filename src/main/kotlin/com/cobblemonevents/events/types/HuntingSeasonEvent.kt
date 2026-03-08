package com.cobblemonevents.events.types

import com.cobblemonevents.CobblemonEventsMod
import com.cobblemonevents.events.ActiveEvent
import com.cobblemonevents.events.EventHandler
import com.cobblemonevents.rewards.RewardManager
import com.cobblemonevents.util.BroadcastUtil
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity

/**
 * 🏹 포켓몬 사냥 시즌
 */
class HuntingSeasonEvent : EventHandler {

    override fun onStart(event: ActiveEvent, server: MinecraftServer) {
        val config = event.definition.huntingConfig ?: return
        val targetPokemon = config.pokemonPool.random()
        event.setData("targetPokemon", targetPokemon)

        CobblemonEventsMod.rankingManager.initEventRanking(event.definition.id)

        BroadcastUtil.announceHunting(server, targetPokemon, event.definition.durationMinutes)
    }

    override fun onTick(event: ActiveEvent, server: MinecraftServer) {
        if (event.ticksRemaining > 0 && event.ticksRemaining % (20 * 300) == 0L) {
            val top3 = CobblemonEventsMod.rankingManager.getTopN(event.definition.id, 3)
            val targetPokemon = event.getData<String>("targetPokemon") ?: "???"

            if (top3.isNotEmpty()) {
                BroadcastUtil.broadcast(server,
                    "${CobblemonEventsMod.config.prefix}§e🏹 사냥 시즌 중간 순위 §7(대상: §e$targetPokemon§7)"
                )
                val medals = listOf("§6🥇", "§f🥈", "§c🥉")
                for ((index, entry) in top3.withIndex()) {
                    BroadcastUtil.broadcast(server,
                        "  ${medals[index]} §f${entry.first} §7- §e${entry.second}마리"
                    )
                }
                BroadcastUtil.broadcast(server,
                    "  §7남은 시간: §f${event.getRemainingMinutes()}분"
                )
            }
        }

        if (event.ticksRemaining % (20 * 60) == 0L) {
            for (player in server.playerManager.playerList) {
                val (rank, count) = CobblemonEventsMod.rankingManager.getPlayerRank(
                    event.definition.id, player.uuid
                )
                if (count > 0) {
                    BroadcastUtil.sendProgress(player,
                        "§c🏹 사냥 시즌 - 내 순위: §f${rank}위 §7(${count}마리) §7| 남은 시간: §f${event.getRemainingMinutes()}분"
                    )
                }
            }
        }
    }

    override fun onEnd(event: ActiveEvent, server: MinecraftServer) {
        val config = event.definition.huntingConfig ?: return
        val targetPokemon = event.getData<String>("targetPokemon") ?: "???"
        val rankings = CobblemonEventsMod.rankingManager.getTopN(event.definition.id, 10)

        BroadcastUtil.announceEventEnd(server, event.definition.displayName, listOf(
            "§7대상 포켓몬: §e$targetPokemon",
            "§7참가자: §f${event.participants.size}명",
            "§7총 포획: §e${event.participants.values.sum()}마리"
        ))

        if (rankings.isNotEmpty()) {
            val rankingTriples = rankings.map { Triple(it.first, it.second, it.third.toString()) }
            BroadcastUtil.announceRanking(server, rankingTriples)
        }

        val rewardPools = listOf(config.top1Rewards, config.top2Rewards, config.top3Rewards)
        for ((index, entry) in rankings.take(3).withIndex()) {
            val (playerName, count, uuid) = entry
            val player = server.playerManager.getPlayer(uuid) ?: continue

            val rewards = rewardPools.getOrNull(index) ?: continue

            if (index == 0) {
                val shinyReward = rewards.pokemon.firstOrNull()
                if (shinyReward != null && shinyReward.species.isEmpty()) {
                    val modifiedRewards = rewards.copy(
                        pokemon = listOf(shinyReward.copy(species = targetPokemon, shinyChance = 1.0))
                    )
                    RewardManager.giveRewards(player, modifiedRewards, event.definition)
                } else {
                    RewardManager.giveRewards(player, rewards, event.definition)
                }
            } else {
                RewardManager.giveRewards(player, rewards, event.definition)
            }

            val medal = when(index) { 0 -> "🥇"; 1 -> "🥈"; else -> "🥉" }
            BroadcastUtil.broadcast(server,
                "${CobblemonEventsMod.config.prefix}$medal §e$playerName§f님에게 ${index + 1}등 보상 지급!"
            )
            CobblemonEventsMod.rankingManager.recordEventComplete(uuid, playerName)
        }

        for ((uuid, count) in event.participants) {
            if (count <= 0) continue
            val player = server.playerManager.getPlayer(uuid) ?: continue
            val rank = rankings.indexOfFirst { it.third == uuid }
            if (rank in 0..2) continue

            RewardManager.giveRewards(player, config.participationRewards, event.definition)
            BroadcastUtil.sendPersonal(player,
                "${CobblemonEventsMod.config.prefix}§a참가 보상이 지급되었습니다!"
            )
        }

        CobblemonEventsMod.rankingManager.clearEventRanking(event.definition.id)
    }

    override fun onPokemonCaught(event: ActiveEvent, player: ServerPlayerEntity, species: String) {
        val targetPokemon = event.getData<String>("targetPokemon") ?: return
        if (!species.equals(targetPokemon, ignoreCase = true)) return

        val count = CobblemonEventsMod.rankingManager.addCatch(
            event.definition.id, player.uuid, player.name.string
        )
        event.addProgress(player.uuid)

        val (rank, _) = CobblemonEventsMod.rankingManager.getPlayerRank(event.definition.id, player.uuid)

        BroadcastUtil.sendProgress(player,
            "§c🏹 $targetPokemon 포획! §f${count}마리 §7(현재 ${rank}위)"
        )

        if (count % 5 == 0) {
            BroadcastUtil.broadcast(
                player.server!!,
                "${CobblemonEventsMod.config.prefix}§e${player.name.string}§7님이 §f$targetPokemon §e${count}마리§7를 포획! §7(현재 ${rank}위)"
            )
        }
    }
}
