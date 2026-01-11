package com.trimsytrack.data.entities

/**
 * How the trip distance was computed.
 *
 * Keep values stable; they are persisted.
 */
enum class DistanceMethod {
    /** Google/Maps routing (preferred). */
    MAPS,

    /** Straight-line estimate fallback. */
    GPS_STRAIGHT_LINE,

    /** Manually entered by user. */
    MANUAL,

    /** Legacy/unknown. */
    UNKNOWN,
}
