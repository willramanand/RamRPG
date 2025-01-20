package dev.willram.ramrpg.ability

enum class Ability(val displayName: String, val manaCost: Double, val unlock: Int, val upgrade: Int) {
    VEIN_MINER("Vein Miner", 10.0, 25, 50),
    DEMETERS_TOUCH("Demeter's Touch", 5.0, 25, 50),
    TREECAPTITOR("Treecapitator", 3.0, 25, 50),
    QUICKSHOT("Quickshot", 15.0, 25, 50),
    EXCAVATOR("Excavator", 2.5, 25, 50);
}