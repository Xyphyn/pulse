package app.phtn.pulse.game

import app.phtn.pulse.enums.bit
import app.phtn.pulse.enums.ding
import app.phtn.pulse.uuidsToPlayers
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import net.minestom.server.utils.NamespaceID
import net.minestom.server.utils.time.TimeUnit
import net.minestom.server.world.DimensionType
import java.time.Duration
import java.util.*


class Queue(private val type: QueueType, val players: MutableSet<UUID>, private var timer: Int = 10) {
    private var queueTimer: Task? = null

    private val state: State
        get() =
            if (this.players.size < this.type.min) State.Waiting
            else if (this.players.size <= this.type.max) State.Ready
            else State.Starting


    private fun inQueue(uuid: UUID): Boolean {
        return players.contains(uuid)
    }

    fun addPlayer(uuid: UUID) {
        if (inQueue(uuid)) return
        players.add(uuid)
        checkState()
    }

    fun removePlayer(uuid: UUID) {
        players.remove(uuid)
        checkState()
    }

    private fun checkState() {
        when (state) {
            State.Ready -> timer()
            else -> {
                queueTimer?.cancel()
                timer = 10
                queueTimer = null
            }
        }
    }

    private fun timer() {
        if (queueTimer != null) return
        queueTimer = MinecraftServer.getSchedulerManager().buildTask {
            if (this.timer == 0) {
                timer = 10
                type.instantiate(players.toMutableSet())
                players.clear()

                queueTimer?.cancel()
                QueueManager.queue.remove(type)
                return@buildTask
            } else {
                if (state != State.Ready) {
                    TaskSchedule.stop()
                    timer = 10

                    queueTimer?.cancel()
                    return@buildTask
                }

                val color: TextColor = when (this.timer) {
                    3 -> NamedTextColor.YELLOW
                    2 -> NamedTextColor.GOLD
                    1 -> NamedTextColor.RED
                    else -> NamedTextColor.GREEN
                }

                uuidsToPlayers(players.toList()).forEach {
                    if (timer == 10) {
                        it.playSound(Sound.sound(Key.key(ding), Sound.Source.MASTER, 1f, 1f))
                    }

                    if (timer <= 3) {
                        it.playSound(Sound.sound(Key.key(bit), Sound.Source.MASTER, 1f, 1f))
                    }

                    it.sendActionBar(Component.text("Joining in ")
                        .color(NamedTextColor.AQUA)
                        .append(Component.text("$timer").color(color).decorate(TextDecoration.BOLD))
                    )
                }
                timer--
            }
        }.repeat(1, TimeUnit.SECOND).schedule()

    }
}

object QueueManager {
    val queue: MutableMap<QueueType, Queue> = mutableMapOf()

    fun addPlayer(type: QueueType, player: Player) {
        removePlayer(player)

        if (queue[type] == null) queue[type] = Queue(type, mutableSetOf())
        val queue = queue[type]!!

        player.sendActionBar(
            Component.text("Queued for ")
                .color(NamedTextColor.AQUA)
                .append(Component.text(type.name).decorate(TextDecoration.BOLD))
        )
        queue.addPlayer(player.uuid)
    }

    private fun removePlayer(player: Player) {
        queue.values.forEach {
            q ->
            if (q.players.contains(player.uuid))
                q.removePlayer(player.uuid)
        }
    }
}

enum class QueueType(val min: Int, val max: Int, val instantiate: (MutableSet<UUID>) -> Game) {
    SPLEEF(2, 16, ::Spleef),
    GUNGAME(2, 16, ::GunGame),
    ESCAPE(2, 16, ::Escape),
    PARKOURRACE(1, 16, ::Parkour);
    companion object {
        fun nameToType(name: String): QueueType? {
            return try {
                QueueType.valueOf(name.uppercase())
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}

enum class State {
    Waiting,
    Ready,
    Starting
}

class QueueCommand : Command("queue") {
    init {
        setDefaultExecutor { sender, context ->
            sender.sendMessage("invalid queue")
        }

        val queueArg = ArgumentType.String("game")

        addSyntax({
            sender, context ->
            val type = QueueType.nameToType(context.get(queueArg))
            if (type != null) {
                QueueManager.addPlayer(type, sender as Player)
                sender.sendMessage("queued for ${type.name}")
            }
        }, queueArg)
    }
}

val spleef: DimensionType = DimensionType.builder(
    NamespaceID.from("pulse:spleef")
).ambientLight(1.0f).build()