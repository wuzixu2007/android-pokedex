/* Pure scanner reducer tests. / 扫描器纯 reducer 测试。 */
package com.example.pokedex

import com.example.pokedex.ui.scanner.RankedPokemonCandidate
import com.example.pokedex.ui.scanner.RecognitionCandidate
import com.example.pokedex.ui.scanner.RecognitionMode
import com.example.pokedex.ui.scanner.RecognitionOptions
import com.example.pokedex.ui.scanner.PokemonCatalog
import com.example.pokedex.ui.scanner.PokemonRecord
import com.example.pokedex.ui.scanner.ScannerAction
import com.example.pokedex.ui.scanner.ScannerMode
import com.example.pokedex.ui.scanner.ScannerPage
import com.example.pokedex.ui.scanner.ScannerUiState
import com.example.pokedex.ui.scanner.canNarratePokemon
import com.example.pokedex.ui.scanner.reduceScannerState
import com.example.pokedex.ui.scanner.rankRecognitionCandidates
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerStateTest {
    @Test
    fun permissionGranted_entersPreview() {
        val state = reduceScannerState(
            ScannerUiState(ScannerMode.PermissionRequired),
            ScannerAction.PermissionChanged(granted = true),
        )

        assertEquals(ScannerMode.Preview, state.mode)
    }

    @Test
    fun captureFlow_keepsPhotoAndEntersFirstCandidate() {
        val image = byteArrayOf(1, 2, 3)
        val candidates = candidates(3)
        val capturing = reduceScannerState(
            ScannerUiState(ScannerMode.Preview),
            ScannerAction.Capture,
        )
        val prepared = reduceScannerState(capturing, ScannerAction.CapturePrepared(image))
        val result = reduceScannerState(prepared, ScannerAction.CaptureCompleted(candidates))

        assertEquals(ScannerMode.Capturing, capturing.mode)
        assertArrayEquals(image, prepared.capturedImageJpeg)
        assertEquals(ScannerMode.Result(0), result.mode)
        assertEquals(candidates, result.candidates)
    }

    @Test
    fun resultNavigation_wrapsWithinCandidates() {
        val start = ScannerUiState(
            mode = ScannerMode.Result(0),
            candidates = candidates(3),
        )

        val previous = reduceScannerState(start, ScannerAction.MoveResult(-1))
        val next = reduceScannerState(previous, ScannerAction.MoveResult(1))

        assertEquals(ScannerMode.Result(2), previous.mode)
        assertEquals(ScannerMode.Result(0), next.mode)
    }

    @Test
    fun singleResultNavigation_doesNotChangeCandidate() {
        val start = ScannerUiState(
            mode = ScannerMode.Result(0),
            candidates = candidates(1),
        )

        assertEquals(start, reduceScannerState(start, ScannerAction.MoveResult(-1)))
        assertEquals(start, reduceScannerState(start, ScannerAction.MoveResult(1)))
    }

    @Test
    fun recognitionMode_changesCandidateCountAndTokenBudget() {
        val single = RecognitionOptions.forMode(RecognitionMode.Single)
        val multiple = RecognitionOptions.forMode(RecognitionMode.Multiple)

        assertEquals(1, single.mode.candidateCount)
        assertEquals(48, single.maxTokens)
        assertEquals(5, multiple.mode.candidateCount)
        assertEquals(128, multiple.maxTokens)

        val updated = reduceScannerState(
            ScannerUiState(ScannerMode.Preview),
            ScannerAction.SetRecognitionMode(RecognitionMode.Multiple),
        )
        assertEquals(RecognitionMode.Multiple, updated.recognitionMode)
    }

    @Test
    fun reset_returnsToPreviewAndClearsRecognitionData() {
        val state = reduceScannerState(
            ScannerUiState(
                mode = ScannerMode.Result(2),
                capturedImageJpeg = byteArrayOf(1),
                candidates = candidates(3),
            ),
            ScannerAction.Reset,
        )

        assertEquals(ScannerMode.Preview, state.mode)
        assertNull(state.capturedImageJpeg)
        assertEquals(emptyList<RankedPokemonCandidate>(), state.candidates)
    }

    @Test
    fun failure_canRetry() {
        val failed = reduceScannerState(
            ScannerUiState(ScannerMode.Preview),
            ScannerAction.Failed("No camera"),
        )
        val retried = reduceScannerState(failed, ScannerAction.Retry)

        assertEquals(ScannerMode.Error("No camera"), failed.mode)
        assertEquals(ScannerMode.Preview, retried.mode)
    }

    @Test
    fun catalogNavigation_opensCatalogAndBackClearsRecognition() {
        val resultState = ScannerUiState(
            mode = ScannerMode.Result(0),
            capturedImageJpeg = byteArrayOf(1),
            candidates = candidates(5),
        )

        val catalog = reduceScannerState(resultState, ScannerAction.OpenCatalog)
        val camera = reduceScannerState(catalog, ScannerAction.BackToCamera)

        assertEquals(ScannerPage.Catalog, catalog.page)
        assertEquals(ScannerPage.Scanner, camera.page)
        assertEquals(ScannerMode.Preview, camera.mode)
        assertNull(camera.capturedImageJpeg)
        assertEquals(emptyList<RankedPokemonCandidate>(), camera.candidates)
    }

    @Test
    fun catalogDetail_keepsSelectedRecordAndReturnsToCatalog() {
        val catalog = reduceScannerState(
            ScannerUiState(ScannerMode.Preview, page = ScannerPage.Catalog),
            ScannerAction.OpenCatalogDetail(3),
        )
        val back = reduceScannerState(catalog, ScannerAction.BackToCatalog)

        assertEquals(ScannerPage.CatalogDetail, catalog.page)
        assertEquals(3, catalog.catalogPokemonIndex)
        assertEquals(ScannerPage.Catalog, back.page)
        assertNull(back.catalogPokemonIndex)
    }

    @Test
    fun narrationGate_rejectsStaleEventsAfterLeavingDetailOrResult() {
        val detail = ScannerUiState(
            mode = ScannerMode.Preview,
            page = ScannerPage.CatalogDetail,
            catalogPokemonIndex = 3,
        )
        val result = ScannerUiState(
            mode = ScannerMode.Result(0),
            candidates = candidates(1),
        )

        assertTrue(detail.canNarratePokemon(3))
        assertFalse(detail.canNarratePokemon(2))
        assertTrue(result.canNarratePokemon(0))
        assertFalse(reduceScannerState(detail, ScannerAction.BackToCatalog).canNarratePokemon(3))
        assertFalse(reduceScannerState(result, ScannerAction.Reset).canNarratePokemon(0))
    }

    @Test
    fun recognitionCandidates_areSortedDeduplicatedAndLimitedToFive() {
        val catalog = PokemonCatalog.fromRecords(
            List(6) { index -> record(index) },
        )
        val ranked = rankRecognitionCandidates(
            catalog = catalog,
            candidates = listOf(
                RecognitionCandidate("Pokemon 2", 0.2f),
                RecognitionCandidate("Pokemon 1", 0.8f),
                RecognitionCandidate("Pokemon 3", 0.5f),
                RecognitionCandidate("Pokemon 2", 0.9f),
                RecognitionCandidate("Pokemon 4", 0.4f),
                RecognitionCandidate("Pokemon 5", 0.3f),
                RecognitionCandidate("Pokemon 6", 0.1f),
            ),
        )

        assertEquals(5, ranked.size)
        assertEquals(listOf(1, 0, 2, 3, 4), ranked.map { it.pokemonIndex })
        assertEquals(listOf(0.9f, 0.8f, 0.5f, 0.4f, 0.3f), ranked.map { it.probability })
    }

    private fun candidates(count: Int) = List(count) { index ->
        RankedPokemonCandidate(
            pokemonIndex = index,
            probability = (count - index) / count.toFloat(),
        )
    }

    private fun record(index: Int) = PokemonRecord(
        key = "p$index",
        id = index.toString(),
        nameZh = "Pokemon ${index + 1}",
        nameEn = "Pokemon ${index + 1}",
        types = listOf("Normal"),
        attributeLabel = "Normal Pokemon",
        category = "Pokemon",
        height = "1.0m",
        weight = "1.0kg",
        description = "",
        profile = "Profile",
        imageAsset = "",
    )
}
