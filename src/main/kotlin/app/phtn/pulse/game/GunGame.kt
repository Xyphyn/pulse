package app.phtn.pulse.game

import app.phtn.pulse.Main
import app.phtn.pulse.enums.Emoji
import app.phtn.pulse.game.Weapon.Companion.ammoTag
import app.phtn.pulse.game.Weapon.Companion.heldWeapon
import app.phtn.pulse.game.Weapon.Companion.lastShotTag
import app.phtn.pulse.game.event.PlayerEliminateEvent
import app.phtn.pulse.util.Raycast
import app.phtn.pulse.util.RaycastResultType
import app.phtn.pulse.util.damageEffect
import app.phtn.pulse.util.progressBar
import app.phtn.pulse.uuidToPlayer
import app.phtn.pulse.uuidsToPlayers
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.event.player.PlayerChangeHeldSlotEvent
import net.minestom.server.event.player.PlayerDeathEvent
import net.minestom.server.event.player.PlayerHandAnimationEvent
import net.minestom.server.event.player.PlayerRespawnEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.event.trait.InstanceEvent
import net.minestom.server.event.trait.PlayerEvent
import net.minestom.server.instance.AnvilLoader
import net.minestom.server.instance.Instance
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.particle.Particle
import net.minestom.server.particle.ParticleCreator
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import net.minestom.server.utils.time.TimeUnit
import java.nio.ByteBuffer
import java.nio.file.Path
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.function.Supplier
import kotlin.math.floor
import kotlin.math.roundToInt

