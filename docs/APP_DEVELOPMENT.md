# Pokédex Scanner App Development Guide

> 中文名称：Pokédex 宝可梦扫描仪 App 完整开发文档  
> Platform: Android 9+ (API 28+), ARM64  
> UI: Kotlin, Jetpack Compose, Material 3  
> Recognition: local MiniCPM-V 4.6 + mmproj through llama.cpp/libmtmd

## 1. Product Scope

Pokédex Scanner is an offline-first Android application that captures or imports a Pokémon image, runs local visual-language inference, validates one or five candidate names, and renders the matching local Pokédex record. Images, models, custom sounds, and feedback samples remain on the device unless the user explicitly exports a feedback package.

当前版本包含：

- CameraX Compose 相机预览与拍照。
- 拍照后冻结原图，并在分析阶段持续展示。
- MiniCPM-V 4.6 语言 GGUF 与 mmproj 视觉投影 GGUF 的本地导入、校验和推理。
- 单候选与五候选识别模式。
- 1082 个标准中文名称白名单、严格 JSON 与 GBNF 解码约束。
- 本地图鉴查询、属性、特性、六项种族值、官方图片和习性展示。
- 正确、纠正、非宝可梦标注与 ZIP 导出。
- 系统中文 TTS 播报。
- 可替换应用音效：扫描开始、识别成功、识别失败。
- 基础、性能、解码、语音、音效五页参数控制台。

不包含：

- 云端识别、账号系统或图片自动上传。
- 在手机端直接训练模型。
- 官方动画配音或游戏提取音效。
- 对通用 VLM 识别准确率的保证。

## 2. Development Environment

| Item | Version/Constraint |
|---|---|
| Android Gradle Plugin | 9.2.1 |
| Kotlin | 2.2.10 |
| Compile/Target SDK | 36 |
| Minimum SDK | 28 |
| CameraX | 1.6.1 |
| NDK | 29.0.14206865 |
| CMake | 3.22.1 |
| ABI | arm64-v8a only |
| C++ | C++17 |

Local prerequisites:

1. Install Android SDK 36, NDK `29.0.14206865`, and CMake `3.22.1`.
2. Use an ARM64 physical device with at least 8 GB RAM for the dual-model runtime.
3. Do not add GGUF files to Git. Users import them through Android's document picker.

Build commands:

```powershell
node tools/generate_pokemon_catalog.mjs
node tools/generate_default_sfx.mjs
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## 3. Required Model Files

### Language model

| Field | Required value |
|---|---|
| Target file | `MiniCPM-V-4_6-Q4_K_M.gguf` |
| Size | 529101504 bytes |
| SHA-256 | `6B0C74962C44BC6BF4B655B9B02C13EDA9D5A0491543AE976D1AC18E4B7892E2` |
| Architecture | `qwen35` |
| Name | `MiniCPM V 4_6` |
| File type | `15` |

### Vision projector

| Field | Required value |
|---|---|
| Target file | `mmproj-model-f16.gguf` |
| Size | 1108746944 bytes |
| SHA-256 | `CA931D861D0801D9003E50697CD764721A334107C0E0415A51168EE1938462DE` |
| Architecture/type | `clip` / `mmproj` |
| Projector type | `minicpmv4_6` |

`ModelStore` copies to a `.part` file, calculates SHA-256 while copying, verifies selected GGUF metadata, and renames only after all checks pass. A failed import never replaces a verified model.

## 4. Main User Flow

1. First launch displays the model setup screen if either GGUF is missing.
2. The user imports both files through the system document picker.
3. The app loads both models and requests camera permission.
4. The user takes a photo.
5. The captured JPEG replaces the live preview and remains visible while “思考中” is shown.
6. The image is normalized to sRGB, resized without cropping, and sent to the local runtime.
7. The output is parsed using the strict candidate protocol.
8. Candidate names are resolved through the local catalog.
9. The highest-ranked result is displayed and narrated once.
10. In multiple mode, left/right switches candidates; up/down does nothing.

Additional navigation:

- Red mechanical button: open the searchable local catalog.
- Tap any catalog row: open its complete local record, including names, attributes, dimensions, ability, six base stats, description, profile, and official image.
- Opening a catalog detail starts narration for that Pokémon.
- Black mechanical button: stop narration immediately; detail returns to the catalog list, and the catalog list returns to camera preview.
- Poké Ball shutter: capture or reset for another capture.
- Pressing the Poké Ball shutter always stops narration immediately before navigation or capture behavior.
- Feedback buttons: confirm, correct, or export local annotations.

## 5. Image Processing Contract

The preprocessing pipeline must preserve orientation and aspect ratio:

```text
Camera file / Photo Picker URI
  -> ImageDecoder orientation handling
  -> software bitmap in sRGB
  -> feedback JPEG, longest edge <= 1024
  -> inference JPEG, longest edge = configured imageMaxEdge
  -> JPEG quality = configured jpegQuality
