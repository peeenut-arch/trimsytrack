package com.trimsytrack.data.entities

/**
 * High-level place classification for audit-friendly trip logs.
 *
 * Keep values stable; they are persisted.
 */
enum class PlaceType {
    HOME,
    WAREHOUSE,
    STORE,
    SUPPLIER,
    OTHER,
}
