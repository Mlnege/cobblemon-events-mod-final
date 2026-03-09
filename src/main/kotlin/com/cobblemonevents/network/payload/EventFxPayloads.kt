package com.cobblemonevents.network.payload

import com.cobblemonevents.CobblemonEventsMod
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos

object EventFxPayloads {
    @Volatile
    private var registered = false

    fun registerPayloadTypes() {
        if (registered) return
        registered = true
        PayloadTypeRegistry.playS2C().register(EventFxStartPayload.ID, EventFxStartPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(EventFxEndPayload.ID, EventFxEndPayload.CODEC)
    }
}

data class EventFxStartPayload(
    val eventId: String,
    val eventType: String,
    val ticksRemaining: Long,
    val center: BlockPos?
) : CustomPayload {
    override fun getId(): CustomPayload.Id<out CustomPayload> = ID

    private fun write(buf: RegistryByteBuf) {
        buf.writeString(eventId)
        buf.writeString(eventType)
        buf.writeVarLong(ticksRemaining)
        buf.writeBoolean(center != null)
        if (center != null) {
            buf.writeBlockPos(center)
        }
    }

    companion object {
        val ID: CustomPayload.Id<EventFxStartPayload> =
            CustomPayload.Id(Identifier.of(CobblemonEventsMod.MOD_ID, "event_fx_start"))

        val CODEC: PacketCodec<RegistryByteBuf, EventFxStartPayload> =
            CustomPayload.codecOf(EventFxStartPayload::write, ::read)

        private fun read(buf: RegistryByteBuf): EventFxStartPayload {
            val eventId = buf.readString()
            val eventType = buf.readString()
            val ticksRemaining = buf.readVarLong()
            val hasCenter = buf.readBoolean()
            val center = if (hasCenter) buf.readBlockPos() else null
            return EventFxStartPayload(
                eventId = eventId,
                eventType = eventType,
                ticksRemaining = ticksRemaining,
                center = center
            )
        }
    }
}

data class EventFxEndPayload(
    val eventId: String
) : CustomPayload {
    override fun getId(): CustomPayload.Id<out CustomPayload> = ID

    private fun write(buf: RegistryByteBuf) {
        buf.writeString(eventId)
    }

    companion object {
        val ID: CustomPayload.Id<EventFxEndPayload> =
            CustomPayload.Id(Identifier.of(CobblemonEventsMod.MOD_ID, "event_fx_end"))

        val CODEC: PacketCodec<RegistryByteBuf, EventFxEndPayload> =
            CustomPayload.codecOf(EventFxEndPayload::write, ::read)

        private fun read(buf: RegistryByteBuf): EventFxEndPayload {
            return EventFxEndPayload(eventId = buf.readString())
        }
    }
}
