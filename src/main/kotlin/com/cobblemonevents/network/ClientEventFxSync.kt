package com.cobblemonevents.network

import com.cobblemonevents.events.ActiveEvent
import com.cobblemonevents.network.payload.EventFxEndPayload
import com.cobblemonevents.network.payload.EventFxStartPayload
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity

object ClientEventFxSync {
    private val FIXED_EVENT_TYPES = setOf(
        "TEMPORAL_RIFT",
        "EXPLORER",
        "HUNTING_SEASON",
        "ULTRA_WORMHOLE",
        "LEGENDARY_RAID",
        "LUCKY_EVENT",
        "GYM_CHALLENGE"
    )

    fun isFixedEventType(eventType: String): Boolean = FIXED_EVENT_TYPES.contains(eventType)

    fun broadcastStart(server: MinecraftServer, event: ActiveEvent) {
        if (!isFixedEventType(event.definition.eventType)) return
        for (player in server.playerManager.playerList) {
            sendStart(player, event)
        }
    }

    fun broadcastEnd(server: MinecraftServer, event: ActiveEvent) {
        if (!isFixedEventType(event.definition.eventType)) return
        for (player in server.playerManager.playerList) {
            sendEnd(player, event.definition.id)
        }
    }

    fun syncPlayer(player: ServerPlayerEntity, event: ActiveEvent) {
        if (!isFixedEventType(event.definition.eventType)) return
        sendStart(player, event)
    }

    private fun sendStart(player: ServerPlayerEntity, event: ActiveEvent) {
        if (!ServerPlayNetworking.canSend(player, EventFxStartPayload.ID)) return
        ServerPlayNetworking.send(
            player,
            EventFxStartPayload(
                eventId = event.definition.id,
                eventType = event.definition.eventType,
                ticksRemaining = event.ticksRemaining,
                center = event.eventLocation
            )
        )
    }

    private fun sendEnd(player: ServerPlayerEntity, eventId: String) {
        if (!ServerPlayNetworking.canSend(player, EventFxEndPayload.ID)) return
        ServerPlayNetworking.send(player, EventFxEndPayload(eventId = eventId))
    }
}