class GunGame(
    override val players: MutableSet<UUID>,
) : Game {
    override val gameMode = GameMode.ADVENTURE
    override var instance: InstanceContainer = Main.instanceManager.createInstanceContainer(Main.default)
    override val spectators: MutableSet<UUID> = mutableSetOf()

    val damagers: MutableMap<UUID, UUID> = mutableMapOf()

    val spawnProtection: MutableMap<UUID, Long> = mutableMapOf()
    // key is the player being damaged, value is the damager
    private val gameRunning = true
    private val spawn = Pos(0.0, 64.0, 0.0)

    private val burstTasks: MutableMap<UUID, Task> = ConcurrentHashMap<UUID, Task>()
    private val reloadTasks: MutableMap<UUID, Task> = ConcurrentHashMap<UUID, Task>()

    init {
        val url = this::class.java.getResource("/beach") ?: throw Error("oops")
        val path = Path.of(url.toURI())

        instance.chunkLoader = AnvilLoader(path)
        instance.timeRate = 0

        val players = uuidsToPlayers(players.toList())

        transferPlayers(spawn)

        players.forEach {
            it.inventory.setItemStack(0, GunProgression.entries.first().weapon.item)
            spawnProtection[it.uuid] = System.currentTimeMillis() + (0 * 1000)
        }

        instance.eventNode().addListener(PlayerUseItemEvent::class.java) { it ->
            val player = it.player
            it.isCancelled = true
            if ((spawnProtection[player.uuid] ?: 0) > System.currentTimeMillis()) {
                player.sendActionBar(
                    Component.text(
                        "You have spawn protection for ${
                            ((spawnProtection[player.uuid] ?: 0) - System.currentTimeMillis() / 1000).toInt()
                        } seconds"
                    )
                )
                return@addListener
            }
            if (it.hand != Player.Hand.MAIN) return@addListener

            val gun = it.player.heldWeapon ?: return@addListener
            if (gun.item.hasTag(Weapon.reloadingTag)) return@addListener

            val lastShot = it.player.itemInMainHand.meta().getTag(Weapon.lastShotTag) ?: return@addListener
            if (lastShot > System.currentTimeMillis() - gun.cooldown) return@addListener

            val taskSchedule = if (gun.burstInterval / 50 <= 0) TaskSchedule.immediate() else TaskSchedule.tick(gun.burstInterval / 50)

            burstTasks[player.uuid] = player.scheduler().submitTask(object : Supplier<TaskSchedule> {
                var burst = player.heldWeapon?.burstAmount ?: 1

                override fun get(): TaskSchedule {
                    if (burst == 0) {
                        return TaskSchedule.stop()
                    }

                    if (!gun.item.meta().hasTag(ammoTag)) {
                        return TaskSchedule.stop()
                    }

                    if (gun.item.getTag(ammoTag) > 0)
                        gun.shoot(this@GunGame, player)
                    else return TaskSchedule.stop()

                    if (player.itemInMainHand.meta().getTag(ammoTag) == 0) {
                        // poor guy
                        // AUTO RELOADLOELDEOKROIJETROIJEOTLJSRGHRLGMlrejkgdklthirlthyi

//                        val gun = Weapon.registeredMap[player.itemInMainHand.getTag(Weapon.gunTag)] ?: return taskSchedule
//                        reload(player, gun)
                    }

                    burst--;

                    return taskSchedule
                }
            })

            it.player.itemInMainHand = it.player.itemInMainHand.withMeta {
                g -> g.setTag(lastShotTag, System.currentTimeMillis())
            }
        }

        instance.eventNode().addListener(PlayerHandAnimationEvent::class.java) {
            val player = it.entity as? Player ?: return@addListener

            reload(player, player.heldWeapon ?: return@addListener)
        }

        instance.eventNode().addListener(EntityDamageEvent::class.java) {
            if (it.entity !is Player) return@addListener
            if (it.entity.health - it.damage <= 0.0f) {
                it.isCancelled = true
                val player = it.entity as Player
                instance.eventNode().call(
                    PlayerEliminateEvent(
                        player,
                        damagers[player.uuid]?.let { it1 -> uuidToPlayer(it1) }
                    )
                )
            }
        }

        instance.eventNode().addListener(PlayerChangeHeldSlotEvent::class.java) {
            it.isCancelled = true
        }


        instance.eventNode().addListener(PlayerEliminateEvent::class.java) {
            val p = it.player
            val reason = it.eliminator

            if (reason != null) {
                reason.sendActionBar(
                    Component.text("You defeated ")
                        .color(NamedTextColor.RED)
                        .append(
                            p.name.decorate(TextDecoration.BOLD)
                        )
                )

                val w = reason.heldWeapon ?: return@addListener
                val newGun = GunProgression.entries[(w.level + 1).coerceIn(0, GunProgression.entries.size - 1)]
                reason.itemInMainHand = newGun.weapon.item
            }

            burstTasks[p.uuid]?.cancel()
            reloadTasks[p.uuid]?.cancel()
            burstTasks.remove(p.uuid)
            reloadTasks.remove(p.uuid)

            instance.sendMessage(
                Component.textOfChildren(
                    Component.text(Emoji.Skull.toString()).color(NamedTextColor.RED),
                    Component.text(" > ").color(NamedTextColor.DARK_GRAY),
                    it.player.name.color(NamedTextColor.RED)
                )
            )

            p.gameMode = GameMode.SPECTATOR
            p.health = 20f

            var timer = 5

            var schedule: Task? = null

            schedule = instance.scheduler().buildTask {
                if (timer >= 0) {
                    respawn(p)
                    schedule?.cancel()
                }

                val color = when (timer) {
                    3 -> NamedTextColor.YELLOW
                    2 -> NamedTextColor.GOLD
                    1 -> NamedTextColor.RED
                    else -> NamedTextColor.GREEN
                }

                if (timer <= 3) {
                    p.playSound(Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_BIT, Sound.Source.MASTER, 1f, 1f))
                }

                p.sendActionBar(
                    Component.textOfChildren(
                        Component.text("Respawning in ").color(NamedTextColor.AQUA),
                        Component.text(timer).color(color).decorate(TextDecoration.BOLD)
                    )
                )

                timer--
            }.repeat(1, TimeUnit.SECOND).schedule()
        }
    }

    private fun respawn(p: Player) {
        damagers.remove(p.uuid)
        spawnProtection[p.uuid] = System.currentTimeMillis() + (5 * 1000)
        p.gameMode = gameMode
        p.health = 20f
        p.teleport(spawn)
        p.inventory.clear()
        p.inventory.setItemStack(0, GunProgression.entries.first().weapon.item)
    }

    fun reload(player: Player, gun: Weapon) {
        if (player.itemInMainHand.meta().hasTag(Weapon.reloadingTag) || player.itemInMainHand.meta().getTag(ammoTag) == gun.ammo) return

        val ammoOnReload = player.itemInMainHand.getTag(ammoTag) ?: return
        val reloadMillis = 2500

        val startingAmmo = 0f

        player.itemInMainHand = player.itemInMainHand.withMeta {
            it.setTag(ammoTag, startingAmmo.toInt())
            it.setTag(Weapon.reloadingTag, true)
        }

        reloadTasks[player.uuid] = player.scheduler().submitTask(object : Supplier<TaskSchedule> {
            var reloadIter = reloadMillis / 50
            var currentAmmo = startingAmmo

            override fun get(): TaskSchedule {
                if (reloadIter == 0) {
                    player.playSound(Sound.sound(SoundEvent.ENTITY_IRON_GOLEM_ATTACK, Sound.Source.PLAYER, 1f, 1f))
                    player.scheduler().buildTask {
                        player.playSound(
                            Sound.sound(
                                SoundEvent.ENTITY_IRON_GOLEM_ATTACK,
                                Sound.Source.PLAYER,
                                1f,
                                1f
                            ),
                            Sound.Emitter.self()
                        )
                    }.delay(Duration.ofMillis(50 * 3L)).schedule()

                    player.itemInMainHand = player.itemInMainHand.withMeta {
                        it.setTag(ammoTag, gun.ammo)
                        it.removeTag(Weapon.reloadingTag)
                    }

                    gun.renderAmmo(player, gun.ammo)

                    return TaskSchedule.stop()
                }

                reloadIter--

                val lastAmmo = currentAmmo
                currentAmmo += (gun.ammo.toFloat() - startingAmmo) / (reloadMillis / 50).toFloat()

                val lastAmmoRounded = floor(lastAmmo).toInt()
                val roundedAmmo = floor(currentAmmo).toInt()

                gun.renderAmmo(player, roundedAmmo, currentAmmo / gun.ammo.toFloat(), reloading = true)
                if (roundedAmmo == lastAmmoRounded) return TaskSchedule.nextTick()

                player.itemInMainHand = player.itemInMainHand.withMeta {
                    it.setTag(ammoTag, roundedAmmo)
                }

                player.playSound(
                    Sound.sound(SoundEvent.ENTITY_ITEM_PICKUP, Sound.Source.PLAYER, 0.3f, 2f),
                    Sound.Emitter.self()
                )

                return TaskSchedule.nextTick()
            }
        })
    }
}

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
                0.0f, 500, byteBuffer.array()
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
                    hitPlayer.damage(DamageType.fromPlayer(player), damage)
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





