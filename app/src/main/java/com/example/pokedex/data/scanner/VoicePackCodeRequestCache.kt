package com.example.pokedex.data.scanner

import android.content.Context

class VoicePackCodeRequestCache(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun read(): String? = preferences.getString(KEY_LAST_CONTENT, null)

    fun write(content: String) {
        preferences.edit().putString(KEY_LAST_CONTENT, content).apply()
    }

    private companion object {
        const val PREFERENCES = "voice_pack_code_request"
        const val KEY_LAST_CONTENT = "last_content"
    }
}
