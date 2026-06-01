package com.azurlane.core.domain.ship

data class ShipProperties(
    val hp: Int = 0,
    val firepower: Int = 0,
    val torpedo: Int = 0,
    val antiAir: Int = 0,
    val aviation: Int = 0,
    val reload: Int = 0,
    val armor: Int = 0,
    val hit: Int = 0,
    val dodge: Int = 0,
    val speed: Int = 0,
    val luck: Int = 0,
    val antiSub: Int = 0
) {
    fun combatPower(): Int {
        return hp / 5 + firepower + torpedo + antiAir + aviation +
                antiSub + reload + hit * 2 + dodge * 2 + speed
    }

    operator fun plus(other: ShipProperties): ShipProperties = ShipProperties(
        hp = hp + other.hp,
        firepower = firepower + other.firepower,
        torpedo = torpedo + other.torpedo,
        antiAir = antiAir + other.antiAir,
        aviation = aviation + other.aviation,
        reload = reload + other.reload,
        armor = armor + other.armor,
        hit = hit + other.hit,
        dodge = dodge + other.dodge,
        speed = speed + other.speed,
        luck = luck + other.luck,
        antiSub = antiSub + other.antiSub
    )
}
