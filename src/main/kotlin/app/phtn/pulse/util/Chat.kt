package app.phtn.pulse.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.util.RGBLike
import kotlin.math.ceil
import kotlin.math.floor

val mm = MiniMessage.miniMessage()
fun gradient(text: String, vararg stops: TextColor): Component {
    return mm.deserialize(
        "<gradient:${stops.joinToString(":") { s -> s.asHexString() }}>${text}</gradient>"
    )
}

fun progressBar(percentage: Float, characterCount: Int = 10, character: String = " ", completeColor: RGBLike, incompleteColor: RGBLike, decoration: TextDecoration? = null): Component {
    val percentage = percentage.coerceIn(0f, 1f)

    val completeCharacters = ceil((percentage * characterCount)).toInt()
    val incompleteCharacters = floor((1 - percentage) * characterCount).toInt()

    val completeStyle = TextColor.color(completeColor)
    val incompleteStyle = TextColor.color(incompleteColor)

    return Component.text(character.repeat(completeCharacters), completeStyle)
        .append(Component.text(character.repeat(incompleteCharacters), incompleteStyle))
}