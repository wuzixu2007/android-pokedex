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

## Download and install the APK

打开 GitHub 仓库的 [Releases](https://github.com/wuzixu2007/android-pokedex/releases) 页面下载最新 APK。源码仓库不直接提交 APK；如暂时没有 Release，可在本地构建 Debug APK：

```powershell
.\gradlew.bat :app:assembleDebug
```

生成文件：

```text
app/build/outputs/apk/debug/app-debug.apk
```

通过 ADB 安装到已连接的 Android 设备：

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`-r` 会覆盖安装并保留应用数据、模型和标注记录。首次使用仍需在应用内导入 GGUF 模型。

## Models

GGUF model files are intentionally not included in this repository. Import them in the app through the model manager. The expected files are:

- Language model: [MiniCPM-V-4_6-Q5_K_M.gguf](https://huggingface.co/openbmb/MiniCPM-V-4.6-gguf/blob/main/MiniCPM-V-4_6-Q5_K_M.gguf)
- Vision projector: [mmproj-model-f16.gguf](https://huggingface.co/openbmb/MiniCPM-V-4.6-gguf/blob/main/mmproj-model-f16.gguf)

Download both files from the official OpenBMB Hugging Face repository, then import them from the in-app model manager. Q5 generally needs more memory than Q4. If the installed app validates an exact filename, size, or SHA-256, update that allowlist together with the selected quantization before importing.

Do not commit model files, private photos, feedback exports, signing keys, or `local.properties`.

## Project layout

```text
pokedex/
├── app/
│   ├── build.gradle.kts                  Android dependencies and build options
│   ├── src/main/AndroidManifest.xml      App, camera, and file-picker metadata
│   ├── src/main/assets/pokemon/          Generated catalog.json and Pokémon images
│   ├── src/main/cpp/                     JNI bridge and native inference
│   │   └── third_party/llama.cpp/        Vendored llama.cpp/libmtmd and notices
│   ├── src/main/java/com/example/pokedex/
│   │   ├── MainActivity.kt               Compose entry point
│   │   └── ui/
│   │       ├── scanner/                   Scanner, catalog, detail, and AI logic
│   │       │   ├── ScannerScreen.kt       Main Compose screens
│   │       │   ├── ScannerShell.kt        Mechanical shell and controls
│   │       │   ├── ScannerViewModel.kt    StateFlow orchestration
│   │       │   ├── ScannerState.kt        UI state and actions
│   │       │   ├── CameraSession.kt       CameraX lifecycle and capture
│   │       │   ├── RecognitionRuntime.kt JSON protocol/runtime adapter
│   │       │   ├── ModelStore.kt          GGUF import and validation
│   │       │   ├── PokemonCatalog.kt      Catalog entities and lookup
│   │       │   ├── PokemonNarrator.kt     Chinese TTS narration
│   │       │   ├── SoundEffects.kt        Replaceable sound effects
│   │       │   ├── ScannerSettings.kt    Persistent recognition console
│   │       │   └── FeedbackStore.kt       Correction and export data
│   │       └── theme/                     Compose theme and color tokens
│   ├── src/main/res/                      Icons, sounds, values, and XML rules
│   ├── src/test/                          JVM unit tests
│   └── src/androidTest/                   Compose/device tests
├── docs/                                  Development and architecture docs
├── tools/                                 Catalog, sound, and feedback scripts
├── gradle/                                Version catalog and Gradle wrapper
├── build.gradle.kts                       Root Gradle configuration
├── settings.gradle.kts                    Module/repository configuration
├── gradlew / gradlew.bat                  Reproducible build entry points
├── .gitignore                             Models, local config, builds, private data
├── LICENSE                                Apache License 2.0 for project code
└── README.md                              Project documentation
```

## License and third-party notices

Application code is released under [Apache License 2.0](LICENSE). Vendored llama.cpp code remains under its own license and notices. Pokémon names, artwork, and related data are trademarks or copyrighted materials of their respective owners; review the source-data licenses before redistribution.

## Contributing

Issues and pull requests are welcome. Please include reproduction steps, device/Android version, and relevant logs with personal data and model output removed.
