package com.nuitcode.daytesk.sync

import androidx.room.withTransaction
import com.nuitcode.daytesk.data.local.AppDatabase

/**
 * The single write boundary of a sync pull.
 *
 * [CloudSync.applyLocked] deletes every row across three tables and re-inserts
 * the merged snapshot. Without an enclosing transaction a crash or DB error
 * midway leaves the local store empty or half-applied (RESIL-002). Naming the
 * boundary keeps that guarantee explicit and gives it one testable seam.
 */
internal suspend fun <T> AppDatabase.withSyncTransaction(block: suspend () -> T): T =
    withTransaction(block)
