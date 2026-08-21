package com.nuitcode.daytesk.model

enum class Repeticion {
    NINGUNA,
    DIARIA,
    SEMANAL,
    MENSUAL,
    ;

    fun label(): String = when (this) {
        NINGUNA -> "No"
        DIARIA -> "Diaria"
        SEMANAL -> "Semanal"
        MENSUAL -> "Mensual"
    }

    fun nextDue(fromMillis: Long = System.currentTimeMillis()): Long {
        val calendar = java.util.Calendar.getInstance().apply { timeInMillis = fromMillis }
        when (this) {
            NINGUNA -> return fromMillis
            DIARIA -> calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
            SEMANAL -> calendar.add(java.util.Calendar.WEEK_OF_YEAR, 1)
            MENSUAL -> calendar.add(java.util.Calendar.MONTH, 1)
        }
        return calendar.timeInMillis
    }
}
