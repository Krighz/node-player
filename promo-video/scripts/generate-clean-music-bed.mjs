import fs from 'fs';
import path from 'path';
import { spawnSync } from 'child_process';

const SAMPLE_RATE = 48000;
const DURATION_SEC = 35.0;
const TOTAL_SAMPLES = Math.floor(DURATION_SEC * SAMPLE_RATE);
const BPM = 124;
const BEAT_DUR = 60 / BPM; // 0.483871 s
const BEAT_SAMPLES = Math.floor(BEAT_DUR * SAMPLE_RATE); // 23226 samples

function createWavHeader(numSamples, numChannels = 2, sampleRate = SAMPLE_RATE) {
  const bytesPerSample = 2;
  const blockAlign = numChannels * bytesPerSample;
  const byteRate = sampleRate * blockAlign;
  const dataSize = numSamples * blockAlign;
  const buffer = Buffer.alloc(44);

  buffer.write('RIFF', 0);
  buffer.writeUInt32LE(36 + dataSize, 4);
  buffer.write('WAVE', 8);
  buffer.write('fmt ', 12);
  buffer.writeUInt32LE(16, 16);
  buffer.writeUInt16LE(1, 20); // PCM
  buffer.writeUInt16LE(numChannels, 22);
  buffer.writeUInt32LE(sampleRate, 24);
  buffer.writeUInt32LE(byteRate, 28);
  buffer.writeUInt16LE(blockAlign, 32);
  buffer.writeUInt16LE(bytesPerSample * 8, 34);
  buffer.write('data', 36);
  buffer.writeUInt32LE(dataSize, 40);

  return buffer;
}

// Float buffers for L and R
const leftBuffer = new Float32Array(TOTAL_SAMPLES);
const rightBuffer = new Float32Array(TOTAL_SAMPLES);

// 1. Generate 124 BPM Minimalist Percussion & Synth Groove
// Chords (frequencies in Hz):
// Section A (0s to 15.5s): Dm9 (D3, F3, A3, C4, E4) -> Bbmaj7 (Bb2, D3, F3, A3)
// Section B (15.5s to 27.5s, DROP!): Powerful Dm bass groove + bright pluck arp + four-on-the-floor kick
// Section C (27.5s to 35.0s, Outro): Lush airy pad + fading beat

const chords = [
  [146.83, 174.61, 220.00, 261.63, 329.63], // Dm9
  [116.54, 146.83, 174.61, 220.00],         // Bbmaj7
  [146.83, 174.61, 220.00, 261.63, 329.63], // Dm9
  [130.81, 164.81, 196.00, 246.94],         // Cmaj9
];

const totalBeats = Math.floor(DURATION_SEC / BEAT_DUR);

