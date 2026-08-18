package com.nuitcode.daytesk.utilities.common

/**
 * Failure with a message already written for the user. Each throw site names the
 * stage that failed so the screen can tell audio problems apart from video ones.
 */
class TranscriptionException(message: String) : Exception(message)
