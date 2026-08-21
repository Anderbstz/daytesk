package com.nuitcode.daytesk.model

object DayteskUser {
    var displayName: String = ""
    var email: String = ""

    val initials: String
        get() = displayName.filter { it.isLetter() }.take(2).uppercase().ifBlank { "AN" }
}