for (let beat = 0; beat < totalBeats; beat++) {
  const beatStartSample = Math.floor(beat * BEAT_SAMPLES);
  const beatTime = beat * BEAT_DUR;
  const isDrop = beatTime >= 15.4 && beatTime < 27.5; // Scene 4 & 5
  const isIntro = beatTime < 4.5;
  const isOutro = beatTime >= 31.0;

  // Pattern: 4 beats per bar
  const barIndex = Math.floor(beat / 4);
  const beatInBar = beat % 4;
  const chord = chords[barIndex % chords.length];

  // A. Kick Drum:
  // Four on the floor during Drop; beats 0 and 2 otherwise
  const playKick = isDrop || (!isIntro && (beatInBar === 0 || beatInBar === 2));
  if (playKick && !isOutro) {
    const kickSamples = Math.floor(0.28 * SAMPLE_RATE);
    for (let k = 0; k < kickSamples; k++) {
      const idx = beatStartSample + k;
      if (idx >= TOTAL_SAMPLES) break;
      const t = k / SAMPLE_RATE;
      const p = k / kickSamples;
      const env = Math.exp(-p * 14);
      // Pitch drop 135Hz -> 42Hz
      const freq = 42 + 93 * Math.exp(-p * 18);
      const sub = Math.sin(2 * Math.PI * freq * t);
      const click = p < 0.03 ? (Math.random() * 2 - 1) * 0.3 * Math.exp(-p * 50) : 0;
      const kickVal = (sub * 0.72 + click) * env * (isDrop ? 0.85 : 0.65);
      leftBuffer[idx] += kickVal;
      rightBuffer[idx] += kickVal;
    }
  }

  // B. Snare / Clap: on beat 1 and 3 (0-indexed 1 and 3)
  const playSnare = !isIntro && !isOutro && (beatInBar === 1 || beatInBar === 3);
  if (playSnare) {
    const snareSamples = Math.floor(0.2 * SAMPLE_RATE);
    for (let s = 0; s < snareSamples; s++) {
      const idx = beatStartSample + s;
      if (idx >= TOTAL_SAMPLES) break;
      const p = s / snareSamples;
      const env = Math.exp(-p * 12);
      const noise = (Math.random() * 2 - 1) * 0.45;
      const tone = Math.sin(2 * Math.PI * 185 * (s / SAMPLE_RATE)) * 0.3;
      const snareVal = (noise + tone) * env * (isDrop ? 0.55 : 0.4);
      leftBuffer[idx] += snareVal * 0.95;
      rightBuffer[idx] += snareVal * 1.05;
    }
  }

  // C. Hi-Hats: 8th notes (every half beat)
  for (let sub = 0; sub < 2; sub++) {
    const hatStart = beatStartSample + Math.floor(sub * (BEAT_SAMPLES / 2));
    const hatSamples = Math.floor(0.045 * SAMPLE_RATE);
    const hatVol = sub === 1 ? (isDrop ? 0.22 : 0.15) : (isDrop ? 0.14 : 0.09);
    for (let h = 0; h < hatSamples; h++) {
      const idx = hatStart + h;
      if (idx >= TOTAL_SAMPLES) break;
      const p = h / hatSamples;
      const env = Math.exp(-p * 22);
      const noise = (Math.random() * 2 - 1);
      leftBuffer[idx] += noise * env * hatVol * 0.85;
      rightBuffer[idx] += noise * env * hatVol * 1.15;
    }
  }

  // D. Synth Pad / Chord Stabs on beat 0 of every 2 beats
  if (beatInBar === 0 || beatInBar === 2) {
    const padSamples = Math.floor(0.85 * BEAT_SAMPLES);
    for (let f = 0; f < padSamples; f++) {
      const idx = beatStartSample + f;
      if (idx >= TOTAL_SAMPLES) break;
      const t = f / SAMPLE_RATE;
      const p = f / padSamples;
      const env = Math.sin(p * Math.PI) * Math.exp(-p * 1.2);
      let chordValL = 0;
      let chordValR = 0;
      for (let n = 0; n < chord.length; n++) {
        const freq = chord[n];
        const s1 = Math.sin(2 * Math.PI * freq * t);
        const s2 = Math.sin(2 * Math.PI * (freq * 1.003) * t); // slight detune for warmth
        chordValL += (s1 * 0.7 + s2 * 0.3) * (0.08 / chord.length);
        chordValR += (s2 * 0.7 + s1 * 0.3) * (0.08 / chord.length);
      }
      const padVol = isDrop ? 0.75 : 0.55;
      leftBuffer[idx] += chordValL * env * padVol;
      rightBuffer[idx] += chordValR * env * padVol;
    }
  }

  // E. 16th-note Pluck Arp during Drop (beats 32 to 57)
  if (isDrop) {
    for (let step = 0; step < 4; step++) {
      const stepStart = beatStartSample + Math.floor(step * (BEAT_SAMPLES / 4));
      const arpFreq = chord[(beat * 4 + step) % chord.length] * 2; // an octave higher
      const arpSamples = Math.floor(0.12 * SAMPLE_RATE);
      for (let a = 0; a < arpSamples; a++) {
        const idx = stepStart + a;
        if (idx >= TOTAL_SAMPLES) break;
        const t = a / SAMPLE_RATE;
        const p = a / arpSamples;
        const env = Math.exp(-p * 18);
        const val = Math.sin(2 * Math.PI * arpFreq * t) * env * 0.08;
        leftBuffer[idx] += val * (step % 2 === 0 ? 1.2 : 0.8);
        rightBuffer[idx] += val * (step % 2 === 0 ? 0.8 : 1.2);
      }
    }
  }
}

// Master fade in (0 to 0.4s) and fade out (32s to 35s)
for (let i = 0; i < TOTAL_SAMPLES; i++) {
  const t = i / SAMPLE_RATE;
  let masterEnv = 1.0;
  if (t < 0.4) {
    masterEnv = t / 0.4;
  } else if (t > 32.0) {
    masterEnv = Math.max(0, 1 - (t - 32.0) / 3.0);
  }
  leftBuffer[i] *= masterEnv;
  rightBuffer[i] *= masterEnv;
}

// Write raw music bed WAV
const rawBedPath = path.resolve('public/audio/bg_beat_raw.wav');
const masteredBedPath = path.resolve('public/audio/bg_beat.wav');

const bedHeader = createWavHeader(TOTAL_SAMPLES, 2, SAMPLE_RATE);
const bedDataBuffer = Buffer.alloc(TOTAL_SAMPLES * 4);

