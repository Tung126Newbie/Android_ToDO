package com.example.simplenotes.util

import java.text.Normalizer
import java.util.regex.Pattern

object StringUtils {
    private val DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")

    fun removeAccents(src: String): String {
        if (src.isBlank()) return ""
        val nfdNormalizedString = Normalizer.normalize(src, Normalizer.Form.NFD)
        return DIACRITICS_PATTERN.matcher(nfdNormalizedString)
            .replaceAll("")
            .replace('đ', 'd')
            .replace('Đ', 'D')
            .lowercase()
    }
}
