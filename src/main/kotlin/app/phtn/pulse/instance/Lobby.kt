package app.phtn.pulse.instance

import app.phtn.pulse.Main
import app.phtn.pulse.enums.Color
import app.phtn.pulse.game.QueueManager
import app.phtn.pulse.game.QueueType
import app.phtn.pulse.npc.NPC
import app.phtn.pulse.util.gradient
import app.phtn.pulse.uuidToPlayer
import net.hollowcube.polar.PolarLoader
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.PlayerSkin
import net.minestom.server.event.instance.AddEntityToInstanceEvent
import net.minestom.server.event.instance.RemoveEntityFromInstanceEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.instance.AnvilLoader
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.scoreboard.Sidebar
import net.minestom.server.utils.time.TimeUnit
import java.nio.file.Path
import java.util.UUID

object Lobby {
    val spawn = Pos(0.5, 2.0, 0.5, 180.0f, 0.0f)
    val sidebars: MutableMap<UUID, Sidebar> = mutableMapOf()

    private const val respawnBelow = -10.0

    fun init(): InstanceContainer {
        MinecraftServer.getDimensionTypeManager().addDimension(Main.default)
        val instance = Main.instanceManager.createInstanceContainer(Main.default)
        instance.eventNode().addListener(PlayerMoveEvent::class.java, ::onMove)
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

        val spleef = NPC(instance, Pos(0.5, 2.0, -4.5, 0.0f, 0.0f))
            .withSkin(PlayerSkin.fromUsername("kevin6191015"))
            .withName(gradient("Spleef", NamedTextColor.AQUA, NamedTextColor.BLUE).decorate(TextDecoration.BOLD))
            .withCallback {
                QueueManager.addPlayer(QueueType.SPLEEF, it)
            }

        val gungame = NPC(instance, Pos(-1.5, 2.0, -4.5, -25.0f, 0.0f))
            .withSkin(PlayerSkin.fromUsername("AG10gamer"))
            .withName(gradient("Gun Game", NamedTextColor.YELLOW, NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
            .withCallback {
                QueueManager.addPlayer(QueueType.GUNGAME, it)
            }

        val parkour = NPC(instance, Pos(2.5, 2.0, -4.5, 25.0f, 0.0f))
            .withSkin(PlayerSkin.fromUsername("Ilovejamaled"))
            .withName(gradient("Parkour Race", NamedTextColor.GREEN, NamedTextColor.DARK_GREEN).decorate(TextDecoration.BOLD))
            .withCallback {
                QueueManager.addPlayer(QueueType.PARKOURRACE, it)
            }

        MinecraftServer.getSchedulerManager().buildTask {
            val players = instance.players

            players.forEach { p ->
                if (sidebars[p.uuid] == null)
                    sidebars[p.uuid] = newSidebar(p)

                sidebars[p.uuid] = updateSidebar(p.uuid)!!
            }
        }.repeat(1, TimeUnit.SECOND).schedule()

        return instance
    }

    fun onMove(event: PlayerMoveEvent) {
        if (event.newPosition.y > respawnBelow) return

        event.player.teleport(spawn)
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

    fun updateSidebar(uuid: UUID): Sidebar? {
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