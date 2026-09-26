package com.sai.cardtrack.util

interface AppLog {
    fun d(message: String)
}

class RecordingLog : AppLog {
    val messages: MutableList<String> = mutableListOf()
    override fun d(message: String) {
        messages.add(message)
    }
}