```

No center crop is allowed. The frozen photo uses `ContentScale.Fit`.

## 6. AI Interaction Protocol

### Single mode

The model must return exactly one object:

```json
[{"name":"杰尼龟","probability":94.8}]
```

### Multiple mode

The model must return exactly five unique objects:

```json
[
  {"name":"杰尼龟","probability":94.8},
  {"name":"卡咪龟","probability":2.1},
  {"name":"水箭龟","probability":1.5},
  {"name":"土台龟","probability":0.9},
  {"name":"皮卡丘","probability":0.7}
]
```

Validation rules:

- Top-level value must be a JSON array.
- Count must equal the active mode's `candidateCount`.
- Every object must contain only `name` and `probability`.
- `name` must exactly match one local standard Chinese name.
- Names must be unique.
- `probability` must be finite and in `0..100`.
- No leading/trailing whitespace or text outside JSON is accepted.
- The app sorts accepted candidates from highest to lowest probability.
- Probabilities are model-reported confidence values and are not calibrated classifier probabilities.

Enforcement is layered:

1. Chinese classification prompt.
2. Runtime GBNF grammar generated from the local catalog.
3. Greedy decoding.
4. Kotlin JSON parser and exact-name whitelist.

The prompt and grammar cannot be disabled from the settings console.

## 7. Recognition Settings

### Basic

| Parameter | Range | Effect |
|---|---:|---|
| Mode | Single / Multiple | Next recognition returns 1 or 5 candidates |
| Single max tokens | 48..96 | Generation cap in single mode |
| Multiple max tokens | 96..192 | Generation cap in multiple mode |
| Timeout | 15..120 seconds | Cancels inference after the limit |
| Image max edge | 224/336/448/672/896/1024 | Vision input size |
| JPEG quality | 70..100 | Compression quality |

### Performance

| Parameter | Values | Reload required |
|---|---|---|
| Threads | 2..min(CPU cores, 6) | Yes |
| Batch size | 128/256/512/1024 | Yes |
| Context size | 4096/6144/8192 | Yes |

Failed model reload automatically attempts to restore the previous working runtime options.

### Decoding

| Parameter | Range |
|---|---:|
| Penalty last N | 64..256 |
| Repetition penalty | 1.0..2.0 |
| Frequency penalty | 0.0..1.0 |
| Presence penalty | 0.0..1.0 |

### Voice

- Offline simplified-Chinese system voice selection.
- Deep male, standard male, fast male, and custom presets.
- Speech rate `0.50..2.50`.
- Pitch `0.35..1.75`.
- Volume `0..100%`.

Speech script:

```text
这是{中文名}。它是{属性描述}。{完整 profile}
```

### Sound Effects

Three sound slots are exposed through `AppSoundEffect`:

| Slot | Trigger | Default resource |
|---|---|---|
| `ScanStarted` | Capture enters analysis | `res/raw/scan_start.wav` |
| `RecognitionSuccess` | Valid candidates accepted | `res/raw/recognition_success.wav` |
| `RecognitionFailure` | Recognition timeout/protocol/runtime failure | `res/raw/recognition_failure.wav` |

The console supports global enable/disable, volume, preview, upload replacement, and restore default. Supported custom formats are MP3, WAV, OGG, M4A, and AAC. Files must be `1..15 MB` after copy and last `0.05..30 seconds`.

Import guarantees:

- Custom files are stored under app-private `filesDir/sound_effects`.
- The source is validated with `MediaMetadataRetriever`.
- Copy writes to `.part` first.
- Existing custom sound is deleted only after validation succeeds.
- Restoring default deletes only the custom copy.
- Packaged defaults remain immutable inside the APK.

Default WAV files are original generated tones, not extracted Pokémon game or anime audio. Regenerate them with:

```powershell
node tools/generate_default_sfx.mjs
```

## 8. Local Catalog

`catalog.json` is generated from the external Chinese Pokémon dataset. UI code does not hard-code species records. `PokemonInfo` contains:

- Stable key and Pokédex number.
- Chinese, English, and Japanese names.
- Types, category, height, weight, ability.
- HP, Attack, Defense, Special Attack, Special Defense, Speed.
- Description, profile, and local image asset path.

The generator must fail on missing required statistics, unresolved forms, duplicate standard names, or missing result images.

## 9. Feedback Data

Feedback is local until the user presses Export. Each sample contains:

- Normalized JPEG.
- SHA-256 of the image.
- Correct catalog key and standard name.
- Original candidate list and confidence values.
- Confirmation/correction flag and model version.

Export format:

```text
pokedex-feedback-YYYYMMDD-HHMMSS.zip
├─ images/{sampleId}.jpg
├─ annotations.jsonl
└─ manifest.json
```

Duplicate image hashes update the existing sample instead of creating uncontrolled duplicates.

## 10. Error Handling

Expected user-facing categories:

- Camera permission denied or CameraX failure.
- Required model missing/importing.
- Model size, SHA-256, GGUF metadata, or storage validation failure.
- Native library/model load failure.
- Inference timeout/cancellation/out-of-memory failure.
- Candidate JSON protocol failure.
- Catalog record missing.
- Audio import/playback failure.
- TTS unavailable or Chinese voice data missing.

TTS and sound failures are non-blocking. Recognition/model errors use the scanner error state and Retry.

## 11. Testing

Unit tests cover reducers, settings sanitization, protocol parsing, model-independent repositories, speech text, and name policy. Instrumentation tests cover Compose UI, catalog assets, candidate protocol, and feedback storage.

Recommended device workflow that preserves imported models and samples:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Do not uninstall the main package during routine testing. Avoid commands that clear application data. Run instrumentation only with an explicitly installed test APK and remove only the test package afterward.

## 12. Privacy and Release Checklist

- Release builds must not log images, prompts, raw model output, or custom audio paths.
- Do not commit GGUF files, imported audio, feedback samples, or local SDK paths.
- Verify every distributed dataset, image, model, and audio license.
- Do not bundle official Pokémon voices or extracted game SFX without authorization.
- Keep `third_party/llama.cpp` license and pinned revision metadata intact.
- Test peak PSS, load time, inference time, and audio playback on a physical ARM64 device.
