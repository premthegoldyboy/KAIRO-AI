package com.kairo.ai

enum class KairoAction { OPEN_INSTALLED_APP }

/** Central allow-list: actions must be supported and explicitly confirmed before execution. */
object KairoActionPolicy {
    fun requiresConfirmation(action: KairoAction): Boolean = when (action) {
        KairoAction.OPEN_INSTALLED_APP -> true
    }

    fun mayExecute(action: KairoAction, userConfirmed: Boolean): Boolean =
        userConfirmed && action in setOf(KairoAction.OPEN_INSTALLED_APP)
}