for (let i = 0; i < TOTAL_SAMPLES; i++) {
  const clL = Math.max(-1, Math.min(1, leftBuffer[i]));
  const clR = Math.max(-1, Math.min(1, rightBuffer[i]));
  bedDataBuffer.writeInt16LE(Math.floor(clL * 32767), i * 4);
  bedDataBuffer.writeInt16LE(Math.floor(clR * 32767), i * 4 + 2);
}

fs.writeFileSync(rawBedPath, Buffer.concat([bedHeader, bedDataBuffer]));
console.log(`Generated raw music bed: ${rawBedPath}`);

// Run FFmpeg clean limiter (-1.0 dBFS ceiling, zero clipping, transparent mastering)
const ffmpegExe = "D:\\Node player\\node-player\\promo-video\\node_modules\\@remotion\\compositor-win32-x64-msvc\\ffmpeg.exe";
if (fs.existsSync(ffmpegExe)) {
  console.log('Mastering music bed via FFmpeg limiter (-1.0 dBFS)...');
  spawnSync(ffmpegExe, [
    '-y',
    '-i', rawBedPath,
    '-af', 'alimiter=limit=0.89:attack=5:release=50:asc=1',
    masteredBedPath
  ]);
  console.log(`Mastered: ${masteredBedPath}`);
} else {
  fs.copyFileSync(rawBedPath, masteredBedPath);
}

// 2. Synthesize Crisp UI SFX with Clean Limiter and Zero Noise
const sfxDir = path.resolve('public/sfx');
fs.mkdirSync(sfxDir, { recursive: true });

function writeWavDirect(filename, generateSample, durationMs) {
  const numSamples = Math.floor((durationMs / 1000) * SAMPLE_RATE);
  const header = createWavHeader(numSamples, 2, SAMPLE_RATE);
  const dataBuffer = Buffer.alloc(numSamples * 4);

  for (let i = 0; i < numSamples; i++) {
    const t = i / SAMPLE_RATE;
    const progress = i / numSamples;
    const val = generateSample(t, progress);
    const clamped = Math.max(-0.89, Math.min(0.89, val)); // clean -1.0 dBFS headroom
    const intSample = Math.floor(clamped * 32767);
    dataBuffer.writeInt16LE(intSample, i * 4);
    dataBuffer.writeInt16LE(intSample, i * 4 + 2);
  }

  const fullBuffer = Buffer.concat([header, dataBuffer]);
  fs.writeFileSync(filename, fullBuffer);
  console.log(`Generated SFX: ${filename} (${(fullBuffer.length / 1024).toFixed(1)} KB)`);
}

// 1. sfx_click.wav (Crisp mechanical micro-click, 45ms, 960Hz transient)
writeWavDirect(path.join(sfxDir, 'sfx_click.wav'), (t, p) => {
  const env = Math.exp(-p * 25);
  const sine = Math.sin(2 * Math.PI * 960 * t);
  const transient = p < 0.05 ? Math.sin(2 * Math.PI * 2400 * t) * 0.4 : 0;
  return (sine * 0.7 + transient) * env * 0.85;
}, 45);

// 2. sfx_pop.wav (Warm elastic spring pop, 110ms, 480Hz -> 180Hz)
writeWavDirect(path.join(sfxDir, 'sfx_pop.wav'), (t, p) => {
  const env = Math.exp(-p * 12);
  const freq = 480 - 300 * Math.pow(p, 0.65);
  const sine = Math.sin(2 * Math.PI * freq * t);
  return sine * env * 0.88;
}, 110);

// 3. sfx_whoosh.wav (Silky filtered stereo whoosh, 200ms)
writeWavDirect(path.join(sfxDir, 'sfx_whoosh.wav'), (t, p) => {
  const env = Math.sin(p * Math.PI);
  const freq = 420 - 280 * p;
  const sine = Math.sin(2 * Math.PI * freq * t);
  const noise = (Math.random() * 2 - 1) * 0.35;
  return (sine * 0.6 + noise) * env * 0.75;
}, 200);

// 4. sfx_sub_drop.wav / sfx_bass_impact.wav (Deep 420ms sub-bass drop, 72Hz -> 32Hz)
writeWavDirect(path.join(sfxDir, 'sfx_sub_drop.wav'), (t, p) => {
  const env = Math.exp(-p * 6);
  const freq = 72 - 40 * Math.pow(p, 0.6);
  const sub = Math.sin(2 * Math.PI * freq * t);
  const punch = p < 0.06 ? Math.sin(2 * Math.PI * 130 * t) * 0.35 : 0;
  return (sub * 0.8 + punch) * env * 0.88;
}, 420);

// Also copy to sfx_bass_impact.wav for compatibility
fs.copyFileSync(path.join(sfxDir, 'sfx_sub_drop.wav'), path.join(sfxDir, 'sfx_bass_impact.wav'));
console.log('SFX generation complete.');
