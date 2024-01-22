package app.phtn.pulse.common.enums

enum class Emoji(private val text: String) {
    Info("ⓘ"),
    Skull("☠"),
    Warning("⚠"),
    Crosshair("⌖"),
    Heart("❤");

    override fun toString(): String {
        return this.text
    }
}