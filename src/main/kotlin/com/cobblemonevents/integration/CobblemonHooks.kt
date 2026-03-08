package com.cobblemonevents.integration

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemonevents.CobblemonEventsMod
import net.minecraft.server.network.ServerPlayerEntity

/**
 * Cobblemon 이벤트 훅 등록 지점
 *
 * - 포획 이벤트: 스케줄러/랭킹에 포획 기록 반영
 * - 배틀 승리 이벤트: 패배 포켓몬 종족 기록 후 승자 랭킹 반영
 */
object CobblemonHooks {

    fun register() {
        CobblemonEventsMod.LOGGER.info("[Cobblemon Events] Cobblemon 이벤트 훅 등록 시작")

        CobblemonEvents.POKEMON_CAPTURED.subscribe { event ->
            val player = event.player
            val species = event.pokemon.species.name.lowercase()

            if (player is ServerPlayerEntity) {
                CobblemonEventsMod.scheduler.onPokemonCaught(player, species)
                CobblemonEventsMod.rankingManager.addCatch(
                    "_global", player.uuid, player.name.string
                )
            }
        }

        CobblemonEvents.BATTLE_VICTORY.subscribe { event ->
            val defeatedSpecies = mutableSetOf<String>()
            for (loser in event.losers) {
                for (activePokemon in loser.pokemonList) {
                    defeatedSpecies.add(activePokemon.originalPokemon.species.name.lowercase())
                }
            }

            for (winner in event.winners) {
                val playerActor = winner as? PlayerBattleActor ?: continue
                // 일부 전투 컨텍스트에서는 엔티티가 없을 수 있으므로 안전하게 건너뜀
                val entity = playerActor.entity ?: continue
                for (species in defeatedSpecies) {
                    CobblemonEventsMod.scheduler.onBattleWon(entity, species)
                }
                CobblemonEventsMod.rankingManager.recordBattleWin(
                    entity.uuid, entity.name.string
                )
            }
        }

        CobblemonEventsMod.LOGGER.info("[Cobblemon Events] Cobblemon 이벤트 훅 등록 완료")
    }
}
