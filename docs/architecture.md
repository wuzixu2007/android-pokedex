# Pokedex architecture

The app is a single Android application module using Jetpack Compose, MVVM-style state ownership, StateFlow, and repository/store boundaries.

## Layers

- `presentation.scanner`: Compose screens, navigation shell, settings, scanner, catalog, gallery, and presentation state. `PageRegistry.kt` is the single registration point for accessible pages, primary-page order, and page render entry points. `MainActivity` is the Android entry point.
- `presentation.games`: registry-driven mini-game host and one registration file per game. New games are enabled only through `MiniGameRegistry.kt`.
- `presentation.scanner.ScannerViewModel`: coordinates camera capture, image preprocessing, recognition, narration, persistence, and UI state transitions.
- `domain.scanner`: UI- and Android-independent state, settings, recognition runtime models, and type-chart logic.
- `data.scanner`: optional user-configured AI recognition, APK-backed catalog data, SharedPreferences settings, app-private files, audio, feedback, and the local Pokemon gallery.
- `assets/` and `res/`: bundled catalog data, Pokemon artwork, audio, and Android resources. The app does not download a project-owned resource manifest.

## Data flow

CameraX capture -> local image preprocessing -> optional user-configured recognition repository -> candidate protocol parsing -> `ScannerUiState` -> Compose UI. Successful recognition writes the normalized JPEG through `PokemonPhotoStore`; there is no photo-reporting side channel.

## Organization rules

- Keep generated output under `build/` and never commit it.
- Keep installable APKs, large ZIPs and release metadata under `artifacts/`.
- Keep non-project documents outside this Android workspace.
- Add new persistence or network access behind a store/repository instead of directly from Compose UI.
