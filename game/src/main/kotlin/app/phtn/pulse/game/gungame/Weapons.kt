package app.phtn.pulse.game.gungame

import net.minestom.server.item.Material

data object Pistol : Weapon("Pistol", 0) {
    override val cooldown: Long = 250
    override val damage: Float = 1.5f
    override val ammo: Int = 10
    override val material: Material = Material.STONE_HOE
    override val bullets: Int = 1
    override val spread: Double = 0.0
    override val maxDistance: Double = 100.0
    override val burstAmount: Int = 1
    override val burstInterval: Int = 1
}

data object Rifle : Weapon("Rifle", 1) {
    override val cooldown: Long = 250
    override val damage: Float = 2.5f
    override val ammo: Int = 10
    override val material: Material = Material.DIAMOND_PICKAXE
    override val bullets: Int = 1
    override val spread: Double = 0.0
    override val maxDistance: Double = 150.0
    override val burstAmount: Int = 1
    override val burstInterval: Int = 1
}

data object Shotgun : Weapon("Shotgun", 2) {
    override val cooldown: Long = 500
    override val damage: Float = 1f
    override val ammo: Int = 200
    override val material: Material = Material.DIAMOND_HOE
    override val bullets: Int = 5
    override val spread: Double = 0.2
    override val maxDistance: Double = 50.0
    override val burstAmount: Int = 1
    override val burstInterval: Int = 1
}

data object SMG : Weapon("SMG", 3) {
    override val cooldown: Long = 100
    override val damage: Float = 0.5f
    override val ammo: Int = 200
    override val material: Material = Material.GOLDEN_SHOVEL
    override val bullets: Int = 1
    override val spread: Double = 0.1
    override val maxDistance: Double = 100.0
    override val burstAmount: Int = 4
    override val burstInterval: Int = 100
}

enum class GunProgression(val weapon: Weapon) {
    PISTOL(Pistol),
    RIFLE(Rifle),
    SHOTGUN(Shotgun),
    Smg(SMG),
}