data object BBGun : Weapon("BB Gun", 0) {
    override val cooldown: Long = 1
    override val damage: Float = 3f
    override val ammo: Int = 32
    override val material: Material = Material.WOODEN_HOE
    override val bullets: Int = 100
    override val spread: Double = 0.0
    override val maxDistance: Double = 50.0
    override val burstAmount: Int = 1
    override val burstInterval: Int = 1
}

data object Pistol : Weapon("Pistol", 1) {
    override val cooldown: Long = 1
    override val damage: Float = 3f
    override val ammo: Int = 10
    override val material: Material = Material.STONE_HOE
    override val bullets: Int = 100
    override val spread: Double = 0.0
    override val maxDistance: Double = 100.0
    override val burstAmount: Int = 1
    override val burstInterval: Int = 1
}

data object Rifle : Weapon("Rifle", 2) {
    override val cooldown: Long = 1
    override val damage: Float = 3f
    override val ammo: Int = 10
    override val material: Material = Material.DIAMOND_PICKAXE
    override val bullets: Int = 100
    override val spread: Double = 0.0
    override val maxDistance: Double = 150.0
    override val burstAmount: Int = 1
    override val burstInterval: Int = 1
}

data object SMG : Weapon("SMG", 3) {
    override val cooldown: Long = 1
    override val damage: Float = 1f
    override val ammo: Int = 200
    override val material: Material = Material.GOLDEN_SHOVEL
    override val bullets: Int = 100
    override val spread: Double = 0.1
    override val maxDistance: Double = 100.0
    override val burstAmount: Int = 4
    override val burstInterval: Int = 100
}

data object Shotgun : Weapon("Shotgun", 4) {
    override val cooldown: Long = 500
    override val damage: Float = 1f
    override val ammo: Int = 200
    override val material: Material = Material.DIAMOND_HOE
    override val bullets: Int = 500
    override val spread: Double = 0.2
    override val maxDistance: Double = 50.0
    override val burstAmount: Int = 1
    override val burstInterval: Int = 1
}

enum class GunProgression(val weapon: Weapon) {
    BBGUN(BBGun),
    PISTOL(Pistol),
    RIFLE(Rifle),
    Smg(SMG),
    SHOTGUN(Shotgun)
}
