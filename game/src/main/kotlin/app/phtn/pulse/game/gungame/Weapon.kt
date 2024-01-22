package app.phtn.pulse.game.gungame

import app.phtn.pulse.common.enums.Emoji
import app.phtn.pulse.common.util.Raycast
import app.phtn.pulse.common.util.damageEffect
import app.phtn.pulse.common.util.progressBar
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.particle.Particle
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag
import java.nio.ByteBuffer
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.roundToInt

sealed class Weapon(val name: String, val level: Int) {
    companion object {
        val gunTag = Tag.String("id")
        val ammoTag = Tag.Integer("ammo")
        val lastShotTag = Tag.Long("lastShot")
        val reloadingTag = Tag.Boolean("reloading")
        val levelTag = Tag.Integer("level")
        val shooterTag = Tag.UUID("shooter")

        val registeredMap: Map<String, Weapon>
            get() = Weapon::class.sealedSubclasses.mapNotNull { it.objectInstance }.associateBy { it.name }

        var Player.heldWeapon: Weapon?
            get() = registeredMap[itemInMainHand.getTag(gunTag)]
            set(value) {
                this.itemInMainHand = value?.item ?: ItemStack.AIR
            }
    }

    abstract val cooldown: Long
    abstract val damage: Float
    abstract val ammo: Int
    abstract val material: Material
    // how many bullets to shoot at a time
    abstract val bullets: Int
    abstract val spread: Double
    abstract val maxDistance: Double
    abstract val burstAmount: Int
    abstract val burstInterval: Int

    open val item by lazy {
        ItemStack.builder(material).meta {
            it.displayName(
                Component.text(name).color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false)
            )
            it.set(gunTag, name)
            it.set(lastShotTag, 0)
            it.set(ammoTag, ammo)
            it.set(levelTag, level)
        }.build()
    }

    fun shoot(game: GunGame, player: Player) {
        val instance = player.instance
        val eyePos = player.position.add(0.0, player.eyeHeight, 0.0)
        val eyeDir = player.position.direction()
        val random = ThreadLocalRandom.current()


        // ammo
        val newAmmo: Int = (player.itemInMainHand.meta().getTag(ammoTag) ?: 1) - 1
        player.itemInMainHand = player.itemInMainHand.withMeta {
            it.set(ammoTag, newAmmo)
        }
        player.itemInMainHand = player.itemInMainHand.withMeta {
            it.set(lastShotTag, System.currentTimeMillis())
        }
        renderAmmo(player, newAmmo)

        instance.playSound(
            Sound.sound(SoundEvent.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, Sound.Source.PLAYER, 1f, 2f),
            player.position
        )

        repeat(bullets) {
            var direction = eyeDir
            if (spread > 0.0) {
                direction = direction
                    .rotateAroundX(random.nextDouble(-spread, spread))
                    .rotateAroundY(random.nextDouble(-spread, spread))
                    .rotateAroundZ(random.nextDouble(-spread, spread))
            }

            val raycast = Raycast.raycast(instance, eyePos, direction, maxDistance) {
                it != player && it.entityType == EntityType.PLAYER && (it as? Player)?.gameMode == GameMode.ADVENTURE
            }

            val byteBuffer: ByteBuffer = ByteBuffer.wrap(ByteArray(16))
            byteBuffer.putFloat(1f) // Red
            byteBuffer.putFloat(0f) // Green
            byteBuffer.putFloat(0f) // Blue
            byteBuffer.putFloat(1.0f) // Size

            val packet = ParticlePacket(
                Particle.DUST.id(), false,
                direction.x, direction.y, direction.z,
                0f, 0f, 0f,
                0.0f, 50, byteBuffer.array()
            )

            instance.sendGroupedPacket(
                packet
            )

            if (raycast.hitEntity != null) {
                val hitPlayer: Player = raycast.hitEntity as? Player ?: return

                val headshot = (hitPlayer.position.y + 1.25) < (raycast.hitPosition?.y() ?: 0.0)

                if (headshot) {
                    player.playSound(
                        Sound.sound(SoundEvent.ENTITY_EXPERIENCE_ORB_PICKUP, Sound.Source.BLOCK, 0.5f, 1.3f)
                    )
                } else {
                    player.playSound(
                        Sound.sound(SoundEvent.ENTITY_EXPERIENCE_ORB_PICKUP, Sound.Source.BLOCK, 0.5f, 1f)
                    )
                }

                val damage = (damage * (if (headshot) 1.5f else 1f))

                player.sendActionBar(
                    Component.text("${Emoji.Crosshair} ${damage.roundToInt()}${Emoji.Heart}")
                        .color(NamedTextColor.RED)
                )

                if ((game.spawnProtection[hitPlayer.uuid] ?: 0) < System.currentTimeMillis()) {
                    instance.sendGroupedPacket(damageEffect(hitPlayer.entityId, player.entityId, player.position))
                    hitPlayer.damage(DamageType.PLAYER_ATTACK, damage)
                    game.damagers[hitPlayer.uuid] = player.uuid
                }
            }
        }
    }

    open fun renderAmmo(
        player: Player,
        currentAmmo: Int,
        percentage: Float = currentAmmo.toFloat() / ammo.toFloat(),
        reloading: Boolean = false
    ) {
        val component = Component.text()

        if (reloading) component.append(Component.text("RELOADING ", NamedTextColor.RED, TextDecoration.BOLD))

        component.append(
            progressBar(
                percentage,
                40,
                "|",
                if (reloading) NamedTextColor.RED else NamedTextColor.GOLD,
                NamedTextColor.DARK_GRAY
            )
        )

        component.append(
            Component.text(
                " ${String.format("%0${ammo.toString().length}d", currentAmmo)}/$ammo",
                NamedTextColor.DARK_GRAY
            )
        )

        player.sendActionBar(component)
    }

}
