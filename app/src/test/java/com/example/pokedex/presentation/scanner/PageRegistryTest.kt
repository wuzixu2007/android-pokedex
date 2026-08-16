package com.example.pokedex.presentation.scanner

import androidx.compose.ui.graphics.Color
import com.example.pokedex.domain.scanner.ScannerPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageRegistryTest {
    @Test
    fun defaultRegistry_registersEveryAccessiblePage() {
        assertEquals(
            listOf(
                ScannerPage.Scanner,
                ScannerPage.Games,
                ScannerPage.Catalog,
                ScannerPage.CatalogDetail,
                ScannerPage.PokemonGallery,
            ),
            defaultPageRegistry.enabledPages.map { it.page },
        )
    }

    @Test
    fun primaryNavigation_usesRegisteredOrderAndSkipsVisualPlaceholders() {
        assertEquals(
            listOf(ScannerPage.Scanner, ScannerPage.Games),
            defaultPageRegistry.primaryPages.map { it.page },
        )
        assertEquals(
            ScannerPage.Games,
            defaultPageRegistry.primaryDestination(ScannerPage.Scanner, PageOrbitalDirection.Forward),
        )
        assertEquals(
            ScannerPage.Scanner,
            defaultPageRegistry.primaryDestination(ScannerPage.Games, PageOrbitalDirection.Forward),
        )
        assertEquals(2, defaultPageRegistry.orbitalItems.count { it.page == null })
    }

    @Test
    fun disabledPage_isNotResolvableOrNavigable() {
        val registry = PageRegistry(
            pages = listOf(
                PageDefinition(
                    page = ScannerPage.Games,
                    enabled = false,
                    primaryNavigation = true,
                    indicatorColor = Color.Red,
                    contentSlotTestTag = "disabled_page",
                    content = { _ -> },
                ),
            ),
        )

        assertNull(registry.enabledPage(ScannerPage.Games))
        assertNull(registry.primaryDestination(ScannerPage.Games, PageOrbitalDirection.Forward))
    }
}
