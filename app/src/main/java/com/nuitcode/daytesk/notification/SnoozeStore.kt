package com.nuitcode.daytesk.notification

import android.content.Context

/** A snooze alarm that is still waiting to fire. */
data class PendingSnooze(
    val id: Long,
    val texto: String,
    val triggerAt: Long,
)

/**
 * Persisted registry of pending snooze alarms (RESIL-001).
 *
 * The at-due roll deletes the source recordatorio row, and [SnoozeReceiver]
 * deliberately re-posts from Intent extras instead of Room. That makes the
 * alarm itself the only owner of a pending snooze — and Android drops every
 * alarm on reboot. Without a persisted record a 10-minute snooze would be
 * silently lost, so every scheduled snooze is mirrored here and
 * [BootReceiver] re-arms whatever is still in the future.
 *
 * One entry per recordatorio id: the snooze alarm slot is unique per id, so a
 * newer snooze overwrites the older one instead of stacking.
 */
object SnoozeStore {
    private const val PREFS = "daytesk_snoozes"
    private const val KEY_PREFIX = "snooze_"
    private const val FIELD_SEPARATOR = "\n"

    fun save(context: Context, id: Long, texto: String, triggerAt: Long) {
        // commit(), not apply(): this record only matters if it survives the
        // process death that follows the receiver returning.
        prefs(context).edit()
            .putString(key(id), encode(PendingSnooze(id, texto, triggerAt)))
            .commit()
    }

    fun remove(context: Context, id: Long) {
        prefs(context).edit().remove(key(id)).commit()
    }

    fun all(context: Context): List<PendingSnooze> =
        prefs(context).all.entries
            .filter { it.key.startsWith(KEY_PREFIX) }
            .mapNotNull { (_, raw) -> decode(raw as? String) }

    fun clear(context: Context) {
        prefs(context).edit().clear().commit()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(id: Long) = "$KEY_PREFIX$id"

    private fun encode(entry: PendingSnooze): String =
        listOf(entry.id, entry.triggerAt, entry.texto).joinToString(FIELD_SEPARATOR)

    private fun decode(raw: String?): PendingSnooze? {
        // limit = 3 keeps every newline inside the display text.
        val parts = raw?.split(FIELD_SEPARATOR, limit = 3) ?: return null
        if (parts.size != 3) return null
        val id = parts[0].toLongOrNull() ?: return null
        val triggerAt = parts[1].toLongOrNull() ?: return null
        return PendingSnooze(id, parts[2], triggerAt)
    }
}
