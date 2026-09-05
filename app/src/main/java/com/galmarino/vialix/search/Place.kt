package com.galmarino.vialix.search

import uniffi.ferrostar.GeographicCoordinate

/** One geocoding hit: what to call it, where it is. */
data class Place(
    /** Primary label: POI, street or locality name. Never blank. */
    val name: String,
    /** Secondary line ("Unter den Linden 77, 10117 Berlin, Germany"), or null when nothing beyond [name] is known. */
    val address: String?,
    val coordinate: GeographicCoordinate,
)
