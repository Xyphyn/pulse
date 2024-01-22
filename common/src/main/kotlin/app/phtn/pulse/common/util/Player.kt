package app.phtn.pulse.common.util

import app.phtn.pulse.common.Main
import net.minestom.server.entity.Player
import java.util.UUID

fun uuidsToPlayers(uuids: List<UUID>): List<Player> =
    uuids.mapNotNull {
        u -> Main.connections.getOnlinePlayerByUuid(u)
    }

fun uuidToPlayer(uuid: UUID): Player? = Main.connections.getOnlinePlayerByUuid(uuid)