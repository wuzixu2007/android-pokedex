# Pokédex Scanner

一个基于 Android、Jetpack Compose 和 CameraX 的宝可梦图鉴扫描应用。公开版本将图鉴数据、图片、小游戏素材和音效打包在 APK 内，不依赖项目方的云服务。

## 功能

- 本地图鉴、形态详情、属性克制、收藏和小游戏。
- 相机和相册取图。拍摄的图片只保存在当前设备的应用私有目录，不会上传到项目方服务器。
- 可选的第三方视觉 AI 识别。识别服务由使用者自行选择、配置和付费；源码和 APK 不包含任何 API Key。
- 本地语音资源读取和本地语音包文件管理。公开版本不提供远程兑换或远程下载。

## 构建要求

- Android Studio Ladybug 或更新版本。
- JDK 11。
- Android SDK Platform 36 和对应 Build Tools。
- Android 8.0（API 26）或更高版本的设备；应用的最低 SDK 以 `app/build.gradle.kts` 为准。

在项目根目录执行：

```powershell
.\gradlew.bat :app:assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/`。也可以直接用 Android Studio 打开项目并运行 `app` 配置。

## 资源和隐私

核心资源位于 `app/src/main/assets/`，首次启动时复制到应用私有目录。应用不会请求项目方的资源清单，也不会连接项目方的云函数。构建产物、发布 APK、大型 ZIP、IDE 文件和本地 SDK 配置被 `.gitignore` 排除。

照片识别前会在本地完成缩放和 JPEG 压缩。只有当用户主动在设置中填写自己的 AI 地址和密钥，并点击识别时，图片才会发送到该用户选择的服务商。照片不会发送到本项目作者的腾讯云或其他项目方服务。

## 配置自己的 AI 识别服务

1. 打开应用设置，选择服务商或“自定义接口”。
2. 填写自己的 API Key、模型 ID、请求协议和 API URL。
3. 按服务商要求设置认证头、认证方案、系统提示词和额外 JSON 参数。
4. 点击连接测试，确认返回的模型能处理图片。

支持 Responses、Chat Completions 和 Anthropic Messages 三种请求协议。应用默认只保存配置在本机的 SharedPreferences 中；不要把密钥写进 Kotlin 文件、Gradle 文件、README、截图或 Git 提交。

服务商的账号、费用、区域限制、数据保留策略和使用条款由使用者自行承担。更完整的接口映射、反向代理和密钥轮换说明见 [`docs/self-hosting-and-api.md`](docs/self-hosting-and-api.md)。

## 本地语音包

公开版本不包含远程语音授权、兑换或下载服务。语音包存储层只读取设备本地文件；需要额外音色的使用者可在自己的分支实现文件选择导入，或部署自己的服务。不要把包含第三方密钥或私有下载地址的 manifest 提交到仓库。

## 常见问题

### 构建失败

确认 Android Studio 使用 JDK 11，SDK Platform 36 已安装，并删除个人机器上的 `local.properties` 后重新同步 Gradle。

### 图鉴显示资源不可用

确认 `app/src/main/assets/` 目录完整，尤其是 `assets/pokemon/`、`assets/game/`、`raw/` 和 `drawable-nodpi/`。不要把 `build/` 或 `artifacts/` 中的生成目录当作 APK 资源目录。

### AI 识别失败

检查 API URL、模型 ID、协议和认证头是否与服务商文档一致。未填写 API Key、模型不支持图片或账户额度不足时，应用会显示配置或服务商返回的错误。

## 开源范围

仓库包含 Android 源码、测试、构建脚本、资源和示例配置，不包含项目方的 CloudBase 配置、令牌、API Key、云函数或发布制品。使用本项目时请遵守相关数据、版权和第三方服务条款。
