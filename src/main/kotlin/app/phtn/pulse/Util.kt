package app.phtn.pulse

import net.minestom.server.entity.Player
import java.util.UUID

fun uuidsToPlayers(uuids: List<UUID>): List<Player> =
    uuids.mapNotNull {
        u -> Main.connections.getPlayer(u)
    }

fun uuidToPlayer(uuid: UUID): Player? = Main.connections.getPlayer(uuid)
