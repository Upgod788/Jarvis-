package com.example.personality

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PersonalityManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("jarvis_personality_prefs", Context.MODE_PRIVATE)

    private val _style = MutableStateFlow(
        CommunicationStyle.fromId(prefs.getString(KEY_STYLE, CommunicationStyle.FRIENDLY.id) ?: CommunicationStyle.FRIENDLY.id)
    )
    val style: StateFlow<CommunicationStyle> = _style.asStateFlow()

    fun setStyle(newStyle: CommunicationStyle) {
        prefs.edit().putString(KEY_STYLE, newStyle.id).apply()
        _style.value = newStyle
    }

    fun getPersonalityInstruction(): String = _style.value.promptInstruction

    companion object {
        private const val KEY_STYLE = "key_communication_style"
    }
}
