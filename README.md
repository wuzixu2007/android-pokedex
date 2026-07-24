# Pokédex Scanner

An offline Android Pokédex scanner built with Kotlin, Jetpack Compose, CameraX, and a local MiniCPM-V/llama.cpp runtime.

> This is an independent community project for learning and research. It is not affiliated with Nintendo, Game Freak, or The Pokémon Company.

## Features

- Camera and photo-picker based scanning.
- On-device MiniCPM-V vision-language inference through llama.cpp/libmtmd.
- Strict JSON candidate parsing with a local Pokémon-name whitelist.
- Single-candidate and five-candidate recognition modes.
- Local Pokédex catalog with names, forms, abilities, types, profiles, and base stats.
- Mechanical Pokédex-style Compose UI, catalog search, detail pages, feedback export, TTS narration, and replaceable sound effects.
- No image or model data is uploaded by the app.

## Requirements

- Android Studio Hedgehog or newer.
- Android SDK 36, NDK `29.0.14206865`, and CMake `3.22.1`.
- Android 9 (API 28) or newer.
- ARM64 device (`arm64-v8a`); 8 GB or more RAM is recommended for local VLM inference.
- Node.js for catalog and sound-asset generation.

## Build

```powershell
node tools/generate_pokemon_catalog.mjs
node tools/generate_default_sfx.mjs
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Models

GGUF model files are intentionally not included in this repository. Import them in the app through the model manager. The expected files are:

- `MiniCPM-V-4_6-Q4_K_M.gguf`
- `mmproj-model-f16.gguf`

Do not commit model files, private photos, feedback exports, signing keys, or `local.properties`.

## Project layout

```text
app/src/main/java/com/example/pokedex/   Kotlin UI and application logic
app/src/main/cpp/                         JNI bridge and llama.cpp runtime
app/src/main/assets/pokemon/              Generated compact catalog and images
app/src/main/res/raw/                     Default sound effects
tools/                                    Catalog, sound, and feedback tools
docs/                                     Development and architecture documents
```

## License and third-party notices

Application code is released under [Apache License 2.0](LICENSE). Vendored llama.cpp code remains under its own license and notices. Pokémon names, artwork, and related data are trademarks or copyrighted materials of their respective owners; review the source-data licenses before redistribution.

## Contributing

Issues and pull requests are welcome. Please include reproduction steps, device/Android version, and relevant logs with personal data and model output removed.
