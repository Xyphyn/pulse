package app.phtn.pulse.npc

import net.kyori.adventure.text.Component
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.*
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.entity.metadata.display.TextDisplayMeta
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.event.entity.EntityTickEvent
import net.minestom.server.event.player.PlayerEntityInteractEvent
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.network.packet.server.play.PlayerInfoRemovePacket
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket
import net.minestom.server.network.packet.server.play.TeamsPacket
import java.util.function.Consumer

class NPC(instance: InstanceContainer, position: Pos) : EntityCreature(EntityType.PLAYER) {
    private var callback: (Player) -> Unit = {  }
    private val nameTag: Entity
    private var skin: PlayerSkin? = null

    init {
        this.isCustomNameVisible = true
        this.setInstance(instance)
        this.teleport(position)

        this.nameTag = Entity(EntityType.TEXT_DISPLAY)
        val meta = nameTag.getEntityMeta() as TextDisplayMeta
        meta.billboardRenderConstraints = AbstractDisplayMeta.BillboardConstraints.VERTICAL
        meta.translation = Pos(0.0, this.eyeHeight + 0.5, 0.0)
        team?.let { it.nameTagVisibility = TeamsPacket.NameTagVisibility.NEVER }

        if (this.customName != null) {
            meta.text = this.customName!!
        }

        nameTag.setInstance(instance, position.add(0.0, 0.5, 0.0))
        nameTag.spawn()

        instance.eventNode().addListener<EntityAttackEvent>(
            EntityAttackEvent::class.java
        ) { event: EntityAttackEvent ->
            val player = event.entity as? Player
            if (player != null && event.target == this) {
                callback(player)
            }
        }

        instance.eventNode().addListener<PlayerEntityInteractEvent>(
            PlayerEntityInteractEvent::class.java
        ) { event: PlayerEntityInteractEvent ->
            if (event.target == this) {
                if (event.hand == Player.Hand.MAIN) {
                    callback(event.player)
                }
            }
        }

        instance.eventNode().addListener(
            EntityTickEvent::class.java
        ) { event: EntityTickEvent? ->
            nameTag.teleport(
                this.position.add(0.0, 0.5, 0.0)
            )
        }
    }

    override fun updateNewViewer(player: Player) {
        val properties: MutableList<PlayerInfoUpdatePacket.Property> = ArrayList()

        if (this.skin != null) {
            properties.add(
                PlayerInfoUpdatePacket.Property(
                    "textures",
                    skin!!.textures(), skin!!.signature()
                )
            )
        }

        val entry = PlayerInfoUpdatePacket.Entry(
            this.getUuid(),
            "",
            properties,
            false,
            0,
            GameMode.ADVENTURE,
            this.customName,
            null
        )

        player.sendPacket(PlayerInfoUpdatePacket(PlayerInfoUpdatePacket.Action.ADD_PLAYER, entry))
        super.updateNewViewer(player)
    }

    fun withCallback(callback: (Player) -> Unit): NPC {
        this.callback = callback
        return this
    }

    fun withName(name: Component?): NPC {
        this.customName = name
        this.updateAllViewers()

        val meta = nameTag.getEntityMeta() as TextDisplayMeta

        if (this.customName != null) {
            meta.text = this.customName!!
        }

        return this
    }

    fun withSkin(skin: PlayerSkin?): NPC {
        this.skin = skin
        this.updateAllViewers()
        return this
    }

    private fun updateAllViewers() {
        instance.players.forEach(Consumer { player: Player ->
            player.sendPacket(
                PlayerInfoRemovePacket(this.uuid)
            )
        })

        instance.players.forEach(Consumer { player: Player ->
            this.updateNewViewer(
                player
            )
        })
    }
}