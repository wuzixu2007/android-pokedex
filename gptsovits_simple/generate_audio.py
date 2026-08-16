#!/usr/bin/env python3
"""Read pokemon_texts.jsonl and save one GPT-SoVITS WAV per line."""

import json
from pathlib import Path
import time
import urllib.error
import urllib.request


ROOT = Path(__file__).resolve().parent
CONFIG_FILE = ROOT / "config.json"
TEXT_FILE = ROOT / "pokemon_texts.jsonl"
OUTPUT_DIR = ROOT / "wav"


def load_json(path):
    with path.open("r", encoding="utf-8-sig") as file:
        return json.load(file)


def load_texts():
    with TEXT_FILE.open("r", encoding="utf-8-sig") as file:
        return [json.loads(line) for line in file if line.strip()]


def request_wav(config, text):
    payload = {
        "text": text,
        "text_lang": "zh",
        "ref_audio_path": config["reference_audio"],
        "prompt_text": config["reference_text"],
        "prompt_lang": "zh",
        "text_split_method": "cut5",
        "batch_size": config.get("batch_size", 8),
        "parallel_infer": True,
        "speed_factor": config.get("speed", 1.0),
        "media_type": "wav",
        "streaming_mode": False,
    }
    request = urllib.request.Request(
        config.get("api_url", "http://127.0.0.1:9880/tts"),
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=config.get("timeout_seconds", 600)) as response:
        data = response.read()
    if not data.startswith(b"RIFF"):
        raise RuntimeError(data[:500].decode("utf-8", errors="replace"))
    return data


def main():
    if not CONFIG_FILE.exists():
        raise SystemExit("请先把 config.example.json 复制为 config.json，并填写参考音频和参考文本。")

    config = load_json(CONFIG_FILE)
    records = load_texts()
    OUTPUT_DIR.mkdir(exist_ok=True)
    print(f"共 {len(records)} 条文字，音频保存到：{OUTPUT_DIR}")

    for index, record in enumerate(records, 1):
        output = OUTPUT_DIR / f"{record['key']}.wav"
        if output.exists() and output.stat().st_size > 44:
            print(f"[{index}/{len(records)}] 跳过 {record['name']}（已有文件）")
            continue

        for attempt in range(1, 4):
            try:
                wav = request_wav(config, record["text"])
                temporary = output.with_suffix(".wav.tmp")
                temporary.write_bytes(wav)
                temporary.replace(output)
                print(f"[{index}/{len(records)}] 完成 {record['name']}")
                break
            except (urllib.error.URLError, RuntimeError, TimeoutError) as error:
                if attempt == 3:
                    print(f"[{index}/{len(records)}] 失败 {record['name']}：{error}")
                else:
                    time.sleep(2 ** attempt)


if __name__ == "__main__":
    main()
