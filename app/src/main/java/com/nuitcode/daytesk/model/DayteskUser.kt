package com.nuitcode.daytesk.model

object DayteskUser {
    const val displayName: String = "ander"
    const val email: String = "anderbstz@gmail.com"

    val initials: String
        get() = displayName.take(2).uppercase()
}
