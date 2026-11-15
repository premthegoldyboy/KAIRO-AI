package com.kairo.ai

import android.content.Context
import android.content.Intent

object AppLauncher {
    fun open(context: Context, requestedName: String): String {
        val query = requestedName.trim()
        if (query.isBlank()) return "Tell me which app to open."

        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val matches = context.packageManager.queryIntentActivities(launchIntent, 0)
            .mapNotNull { info ->
                val label = info.loadLabel(context.packageManager)?.toString() ?: return@mapNotNull null
                Triple(label, info.activityInfo.packageName, info.activityInfo.name)
            }
            .distinctBy { it.second }
        val exact = matches.filter { it.first.equals(query, ignoreCase = true) }
        val candidates = if (exact.isNotEmpty()) exact else matches.filter {
            it.first.contains(query, ignoreCase = true)
        }

        if (candidates.isEmpty()) return "I couldn't find an installed app named $query."
        if (candidates.size > 1) {
            return "I found multiple matching apps: " +
                candidates.take(5).joinToString(", ") { it.first } +
                ". Please say the exact app name."
        }

        val chosen = candidates.first()
        return runCatching {
            val intent = context.packageManager.getLaunchIntentForPackage(chosen.second)
                ?: return "I couldn't open ${chosen.first}."
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Opening ${chosen.first}."
        }.getOrElse { "I couldn't open ${chosen.first}: ${it.message ?: "unknown error"}" }
    }
}
