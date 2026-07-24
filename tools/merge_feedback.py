#!/usr/bin/env python3
"""Validate and merge Android feedback ZIPs. / 校验并合并 Android 反馈 ZIP。"""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import sys
import tempfile
import zipfile
from pathlib import Path


SCHEMA_VERSION = 1
NOT_POKEMON_KEY = "__NOT_POKEMON__"


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def load_catalog(path: Path) -> dict[str, str]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    labels = {record["key"]: record["nameZh"] for record in payload["records"]}
    labels[NOT_POKEMON_KEY] = "non-pokemon"
    return labels


def read_archive(path: Path) -> list[tuple[dict, bytes]]:
    with zipfile.ZipFile(path) as archive:
        manifest = json.loads(archive.read("manifest.json"))
        if manifest.get("schemaVersion") != SCHEMA_VERSION:
            raise ValueError(f"{path}: unsupported schema version")
        lines = archive.read("annotations.jsonl").decode("utf-8").splitlines()
        records = [json.loads(line) for line in lines if line.strip()]
        if len(records) != manifest.get("sampleCount"):
            raise ValueError(f"{path}: sample count does not match manifest")

        samples: list[tuple[dict, bytes]] = []
        for record in records:
            image_path = record.get("imageFile", "")
            if not image_path.startswith("images/") or ".." in Path(image_path).parts:
                raise ValueError(f"{path}: unsafe image path {image_path!r}")
            image = archive.read(image_path)
            if sha256(image) != record.get("imageSha256"):
                raise ValueError(f"{path}: image hash failed for {image_path}")
            samples.append((record, image))
        return samples


def merge(archives: list[Path], output: Path, catalog_path: Path) -> dict:
    if output.exists():
        raise ValueError(f"output already exists: {output}")
    labels = load_catalog(catalog_path)
    by_hash: dict[str, tuple[dict, bytes, str]] = {}
    duplicates = 0

    for archive in archives:
        for record, image in read_archive(archive):
            key = record.get("correctKey")
            if key not in labels:
                raise ValueError(f"{archive}: unknown label key {key!r}")
            digest = record["imageSha256"]
            existing = by_hash.get(digest)
            if existing is not None:
                if existing[0].get("correctKey") != key:
                    raise ValueError(
                        f"conflicting labels for image {digest}: "
                        f"{existing[0].get('correctKey')} vs {key}"
                    )
                duplicates += 1
                continue
            by_hash[digest] = (record, image, archive.name)

    with tempfile.TemporaryDirectory(prefix="pokedex-feedback-") as temp_name:
        temp = Path(temp_name)
        images = temp / "images"
        images.mkdir()
        annotation_path = temp / "annotations.jsonl"
        with annotation_path.open("w", encoding="utf-8", newline="\n") as annotations:
            for index, (record, image, source) in enumerate(by_hash.values(), start=1):
                image_name = f"sample-{index:07d}.jpg"
                (images / image_name).write_bytes(image)
                merged_record = dict(record)
                merged_record["imageFile"] = f"images/{image_name}"
                merged_record["sourceArchive"] = source
                annotations.write(json.dumps(merged_record, ensure_ascii=False) + "\n")

        report = {
            "schemaVersion": SCHEMA_VERSION,
            "archiveCount": len(archives),
            "sampleCount": len(by_hash),
            "duplicatesRemoved": duplicates,
            "labelCount": len({record[0]["correctKey"] for record in by_hash.values()}),
        }
        (temp / "report.json").write_text(
            json.dumps(report, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        shutil.copytree(temp, output)
    return report


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("archives", nargs="+", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument(
        "--catalog",
        type=Path,
        default=Path("app/src/main/assets/pokemon/catalog.json"),
    )
    args = parser.parse_args()
    try:
        report = merge(args.archives, args.output, args.catalog)
    except (OSError, KeyError, ValueError, zipfile.BadZipFile) as error:
        print(f"merge failed: {error}", file=sys.stderr)
        return 1
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
