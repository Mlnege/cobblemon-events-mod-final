package com.cobblemonevents.client

import com.cobblemonevents.network.payload.EventFxEndPayload
import com.cobblemonevents.network.payload.EventFxPayloads
import com.cobblemonevents.network.payload.EventFxStartPayload
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking

object CobblemonEventsClient : ClientModInitializer {
    override fun onInitializeClient() {
        EventFxPayloads.registerPayloadTypes()

        ClientPlayNetworking.registerGlobalReceiver(EventFxStartPayload.ID) { payload, context ->
            context.client().execute {
                ClientEventFxManager.onStart(
                    eventId = payload.eventId,
                    eventType = payload.eventType,
                    ticksRemaining = payload.ticksRemaining,
                    center = payload.center
                )
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(EventFxEndPayload.ID) { payload, context ->
            context.client().execute {
                ClientEventFxManager.onEnd(payload.eventId)
            }
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            ClientEventFxManager.tick(client)
        }
    }
}
