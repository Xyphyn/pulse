package app.phtn.pulse.command

import net.kyori.adventure.text.Component
import net.minestom.server.advancements.FrameType
import net.minestom.server.advancements.notifications.Notification
import net.minestom.server.advancements.notifications.NotificationCenter
import net.minestom.server.command.builder.Command
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

class IUseArchBtw : Command("iusearchbtw") {
    init {
        setDefaultExecutor { sender, context ->
            val player = sender as? Player ?: return@setDefaultExecutor

            val notification = Notification(
                Component.text("nobody asked"),
                FrameType.CHALLENGE,
                ItemStack.of(Material.COMPASS)
            )

            NotificationCenter.send(notification, player)
        }
    }
}