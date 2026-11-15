package com.kairo.ai

import android.content.Context

enum class AIExecutionMode { LOCAL, CLOUD }

/** Persists the user's explicit inference choice across app restarts. */
class AIExecutionModeStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "kairo_execution_mode",
        Context.MODE_PRIVATE
    )

    fun load(defaultMode: AIExecutionMode): AIExecutionMode =
        runCatching {
            AIExecutionMode.valueOf(
                preferences.getString(KEY_MODE, defaultMode.name) ?: defaultMode.name
            )
        }.getOrDefault(defaultMode)

    fun save(mode: AIExecutionMode) {
        preferences.edit().putString(KEY_MODE, mode.name).apply()
    }

    private companion object {
        const val KEY_MODE = "selected_mode"
    }
}
