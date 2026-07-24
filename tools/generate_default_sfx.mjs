/**
 * Generates original, royalty-free PCM WAV effects bundled by the Android app.
 * 生成 Android 应用内置的原创免版税 PCM WAV 音效。
 */
import fs from "node:fs";
import path from "node:path";

const sampleRate = 44_100;
const outputDir = path.resolve("app/src/main/res/raw");

const effects = [
  {
    name: "scan_start.wav",
    duration: 0.56,
    notes: [
      { start: 0.00, length: 0.12, frequency: 660, gain: 0.34 },
      { start: 0.14, length: 0.12, frequency: 880, gain: 0.36 },
      { start: 0.28, length: 0.20, frequency: 1_320, gain: 0.32 },
    ],
  },
  {
    name: "recognition_success.wav",
    duration: 0.78,
    notes: [
      { start: 0.00, length: 0.16, frequency: 784, gain: 0.30 },
      { start: 0.16, length: 0.16, frequency: 988, gain: 0.32 },
      { start: 0.32, length: 0.36, frequency: 1_318, gain: 0.34 },
      { start: 0.32, length: 0.36, frequency: 1_976, gain: 0.12 },
    ],
  },
  {
    name: "recognition_failure.wav",
    duration: 0.62,
    notes: [
      { start: 0.00, length: 0.18, frequency: 520, gain: 0.32 },
      { start: 0.20, length: 0.30, frequency: 330, gain: 0.34 },
    ],
  },
];

function envelope(time, length) {
  const attack = Math.min(1, time / 0.012);
  const release = Math.min(1, (length - time) / 0.055);
  return Math.max(0, Math.min(attack, release));
}

function synthesize(effect) {
  const sampleCount = Math.ceil(effect.duration * sampleRate);
  const pcm = Buffer.alloc(sampleCount * 2);
  for (let index = 0; index < sampleCount; index += 1) {
    const time = index / sampleRate;
    let sample = 0;
    for (const note of effect.notes) {
      const localTime = time - note.start;
      if (localTime < 0 || localTime > note.length) continue;
      // Blend a clean sine with a quiet square harmonic for a compact device-like tone.
      // 将正弦波与轻微方波谐波混合，形成紧凑的电子设备提示音。
      const phase = 2 * Math.PI * note.frequency * localTime;
      const electronic = Math.sin(phase) * 0.84 + Math.sign(Math.sin(phase * 2)) * 0.16;
      sample += electronic * note.gain * envelope(localTime, note.length);
    }
    const clamped = Math.max(-1, Math.min(1, sample));
    pcm.writeInt16LE(Math.round(clamped * 32_767), index * 2);
  }
  return wavFile(pcm);
}

function wavFile(pcm) {
  const header = Buffer.alloc(44);
  header.write("RIFF", 0);
  header.writeUInt32LE(36 + pcm.length, 4);
  header.write("WAVE", 8);
  header.write("fmt ", 12);
  header.writeUInt32LE(16, 16);
  header.writeUInt16LE(1, 20);
  header.writeUInt16LE(1, 22);
  header.writeUInt32LE(sampleRate, 24);
  header.writeUInt32LE(sampleRate * 2, 28);
  header.writeUInt16LE(2, 32);
  header.writeUInt16LE(16, 34);
  header.write("data", 36);
  header.writeUInt32LE(pcm.length, 40);
  return Buffer.concat([header, pcm]);
}

fs.mkdirSync(outputDir, { recursive: true });
for (const effect of effects) {
  fs.writeFileSync(path.join(outputDir, effect.name), synthesize(effect));
}

console.log(`Generated ${effects.length} default effects in ${outputDir}`);
