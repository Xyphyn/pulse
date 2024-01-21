package app.phtn.pulse.game

import app.phtn.pulse.Main
import app.phtn.pulse.enums.Emoji
import app.phtn.pulse.game.event.PlayerEliminateEvent
import app.phtn.pulse.util.SongManager
import app.phtn.pulse.uuidsToPlayers
import dev.emortal.nbstom.NBS
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.batch.AbsoluteBlockBatch
import net.minestom.server.instance.batch.BatchOption
import net.minestom.server.instance.block.Block
import net.minestom.server.network.packet.server.play.TeamsPacket
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import net.minestom.server.scoreboard.Team
import net.minestom.server.scoreboard.TeamBuilder
import net.minestom.server.scoreboard.TeamManager
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag
import net.minestom.server.utils.time.TimeUnit
import java.util.*
import kotlin.collections.ArrayDeque
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.random.Random

class Parkour(override val players: MutableSet<UUID>) : Game() {
    companion object {
        private val tm = MinecraftServer.getTeamManager()
        val team: Team by lazy {
            val t = tm.createTeam(
                "parkour",
                Component.text("parkour"),
                Component.empty(),
                NamedTextColor.BLUE,
                Component.empty(),
            )

            t.updateCollisionRule(TeamsPacket.CollisionRule.NEVER)

            return@lazy t
        }

        val scoreTag = Tag.Integer("score")
        val lastPosX = Tag.Double("lastPosX")
        val lastPosY = Tag.Double("lastPosY")
        val lastPosZ = Tag.Double("lastPosZ")
        val spawn = Pos(0.0, 164.0, 0.0)
        val killBelow = (spawn.y - 20).roundToInt()

        val parkourBlocks: List<Block> = listOf(
            Block.GRASS_BLOCK,
            Block.DEEPSLATE,
            Block.STONE,
            Block.STRIPPED_BIRCH_LOG,
            Block.PODZOL,
            Block.DIAMOND_BLOCK,
            Block.DIRT,
            Block.DIRT_PATH,
            Block.BIRCH_PLANKS,
            Block.BOOKSHELF
        )
    }

    override val spectators: MutableSet<UUID> = mutableSetOf()

    override var instance: InstanceContainer = Main.instanceManager.createInstanceContainer(Main.default)
    override val gameMode: GameMode = GameMode.ADVENTURE


    private val playerSet: MutableSet<Player> = uuidsToPlayers(players.toList()).toMutableSet()

    private val leadingPlayer: Player?
        get() {
            if (playerSet.isEmpty()) return null
            return playerSet.maxBy { player -> player.position.z }
        }

    private val blocks: ArrayDeque<Pos> = ArrayDeque()
    private var targetY: Double = 0.0

    private val numBlocks = 20
    private val stopId = UUID.randomUUID()

    init {
        registerEvents()

        onJoin()

        instance.setGenerator {
            it.modifier().fillHeight(killBelow - 2, killBelow - 1, Block.LAVA)
        }

        blocks.addLast(spawn.add(0.0, -1.0, 2.0))

        // batches wont work for some reason
        for (x in -2..2) {
            for (z in -2..2) {
                instance.setBlock(x, (spawn.y - 1.0).toInt(), z, Block.DEEPSLATE_BRICKS)
            }
        }

        instance.scheduler().buildTask {
            for (x in -2..2) {
                for (z in -2..2) {
                    instance.setBlock(x, (spawn.y - 1.0).toInt(), z, Block.AIR)
                }
            }
        }.delay(10, TimeUnit.SECOND).schedule()

        playerSet.forEachIndexed() {
            index, player ->
            player.isInvisible = true
            player.isGlowing = true
            player.team = team
            player.gameMode = gameMode
            player.setTag(scoreTag, 0)
            player.setTag(lastPosX, spawn.x)
            player.setTag(lastPosY, spawn.y)
            player.setTag(lastPosZ, spawn.z)
            player.setInstance(instance, spawn.add(0.5, 0.0, 0.5))
        }

        NBS.play(SongManager.badApple, this, instance.scheduler(), stopId)

        for (block in 0..numBlocks) {
            generateNextBlock(false)
        }
    }

