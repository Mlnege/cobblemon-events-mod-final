package com.cobblemonevents.client

import net.minecraft.client.MinecraftClient
import net.minecraft.particle.ParticleTypes
import net.minecraft.util.math.BlockPos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object ClientEventFxManager {
    private data class ActiveFx(
        val eventType: String,
        val center: BlockPos?,
        var ticksRemaining: Long
    )

    private val activeEffects = mutableMapOf<String, ActiveFx>()
    private var tickCounter = 0L

    fun onStart(eventId: String, eventType: String, ticksRemaining: Long, center: BlockPos?) {
        activeEffects[eventId] = ActiveFx(
            eventType = eventType,
            center = center,
            ticksRemaining = ticksRemaining.coerceAtLeast(20L)
        )
    }

    fun onEnd(eventId: String) {
        activeEffects.remove(eventId)
    }

    fun tick(client: MinecraftClient) {
        val world = client.world ?: return
        val player = client.player ?: return
        tickCounter++

        val iterator = activeEffects.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val fx = entry.value
            fx.ticksRemaining--
            if (fx.ticksRemaining <= 0L) {
                iterator.remove()
            }
        }

        if (activeEffects.isEmpty() || tickCounter % 2L != 0L) return

        for ((_, fx) in activeEffects) {
            val center = fx.center ?: player.blockPos
            if (fx.center != null) {
                val distSq = player.squaredDistanceTo(center.x + 0.5, center.y + 0.5, center.z + 0.5)
                if (distSq > 220.0 * 220.0) continue
            }
            spawnByType(client, fx.eventType, center)
        }
    }

    private fun spawnByType(client: MinecraftClient, eventType: String, center: BlockPos) {
        when (eventType) {
            "EXPLORER" -> {
                spawnAura(client, center, ParticleTypes.HAPPY_VILLAGER, 8, 2.4, 0.02)
                spawnAura(client, center, ParticleTypes.ENCHANT, 6, 2.0, 0.01)
            }
            "HUNTING_SEASON" -> {
                spawnAura(client, center, ParticleTypes.CRIT, 9, 2.8, 0.03)
                spawnAura(client, center, ParticleTypes.ENCHANT, 6, 2.4, 0.01)
            }
            "ULTRA_WORMHOLE" -> {
                spawnSpiral(client, center, ParticleTypes.REVERSE_PORTAL, 14, 2.0)
                spawnAura(client, center, ParticleTypes.END_ROD, 8, 2.2, 0.0)
            }
            "LEGENDARY_RAID" -> {
                spawnAura(client, center, ParticleTypes.ELECTRIC_SPARK, 10, 3.0, 0.01)
                spawnAura(client, center, ParticleTypes.FLAME, 8, 2.8, 0.01)
                spawnAura(client, center, ParticleTypes.END_ROD, 5, 2.2, 0.0)
            }
            "LUCKY_EVENT" -> {
                spawnAura(client, center, ParticleTypes.TOTEM_OF_UNDYING, 7, 1.8, 0.0)
                spawnAura(client, center, ParticleTypes.HAPPY_VILLAGER, 9, 2.0, 0.01)
            }
            "GYM_CHALLENGE" -> {
                spawnAura(client, center, ParticleTypes.SWEEP_ATTACK, 8, 2.0, 0.0)
                spawnAura(client, center, ParticleTypes.ENCHANT, 8, 2.4, 0.01)
            }
            "TEMPORAL_RIFT" -> {
                spawnSpiral(client, center, ParticleTypes.REVERSE_PORTAL, 12, 1.9)
                spawnAura(client, center, ParticleTypes.SOUL_FIRE_FLAME, 10, 2.0, 0.0)
            }
            else -> {
                spawnAura(client, center, ParticleTypes.END_ROD, 6, 2.0, 0.0)
            }
        }
    }

    private fun spawnAura(
        client: MinecraftClient,
        center: BlockPos,
        particle: net.minecraft.particle.ParticleEffect,
        count: Int,
        radius: Double,
        velocityY: Double
    ) {
        val world = client.world ?: return
        repeat(count) {
            val angle = Random.nextDouble(0.0, Math.PI * 2.0)
            val r = Random.nextDouble(0.2, radius)
            val x = center.x + 0.5 + cos(angle) * r
            val y = center.y + 1.0 + Random.nextDouble(0.0, 2.4)
            val z = center.z + 0.5 + sin(angle) * r
            world.addParticle(particle, x, y, z, 0.0, velocityY, 0.0)
        }
    }

    private fun spawnSpiral(
        client: MinecraftClient,
        center: BlockPos,
        particle: net.minecraft.particle.ParticleEffect,
        count: Int,
        radius: Double
    ) {
        val world = client.world ?: return
        val base = (tickCounter % 360L).toDouble() * 0.045
        repeat(count) { i ->
            val angle = base + (i * (Math.PI / 5.5))
            val r = radius + (i % 4) * 0.12
            val x = center.x + 0.5 + cos(angle) * r
            val y = center.y + 0.9 + (i % 6) * 0.18
            val z = center.z + 0.5 + sin(angle) * r
            world.addParticle(particle, x, y, z, 0.0, 0.01, 0.0)
        }
    }
}

