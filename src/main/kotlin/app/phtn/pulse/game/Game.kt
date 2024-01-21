package app.phtn.pulse.game

import app.phtn.pulse.Main
import app.phtn.pulse.game.event.PlayerEliminateEvent
import app.phtn.pulse.instance.Lobby
import app.phtn.pulse.uuidToPlayer
import app.phtn.pulse.uuidsToPlayers
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.audience.ForwardingAudience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.kyori.adventure.title.TitlePart
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.utils.time.TimeUnit
import java.time.Duration
import java.util.UUID

abstract class Game : ForwardingAudience {
    abstract val players: MutableSet<UUID>
    abstract val spectators: MutableSet<UUID>
    abstract var instance: InstanceContainer
    abstract val gameMode: GameMode

    open fun registerEvents() {
        instance.eventNode().addListener(PlayerDisconnectEvent::class.java) {
            instance.eventNode().call(PlayerEliminateEvent(it.player))
            players.remove(it.player.uuid)
            spectators.remove(it.player.uuid)
        }
    }

    open fun onJoin() {
        uuidsToPlayers(players.toList()).forEach {
            it.inventory.clear()
        }
    }

    open fun endGame() {
        players.mapNotNull {
                p ->
            uuidToPlayer(p)
        }.forEach {
                p ->
            p.setInstance(Main.lobby, Lobby.spawn)
        }
        spectators.mapNotNull {
                p ->
            uuidToPlayer(p)
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

    fun transferPlayers(pos: Pos) {
        uuidsToPlayers(players.toList()).forEach {
            p ->
            p.setInstance(instance, pos)
        }
    }

    override fun audiences(): MutableIterable<Audience> = this.instance.players
}

data class Team(val players: MutableList<Player>, val spawn: Pos? = null)

fun teamOfPlayer(player: Player, vararg teams: Team) = teams.find { t -> t.players.contains(player) }