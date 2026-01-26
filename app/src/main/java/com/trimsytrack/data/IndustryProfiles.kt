package com.trimsytrack.data

import java.time.DayOfWeek

data class RadiusPreset(
    val id: String,
    val label: String,
    val radiusKm: Int,
)

data class DwellPreset(
    val label: String,
    val minutes: Int,
)

data class ActiveHoursPreset(
    val id: String,
    val label: String,
    val startMinutes: Int,
    val endMinutes: Int,
    val enabledDays: Set<DayOfWeek>,
)

object PresetDefaults {
    val radiusPresets: List<RadiusPreset> = listOf(
        RadiusPreset(id = "city", label = "Stad", radiusKm = 10),
        RadiusPreset(id = "standard", label = "Standard", radiusKm = 25),
        RadiusPreset(id = "rural", label = "Landsbygd", radiusKm = 50),
    )

    val dwellPresets: List<DwellPreset> = listOf(
        DwellPreset(label = "3 min", minutes = 3),
        DwellPreset(label = "5 min", minutes = 5),
        DwellPreset(label = "10 min", minutes = 10),
        DwellPreset(label = "15 min", minutes = 15),
    )

    val activeHoursPresets: List<ActiveHoursPreset> = listOf(
        ActiveHoursPreset(
            id = "workday_07_18",
            label = "07:00–18:00 (vardagar)",
            startMinutes = 7 * 60,
            endMinutes = 18 * 60,
            enabledDays = setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
        ),
        ActiveHoursPreset(
            id = "workday_06_17",
            label = "06:00–17:00 (vardagar)",
            startMinutes = 6 * 60,
            endMinutes = 17 * 60,
            enabledDays = setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
        ),
        ActiveHoursPreset(
            id = "workday_08_19",
            label = "08:00–19:00 (vardagar)",
            startMinutes = 8 * 60,
            endMinutes = 19 * 60,
            enabledDays = setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
        ),
        ActiveHoursPreset(
            id = "allweek_07_18",
            label = "07:00–18:00 (alla dagar)",
            startMinutes = 7 * 60,
            endMinutes = 18 * 60,
            enabledDays = DayOfWeek.entries.toSet(),
        ),
    )
}
