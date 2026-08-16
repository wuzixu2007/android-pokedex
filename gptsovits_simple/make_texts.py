import json
from pathlib import Path

source = Path(__file__).parents[1] / "gptsovits_batch" / "output" / "speech_manifest.jsonl"
target = Path(__file__).with_name("pokemon_texts.jsonl")
with source.open("r", encoding="utf-8-sig") as source_file, target.open("w", encoding="utf-8", newline="\n") as target_file:
    for line in source_file:
        if not line.strip():
            continue
        item = json.loads(line)
        target_file.write(json.dumps({"key": item["key"], "name": item["nameZh"], "text": item["text"]}, ensure_ascii=False, separators=(",", ":")))
        target_file.write("\n")
