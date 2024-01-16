package app.phtn.pulse.game.event

import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.trait.InstanceEvent
import net.minestom.server.event.trait.PlayerEvent
import net.minestom.server.instance.Instance

class PlayerEliminateEvent(private val player: Player, val eliminator: Player? = null) : InstanceEvent, PlayerEvent {
    override fun getInstance(): Instance {
        return player.instance
    }

    override fun getPlayer(): Player {
        return player
    }

}