package app.phtn.pulse.lobby

import app.phtn.pulse.common.Main
import app.phtn.pulse.common.enums.Color
import app.phtn.pulse.common.npc.NPC
import app.phtn.pulse.common.util.gradient
import app.phtn.pulse.common.util.uuidToPlayer
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.PlayerSkin
import net.minestom.server.event.instance.AddEntityToInstanceEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.instance.AnvilLoader
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.scoreboard.Sidebar
import net.minestom.server.utils.time.TimeUnit
import java.nio.file.Path
import java.util.UUID

object Lobby {
    val instance = MinecraftServer.getInstanceManager().createInstanceContainer(Main.default)
    val spawn = Pos(0.5, 2.0, 0.5, 180.0f, 0.0f)
    val sidebars: MutableMap<UUID, Sidebar> = mutableMapOf()

    private val respawnBelow = -10.0

    init {
        val url = this::class.java.getResource("/lobby") ?: throw Error("oops")
        val path = Path.of(url.toURI())

        instance.chunkLoader = AnvilLoader(path)

        instance.eventNode().addListener(AddEntityToInstanceEvent::class.java) {
            val player = it.entity as? Player ?: return@addListener

            player.setGameMode(GameMode.ADVENTURE)
            player.isGlowing = false
            player.isInvisible = false
            player.inventory.clear()
            player.inventory.setItemStack(0, ItemStack.of(Material.ELYTRA))
        }

        instance.eventNode().addListener(PlayerMoveEvent::class.java) {
            if (it.newPosition.y <= respawnBelow) it.player.teleport(spawn)
        }

        registerNPCs()

        MinecraftServer.getSchedulerManager().buildTask {
            val players = instance.players

            players.forEach { p ->
                if (sidebars[p.uuid] == null)
                    sidebars[p.uuid] = newSidebar(p)

                sidebars[p.uuid] = updateSidebar(p.uuid)!!
            }
        }.repeat(1, TimeUnit.SECOND).schedule()
    }

    private fun registerNPCs() {
        NPC(instance, Pos(0.5, 2.0, -4.5, 0.0f, 0.0f))
            .withSkin(PlayerSkin.fromUsername("kevin6191015"))
            .withName(gradient("Spleef", NamedTextColor.AQUA, NamedTextColor.BLUE).decorate(TextDecoration.BOLD))
            .withCallback {
                MinecraftServer.getCommandManager().execute(it, "queue spleef")
            }

        NPC(instance, Pos(-1.5, 2.0, -4.5, -25.0f, 0.0f))
            .withSkin(PlayerSkin.fromUsername("AG10gamer"))
            .withName(gradient("Gun Game", NamedTextColor.YELLOW, NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
            .withCallback {
                MinecraftServer.getCommandManager().execute(it, "queue gungame")
            }

        NPC(instance, Pos(2.5, 2.0, -4.5, 25.0f, 0.0f))
            .withSkin(PlayerSkin.fromUsername("Ilovejamaled"))
            .withName(gradient("Parkour Race", NamedTextColor.GREEN, NamedTextColor.DARK_GREEN).decorate(TextDecoration.BOLD))
            .withCallback {
                MinecraftServer.getCommandManager().execute(it, "queue parkourrace")
            }
    }

    fun newSidebar(player: Player): Sidebar {
        val sidebar = Sidebar(gradient("ᴘᴜʟsᴇ", Color.Brand.color, Color.Secondary.color))

        sidebar.createLine(Sidebar.ScoreboardLine("10", Component.empty(), 10))
        sidebar.createLine(Sidebar.ScoreboardLine("9", Component.textOfChildren(
            Component.text("ᴘʟᴀʏᴇʀs  ").color(Color.Secondary.color),
            Component.text(Main.connections.onlinePlayers.size)
        ), 9))
        sidebar.createLine(Sidebar.ScoreboardLine("8", Component.textOfChildren(
            Component.text("ᴘɪɴɢ  ").color(Color.Secondary.color),
            Component.text("idk")
        ), 8))
        sidebar.createLine(Sidebar.ScoreboardLine("7", Component.empty(), 7))
        sidebar.addViewer(player)

        return sidebar
    }

    private fun updateSidebar(uuid: UUID): Sidebar? {
        val sidebar = sidebars[uuid] ?: return null

        sidebar.updateLineContent("9", Component.textOfChildren(
            Component.text("ᴘʟᴀʏᴇʀs   ").color(Color.Secondary.color),
            Component.text(Main.connections.onlinePlayers.size).color(NamedTextColor.WHITE)
        ))
        sidebar.updateLineContent("8", Component.textOfChildren(
            Component.text("ᴘɪɴɢ        ").color(Color.Secondary.color),
            Component.text("${(uuidToPlayer(uuid)?.latency ?: 50) - 50}").color(NamedTextColor.WHITE)
        ))

        return sidebar
    }
}