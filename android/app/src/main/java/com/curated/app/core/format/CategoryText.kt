package com.curated.app.core.format

import com.curated.app.core.model.StopCategory

/** "Food", "Sight", "Stay"... - how a stop's category reads in the UI. */
fun StopCategory.label(): String = when (this) {
    StopCategory.FOOD -> "Food"
    StopCategory.SIGHT -> "Sight"
    StopCategory.HOTEL -> "Stay"
    StopCategory.TRANSPORT -> "Transport"
    StopCategory.OTHER -> "Other"
}
