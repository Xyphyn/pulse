package app.phtn.pulse.game.gungame

import app.phtn.pulse.common.Main
import app.phtn.pulse.common.enums.Emoji
import app.phtn.pulse.game.Game
import app.phtn.pulse.game.gungame.Weapon.Companion.ammoTag
import app.phtn.pulse.game.gungame.Weapon.Companion.heldWeapon
import app.phtn.pulse.game.gungame.Weapon.Companion.lastShotTag
import app.phtn.pulse.game.event.PlayerEliminateEvent
import app.phtn.pulse.common.util.uuidToPlayer
import app.phtn.pulse.common.util.uuidsToPlayers
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.event.player.PlayerChangeHeldSlotEvent
import net.minestom.server.event.player.PlayerHandAnimationEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.instance.AnvilLoader
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import net.minestom.server.utils.time.TimeUnit
import java.nio.file.Path
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier
import kotlin.math.floor

class GunGame(
    override val players: MutableSet<UUID>,
) : Game() {
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
        onJoin()

        val url = this::class.java.getResource("/beach") ?: throw Error("oops")
        val path = Path.of(url.toURI())

        instance.chunkLoader = AnvilLoader(path)
        instance.timeRate = 0

        val players = uuidsToPlayers(players.toList())

        transferPlayers(spawn)

        players.forEach {
            it.inventory.setItemStack(0, GunProgression.entries.first().weapon.item)
            spawnProtection[it.uuid] = System.currentTimeMillis() + (5 * 1000)
            it.setTag(Tag.String("gun"), "BBGun")
        }
    }

    override fun registerEvents() {
        instance.eventNode().addListener(PlayerUseItemEvent::class.java) { it ->
            val player = it.player
            it.isCancelled = true
            if ((spawnProtection[player.uuid] ?: 0) > System.currentTimeMillis()) {
                player.sendActionBar(
                    Component.text(
                        "You have spawn protection for ${
                            (((spawnProtection[player.uuid] ?: 0) - System.currentTimeMillis()) / 1000).toInt()
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

                        val gun = Weapon.registeredMap[player.itemInMainHand.getTag(Weapon.gunTag)] ?: return taskSchedule
                        reload(player, gun)
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
            if (it.entity.health - it.damage.amount <= 0.0f) {
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
            it.player.setHeldItemSlot(0)
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
                reason.setTag(Tag.String("gun"), newGun.name)
                reason.itemInMainHand = newGun.weapon.item
                reason.health = 20f
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
                if (timer == 0) {
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

        super.registerEvents()
    }

    private fun respawn(p: Player) {
        damagers.remove(p.uuid)
        spawnProtection[p.uuid] = System.currentTimeMillis() + (5 * 1000)
        p.gameMode = gameMode
        p.health = 20f
        p.teleport(spawn)
        p.inventory.clear()
        val prevGun = GunProgression.entries.find { e -> e.name == p.getTag(Tag.String("gun")) }
        if (prevGun == null) {
            p.inventory.setItemStack(0, GunProgression.entries.first().weapon.item)
        } else {
            val newGun = GunProgression.entries[(prevGun.ordinal - 1).coerceIn(0, GunProgression.entries.size - 1)]
            p.inventory.setItemStack(0, newGun.weapon.item)
        }
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
