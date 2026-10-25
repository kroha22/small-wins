package dev.smallwins.puzzle.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CatalogTest {
    @Test
    fun canonicalIdsAreUnique() {
        assertEquals(mechanicCatalog.size, mechanicCatalog.map { it.mechanicId }.distinct().size)
    }

    @Test
    fun compatibilityAliasesResolveWithoutIncludingProductActivities() {
        assertEquals("network", canonicalMechanicId("pipes"))
        assertEquals("route", canonicalMechanicId("waypoints"))
        assertNull(canonicalMechanicId("layer-restore"))
    }
}
