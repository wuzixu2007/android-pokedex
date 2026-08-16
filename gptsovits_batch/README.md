# GPT-SoVITS 宝可梦批量播报

此目录可整体复制到已经跑通 GPT-SoVITS 的 Windows 电脑。工具只生成 WAV 素材，不修改 Android 项目，也不上传资源。

## 准备

1. 确认使用 Python 3.10 或更高版本。
2. 复制 `config.example.json` 为 `config.json`。
3. 在 `config.json` 中填写 GPT-SoVITS API 地址、参考音频的绝对路径和准确参考文本。
4. 启动 GPT-SoVITS API。新版常见命令：

```powershell
python api_v2.py -a 127.0.0.1 -p 9880 -c GPT_SoVITS/configs/tts_infer.yaml
```

若你使用整合包，请以整合包实际的 Python 和配置路径为准。参考音频路径是 API 服务所在电脑上的路径。

## 执行顺序

只生成并检查 1318 条文本清单：

```powershell
python generate_pokemon_audio.py --prepare-only
```

探测新版/旧版 API，并生成一个普通样本和一个最长文本样本：

```powershell
python generate_pokemon_audio.py --probe
```

先生成 20 条代表性样本：

```powershell
python generate_pokemon_audio.py --trial 20
```

生成全部缺失或已失效条目：

```powershell
python generate_pokemon_audio.py
```

只生成或重做指定记录：

```powershell
python generate_pokemon_audio.py --key p0001_2662b6cbaa --force
```

只检查现有音频：

```powershell
python generate_pokemon_audio.py --qa-only
```

## 断点与输出

- `output/wav/`：最终每记录一个 WAV。
- `output/speech_manifest.jsonl`：固定播报文本、分段和文本哈希。
- `output/audio_manifest.json`：已完成音频、配置哈希、时长和音频哈希。
- `output/qa_report.json`：全量可解码、静音和格式检查报告。
- `output/failed.jsonl`：本次仍失败的记录。
- `work/segments/`：已经完成的分段，重启后可继续使用。
- `work/logs/`：请求最终失败的错误信息。

脚本每完成或失败一条都会刷新清单。相同文本和配置下再次执行会跳过已完成音频；删除某个 WAV 后只会补生成该条。修改参考音频、参考文本、声音参数或文本会使相关结果重新生成。

`speech_manifest.jsonl` 中的 `splitFallback=true` 表示该特殊形态没有匹配到足够明确的专属段落，因此按照既定规则使用了原始完整资料。正式生成前可以搜索该字段了解自动拆稿结果；工具不会对原文进行 AI 改写。

## API 兼容

`api_mode` 可设为：

- `auto`：先尝试新版 `/tts` POST，再尝试旧版 GET。
- `v2`：只使用新版 `/tts` JSON API。
- `legacy`：只使用旧版 `api.py` GET 参数。

API 可以直接返回 WAV，也可以返回含 Base64、本地文件路径或音频 URL 的 JSON。若你的第三方封装使用其他字段，请在 `JSON_AUDIO_KEYS` 中加入字段名。

## 运行注意

- 默认 `workers=1`，不要在未确认显存余量前提高并发。
- 用 `nvidia-smi` 确认推理期间 GPU 有负载。CUDA DLL 126 错误通常表示 CUDA 运行库没有正常加载。
- 全量约 55.5 万汉字，可能产生 37 至 46 小时音频，请至少准备 20 GB 可用空间。
- 工具不做响度归一化或静音裁剪，仅拼接模型返回的 WAV。
- 如果分段的采样率、声道或位深不同，脚本会调用 FFmpeg 转换；格式始终一致时不需要安装 FFmpeg。
