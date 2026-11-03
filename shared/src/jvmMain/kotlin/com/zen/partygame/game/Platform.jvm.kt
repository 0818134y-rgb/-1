package com.zen.partygame.game

import java.text.Normalizer

actual fun normalizeNfkc(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFKC)
