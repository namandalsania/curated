package com.curated.app.core.format

/** "JP" -> 🇯🇵, via the two regional-indicator letters. Null for anything that isn't two A-Z letters. */
fun flagEmoji(isoCode: String): String? {
    if (isoCode.length != 2 || !isoCode.all { it in 'A'..'Z' }) return null
    return isoCode.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
}
