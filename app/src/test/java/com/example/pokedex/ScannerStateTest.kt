package com.example.pokedex

import com.example.pokedex.domain.scanner.*
import com.example.pokedex.data.scanner.*
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerStateTest {
    private val candidate = RankedPokemonCandidate(7, 0.964f, isShiny = true)

    @Test fun permissionGranted_entersPreview() {
        val state = reduceScannerState(ScannerUiState(ScannerMode.PermissionRequired), ScannerAction.PermissionChanged(true))
        assertEquals(ScannerMode.Preview, state.mode)
    }

    @Test fun captureFlow_keepsPhotoAndSingleResult() {
        val image = byteArrayOf(1, 2, 3)
        val capturing = reduceScannerState(ScannerUiState(ScannerMode.Preview), ScannerAction.Capture)
        val prepared = reduceScannerState(capturing, ScannerAction.CapturePrepared(image))
        val result = reduceScannerState(prepared, ScannerAction.CaptureCompleted(candidate))
        assertEquals(ScannerMode.Capturing, capturing.mode)
        assertArrayEquals(image, prepared.capturedImageJpeg)
        assertEquals(ScannerMode.Result(candidate), result.mode)
        assertTrue((result.mode as ScannerMode.Result).candidate.isShiny)
    }

    @Test fun reset_returnsToPreviewAndClearsPhoto() {
        val state = reduceScannerState(
            ScannerUiState(ScannerMode.Result(candidate), cameraPermissionGranted = true, capturedImageJpeg = byteArrayOf(1)),
            ScannerAction.Reset,
        )
        assertEquals(ScannerMode.Preview, state.mode)
        assertNull(state.capturedImageJpeg)
    }

    @Test fun failure_canRetry() {
        val failed = reduceScannerState(ScannerUiState(ScannerMode.Preview, cameraPermissionGranted = true), ScannerAction.Failed("网络失败"))
        assertEquals(ScannerMode.Error("网络失败"), failed.mode)
        assertEquals(ScannerMode.Preview, reduceScannerState(failed, ScannerAction.Retry).mode)
    }

    @Test fun catalogNavigation_preservesCollectionAndReturnsToCamera() {
        val keys = setOf("0001:一般")
        val start = ScannerUiState(ScannerMode.Result(candidate), cameraPermissionGranted = true, collectedKeys = keys)
        val catalog = reduceScannerState(start, ScannerAction.OpenCatalog)
        val camera = reduceScannerState(catalog, ScannerAction.BackToCamera)
        assertEquals(ScannerPage.Catalog, catalog.page)
        assertEquals(ScannerPage.Scanner, camera.page)
        assertEquals(keys, camera.collectedKeys)
        assertEquals(ScannerMode.Preview, camera.mode)
    }

    @Test fun gamesPage_preservesScannerSession() {
        val state = ScannerUiState(ScannerMode.Preview, cameraPermissionGranted = true)
        val games = reduceScannerState(state, ScannerAction.OpenGames)
        assertEquals(ScannerPage.Games, games.page)
        assertEquals(ScannerMode.Preview, games.mode)
        assertTrue(games.cameraPermissionGranted)
    }

    @Test fun registeredPrimaryNavigation_usesGenericPageAction() {
        val state = ScannerUiState(ScannerMode.Preview, cameraPermissionGranted = true)
        val games = reduceScannerState(state, ScannerAction.OpenPage(ScannerPage.Games))
        val scanner = reduceScannerState(games, ScannerAction.OpenPage(ScannerPage.Scanner))

        assertEquals(ScannerPage.Games, games.page)
        assertEquals(ScannerPage.Scanner, scanner.page)
        assertEquals(ScannerMode.Preview, scanner.mode)
    }

    @Test fun catalogDetail_andNarrationGate_useExactSelection() {
        val detail = reduceScannerState(
            ScannerUiState(ScannerMode.Preview, page = ScannerPage.Catalog),
            ScannerAction.OpenCatalogDetail(3),
        )
        assertTrue(detail.canNarratePokemon(3))
        assertFalse(detail.canNarratePokemon(2))
        val back = reduceScannerState(detail, ScannerAction.BackToCatalog)
        assertEquals(ScannerPage.Catalog, back.page)
        assertNull(back.catalogPokemonIndex)
    }

    @Test fun resultNarrationGate_rejectsStaleCandidate() {
        val result = ScannerUiState(ScannerMode.Result(candidate))
        assertTrue(result.canNarratePokemon(7))
        assertFalse(result.canNarratePokemon(6))
        assertFalse(reduceScannerState(result, ScannerAction.Reset).canNarratePokemon(7))
    }
}
