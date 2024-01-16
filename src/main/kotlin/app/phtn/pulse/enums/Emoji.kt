package app.phtn.pulse.enums

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