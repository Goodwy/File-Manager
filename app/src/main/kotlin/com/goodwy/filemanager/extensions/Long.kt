package com.goodwy.filemanager.extensions

import java.text.DecimalFormat
import kotlin.math.log10
import kotlin.math.pow

// use 1000 instead of 1024 at dividing
fun Long.formatSizeThousand(): String {
    if (this <= 0) {
        return "0 B"
    }

    val units = arrayOf("B", "kB", "MB", "GB", "TB")
    val digitGroups = (log10(toDouble()) / log10(1000.0)).toInt()
    return "${DecimalFormat("#,##0.#").format(this / 1000.0.pow(digitGroups.toDouble()))} ${units[digitGroups]}"
}
