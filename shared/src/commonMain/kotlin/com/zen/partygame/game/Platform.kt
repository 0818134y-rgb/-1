package com.zen.partygame.game

/** 全角・半角などをそろえる（NFKC） */
expect fun normalizeNfkc(s: String): String
