package dev.smallwins.puzzle.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogTest {
    @Test
    fun canonicalIdsAreUnique() {
        assertEquals(mechanicCatalog.size, mechanicCatalog.map { it.mechanicId }.distinct().size)
    }

    @Test
    fun compatibilityAliasesResolveWithoutIncludingProductActivities() {
        assertEquals("network", canonicalMechanicId("pipes"))
        assertEquals("route", canonicalMechanicId("waypoints"))
        assertEquals("habitat-search", canonicalMechanicId("wildlife-survey"))
        assertNull(canonicalMechanicId("layer-restore"))
    }

    @Test
    fun onlyExecutableMechanicsAdvertisePilotCapabilities() {
        val executable = setOf("color-neighbors", "habitat-search", "network", "packing", "purrdoku", "route", "shikaku", "sokoban", "untangle")
        mechanicCatalog.forEach { entry ->
            if (entry.mechanicId in executable) {
                assertEquals(LibraryStage.PILOT, entry.stage)
                assertTrue(entry.capabilities.isNotEmpty())
            } else {
                assertEquals(LibraryStage.RESEARCH, entry.stage)
                assertTrue(entry.capabilities.isEmpty())
            }
        }
    }
}
