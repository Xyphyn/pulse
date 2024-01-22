package app.phtn.pulse.game.spleef

import app.phtn.pulse.common.util.Raycast
import app.phtn.pulse.common.util.RaycastResultType
import app.phtn.pulse.common.util.uuidsToPlayers
import app.phtn.pulse.common.vanilla.VanillaExplosion
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag
import net.minestom.server.utils.time.TimeUnit
import java.util.*

enum class Powerup(val act: (Player, InstanceContainer, MutableSet<UUID>) -> Unit, val itemStack: ItemStack, val tag: String, val consume: Boolean = true) {
    Railgun(
        { player, instance, _ ->
            val target = Raycast.raycastBlock(
                instance,
                player.position.add(0.0, player.eyeHeight, 0.0),
                player.position.direction(),
                100.0
            )
            if (target is Point)
                VanillaExplosion.builder(target.add(0.0, 1.0, 0.0), 3.0f).build().trigger(instance)
        },
        ItemStack.of(Material.GOLDEN_HOE)
            .withDisplayName(
                Component.text("Rail gun")
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false)
                    .decorate(TextDecoration.BOLD)
            ).withTag(Tag.String("tag"), "railgun"),
        "railgun"
    ),
    Boost({ player, _, _ ->
        player.velocity = Vec(player.velocity.x, 25.0, player.velocity.z)
    },
        ItemStack.of(Material.RABBIT_FOOT)
            .withDisplayName(
                Component.text("Boost")
                    .color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false)
                    .decorate(TextDecoration.BOLD)
            ).withTag(Tag.String("tag"), "boost"),
        "boost"),
    Blocks({ _, _, _ -> },
        ItemStack.of(Material.SNOW_BLOCK).withAmount(5).withTag(Tag.String("tag"), "blocks"),
        "blocks"),
    Swapper(
        fun(player, i, players) {
            if (i.getBlock(player.position.sub(0.0, 1.0, 0.0)) == Block.AIR) return
            val playerList = players.toMutableSet()
            playerList.remove(player.uuid)

            val randomPlayer = uuidsToPlayers(playerList.toList()).random()
            val newPos = randomPlayer.position

            randomPlayer.teleport(player.position)
            randomPlayer.sendActionBar(
                Component.text("You swapped positions with ").color(NamedTextColor.AQUA)
                    .append(player.name.decorate(TextDecoration.BOLD))
            )
            player.teleport(newPos)
            player.sendActionBar(
                Component.text("You swapped positions with ").color(NamedTextColor.AQUA)
                    .append(randomPlayer.name.decorate(TextDecoration.BOLD))
            )
            i.playSound(
                Sound.sound(SoundEvent.ENTITY_ENDERMAN_TELEPORT, Sound.Source.MASTER, 1f, 1f),
                player.position
            )
        },
        ItemStack.of(Material.COMPASS).withDisplayName(
            Component.text("Swapper").color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false)
        ).withTag(Tag.String("tag"), "swapper"),
        "swapper"
    ),
    Vortex(
        { player, i, _ ->
            val hit = Raycast.raycast(
                i,
                player.position.add(0.0, player.eyeHeight, 0.0),
                player.position.direction(),
                100.0
            ) {
                it != player && it.entityType == EntityType.PLAYER
            }

            if (hit.resultType == RaycastResultType.HIT_ENTITY && hit.hitEntity != null) {
                i.playSound(
                    Sound.sound(SoundEvent.ENTITY_BLAZE_HURT, Sound.Source.MASTER, 1f, 1f),
                    hit.hitEntity!!.position
                )
                hit.hitEntity!!.takeKnockback(2.0f, player.position.x, player.position.y)
            }
        },
        ItemStack.of(Material.BLAZE_ROD).withDisplayName(
            Component.text("Vortex Burst").color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false)
        ).withTag(Tag.String("tag"), "vortex"),
        "vortex"
    ),
    Invisibility(
        { player, i, _ ->
            player.isInvisible = true

            player.addEffect(
                Potion(
                    PotionEffect.INVISIBILITY,
                    1,
                    10
                )
            )

            player.sendActionBar(
                Component.text("You're invisible for ").color(NamedTextColor.AQUA)
                    .append(Component.text("10 seconds").decorate(TextDecoration.BOLD))
            )

            i.scheduler().buildTask {
                player.isInvisible = false
                player.clearEffects()
            }.delay(10, TimeUnit.SECOND).schedule()
        },
        ItemStack.of(Material.SPLASH_POTION).withDisplayName(
            Component.text("Invisibility Potion").color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false)
        ).withTag(Tag.String("tag"), "invisibility"),
        "invisibility"
    )
    ;
    companion object {
        fun fromTag(tag: String): Powerup? {
            return Powerup.entries.find { p -> p.tag == tag }
        }
    }
}