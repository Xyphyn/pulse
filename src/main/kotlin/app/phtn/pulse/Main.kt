package app.phtn.pulse

import app.phtn.pulse.command.IUseArchBtw
import app.phtn.pulse.common.Main
import app.phtn.pulse.common.enums.Color
import app.phtn.pulse.common.enums.Emoji
import app.phtn.pulse.game.QueueCommand
import app.phtn.pulse.game.event.PlayerEliminateEvent
import app.phtn.pulse.game.spleef
import app.phtn.pulse.lobby.Lobby
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.MinecraftServer
import net.minestom.server.event.GlobalEventHandler
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerChatEvent
import net.minestom.server.event.player.PlayerRespawnEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.event.server.ServerListPingEvent
import net.minestom.server.extras.MojangAuth
import net.minestom.server.utils.NamespaceID
import net.minestom.server.world.DimensionType

fun main() {
    val server = MinecraftServer.init()

    MojangAuth.init()

    MinecraftServer.getCommandManager().register(QueueCommand())
    MinecraftServer.getCommandManager().register(IUseArchBtw())

    MinecraftServer.getDimensionTypeManager().addDimension(Main.default)

    val handler = MinecraftServer.getGlobalEventHandler()

    handler.addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
        event.spawningInstance = Lobby.instance
        event.player.respawnPoint = Lobby.spawn
    }

    handler.addListener(PlayerEliminateEvent::class.java) { event ->
        event.instance.sendMessage(Component.text("${Emoji.Skull} ${event.player.name}").color(NamedTextColor.RED))
    }

    handler.addListener(PlayerChatEvent::class.java) { event ->
        event.setChatFormat {
            e ->
            Component.textOfChildren(
                e.player.name.color(NamedTextColor.AQUA),
                Component.text(" > ").color(NamedTextColor.DARK_GRAY),
                Component.text(e.message).color(NamedTextColor.WHITE)
            )
        }
    }

    handler.addListener(PlayerSpawnEvent::class.java) {
        if (it.isFirstSpawn) Lobby.sidebars[it.player.uuid] = Lobby.newSidebar(it.player)
    }

    handler.addListener(PlayerRespawnEvent::class.java) {
        it.player.setInstance(Lobby.instance, Lobby.spawn)
    }

    handler.addListener(ServerListPingEvent::class.java) {
        it.responseData.description = Component.textOfChildren(
            Component.text("ᴘᴜʟsᴇ ").color(Color.Brand.color),
            Component.text("                                                   ")
                .color(NamedTextColor.DARK_GRAY)
                .decorate(TextDecoration.STRIKETHROUGH),

            Component.text(" 1.20.4").color(NamedTextColor.GRAY).appendNewline(),
            Component.empty()
        )
    }

    server.start("0.0.0.0", 25565)
}