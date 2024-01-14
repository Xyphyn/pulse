package app.phtn.pulse.game

import app.phtn.pulse.Main
import app.phtn.pulse.instance.Lobby
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.kyori.adventure.title.TitlePart
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.utils.time.TimeUnit
import java.time.Duration
import java.util.UUID

interface Game {
    val players: MutableSet<UUID>
    val spectators: MutableSet<UUID>
    var instance: InstanceContainer
    val gameMode: GameMode

    fun endGame() {
        players.mapNotNull {
                p ->
            Main.connections.getPlayer(p)
        }.forEach {
                p ->
            p.setInstance(Main.lobby, Lobby.spawn)
        }
        spectators.mapNotNull {
                p ->
            Main.connections.getPlayer(p)
        }.forEach {
                p ->
            p.setInstance(Main.lobby, Lobby.spawn)
        }
        Main.instanceManager.unregisterInstance(instance)
    }

    fun victory(player: Player, victory: Boolean) {
        player.sendTitlePart(
            TitlePart.TITLE,
            if (victory) Component.text("Victory!", NamedTextColor.GOLD, TextDecoration.BOLD)
            else Component.text("Defeat", NamedTextColor.RED, TextDecoration.BOLD)
        )

        MinecraftServer.getSchedulerManager().buildTask {
            endGame()
        }.delay(5, TimeUnit.SECOND).schedule()
    }
}