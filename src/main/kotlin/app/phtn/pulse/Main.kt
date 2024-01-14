package app.phtn.pulse

import app.phtn.pulse.game.QueueCommand
import app.phtn.pulse.game.spleef
import app.phtn.pulse.instance.Lobby
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.event.GlobalEventHandler
import net.minestom.server.event.player.PlayerLoginEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.extras.MojangAuth
import net.minestom.server.utils.NamespaceID
import net.minestom.server.world.DimensionType
import kotlin.io.path.Path

fun main() {
    val server = MinecraftServer.init()
    MojangAuth.init()

    MinecraftServer.getCommandManager().register(QueueCommand())


    Main.eventHandler.addListener(PlayerLoginEvent::class.java) { event ->
        event.setSpawningInstance(Main.lobby)
        event.player.respawnPoint = Lobby.spawn
    }

    server.start("0.0.0.0", 25565)
}

fun registerDimensions() {
    MinecraftServer.getDimensionTypeManager().addDimension(Main.default)
    MinecraftServer.getDimensionTypeManager().addDimension(spleef)
}

object Main {
    val instanceManager = MinecraftServer.getInstanceManager()
    val default: DimensionType = DimensionType.builder(
        NamespaceID.from("pulse:lobby")
    ).ambientLight(1.0f).build()
    val eventHandler: GlobalEventHandler = MinecraftServer.getGlobalEventHandler()
    val connections = MinecraftServer.getConnectionManager()
    val lobby = Lobby.init()
}