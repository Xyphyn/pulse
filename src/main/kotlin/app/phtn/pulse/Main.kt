package app.phtn.pulse

import app.phtn.pulse.enums.Color
import app.phtn.pulse.enums.Emoji
import app.phtn.pulse.game.QueueCommand
import app.phtn.pulse.game.event.PlayerEliminateEvent
import app.phtn.pulse.game.spleef
import app.phtn.pulse.instance.Lobby
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.event.GlobalEventHandler
import net.minestom.server.event.player.PlayerChatEvent
import net.minestom.server.event.player.PlayerDeathEvent
import net.minestom.server.event.player.PlayerLoginEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.event.server.ServerListPingEvent
import net.minestom.server.extras.MojangAuth
import net.minestom.server.ping.ResponseData
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

    Main.eventHandler.addListener(PlayerEliminateEvent::class.java) { event ->
        event.instance.sendMessage(Component.text("${Emoji.Skull} ${event.player.name}").color(NamedTextColor.RED))
    }

    Main.eventHandler.addListener(PlayerChatEvent::class.java) { event ->
        event.setChatFormat {
            e ->
            Component.textOfChildren(
                e.player.name.color(NamedTextColor.AQUA),
                Component.text(" > ").color(NamedTextColor.DARK_GRAY),
                Component.text(e.message).color(NamedTextColor.WHITE)
            )
        }
    }

    Main.eventHandler.addListener(PlayerSpawnEvent::class.java) {
        if (it.isFirstSpawn) Lobby.sidebars[it.player.uuid] = Lobby.newSidebar(it.player)
    }

    Main.eventHandler.addListener(ServerListPingEvent::class.java) {
        it.responseData.description = Component.textOfChildren(
            Component.text("ᴘᴜʟsᴇ ").color(Color.Brand.color),
            Component.text("                                                   ")
                .color(NamedTextColor.DARK_GRAY)
                .decorate(TextDecoration.STRIKETHROUGH),

            Component.text(" 1.20.1").color(NamedTextColor.GRAY).appendNewline(),
            Component.empty()
        )
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