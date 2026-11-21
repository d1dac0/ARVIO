package com.arflix.tv.ui.components

import androidx.media3.common.TrackSelectionParameters

/** Preview size must not limit decoding to a small card's dimensions. Keep ABR fallback. */
internal fun TrackSelectionParameters.forFullHdTrailer(lowRam: Boolean = false): TrackSelectionParameters = buildUpon()
    .setViewportSize(Int.MAX_VALUE, Int.MAX_VALUE, true)
    .setMaxVideoSize(if (lowRam) 1280 else 1920, if (lowRam) 720 else 1080)
    .build()
