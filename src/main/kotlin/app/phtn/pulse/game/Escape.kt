package app.phtn.pulse.game

import app.phtn.pulse.Main
import app.phtn.pulse.uuidsToPlayers
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.entity.EntityShootEvent
import net.minestom.server.instance.AnvilLoader
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.nio.file.Path
import java.util.UUID

class Escape(override val players: MutableSet<UUID>) : Game() {
    override val spectators: MutableSet<UUID> = mutableSetOf()
    override var instance: InstanceContainer = Main.instanceManager.createInstanceContainer(Main.default)
    override val gameMode: GameMode = GameMode.SURVIVAL

    private val attackers = Team(mutableListOf(), Pos(6.0, 64.0, 0.0))
    private val defenders = Team(mutableListOf(), Pos(0.0, 46.0, 0.0))

    init {
        val url = this::class.java.getResource("/escape") ?: throw Error("oops")
        val path = Path.of(url.toURI())

        instance.chunkLoader = AnvilLoader(path)

        val initPlayers = uuidsToPlayers(players.toList()).shuffled()

        initPlayers.forEachIndexed { index, player ->
            if (index == 0) {
                attackers.players.add(player)
                player.setInstance(instance, attackers.spawn!!)
                player.inventory.setItemStack(0, ItemStack.of(Material.TRIDENT))
            } else {
                defenders.players.add(player)
                player.setInstance(instance, defenders.spawn!!)
                player.inventory.setItemStack(0, ItemStack.of(Material.SAND).withAmount(64))
            }
        }

        instance.eventNode().addListener(EntityShootEvent::class.java) {
            (it.entity as? Player)?.sendMessage("shot")
        }
    }
}

