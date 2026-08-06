package com.wwwjsw.musicserver.helpers

import android.provider.MediaStore

object MusicFilter {
    private val excludedKeywords = listOf(
        "WhatsApp Audio",
        "WhatsApp Voice Notes",
        "Telegram Audio"
    )

    /**
     * Returns a selection string for MediaStore queries that excludes unwanted audio folders.
     * 
     * @param extraConditions Optional additional SQL conditions to be ANDed with the filter.
     * @return A SQL selection string.
     */
    fun getSelection(extraConditions: String? = null): String {
        val baseSelection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        
        // MediaStore.Audio.Media.DATA is deprecated in API 29+ but still the most reliable way 
        // to filter by file path for this purpose if we don't have a more structured way.
        // For scoped storage, we might need a different approach if DATA is null, 
        // but for general filtering of known directory names, it often still works or we can use relative_path.
        
        val filterSelection = excludedKeywords.joinToString(" AND ") {
            "${MediaStore.Audio.Media.DATA} NOT LIKE '%$it%'"
        }
        
        val combined = "($baseSelection) AND ($filterSelection)"
        return if (extraConditions != null) "($combined) AND ($extraConditions)" else combined
    }
}
