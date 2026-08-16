#!/usr/bin/env python3
"""Batch-generate Pokemon narration WAV files through GPT-SoVITS HTTP APIs."""

from __future__ import annotations

import argparse
import base64
import concurrent.futures
import contextlib
import hashlib
import io
import json
import math
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import wave


SCRIPT_VERSION = "1.0.0"
DEFAULT_CONFIG = "config.json"
DEFAULT_CATALOG = "catalog.json"
OUTPUT_DIR = "output"
WORK_DIR = "work"

SENTENCE_END_RE = re.compile(r"(?<=[。！？!?；;])")
SPACE_RE = re.compile(r"\s+")

# Ordered longest-first so specific forms win over their shorter prefixes.
FORM_MARKERS = (
    "超极巨化", "超级进化", "超级", "阿罗拉", "伽勒尔", "洗翠", "帕底亚",
    "起源形态", "别种形态", "攻击形态", "防御形态", "速度形态", "天空形态",
    "陆上形态", "灵兽形态", "化身形态", "黑马骑士", "白马骑士", "黄昏之鬃",
    "拂晓之翼", "究极", "羁绊进化", "永恒花叶", "英雄形态", "全能形态",
    "高调的样子", "低调的样子", "满腹花纹", "空腹花纹", "上弦之月",
    "下弦之月", "三节形态", "两节形态", "家族", "太晶",
)
GENERIC_FORM_WORDS = (
    "的样子", "形态", "模式", "花纹", "面具", "姿势", "外观", "性别",
)
JSON_AUDIO_KEYS = (
    "audio", "audio_data", "audio_base64", "wav", "wav_base64", "data",
    "file", "file_path", "path", "output", "url",
)


class BatchError(RuntimeError):
    pass


def load_json(path: Path):
    with path.open("r", encoding="utf-8-sig") as handle:
        return json.load(handle)


def write_json(path: Path, value) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(prefix=path.name + ".", suffix=".tmp", dir=path.parent)
    temporary = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as handle:
            json.dump(value, handle, ensure_ascii=False, indent=2)
            handle.write("\n")
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def write_jsonl(path: Path, rows) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(prefix=path.name + ".", suffix=".tmp", dir=path.parent)
    temporary = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as handle:
            for row in rows:
                handle.write(json.dumps(row, ensure_ascii=False, separators=(",", ":")))
                handle.write("\n")
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def normalize_text(value: str) -> str:
    return SPACE_RE.sub(" ", value.replace("\r", " ").replace("\n", " ")).strip()


def split_sentences(value: str) -> list[str]:
    return [part.strip() for part in SENTENCE_END_RE.split(normalize_text(value)) if part.strip()]


def split_paragraphs(value: str) -> list[str]:
    return [normalize_text(part) for part in re.split(r"[\r\n]+", value) if normalize_text(part)]


def chunk_text(value: str, max_chars: int) -> list[str]:
    """Split at Chinese punctuation, then hard-wrap only sentences over the limit."""
    if max_chars < 20:
        raise BatchError("max_chars must be at least 20")
    chunks: list[str] = []
    current = ""
    for sentence in split_sentences(value):
        pieces = [sentence[index:index + max_chars] for index in range(0, len(sentence), max_chars)]
        for piece in pieces:
            if current and len(current) + len(piece) > max_chars:
                chunks.append(current)
                current = piece
            else:
                current += piece
    if current:
        chunks.append(current)
    return chunks


def canonical_species_name(record: dict) -> str:
    description = normalize_text(record.get("description", ""))
    match = re.match(r"^([^（(，,。]{1,20})(?:[（(]|是)", description)
    if match:
        return match.group(1).strip()
    return record.get("nameZh", "").split("-", 1)[0].strip()


