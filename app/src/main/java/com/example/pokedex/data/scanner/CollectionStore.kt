package com.example.pokedex.data.scanner

import com.example.pokedex.domain.scanner.*

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CollectionStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val _collectedKeys = MutableStateFlow(preferences.getStringSet(KEY_COLLECTED, emptySet()).orEmpty().toSet())

    val collectedKeys: StateFlow<Set<String>> = _collectedKeys.asStateFlow()

    fun collect(formKey: String): Boolean {
        if (formKey.isBlank() || formKey in _collectedKeys.value) return false
        val updated = _collectedKeys.value + formKey
        preferences.edit().putStringSet(KEY_COLLECTED, updated).apply()
        _collectedKeys.value = updated
        return true
    }

    companion object {
        private const val FILE_NAME = "pokemon_collection"
        private const val KEY_COLLECTED = "collected_form_keys"
    }
}
