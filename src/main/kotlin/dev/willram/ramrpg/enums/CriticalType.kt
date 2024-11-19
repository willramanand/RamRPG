package dev.willram.ramrpg.enums

enum class CriticalType(val color: String) {
    NONE("<red>"),
    REGULAR("<gold>"),
    ENHANCED("<dark_red>"),
    ULTRA("<dark_purple>");


    companion object {
        fun getByLevel(level: Int): CriticalType {
            val actualLvl = if (level > 3) {
                3
            } else if (level < 0) {
                0
            } else {
                level
            }

            return when (actualLvl) {
                0 -> NONE
                1 -> REGULAR
                2 -> ENHANCED
                3 -> ULTRA
                else -> NONE
            }
        }
    }
}
