package com.nuitcode.daytesk.sync

import com.nuitcode.daytesk.auth.AuthApi

/**
 * Network boundary for [CloudSync].
 *
 * Extracted so the pull/push behaviour can be exercised on the JVM with a fake
 * transport. Production uses [HttpSyncTransport], which delegates to [AuthApi]
 * (whose blocking calls already hop to `Dispatchers.IO`).
 */
interface SyncTransport {
    suspend fun pull(token: String): Result<AuthApi.SyncSnapshot>

    suspend fun push(token: String, snapshot: AuthApi.SyncSnapshot): Result<Unit>
}

/** Production transport: the real HTTP sync endpoints. */
object HttpSyncTransport : SyncTransport {
    override suspend fun pull(token: String): Result<AuthApi.SyncSnapshot> = AuthApi.pullSync(token)

    override suspend fun push(token: String, snapshot: AuthApi.SyncSnapshot): Result<Unit> =
        AuthApi.pushSync(token, snapshot)
}
