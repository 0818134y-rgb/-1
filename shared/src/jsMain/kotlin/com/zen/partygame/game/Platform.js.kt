package com.zen.partygame.game

actual fun normalizeNfkc(s: String): String = s.asDynamic().normalize("NFKC") as String
