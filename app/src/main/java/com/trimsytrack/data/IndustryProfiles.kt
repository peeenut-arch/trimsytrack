package com.trimsytrack.data

import java.time.DayOfWeek

enum class IndustryProfile(
    val id: String,
    val displayName: String,
    val defaultCategories: List<String>,
) {
    ELECTRICIAN(
        id = "electrician",
        displayName = "Elektriker",
        defaultCategories = listOf(
            "Elgrossist",
            "Byggvaruhus",
            "Postombud",
            "Paketutlämning",
            "Lager",
            "Förråd",
            "Verkstad",
            "Företagsadress",
        ),
    ),
    CARPENTER_BUILD(
        id = "carpenter_build",
        displayName = "Snickare / Bygg",
        defaultCategories = listOf(
            "Byggvaruhus",
            "Trähandel",
            "Byggmaterialgrossist",
            "Postombud",
            "Lager",
            "Förråd",
            "Verkstad",
            "Företagsadress",
        ),
    ),
    PLUMBER_HVAC(
        id = "plumber_hvac",
        displayName = "Rörmokare / VVS",
        defaultCategories = listOf(
            "VVS-grossist",
            "Byggvaruhus",
            "Postombud",
            "Lager",
            "Förråd",
            "Verkstad",
            "Företagsadress",
        ),
    ),
    ANTIQUE_DEALER(
        id = "antique_dealer",
        displayName = "Antikhandlare",
        defaultCategories = listOf(
            "Antikaffär",
            "Auktionshus",
            "Loppis",
            "Second hand-butik",
            "Postombud",
            "Speditör",
            "Återvinningscentral",
            "Lager",
            "Butik",
            "Företagsadress",
        ),
    ),
    RESELLER(
        id = "reseller",
        displayName = "Reseller",
        defaultCategories = listOf(
            "Second hand-butik",
            "Loppis",
            "Auktionshus",
            "Postombud",
            "Speditör",
            "Lager",
            "Packplats",
            "Företagsadress",
        ),
    ),
    CONSULTANT(
        id = "consultant",
        displayName = "Konsult",
        defaultCategories = listOf(
            "Kontor",
            "Kontorshotell",
            "Coworking space",
            "Tågstation",
            "Flygplats",
            "Hotell",
            "Företagsadress",
        ),
    ),
}

data class ProfileCategoryGroup(
    val title: String,
    val categories: List<String>,
)

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

object ProfileDefaults {
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

    fun profileById(id: String): IndustryProfile? = IndustryProfile.entries.firstOrNull { it.id == id }

    fun categoryGroupsFor(profile: IndustryProfile): List<ProfileCategoryGroup> {
        // Simple, readable grouping for the UI. The actual search terms remain the category strings.
        return when (profile) {
            IndustryProfile.ELECTRICIAN -> listOf(
                ProfileCategoryGroup("Material", listOf("Elgrossist", "Byggvaruhus")),
                ProfileCategoryGroup("Logistik", listOf("Postombud", "Paketutlämning")),
                ProfileCategoryGroup("Bas", listOf("Lager", "Förråd", "Verkstad", "Företagsadress")),
            )

            IndustryProfile.CARPENTER_BUILD -> listOf(
                ProfileCategoryGroup("Material", listOf("Byggvaruhus", "Trähandel", "Byggmaterialgrossist")),
                ProfileCategoryGroup("Logistik", listOf("Postombud")),
                ProfileCategoryGroup("Bas", listOf("Lager", "Förråd", "Verkstad", "Företagsadress")),
            )

            IndustryProfile.PLUMBER_HVAC -> listOf(
                ProfileCategoryGroup("Material", listOf("VVS-grossist", "Byggvaruhus")),
                ProfileCategoryGroup("Logistik", listOf("Postombud")),
                ProfileCategoryGroup("Bas", listOf("Lager", "Förråd", "Verkstad", "Företagsadress")),
            )

            IndustryProfile.ANTIQUE_DEALER -> listOf(
                ProfileCategoryGroup("Inköp", listOf("Antikaffär", "Auktionshus", "Loppis", "Second hand-butik")),
                ProfileCategoryGroup("Logistik", listOf("Postombud", "Speditör")),
                ProfileCategoryGroup("Avfall", listOf("Återvinningscentral")),
                ProfileCategoryGroup("Bas", listOf("Lager", "Butik", "Företagsadress")),
            )

            IndustryProfile.RESELLER -> listOf(
                ProfileCategoryGroup("Inköp", listOf("Second hand-butik", "Loppis", "Auktionshus")),
                ProfileCategoryGroup("Logistik", listOf("Postombud", "Speditör")),
                ProfileCategoryGroup("Bas", listOf("Lager", "Packplats", "Företagsadress")),
            )

            IndustryProfile.CONSULTANT -> listOf(
                ProfileCategoryGroup("Arbetsplats", listOf("Kontor", "Kontorshotell", "Coworking space")),
                ProfileCategoryGroup("Resa", listOf("Tågstation", "Flygplats", "Hotell")),
                ProfileCategoryGroup("Bas", listOf("Företagsadress")),
            )
        }
    }
}
