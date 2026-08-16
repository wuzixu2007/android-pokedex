import base64
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import threading
import unittest
import wave
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


MODULE_PATH = Path(__file__).parents[1] / "generate_pokemon_audio.py"
SPEC = importlib.util.spec_from_file_location("pokemon_audio", MODULE_PATH)
audio = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(audio)


def make_wav(frames=800, rate=8000, value=1000):
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as writer:
        writer.setnchannels(1)
        writer.setsampwidth(2)
        writer.setframerate(rate)
        writer.writeframes(int(value).to_bytes(2, "little", signed=True) * frames)
    return buffer.getvalue()


class ApiHandler(BaseHTTPRequestHandler):
    calls = []

    def log_message(self, *args):
        pass

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        payload = json.loads(self.rfile.read(length))
        self.__class__.calls.append(("POST", self.path, payload))
        body = make_wav(frames=max(800, len(payload["text"]) * 50))
        self.send_response(200)
        self.send_header("Content-Type", "audio/wav")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        self.__class__.calls.append(("GET", self.path, None))
        body = json.dumps({"audio_base64": base64.b64encode(make_wav()).decode("ascii")}).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(body)


class PokemonAudioTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        ApiHandler.calls = []
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), ApiHandler)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        cls.url = f"http://127.0.0.1:{cls.server.server_port}"

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()

    def config(self, mode="v2"):
        return {
            "api_url": self.url,
            "api_mode": mode,
            "reference_audio": "reference.wav",
            "reference_text": "参考文本。",
            "prompt_language": "zh",
            "text_language": "zh",
            "max_chars": 40,
            "workers": 1,
            "timeout_seconds": 10,
            "retry_attempts": 2,
            "retry_base_seconds": 0,
        }

    def records(self):
        shared = (
            "普通段落。\n"
            "超级测试兽拥有更强的力量。后一句延续描述，并继续说明这个特殊形态的外观、能力和行动方式，"
            "确保整段内容都应该归入超级形态而不是普通形态。"
        )
        return [
            {"key": "normal", "id": "0001", "nameZh": "测试兽", "sourceFormName": "测试兽",
             "attributeLabel": "一般属性宝可梦", "description": "测试兽（英文：Test）是一般属性宝可梦。", "profile": shared},
            {"key": "mega", "id": "0001", "nameZh": "超级测试兽", "sourceFormName": "超级测试兽",
             "attributeLabel": "一般属性宝可梦", "description": "测试兽（英文：Test）是一般属性宝可梦。", "profile": shared},
        ]

    def test_form_split_keeps_whole_special_paragraph(self):
        rows = audio.build_speech_rows(self.records(), 40)
        self.assertNotIn("超级测试兽", rows[0]["text"])
        self.assertIn("后一句延续描述", rows[1]["text"])
        self.assertFalse(rows[1]["splitFallback"])

    def test_v2_and_legacy_json_response(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            v2 = audio.GptSovitsClient(self.config("v2"), root).synthesize("测试。")
            legacy = audio.GptSovitsClient(self.config("legacy"), root).synthesize("测试。")
            self.assertGreater(audio.validate_wav_bytes(v2)["frames"], 0)
            self.assertGreater(audio.validate_wav_bytes(legacy)["frames"], 0)

    def test_runner_merges_and_resumes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            rows = audio.build_speech_rows(self.records(), 40)
            runner = audio.BatchRunner(root, self.config("v2"), rows)
            runner.generate(rows)
            first_calls = len(ApiHandler.calls)
            self.assertEqual(2, len(runner.results))
            report = runner.run_qa()
            self.assertEqual(2, report["validCount"])
            runner.generate(rows)
            self.assertEqual(first_calls, len(ApiHandler.calls))
            (runner.wav_dir / "normal.wav").unlink()
            runner.generate(rows)
            self.assertTrue((runner.wav_dir / "normal.wav").is_file())
            self.assertEqual(first_calls, len(ApiHandler.calls), "cached segments should avoid another API call")

    def test_silent_wav_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "silent.wav"
            path.write_bytes(make_wav(value=0))
            with self.assertRaises(audio.BatchError):
                audio.validate_wav_file(path)

    def test_failure_list_survives_restart_and_clears_after_success(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            rows = audio.build_speech_rows(self.records(), 40)
            runner = audio.BatchRunner(root, self.config("v2"), rows)
            runner.failures[rows[0]["key"]] = {"key": rows[0]["key"], "error": "old failure"}
            runner.persist()
            restarted = audio.BatchRunner(root, self.config("v2"), rows)
            self.assertIn(rows[0]["key"], restarted.failures)
            restarted.generate([rows[0]])
            self.assertNotIn(rows[0]["key"], restarted.failures)


if __name__ == "__main__":
    unittest.main()
