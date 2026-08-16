package com.example.pokedex.presentation.scanner

import androidx.camera.core.SurfaceRequest
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import com.example.pokedex.data.scanner.AppSoundEffect
import com.example.pokedex.data.scanner.PokemonCatalog
import com.example.pokedex.data.scanner.PokemonDetailsRepository
import com.example.pokedex.data.scanner.PokemonPhotoStore
import com.example.pokedex.domain.scanner.ScannerPage
import com.example.pokedex.domain.scanner.ScannerUiState
import com.example.pokedex.presentation.games.MiniGamesPage

/** Direction used by the registry-driven primary-page pager and indicator. */
internal enum class PageOrbitalDirection { Forward, Backward }

/** A visual indicator that may or may not resolve to a navigable page. */
internal data class PageOrbitalItem(
    val page: ScannerPage?,
    val color: Color,
)

/** One app page's registration metadata and rendering entry point. */
internal data class PageDefinition(
    val page: ScannerPage,
    val enabled: Boolean,
    val primaryNavigation: Boolean,
    val indicatorColor: Color,
    val showScannerControls: Boolean = true,
    val contentSlotTestTag: String,
    val content: @Composable (PokedexPageHost) -> Unit,
)

/** Dependencies supplied by the scanner screen to independently registered app pages. */
internal data class PokedexPageHost(
    val state: ScannerUiState,
    val catalog: PokemonCatalog,
    val surfaceRequest: SurfaceRequest?,
    val useFakeCamera: Boolean,
    val pokemonPhotoStore: PokemonPhotoStore?,
    val detailsRepository: PokemonDetailsRepository,
    val detailsPageIndex: Int,
    val onRequestPermission: () -> Unit,
    val onRetry: () -> Unit,
    val onOpenPokemonDetail: (Int) -> Unit,
    val onOpenPokemonGallery: (Int) -> Unit,
    val onZoomCamera: (Float) -> Unit,
    val onPlaySound: (AppSoundEffect) -> Unit,
    val onSpeakText: (String) -> Unit,
    val onScoreChanged: (Int) -> Unit,
)

/**
 * The only place where app pages are enabled, ordered, and assigned primary navigation.
 * Page UI remains in its feature file; registering a primary page here makes it available to
 * the pager and the shell's orbital indicator.
 */
internal class PageRegistry(
    pages: List<PageDefinition>,
    private val visualPlaceholders: List<PageOrbitalItem> = emptyList(),
) {
    val pages: List<PageDefinition> = pages.toList()
    val enabledPages: List<PageDefinition> get() = pages.filter(PageDefinition::enabled)
    val primaryPages: List<PageDefinition> get() = enabledPages.filter(PageDefinition::primaryNavigation)
    val orbitalItems: List<PageOrbitalItem>
        get() = primaryPages.map { PageOrbitalItem(it.page, it.indicatorColor) } + visualPlaceholders

    fun enabledPage(page: ScannerPage): PageDefinition? =
        enabledPages.firstOrNull { it.page == page }

    fun primaryDestination(page: ScannerPage, direction: PageOrbitalDirection): ScannerPage? {
        val activeIndex = primaryPages.indexOfFirst { it.page == page }
        if (activeIndex < 0 || primaryPages.size < 2) return null
        val offset = if (direction == PageOrbitalDirection.Forward) 1 else -1
        return primaryPages[(activeIndex + offset).mod(primaryPages.size)].page
    }
}

internal val defaultPageRegistry = PageRegistry(
    pages = listOf(
        PageDefinition(
            page = ScannerPage.Scanner,
            enabled = true,
            primaryNavigation = true,
            indicatorColor = Color(0xFF7E57C2),
            contentSlotTestTag = "scanner_content_slot",
            content = { host ->
                ScannerViewport(
                    state = host.state,
                    surfaceRequest = host.surfaceRequest,
                    useFakeCamera = host.useFakeCamera,
                    catalog = host.catalog,
                    detailsRepository = host.detailsRepository,
                    detailsPageIndex = host.detailsPageIndex,
                    onRequestPermission = host.onRequestPermission,
                    onRetry = host.onRetry,
                    onOpenPokemonDetail = host.onOpenPokemonDetail,
                    onOpenPokemonGallery = host.onOpenPokemonGallery,
                    onZoomCamera = host.onZoomCamera,
                )
            },
        ),
        PageDefinition(
            page = ScannerPage.Games,
            enabled = true,
            primaryNavigation = true,
            indicatorColor = Color(0xFFE96B9A),
            showScannerControls = false,
            contentSlotTestTag = "games_content_slot",
            content = { host ->
                MiniGamesPage(
                    catalog = host.catalog,
                    onPlaySound = host.onPlaySound,
                    onSpeakText = host.onSpeakText,
                    onScoreChanged = host.onScoreChanged,
                    modifier = Modifier.fillMaxSize(),
                )
            },
        ),
        PageDefinition(
            page = ScannerPage.Catalog,
            enabled = true,
            primaryNavigation = false,
            indicatorColor = Color(0xFFF4C451),
            contentSlotTestTag = "catalog_content_slot",
            content = { host ->
                CatalogPage(
                    catalog = host.catalog,
                    collectedKeys = host.state.collectedKeys,
                    onPokemonClick = { pokemon ->
                        host.catalog.records.indexOfFirst { it.key == pokemon.key }
                            .takeIf { it >= 0 }
                            ?.let(host.onOpenPokemonDetail)
                    },
                )
            },
        ),
        PageDefinition(
            page = ScannerPage.CatalogDetail,
            enabled = true,
            primaryNavigation = false,
            indicatorColor = Color(0xFF54A76E),
            contentSlotTestTag = "catalog_detail_content_slot",
            content = { host ->
                host.state.catalogPokemonIndex?.let { pokemonIndex ->
                    val pokemon = host.catalog.recordAt(pokemonIndex)
                    CatalogDetailPage(
                        pokemon = pokemon,
                        catalog = host.catalog,
                        collected = pokemon.key in host.state.collectedKeys,
                        detailsRepository = host.detailsRepository,
                        pageIndex = host.detailsPageIndex,
                        onOpenPokemonDetail = host.onOpenPokemonDetail,
                        onOpenGallery = { host.state.catalogPokemonIndex?.let(host.onOpenPokemonGallery) },
                    )
                }
            },
        ),
        PageDefinition(
            page = ScannerPage.PokemonGallery,
            enabled = true,
            primaryNavigation = false,
            indicatorColor = Color(0xFF5A9DDE),
            contentSlotTestTag = "pokemon_gallery_content_slot",
            content = { host ->
                host.state.galleryPokemonIndex?.let { index ->
                    host.pokemonPhotoStore?.let { store ->
                        PokemonGalleryPage(
                            pokemon = host.catalog.recordAt(index),
                            store = store,
                            onBack = { host.onOpenPokemonDetail(index) },
                        )
                    }
                }
            },
        ),
    ),
    visualPlaceholders = listOf(
        PageOrbitalItem(page = null, color = Color(0xFFF4C451)),
        PageOrbitalItem(page = null, color = Color(0xFF54A76E)),
    ),
)
