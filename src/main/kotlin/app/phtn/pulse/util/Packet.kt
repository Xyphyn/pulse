package app.phtn.pulse.util

import net.minestom.server.coordinate.Pos
import net.minestom.server.network.packet.server.play.DamageEventPacket
import net.minestom.server.network.packet.server.play.EntityAnimationPacket
import java.util.UUID

fun damageEffect(entityId: Int, source: Int = -1, sourcePos: Pos?): DamageEventPacket {
    return DamageEventPacket(
        entityId,
        0,
        source + 1,
        source + 1,
        sourcePos
    )
}