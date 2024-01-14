package app.phtn.pulse.game

import app.phtn.pulse.uuidsToPlayers
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player
import net.minestom.server.timer.TaskSchedule
import net.minestom.server.utils.NamespaceID
import net.minestom.server.world.DimensionType
import java.time.Duration
import java.util.*


class Queue(val type: QueueType, val players: MutableSet<UUID>, var timer: Int = 10) {
    var state: State = State.Waiting
        get() =
            if (this.players.size < this.type.min) State.Waiting
            else if (this.players.size <= this.type.max) State.Ready
            else State.Starting


    fun addPlayer(uuid: UUID) {
        players.add(uuid)
        checkState()
    }

    fun removePlayer(uuid: UUID) {
        players.remove(uuid)
        checkState()
    }

    fun checkState() {
        when (state) {
            State.Ready -> timer()
            else -> {  }
        }
    }

    private fun timer() {
        val joiners = uuidsToPlayers(players.toList())

        MinecraftServer.getSchedulerManager().submitTask {
            if (this.timer == 0) {
                timer = 10
                type.instantiate(players.toMutableSet())
                players.clear()

                return@submitTask TaskSchedule.stop()
            } else {
                if (state != State.Ready) {
                    TaskSchedule.stop()
                    timer = 10
                }

                val color: TextColor = when (this.timer) {
                    3 -> NamedTextColor.YELLOW
                    2 -> NamedTextColor.GOLD
                    1 -> NamedTextColor.RED
                    else -> NamedTextColor.GREEN
                }

                joiners.forEach {
                    it.sendActionBar(Component.text("Joining in ")
                        .color(NamedTextColor.AQUA)
                        .append(Component.text("$timer").color(color).decorate(TextDecoration.BOLD))
                    )
                }
                timer--

                return@submitTask TaskSchedule.duration(Duration.ofSeconds(1))
            }
        }

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

    fun removePlayer(player: Player) {
        queue.values.forEach {
            q ->
            q.removePlayer(player.uuid)
        }
    }
}

enum class QueueType(val min: Int, val max: Int, val instantiate: (MutableSet<UUID>) -> Game) {
    SPLEEF(2, 16, ::Spleef);
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