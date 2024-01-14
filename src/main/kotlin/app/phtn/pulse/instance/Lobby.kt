package app.phtn.pulse.instance

import app.phtn.pulse.Main
import app.phtn.pulse.game.QueueManager
import app.phtn.pulse.game.QueueType
import app.phtn.pulse.npc.NPC
import net.hollowcube.polar.PolarLoader
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.PlayerSkin
import net.minestom.server.event.instance.AddEntityToInstanceEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.instance.AnvilLoader
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import java.nio.file.Path

object Lobby {
    val spawn = Pos(0.5, 2.0, 0.5, 180.0f, 0.0f)

    private const val respawnBelow = -5.0

    fun init(): InstanceContainer {
        MinecraftServer.getDimensionTypeManager().addDimension(Main.default)
        val instance = Main.instanceManager.createInstanceContainer(Main.default)
        instance.eventNode().addListener(PlayerMoveEvent::class.java, ::onMove)
        val url = this::class.java.getResource("/lobby") ?: throw Error("oops")
        val path = Path.of(url.toURI())

        instance.chunkLoader = AnvilLoader(path)

        instance.eventNode().addListener(AddEntityToInstanceEvent::class.java) {
            val player = it.entity as? Player

            if (player != null) {
                player.setGameMode(GameMode.ADVENTURE)
                player.inventory.clear()
                player.teleport(spawn)
            }
        }

        val npc = NPC(instance, Pos(0.5, 2.0, -4.5, 0.0f, 0.0f))
            .withSkin(PlayerSkin.fromUsername("OnlyFlare"))
            .withName(Component.text("Spleef").color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD))
            .withCallback {
                QueueManager.addPlayer(QueueType.SPLEEF, it)
            }

        return instance
    }

    fun onMove(event: PlayerMoveEvent) {
        if (event.newPosition.y > respawnBelow) return

        event.player.teleport(spawn)
    }
}