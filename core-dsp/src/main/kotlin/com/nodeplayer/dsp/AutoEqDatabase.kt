package com.nodeplayer.dsp

import com.nodeplayer.model.AutoEqFilter
import com.nodeplayer.model.AutoEqProfile

/**
 * Built-in catalog of curated AutoEQ profiles for popular IEMs and headphones.
 * Converted to 10-band parametric EQ curves targeting the Harman Over-Ear / In-Ear Target.
 */
object AutoEqDatabase {

    val PROFILES: List<AutoEqProfile> = listOf(
        AutoEqProfile(
            name = "Sony WH-1000XM4 (Harman Target)",
            preampDb = -4.5f,
            filters = listOf(
                AutoEqFilter(0, true, "PK", 31f, -1.2f, 1.4f),
                AutoEqFilter(1, true, "PK", 62f, -2.5f, 1.4f),
                AutoEqFilter(2, true, "PK", 125f, -3.8f, 1.4f),
                AutoEqFilter(3, true, "PK", 250f, -2.1f, 1.4f),
                AutoEqFilter(4, true, "PK", 500f, 0.4f, 1.4f),
                AutoEqFilter(5, true, "PK", 1000f, 1.8f, 1.4f),
                AutoEqFilter(6, true, "PK", 2000f, 2.5f, 1.4f),
                AutoEqFilter(7, true, "PK", 4000f, -1.0f, 1.4f),
                AutoEqFilter(8, true, "PK", 8000f, 2.0f, 1.4f),
                AutoEqFilter(9, true, "PK", 16000f, -0.5f, 1.4f)
            )
        ),
        AutoEqProfile(
            name = "Sony WH-1000XM5 (Harman Target)",
            preampDb = -4.0f,
            filters = listOf(
                AutoEqFilter(0, true, "PK", 31f, -0.8f, 1.4f),
                AutoEqFilter(1, true, "PK", 62f, -1.8f, 1.4f),
                AutoEqFilter(2, true, "PK", 125f, -2.6f, 1.4f),
                AutoEqFilter(3, true, "PK", 250f, -1.5f, 1.4f),
                AutoEqFilter(4, true, "PK", 500f, 0.2f, 1.4f),
                AutoEqFilter(5, true, "PK", 1000f, 1.5f, 1.4f),
                AutoEqFilter(6, true, "PK", 2000f, 2.0f, 1.4f),
                AutoEqFilter(7, true, "PK", 4000f, -0.5f, 1.4f),
                AutoEqFilter(8, true, "PK", 8000f, 1.5f, 1.4f),
                AutoEqFilter(9, true, "PK", 16000f, 0.0f, 1.4f)
            )
        ),
        AutoEqProfile(
            name = "Sennheiser HD 600 (Harman Target)",
            preampDb = -5.8f,
            filters = listOf(
                AutoEqFilter(0, true, "PK", 31f, 5.5f, 1.4f),
                AutoEqFilter(1, true, "PK", 62f, 4.0f, 1.4f),
                AutoEqFilter(2, true, "PK", 125f, 1.5f, 1.4f),
                AutoEqFilter(3, true, "PK", 250f, -0.5f, 1.4f),
                AutoEqFilter(4, true, "PK", 500f, 0.0f, 1.4f),
                AutoEqFilter(5, true, "PK", 1000f, -0.5f, 1.4f),
                AutoEqFilter(6, true, "PK", 2000f, -1.2f, 1.4f),
                AutoEqFilter(7, true, "PK", 4000f, 1.0f, 1.4f),
                AutoEqFilter(8, true, "PK", 8000f, 0.5f, 1.4f),
                AutoEqFilter(9, true, "PK", 16000f, 1.2f, 1.4f)
            )
        ),
        AutoEqProfile(
            name = "Sennheiser HD 650 / 6XX (Harman Target)",
            preampDb = -6.0f,
            filters = listOf(
                AutoEqFilter(0, true, "PK", 31f, 6.0f, 1.4f),
                AutoEqFilter(1, true, "PK", 62f, 4.2f, 1.4f),
                AutoEqFilter(2, true, "PK", 125f, 1.2f, 1.4f),
                AutoEqFilter(3, true, "PK", 250f, -1.0f, 1.4f),
                AutoEqFilter(4, true, "PK", 500f, 0.0f, 1.4f),
                AutoEqFilter(5, true, "PK", 1000f, -0.8f, 1.4f),
                AutoEqFilter(6, true, "PK", 2000f, -1.0f, 1.4f),
                AutoEqFilter(7, true, "PK", 4000f, 1.5f, 1.4f),
                AutoEqFilter(8, true, "PK", 8000f, 1.0f, 1.4f),
                AutoEqFilter(9, true, "PK", 16000f, 1.5f, 1.4f)
            )
        ),
        AutoEqProfile(
            name = "Apple AirPods Pro 2 (Harman Target)",
            preampDb = -3.2f,
            filters = listOf(
                AutoEqFilter(0, true, "PK", 31f, 0.5f, 1.4f),
                AutoEqFilter(1, true, "PK", 62f, 0.0f, 1.4f),
                AutoEqFilter(2, true, "PK", 125f, -0.8f, 1.4f),
                AutoEqFilter(3, true, "PK", 250f, -0.5f, 1.4f),
                AutoEqFilter(4, true, "PK", 500f, 0.0f, 1.4f),
                AutoEqFilter(5, true, "PK", 1000f, 0.5f, 1.4f),
                AutoEqFilter(6, true, "PK", 2000f, 1.0f, 1.4f),
                AutoEqFilter(7, true, "PK", 4000f, -1.5f, 1.4f),
                AutoEqFilter(8, true, "PK", 8000f, 2.2f, 1.4f),
                AutoEqFilter(9, true, "PK", 16000f, -0.8f, 1.4f)
            )
        ),
        AutoEqProfile(
            name = "Moondrop Blessing 2 (Harman Target)",
            preampDb = -4.0f,
            filters = listOf(
                AutoEqFilter(0, true, "PK", 31f, 3.8f, 1.4f),
                AutoEqFilter(1, true, "PK", 62f, 2.5f, 1.4f),
                AutoEqFilter(2, true, "PK", 125f, 0.8f, 1.4f),
                AutoEqFilter(3, true, "PK", 250f, 0.0f, 1.4f),
                AutoEqFilter(4, true, "PK", 500f, 0.0f, 1.4f),
                AutoEqFilter(5, true, "PK", 1000f, 0.0f, 1.4f),
                AutoEqFilter(6, true, "PK", 2000f, 0.5f, 1.4f),
                AutoEqFilter(7, true, "PK", 4000f, -1.0f, 1.4f),
                AutoEqFilter(8, true, "PK", 8000f, -2.5f, 1.4f),
                AutoEqFilter(9, true, "PK", 16000f, 0.5f, 1.4f)
            )
        ),
        AutoEqProfile(
            name = "Moondrop Aria / Chu (Harman Target)",
            preampDb = -3.5f,
            filters = listOf(
                AutoEqFilter(0, true, "PK", 31f, 1.5f, 1.4f),
                AutoEqFilter(1, true, "PK", 62f, 0.8f, 1.4f),
                AutoEqFilter(2, true, "PK", 125f, -0.5f, 1.4f),
                AutoEqFilter(3, true, "PK", 250f, 0.0f, 1.4f),
                AutoEqFilter(4, true, "PK", 500f, 0.2f, 1.4f),
                AutoEqFilter(5, true, "PK", 1000f, 0.0f, 1.4f),
                AutoEqFilter(6, true, "PK", 2000f, 0.8f, 1.4f),
                AutoEqFilter(7, true, "PK", 4000f, -2.0f, 1.4f),
                AutoEqFilter(8, true, "PK", 8000f, -1.5f, 1.4f),
                AutoEqFilter(9, true, "PK", 16000f, 1.0f, 1.4f)
            )
        ),
        AutoEqProfile(
            name = "Tangzu Wan'er S.G (Harman Target)",
            preampDb = -3.8f,
            filters = listOf(
                AutoEqFilter(0, true, "PK", 31f, 1.8f, 1.4f),
                AutoEqFilter(1, true, "PK", 62f, 0.5f, 1.4f),
                AutoEqFilter(2, true, "PK", 125f, -1.0f, 1.4f),
                AutoEqFilter(3, true, "PK", 250f, -0.5f, 1.4f),
                AutoEqFilter(4, true, "PK", 500f, 0.0f, 1.4f),
                AutoEqFilter(5, true, "PK", 1000f, 0.4f, 1.4f),
                AutoEqFilter(6, true, "PK", 2000f, 1.2f, 1.4f),
                AutoEqFilter(7, true, "PK", 4000f, -1.8f, 1.4f),
                AutoEqFilter(8, true, "PK", 8000f, -0.8f, 1.4f),
                AutoEqFilter(9, true, "PK", 16000f, 0.5f, 1.4f)
            )
        ),
        AutoEqProfile(
            name = "7Hz Zero / Zero 2 (Harman Target)",
            preampDb = -3.5f,
            filters = listOf(
                AutoEqFilter(0, true, "PK", 31f, 2.2f, 1.4f),
                AutoEqFilter(1, true, "PK", 62f, 1.0f, 1.4f),
                AutoEqFilter(2, true, "PK", 125f, -0.6f, 1.4f),
                AutoEqFilter(3, true, "PK", 250f, 0.0f, 1.4f),
                AutoEqFilter(4, true, "PK", 500f, 0.0f, 1.4f),
                AutoEqFilter(5, true, "PK", 1000f, 0.2f, 1.4f),
                AutoEqFilter(6, true, "PK", 2000f, 0.5f, 1.4f),
                AutoEqFilter(7, true, "PK", 4000f, -1.2f, 1.4f),
                AutoEqFilter(8, true, "PK", 8000f, -1.0f, 1.4f),
                AutoEqFilter(9, true, "PK", 16000f, 0.8f, 1.4f)
            )
        )
    )

    fun search(query: String): List<AutoEqProfile> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return PROFILES
        return PROFILES.filter { it.name.lowercase().contains(q) }
    }
}
