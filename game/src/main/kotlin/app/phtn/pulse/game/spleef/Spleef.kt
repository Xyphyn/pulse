package app.phtn.pulse.game.spleef

import app.phtn.pulse.common.Main
import app.phtn.pulse.game.event.PlayerEliminateEvent
import app.phtn.pulse.common.enums.Emoji
import app.phtn.pulse.common.enums.ding
import app.phtn.pulse.game.Game
import app.phtn.pulse.common.util.uuidToPlayer
import app.phtn.pulse.common.util.uuidsToPlayers
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.instance.AddEntityToInstanceEvent
import net.minestom.server.event.item.ItemDropEvent
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.batch.AbsoluteBlockBatch
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.utils.time.TimeUnit
import java.util.UUID

class Spleef(override val players: MutableSet<UUID>) : Game() {
    override var instance: InstanceContainer = Main.instanceManager.createInstanceContainer(Main.default)
    override val spectators: MutableSet<UUID> = mutableSetOf()
    override val gameMode: GameMode = GameMode.SURVIVAL

    private var gameRunning = true

    private val powerupTimer = instance.scheduler().buildTask {
        val players = uuidsToPlayers(players.toList())
        instance.sendMessage(
            Component.text("${Emoji.Info} Everybody has received a powerup!")
                .color(NamedTextColor.AQUA)
        )
        players.forEach { p ->
            val powerup = Powerup.entries.random()
            p.inventory.addItemStack(powerup.itemStack)
            p.playSound(Sound.sound(Key.key(ding), Sound.Source.MASTER, 1f, 1f))
        }
    }.repeat(15 + (players.size * 5L), TimeUnit.SECOND).delay(15L + (players.size * 5L), TimeUnit.SECOND).schedule()

    private var destroyLayer = 4

    private val layerDestroyer = instance.scheduler().buildTask {
        instance.sendMessage(
            Component.text("${Emoji.Warning} A layer is being destroyed...")
                .color(NamedTextColor.GOLD)
        )

        var destroyProgress = 8

        var task: Task? = null

        task = MinecraftServer.getSchedulerManager().buildTask {
            if (destroyProgress == 0) {
                val blockBatch = AbsoluteBlockBatch()
                for (y in destroyLayer * 5 + 5..destroyLayer * 5 + 10) {
                    for (x in -20..20) {
                        for (z in -20..20) {
                            blockBatch.setBlock(
                                Pos(x.toDouble(), y.toDouble(), z.toDouble()),
                                Block.AIR
                            )
                        }
                    }
                }
                blockBatch.apply(instance, null)
                destroyLayer--
                task?.cancel()
            }

            destroyProgress -= 1
        }.repeat(1, TimeUnit.SECOND).schedule()

    }.repeat(90, TimeUnit.SECOND).delay(90, TimeUnit.SECOND).schedule()

    private val lava = 10
    private val maxBuildHeight = 30

    init {
        onJoin()

        instance.setGenerator {
            g ->
            g.modifier().fillHeight(9, 10, Block.LAVA)
        }

        instance.timeRate = 0

        for (y in 0..3) {
            for (x in -20..20) {
                for (z in -20..20) {
                    instance.setBlock(x, (y * 5) + 10, z, Block.SNOW_BLOCK)
                }
            }
        }

        players.mapNotNull {
            p ->
            uuidToPlayer(p)
        }.forEach {
            p ->
            p.setInstance(instance, Pos(0.5, 32.0, 0.5))
        }
    }

    override fun registerEvents() {
        instance.eventNode().addListener(AddEntityToInstanceEvent::class.java) {
            val player = it.entity as? Player ?: return@addListener

            player.inventory.setItemStack(0, ItemStack.of(Material.NETHERITE_SHOVEL))
            player.setGameMode(GameMode.SURVIVAL)
            player.isInstantBreak = true
        }

        instance.eventNode().addListener(PlayerMoveEvent::class.java) {
                e ->
            run {
                if (e.newPosition.y <= lava && players.contains(e.player.uuid)) {
                    instance.eventNode().call(PlayerEliminateEvent(e.player))
                }
            }
        }

        instance.eventNode().addListener(ItemDropEvent::class.java) {
                e ->
            run {
                e.isCancelled = true
            }
        }

        instance.eventNode().addListener(PlayerEliminateEvent::class.java) {
                e ->
            run {
                if (!gameRunning) return@addListener

                players.remove(e.player.uuid)
                spectators.add(e.player.uuid)
                e.player.gameMode = GameMode.SPECTATOR


                if (players.size <= 1) {
                    val players = uuidsToPlayers(players.toList())
                    val spectators = uuidsToPlayers(spectators.toList())

                    gameRunning = false

                    if (players.size == 1) {
                        instance.sendMessage(
                            Component.textOfChildren(
                                players[0].name
                                    .color(NamedTextColor.GOLD)
                                    .decorate(TextDecoration.BOLD),
                                Component.text(" wins!")
                                    .color(NamedTextColor.GOLD)
                            )
                        )
                    }

                    players.forEach {
                        victory(it, true)
                    }
                    spectators.forEach {
                        victory(it, false)
                    }
                }
            }
        }

        instance.eventNode().addListener(PlayerBlockBreakEvent::class.java) {
            it.isCancelled = false
            it.resultBlock = Block.AIR
        }

        instance.eventNode().addListener(PlayerUseItemEvent::class.java) {
            if (!it.player.itemInMainHand.hasTag(Tag.String("tag"))) {
                return@addListener
            }

            val powerup = Powerup.fromTag(it.player.itemInMainHand.getTag(Tag.String("tag"))) ?: return@addListener
            powerup.act(it.player, instance, players)
            if (powerup.consume)
                it.player.itemInMainHand = it.player.itemInMainHand.consume(1)
        }

        instance.eventNode().addListener(PlayerBlockPlaceEvent::class.java) {
            if (it.blockPosition.y() > maxBuildHeight) it.isCancelled = true
        }

        super.registerEvents()
    }

    override fun endGame() {
        super.endGame()

        powerupTimer.cancel()
        layerDestroyer.cancel()
    }
}