    override fun registerEvents() {
        instance.eventNode().addListener(PlayerEliminateEvent::class.java) {
            instance.sendMessage(
                Component.textOfChildren(
                    Component.text(Emoji.Skull.toString()).color(NamedTextColor.RED),
                    Component.text(" > ").color(NamedTextColor.DARK_GRAY),
                    it.player.name.color(NamedTextColor.RED)
                )
            )

            playerSet.remove(it.player)
            players.remove(it.player.uuid)
            spectators.add(it.player.uuid)
            it.player.gameMode = GameMode.SPECTATOR

            if (playerSet.size <= 1) {
                if (playerSet.size == 1) {
                    instance.sendMessage(
                        Component.textOfChildren(
                            playerSet.toList()[0].name
                                .color(NamedTextColor.GOLD)
                                .decorate(TextDecoration.BOLD),
                            Component.text(" wins!")
                                .color(NamedTextColor.GOLD)
                        )
                    )
                }

                playerSet.forEach {
                        player ->
                    victory(player, true)
                }
                uuidsToPlayers(spectators.toList()).forEach {
                        spectator ->
                    victory(spectator, false)
                }
            }
        }

        instance.eventNode().addListener(PlayerMoveEvent::class.java) {
            if (!playerSet.contains(it.player)) return@addListener
            if (it.player.position.y <= killBelow) {
                instance.eventNode().call(PlayerEliminateEvent(it.player))
            }
            it.player.setTag(scoreTag, floor(it.player.position.z).toInt())
            if (leadingPlayer?.uuid != it.player.uuid) return@addListener

            if (leadingPlayer != null) {
                playerSet.forEach {
                        p ->
                    p.sendActionBar(
                        Component.textOfChildren(
                            leadingPlayer!!.name
                                .color(NamedTextColor.AQUA)
                                .decorate(TextDecoration.BOLD)
                                .append(
                                    Component.text(" is the leader")
                                        .decoration(TextDecoration.BOLD, false)
                                ),
                            Component.text(" | ").color(NamedTextColor.DARK_GRAY),
                            Component.text(p.getTag(scoreTag)).color(NamedTextColor.GREEN)
                                .decorate(TextDecoration.BOLD)
                        )
                    )
                }
            }

            val pos = it.player.position

            val playerBlock = Pos(
                round(pos.x - 0.5),
                round(pos.y - 1),
                round(pos.z - 0.5),
            )

            val index = blocks.indexOf(playerBlock)

            if (index == -1 || index == 0) return@addListener


            if (index < 10) return@addListener


//            it.player.playSound(
//                Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_BASS, Sound.Source.BLOCK, 1f, ((score % 15f) / 10f) + 0.5f)
//            )

            for (block in 0..index - 10) {
                generateNextBlock()
            }
        }

        super.registerEvents()
    }

    private fun generateNextBlock(delete: Boolean = true) {
        val lastPos = if (blocks.size == 0) spawn else blocks.last()
        if (delete) {
            instance.setBlock(blocks.first(), Block.AIR)
            blocks.removeFirst()
        }
        val newPos = randomBlock(lastPos)

        if (lastPos.y == spawn.y) {
            targetY = 0.0
        } else if (lastPos.y < spawn.y - 10 || lastPos.y > spawn.y + 10) {
            targetY = spawn.y
        }

        instance.setBlock(newPos, parkourBlocks.random())
        blocks.addLast(newPos)
    }

    private fun randomBlock(pos: Pos): Pos {
        val y: Int = when (targetY) {
            0.0 -> (Random.nextInt(3) - 1)
            in pos.y + 1..Int.MAX_VALUE.toDouble() -> 1
            else -> -1
        }
        val z: Int = when (y) {
            1 -> Random.nextInt(1, 3)
            -1 -> Random.nextInt(2, 5)
            else -> Random.nextInt(1, 4)
        }
        val x = Random.nextInt(-3, 4)

        return pos.add(x.toDouble(), y.toDouble(), z.toDouble())
    }

    override fun endGame() {
        playerSet.forEach {
            it.clearEffects()
            it.isInvisible = false
            it.isGlowing = false
        }
        uuidsToPlayers(spectators.toList()).forEach {
            it.isInvisible = false
            it.isGlowing = false
        }
        NBS.stop(stopId)

        super.endGame()
    }
}