def form_terms(record: dict, species_name: str, group_size: int) -> set[str]:
    label = f"{record.get('nameZh', '')} {record.get('sourceFormName', '')}"
    terms = {marker for marker in FORM_MARKERS if marker in label}
    is_base = record.get("nameZh") == species_name or record.get("sourceFormName") == species_name
    if terms or (group_size > 1 and not is_base):
        terms.update(
            source for source in (record.get("sourceFormName", ""), record.get("nameZh", ""))
            if 2 <= len(source) <= 24
        )
        for source in (record.get("sourceFormName", ""), record.get("nameZh", "")):
            descriptor = source.replace(species_name, "").strip(" -—·（）()")
            if 2 <= len(descriptor) <= 20:
                terms.add(descriptor)
    for source in (record.get("sourceFormName", ""), record.get("nameZh", "")):
        for word in GENERIC_FORM_WORDS:
            if word in source and len(source) <= 24:
                terms.add(source)
    return {term for term in terms if term}


def split_form_profiles(records: list[dict]) -> dict[str, dict]:
    """Create deterministic form-specific profiles without rewriting source prose."""
    by_id: dict[str, list[dict]] = {}
    for record in records:
        by_id.setdefault(record["id"], []).append(record)

    result: dict[str, dict] = {}
    for group in by_id.values():
        species_counts: dict[str, int] = {}
        for record in group:
            candidate = canonical_species_name(record)
            species_counts[candidate] = species_counts.get(candidate, 0) + 1
        species_name = max(species_counts, key=lambda name: (species_counts[name], -len(name)))
        term_map = {record["key"]: form_terms(record, species_name, len(group)) for record in group}
        all_special_terms = set().union(*term_map.values()) if term_map else set()
        for record in group:
            raw_profile = record.get("profile", "")
            original = normalize_text(raw_profile)
            own_terms = term_map[record["key"]]
            fallback = False
            reason = None
            if len(group) == 1 or not all_special_terms:
                selected = original
            else:
                # Source profiles use line breaks to separate ordinary, regional, Mega,
                # and Gigantamax descriptions. Keeping a whole paragraph preserves
                # continuation sentences that do not repeat the form name.
                units = split_paragraphs(raw_profile)
                if len(units) == 1:
                    units = split_sentences(original)
                if own_terms:
                    selected_units = [
                        unit for unit in units
                        if any(term in unit for term in own_terms)
                    ]
                else:
                    selected_units = [
                        unit for unit in units
                        if not any(term in unit for term in all_special_terms)
                    ]
                selected = " ".join(selected_units).strip()
                minimum = min(60, max(24, len(original) // 20))
                # A short ordinary paragraph is still safer than mixing special-form
                # prose back into the base form. Special forms require enough matched
                # material and otherwise follow the documented full-profile fallback.
                needs_fallback = (bool(own_terms) and len(selected) < minimum) or not selected
                if needs_fallback:
                    selected = original
                    fallback = True
                    reason = "no sufficiently specific form text"
            result[record["key"]] = {
                "profile": selected,
                "splitFallback": fallback,
                "splitReason": reason,
                "formTerms": sorted(own_terms),
            }
    return result


def build_speech_rows(records: list[dict], max_chars: int) -> list[dict]:
    split_profiles = split_form_profiles(records)
    rows = []
    for index, record in enumerate(records):
        split = split_profiles[record["key"]]
        text = normalize_text(
            f"这是{record['nameZh']}。它是{record['attributeLabel']}。{split['profile']}"
        )
        chunks = chunk_text(text, max_chars)
        rows.append({
            "index": index,
            "key": record["key"],
            "id": record["id"],
            "nameZh": record["nameZh"],
            "sourceFormName": record.get("sourceFormName", ""),
            "attributeLabel": record["attributeLabel"],
            "text": text,
            "textSha256": sha256_text(text),
            "characterCount": len(text),
            "chunks": chunks,
            "chunkCount": len(chunks),
            "splitFallback": split["splitFallback"],
            "splitReason": split["splitReason"],
            "formTerms": split["formTerms"],
            "audioPath": f"wav/{record['key']}.wav",
        })
    return rows


def config_hash(config: dict, base_dir: Path) -> str:
    relevant = {key: value for key, value in config.items() if key not in {"api_url", "timeout_seconds", "workers"}}
    reference = Path(str(config.get("reference_audio", "")))
    if not reference.is_absolute():
        reference = base_dir / reference
    if reference.is_file():
        relevant["reference_audio_sha256"] = sha256_file(reference)
    return sha256_text(json.dumps(relevant, ensure_ascii=False, sort_keys=True, separators=(",", ":")))


def validate_config(config: dict) -> None:
    required = ("api_url", "api_mode", "reference_audio", "reference_text")
    missing = [name for name in required if not str(config.get(name, "")).strip()]
    if missing:
        raise BatchError(f"config is missing: {', '.join(missing)}")
    if config.get("api_mode") not in {"auto", "v2", "legacy"}:
        raise BatchError("api_mode must be auto, v2, or legacy")
    if int(config.get("workers", 1)) < 1:
        raise BatchError("workers must be at least 1")


def request_bytes(url: str, *, data: bytes | None, headers: dict[str, str], timeout: int) -> tuple[bytes, str]:
    request = urllib.request.Request(url, data=data, headers=headers, method="POST" if data is not None else "GET")
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return response.read(), response.headers.get_content_type()
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")[:2000]
        raise BatchError(f"HTTP {error.code}: {body}") from error
    except urllib.error.URLError as error:
        raise BatchError(f"request failed: {error.reason}") from error


def ensure_tts_url(api_url: str) -> str:
    return api_url.rstrip("/") if api_url.rstrip("/").endswith("/tts") else api_url.rstrip("/") + "/tts"


def decode_json_audio(value, base_dir: Path) -> bytes:
    candidates = []
    if isinstance(value, dict):
        for key in JSON_AUDIO_KEYS:
            if key in value:
                candidates.append(value[key])
        candidates.extend(item for key, item in value.items() if key not in JSON_AUDIO_KEYS)
    elif isinstance(value, list):
        candidates.extend(value)
    else:
        candidates.append(value)

    for candidate in candidates:
        if isinstance(candidate, (dict, list)):
            with contextlib.suppress(BatchError):
                return decode_json_audio(candidate, base_dir)
        if not isinstance(candidate, str) or not candidate.strip():
            continue
        text = candidate.strip()
        if text.startswith("data:audio") and "," in text:
            text = text.split(",", 1)[1]
        with contextlib.suppress(Exception):
            decoded = base64.b64decode(text, validate=True)
            if decoded.startswith((b"RIFF", b"RIFX", b"RF64")):
                return decoded
        path = Path(text)
        if not path.is_absolute():
            path = base_dir / path
        if path.is_file():
            return path.read_bytes()
        if text.startswith(("http://", "https://")):
            body, _ = request_bytes(text, data=None, headers={}, timeout=180)
            return body
    raise BatchError("JSON response did not contain readable WAV audio")


class GptSovitsClient:
    def __init__(self, config: dict, base_dir: Path):
        self.config = config
        self.base_dir = base_dir
        self.mode = config["api_mode"]
        self.timeout = int(config.get("timeout_seconds", 180))

    def synthesize(self, text: str) -> bytes:
        modes = [self.mode] if self.mode != "auto" else ["v2", "legacy"]
        errors = []
        for mode in modes:
            try:
                audio = self._v2(text) if mode == "v2" else self._legacy(text)
                validate_wav_bytes(audio)
                if self.mode == "auto":
                    self.mode = mode
                return audio
            except Exception as error:
                errors.append(f"{mode}: {error}")
        raise BatchError("; ".join(errors))

    def _v2(self, text: str) -> bytes:
        payload = {
            "text": text,
            "text_lang": self.config.get("text_language", "zh"),
            "ref_audio_path": self.config["reference_audio"],
            "aux_ref_audio_paths": self.config.get("aux_reference_audio", []),
            "prompt_text": self.config["reference_text"],
            "prompt_lang": self.config.get("prompt_language", "zh"),
            "top_k": int(self.config.get("top_k", 15)),
            "top_p": float(self.config.get("top_p", 1.0)),
            "temperature": float(self.config.get("temperature", 1.0)),
            "text_split_method": self.config.get("text_split_method", "cut0"),
            "batch_size": int(self.config.get("batch_size", 1)),
            "speed_factor": float(self.config.get("speed", 1.0)),
            "media_type": "wav",
            "streaming_mode": False,
            "seed": int(self.config.get("seed", -1)),
            "parallel_infer": bool(self.config.get("parallel_infer", True)),
            "repetition_penalty": float(self.config.get("repetition_penalty", 1.35)),
        }
        body, content_type = request_bytes(
            ensure_tts_url(self.config["api_url"]),
            data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
            headers={"Content-Type": "application/json", "Accept": "audio/wav, application/json"},
            timeout=self.timeout,
        )
        return self._decode_response(body, content_type)

    def _legacy(self, text: str) -> bytes:
        query = urllib.parse.urlencode({
            "refer_wav_path": self.config["reference_audio"],
            "prompt_text": self.config["reference_text"],
            "prompt_language": self.config.get("prompt_language", "zh"),
            "text": text,
            "text_language": self.config.get("text_language", "zh"),
            "cut_punc": self.config.get("legacy_cut_punc", "。！？!?"),
        })
        legacy_url = self.config["api_url"].rstrip("/")
        if legacy_url.endswith("/tts"):
            legacy_url = legacy_url[:-4]
        body, content_type = request_bytes(
            f"{legacy_url}?{query}",
            data=None,
            headers={"Accept": "audio/wav, application/json"},
            timeout=self.timeout,
        )
        return self._decode_response(body, content_type)

    def _decode_response(self, body: bytes, content_type: str) -> bytes:
        if body.startswith((b"RIFF", b"RIFX", b"RF64")) or content_type.startswith("audio/"):
            return body
        try:
            value = json.loads(body.decode("utf-8-sig"))
        except Exception as error:
            preview = body[:300].decode("utf-8", errors="replace")
            raise BatchError(f"response was not WAV or JSON: {preview}") from error
        return decode_json_audio(value, self.base_dir)


def validate_wav_bytes(value: bytes) -> dict:
    try:
        with wave.open(io.BytesIO(value), "rb") as handle:
            frames = handle.getnframes()
            rate = handle.getframerate()
            channels = handle.getnchannels()
            width = handle.getsampwidth()
            compression = handle.getcomptype()
    except (wave.Error, EOFError) as error:
        raise BatchError(f"invalid WAV: {error}") from error
    if frames <= 0 or rate <= 0 or channels <= 0 or width not in {1, 2, 3, 4}:
        raise BatchError("WAV has invalid audio parameters")
    if compression != "NONE":
        raise BatchError(f"compressed WAV is not supported: {compression}")
    return {"frames": frames, "sampleRate": rate, "channels": channels, "sampleWidth": width}


def wav_parameters(path: Path) -> tuple[int, int, int]:
    with wave.open(str(path), "rb") as handle:
        return handle.getnchannels(), handle.getsampwidth(), handle.getframerate()


def convert_wav(source: Path, target: Path, parameters: tuple[int, int, int]) -> None:
    ffmpeg = shutil.which("ffmpeg")
    if not ffmpeg:
        raise BatchError("segment WAV formats differ and ffmpeg is not installed")
    channels, width, rate = parameters
    codec = {1: "pcm_u8", 2: "pcm_s16le", 3: "pcm_s24le", 4: "pcm_s32le"}[width]
    command = [ffmpeg, "-hide_banner", "-loglevel", "error", "-y", "-i", str(source),
               "-ar", str(rate), "-ac", str(channels), "-c:a", codec, str(target)]
    completed = subprocess.run(command, capture_output=True, text=True)
    if completed.returncode != 0:
        raise BatchError(f"ffmpeg conversion failed: {completed.stderr.strip()}")


def merge_wavs(parts: list[Path], output: Path) -> None:
    if not parts:
        raise BatchError("no WAV segments to merge")
    target_parameters = wav_parameters(parts[0])
    normalized: list[Path] = []
    temporary_paths: list[Path] = []
    try:
        for index, part in enumerate(parts):
            if wav_parameters(part) == target_parameters:
                normalized.append(part)
                continue
            converted = part.with_name(f"{part.stem}.normalized-{index}.wav")
            convert_wav(part, converted, target_parameters)
            normalized.append(converted)
            temporary_paths.append(converted)

        output.parent.mkdir(parents=True, exist_ok=True)
        temporary_output = output.with_suffix(".wav.tmp")
        channels, width, rate = target_parameters
        with wave.open(str(temporary_output), "wb") as writer:
            writer.setnchannels(channels)
            writer.setsampwidth(width)
            writer.setframerate(rate)
            for part in normalized:
                with wave.open(str(part), "rb") as reader:
                    writer.writeframes(reader.readframes(reader.getnframes()))
        validate_wav_file(temporary_output)
        os.replace(temporary_output, output)
    finally:
        for path in temporary_paths:
            path.unlink(missing_ok=True)


def sample_peak(frames: bytes, width: int) -> int:
    if not frames:
        return 0
    peak = 0
    if width == 1:
        return max(abs(value - 128) for value in frames)
    if width == 2:
        count = len(frames) // 2
        return max(abs(value) for value in struct.unpack(f"<{count}h", frames[:count * 2]))
    if width == 4:
        count = len(frames) // 4
        return max(abs(value) for value in struct.unpack(f"<{count}i", frames[:count * 4]))
    for index in range(0, len(frames) - 2, 3):
        value = int.from_bytes(frames[index:index + 3], "little", signed=False)
        if value & 0x800000:
            value -= 0x1000000
        peak = max(peak, abs(value))
    return peak


def validate_wav_file(path: Path, expected: dict | None = None) -> dict:
    try:
        with wave.open(str(path), "rb") as handle:
            channels = handle.getnchannels()
            width = handle.getsampwidth()
            rate = handle.getframerate()
            frames_count = handle.getnframes()
            compression = handle.getcomptype()
            peak = 0
            while True:
                frames = handle.readframes(65536)
                if not frames:
                    break
                peak = max(peak, sample_peak(frames, width))
    except (wave.Error, EOFError, OSError) as error:
        raise BatchError(f"cannot decode {path.name}: {error}") from error
    if compression != "NONE" or frames_count <= 0 or rate <= 0:
        raise BatchError(f"invalid WAV parameters: {path.name}")
    if peak == 0:
        raise BatchError(f"WAV is completely silent: {path.name}")
    duration = frames_count / rate
    result = {
        "bytes": path.stat().st_size,
        "sha256": sha256_file(path),
        "durationSeconds": round(duration, 3),
        "sampleRate": rate,
        "channels": channels,
        "sampleWidth": width,
        "peak": peak,
    }
    if expected:
        expected_rate = expected.get("expected_sample_rate")
        expected_channels = expected.get("expected_channels")
        expected_width = expected.get("expected_sample_width")
        if expected_rate and rate != int(expected_rate):
            raise BatchError(f"unexpected sample rate {rate}: {path.name}")
        if expected_channels and channels != int(expected_channels):
            raise BatchError(f"unexpected channel count {channels}: {path.name}")
        if expected_width and width != int(expected_width):
            raise BatchError(f"unexpected sample width {width}: {path.name}")
    return result


class BatchRunner:
    def __init__(self, root: Path, config: dict, rows: list[dict]):
        self.root = root
        self.config = config
        self.rows = rows
        self.output = root / OUTPUT_DIR
        self.wav_dir = self.output / "wav"
        self.segment_dir = root / WORK_DIR / "segments"
        self.log_dir = root / WORK_DIR / "logs"
        self.speech_manifest = self.output / "speech_manifest.jsonl"
        self.audio_manifest = self.output / "audio_manifest.json"
        self.qa_report = self.output / "qa_report.json"
        self.failed_file = self.output / "failed.jsonl"
        self.config_sha = config_hash(config, root)
        self.lock = threading.Lock()
        self.results: dict[str, dict] = self._load_previous_results()
        self.failures: dict[str, dict] = self._load_previous_failures()
        self.client_local = threading.local()
        for directory in (self.wav_dir, self.segment_dir, self.log_dir):
            directory.mkdir(parents=True, exist_ok=True)

    def _load_previous_results(self) -> dict[str, dict]:
        if not self.audio_manifest.is_file():
            return {}
        with contextlib.suppress(Exception):
            value = load_json(self.audio_manifest)
            return {item["key"]: item for item in value.get("records", [])}
        return {}

    def _load_previous_failures(self) -> dict[str, dict]:
        if not self.failed_file.is_file():
            return {}
        failures = {}
        with contextlib.suppress(Exception):
            with self.failed_file.open("r", encoding="utf-8-sig") as handle:
                for line in handle:
                    if line.strip():
                        failure = json.loads(line)
                        failures[failure["key"]] = failure
        return failures

    def client(self) -> GptSovitsClient:
        client = getattr(self.client_local, "client", None)
        if client is None:
            client = GptSovitsClient(self.config, self.root)
            self.client_local.client = client
        return client

    def is_complete(self, row: dict) -> bool:
        previous = self.results.get(row["key"])
        path = self.wav_dir / f"{row['key']}.wav"
        if not previous or not path.is_file():
            return False
        if previous.get("textSha256") != row["textSha256"] or previous.get("configSha256") != self.config_sha:
            return False
        try:
            qa = validate_wav_file(path, self.config)
        except BatchError:
            return False
        return qa["sha256"] == previous.get("audioSha256")

    def generate(self, selected: list[dict], force: bool = False) -> None:
        pending = [row for row in selected if force or not self.is_complete(row)]
        skipped = len(selected) - len(pending)
        print(f"Selected {len(selected)} records; pending {len(pending)}; skipped {skipped}.", flush=True)
        workers = int(self.config.get("workers", 1))
        if workers == 1:
            for position, row in enumerate(pending, 1):
                self._generate_and_record(row, position, len(pending))
        else:
            with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as executor:
                futures = {executor.submit(self._generate_one, row): row for row in pending}
                for position, future in enumerate(concurrent.futures.as_completed(futures), 1):
                    row = futures[future]
                    try:
                        result = future.result()
                        with self.lock:
                            self.results[row["key"]] = result
                            self.failures.pop(row["key"], None)
                        print(f"[{position}/{len(pending)}] OK {row['key']} {row['nameZh']}", flush=True)
                    except Exception as error:
                        self._record_failure(row, error)
                    self.persist()
        self.persist()

    def _generate_and_record(self, row: dict, position: int, total: int) -> None:
        try:
            result = self._generate_one(row)
            self.results[row["key"]] = result
            self.failures.pop(row["key"], None)
            print(f"[{position}/{total}] OK {row['key']} {row['nameZh']}", flush=True)
        except Exception as error:
            self._record_failure(row, error)
        self.persist()

    def _record_failure(self, row: dict, error: Exception) -> None:
        failure = {
            "key": row["key"], "nameZh": row["nameZh"], "textSha256": row["textSha256"],
            "error": str(error), "timestamp": int(time.time()),
        }
        with self.lock:
            self.failures[row["key"]] = failure
        print(f"FAIL {row['key']} {row['nameZh']}: {error}", file=sys.stderr, flush=True)

    def _generate_one(self, row: dict) -> dict:
        record_segment_dir = self.segment_dir / row["key"]
        record_segment_dir.mkdir(parents=True, exist_ok=True)
        parts: list[Path] = []
        for index, text in enumerate(row["chunks"]):
            text_sha = sha256_text(text)
            part = record_segment_dir / f"{index:03d}-{text_sha[:12]}.wav"
            if part.is_file():
                try:
                    validate_wav_file(part)
                    parts.append(part)
                    continue
                except BatchError:
                    part.unlink(missing_ok=True)
            audio = self._request_with_retry(text, row, index)
            temporary = part.with_suffix(".wav.tmp")
            temporary.write_bytes(audio)
            validate_wav_file(temporary)
            os.replace(temporary, part)
            parts.append(part)

        final_path = self.wav_dir / f"{row['key']}.wav"
        merge_wavs(parts, final_path)
        qa = validate_wav_file(final_path, self.config)
        return {
            "key": row["key"], "id": row["id"], "nameZh": row["nameZh"],
            "textSha256": row["textSha256"], "configSha256": self.config_sha,
            "audioPath": row["audioPath"], "audioSha256": qa["sha256"],
            "bytes": qa["bytes"], "durationSeconds": qa["durationSeconds"],
            "sampleRate": qa["sampleRate"], "channels": qa["channels"],
            "sampleWidth": qa["sampleWidth"], "chunkCount": row["chunkCount"],
            "splitFallback": row["splitFallback"], "generatedAt": int(time.time()),
        }

    def _request_with_retry(self, text: str, row: dict, chunk_index: int) -> bytes:
        attempts = int(self.config.get("retry_attempts", 3))
        delay = float(self.config.get("retry_base_seconds", 2.0))
        errors = []
        for attempt in range(1, attempts + 1):
            try:
                return self.client().synthesize(text)
            except Exception as error:
                errors.append(f"attempt {attempt}: {error}")
                if attempt < attempts:
                    time.sleep(delay * (2 ** (attempt - 1)))
        log = self.log_dir / f"{row['key']}-{chunk_index:03d}.error.txt"
        log.write_text("\n".join(errors), encoding="utf-8")
        raise BatchError(" | ".join(errors))

    def persist(self) -> None:
        with self.lock:
            ordered_results = [self.results[row["key"]] for row in self.rows if row["key"] in self.results]
            write_json(self.audio_manifest, {
                "schemaVersion": 1, "scriptVersion": SCRIPT_VERSION,
                "configSha256": self.config_sha, "catalogRecordCount": len(self.rows),
                "completedCount": len(ordered_results), "records": ordered_results,
            })
            write_jsonl(self.failed_file, [self.failures[key] for key in sorted(self.failures)])

    def run_qa(self) -> dict:
        records = []
        failures = []
        warnings = []
        durations = []
        for row in self.rows:
            path = self.wav_dir / f"{row['key']}.wav"
            if not path.is_file():
                failures.append({"key": row["key"], "error": "missing WAV"})
                continue
            try:
                qa = validate_wav_file(path, self.config)
                manifest_entry = self.results.get(row["key"])
                if manifest_entry is None:
                    raise BatchError("audio manifest entry is missing")
                if manifest_entry.get("textSha256") != row["textSha256"]:
                    raise BatchError("audio was generated from a different text revision")
                if manifest_entry.get("configSha256") != self.config_sha:
                    raise BatchError("audio was generated with a different voice configuration")
                if manifest_entry.get("audioSha256") != qa["sha256"]:
                    raise BatchError("audio hash does not match audio manifest")
                qa.update({"key": row["key"], "nameZh": row["nameZh"], "textSha256": row["textSha256"]})
                records.append(qa)
                durations.append(qa["durationSeconds"])
                if qa["durationSeconds"] < 0.3:
                    warnings.append({"key": row["key"], "warning": "unusually short audio"})
                if qa["bytes"] < 1024:
                    warnings.append({"key": row["key"], "warning": "unusually small WAV"})
            except BatchError as error:
                failures.append({"key": row["key"], "error": str(error)})
        report = {
            "schemaVersion": 1, "generatedAt": int(time.time()), "expectedCount": len(self.rows),
            "validCount": len(records), "failureCount": len(failures), "warningCount": len(warnings),
            "totalDurationSeconds": round(sum(durations), 3),
            "minDurationSeconds": min(durations) if durations else None,
            "maxDurationSeconds": max(durations) if durations else None,
            "failures": failures, "warnings": warnings, "records": records,
        }
        write_json(self.qa_report, report)
        return report


def select_trial_rows(rows: list[dict], count: int) -> list[dict]:
    selected: list[dict] = []
    candidates = [
        rows[0],
        max(rows, key=lambda row: row["characterCount"]),
        max(rows, key=lambda row: row["chunkCount"]),
    ]
    candidates.extend(row for row in rows if row["formTerms"][:1])
    candidates.extend(row for row in rows if any(token in row["text"] for token in ("（", "(", "10", "Ｘ")))
    candidates.extend(rows)
    seen = set()
    for row in candidates:
        if row["key"] in seen:
            continue
        selected.append(row)
        seen.add(row["key"])
        if len(selected) >= count:
            break
    return selected


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", default=DEFAULT_CONFIG, help="JSON configuration path")
    parser.add_argument("--catalog", default=DEFAULT_CATALOG, help="Pokemon catalog JSON path")
    parser.add_argument("--prepare-only", action="store_true", help="only build speech_manifest.jsonl")
    parser.add_argument("--probe", action="store_true", help="generate two API compatibility samples")
    parser.add_argument("--trial", type=int, metavar="N", help="generate a representative trial set")
    parser.add_argument("--key", action="append", default=[], help="generate one record key; repeatable")
    parser.add_argument("--qa-only", action="store_true", help="validate existing WAV files")
    parser.add_argument("--force", action="store_true", help="regenerate selected records")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    root = Path(__file__).resolve().parent
    catalog_path = (root / args.catalog).resolve() if not Path(args.catalog).is_absolute() else Path(args.catalog)
    config_path = (root / args.config).resolve() if not Path(args.config).is_absolute() else Path(args.config)
    catalog = load_json(catalog_path)
    records = catalog.get("records", [])
    if not records:
        raise BatchError("catalog has no records")

    config_exists = config_path.is_file()
    config = load_json(config_path) if config_exists else load_json(root / "config.example.json")
    max_chars = int(config.get("max_chars", 200))
    rows = build_speech_rows(records, max_chars)
    output = root / OUTPUT_DIR
    write_jsonl(output / "speech_manifest.jsonl", rows)
    print(f"Prepared {len(rows)} speech records ({sum(row['characterCount'] for row in rows)} characters).")
    if args.prepare_only:
        return 0

    if not config_exists:
        raise BatchError(
            f"configuration not found: {config_path}. Copy config.example.json to config.json and edit it first"
        )

    validate_config(config)
    runner = BatchRunner(root, config, rows)
    if args.qa_only:
        report = runner.run_qa()
        print(f"QA valid={report['validCount']} failures={report['failureCount']} warnings={report['warningCount']}")
        return 0 if report["failureCount"] == 0 else 2

    if args.key:
        requested = set(args.key)
        selected = [row for row in rows if row["key"] in requested]
        missing = requested - {row["key"] for row in selected}
        if missing:
            raise BatchError(f"unknown keys: {', '.join(sorted(missing))}")
    elif args.probe:
        selected = [rows[0], max(rows, key=lambda row: row["characterCount"])]
    elif args.trial is not None:
        selected = select_trial_rows(rows, max(1, args.trial))
    else:
        selected = rows

    runner.generate(selected, force=args.force)
    report = runner.run_qa()
    selected_failures = [failure for failure in report["failures"] if failure["key"] in {row["key"] for row in selected}]
    return 0 if not runner.failures and not selected_failures else 2


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except BatchError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(2)
