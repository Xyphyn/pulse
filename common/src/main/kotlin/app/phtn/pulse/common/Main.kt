package app.phtn.pulse.common

import net.minestom.server.MinecraftServer
import net.minestom.server.event.GlobalEventHandler
import net.minestom.server.utils.NamespaceID
import net.minestom.server.world.DimensionType


object Main {
    val instanceManager = MinecraftServer.getInstanceManager()
    val default: DimensionType = DimensionType.builder(
        NamespaceID.from("pulse:lobby")
    ).skylightEnabled(true).ambientLight(15.0f).build()
    val eventHandler: GlobalEventHandler = MinecraftServer.getGlobalEventHandler()
    val connections = MinecraftServer.getConnectionManager()
}