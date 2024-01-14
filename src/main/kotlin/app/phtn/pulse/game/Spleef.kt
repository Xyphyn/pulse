package app.phtn.pulse.game

import app.phtn.pulse.Main
import app.phtn.pulse.game.event.PlayerEliminateEvent
import app.phtn.pulse.uuidsToPlayers
import app.phtn.pulse.vanilla.VanillaExplosion
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.instance.AddEntityToInstanceEvent
import net.minestom.server.event.item.ItemDropEvent
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import net.minestom.server.tag.Tag
import net.minestom.server.utils.time.TimeUnit
import java.util.UUID

class Spleef(override val players: MutableSet<UUID>) : Game {
    override var instance: InstanceContainer = Main.instanceManager.createInstanceContainer(Main.default)
    override val spectators: MutableSet<UUID> = mutableSetOf()
    override val gameMode: GameMode = GameMode.SURVIVAL

    val powerupTimer = MinecraftServer.getSchedulerManager().buildTask {
        val players = uuidsToPlayers(players.toList())
        players.forEach { p ->
            val powerup = Powerup.entries.random()
            p.inventory.addItemStack(powerup.itemStack)
        }
    }.repeat(5, TimeUnit.SECOND).schedule()

    val lava = 10

    init {
        instance.setGenerator {
            g ->
            g.modifier().fillHeight(9, 10, Block.LAVA)
        }

        instance.timeRate = 0

        for (y in 2..6) {
            for (x in -20..20) {
                for (z in -20..20) {
                    instance.setBlock(x, y * 5, z, Block.SNOW_BLOCK)
                }
            }
        }

        players.mapNotNull {
            p ->
            Main.connections.getPlayer(p)
        }.forEach {
            p ->
            p.setInstance(instance, Pos(0.5, 32.0, 0.5))
        }

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
                players.remove(e.player.uuid)
                spectators.add(e.player.uuid)
                e.player.gameMode = GameMode.SPECTATOR

                if (players.size <= 1) {
                    uuidsToPlayers(players.toList()).forEach {
                        victory(it, true)
                    }
                    uuidsToPlayers(spectators.toList()).forEach {
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
            val powerup = Powerup.fromTag(it.player.itemInMainHand.getTag(Tag.String("tag"))) ?: return@addListener
            powerup.act(it.player, instance)
            it.player.itemInMainHand = it.player.itemInMainHand.consume(1)
        }
    }

    override fun endGame() {
        super.endGame()

        powerupTimer.cancel()
    }
}

enum class Powerup(val act: (Player, InstanceContainer) -> Unit, val itemStack: ItemStack, val tag: String) {
    Railgun(
        { p, i ->
            val target = p.getTargetBlockPosition(100)
            VanillaExplosion.builder(target, 2.0f).build().trigger(i)
        },
        ItemStack.of(Material.GOLDEN_HOE)
            .withDisplayName(
                Component.text("Rail gun")
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false)
                    .decorate(TextDecoration.BOLD)
            ).withTag(Tag.String("tag"), "railgun"),
        "railgun"
    ),
    Boost({ p, i ->
        p.velocity = Vec(p.velocity.x, 10.0, p.velocity.z)
    },
        ItemStack.of(Material.RABBIT_FOOT)
            .withDisplayName(
                Component.text("Boost")
                    .color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false)
                    .decorate(TextDecoration.BOLD)
            ).withTag(Tag.String("tag"), "boost"),
        "boost"),
    ;
    companion object {
        fun fromTag(tag: String): Powerup? {
            return Powerup.entries.find { p -> p.tag == tag }
        }
    }
}