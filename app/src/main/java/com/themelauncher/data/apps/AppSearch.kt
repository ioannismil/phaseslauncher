package com.themelauncher.data.apps

import java.text.Normalizer

private val DIACRITICS = "\\p{Mn}+".toRegex()

/** Lowercase and strip accents so "camera" matches "Cámara" and "ελ" matches "Ελλάδα". */
fun String.normalizedForSearch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(DIACRITICS, "").lowercase()

/**
 * Filters [apps] (already alphabetical) by [query]. Apps whose label or a word in it starts
 * with the query come first, then any label or package name that merely contains it.
 */
fun filterApps(apps: List<AppInfo>, query: String): List<AppInfo> {
    val q = query.trim().normalizedForSearch()
    if (q.isEmpty()) return apps

    val prefixMatches = mutableListOf<AppInfo>()
    val otherMatches = mutableListOf<AppInfo>()
    for (app in apps) {
        val label = app.label.normalizedForSearch()
        when {
            label.split(' ').any { it.startsWith(q) } -> prefixMatches += app
            label.contains(q) || app.packageName.contains(q) -> otherMatches += app
        }
    }
    return prefixMatches + otherMatches
}
