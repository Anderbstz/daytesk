package com.nuitcode.daytesk.model

object DayteskUser {
    var displayName: String = "ander"
    var email: String = "anderbstz@gmail.com"

    val initials: String
        get() = displayName.filter { it.isLetter() }.take(2).uppercase().ifBlank { "AN" }
}
