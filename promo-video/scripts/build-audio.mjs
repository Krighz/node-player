import fs from 'fs';
import path from 'path';
import { spawnSync } from 'child_process';

const SAMPLE_RATE = 48000;

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
  buffer.writeUInt32LE(16, 16); // Subchunk1Size (16 for PCM)
  buffer.writeUInt16LE(1, 20);  // AudioFormat (1 for PCM)
  buffer.writeUInt16LE(numChannels, 22);
  buffer.writeUInt32LE(sampleRate, 24);
  buffer.writeUInt32LE(byteRate, 28);
  buffer.writeUInt16LE(blockAlign, 32);
  buffer.writeUInt16LE(bytesPerSample * 8, 34); // BitsPerSample
  buffer.write('data', 36);
  buffer.writeUInt32LE(dataSize, 40);

  return buffer;
}

function writeWavFile(filename, generateSample, durationMs) {
  const numSamples = Math.floor((durationMs / 1000) * SAMPLE_RATE);
  const header = createWavHeader(numSamples, 2, SAMPLE_RATE);
  const dataBuffer = Buffer.alloc(numSamples * 4); // 2 channels * 2 bytes

  for (let i = 0; i < numSamples; i++) {
    const t = i / SAMPLE_RATE;
    const progress = i / numSamples;
    const val = generateSample(t, progress);
    const clamped = Math.max(-1, Math.min(1, val));
    const intSample = Math.floor(clamped * 32767);
    
    // Left channel
    dataBuffer.writeInt16LE(intSample, i * 4);
    // Right channel
    dataBuffer.writeInt16LE(intSample, i * 4 + 2);
  }

  const fullBuffer = Buffer.concat([header, dataBuffer]);
  fs.writeFileSync(filename, fullBuffer);
  console.log(`Generated: ${filename} (${(fullBuffer.length / 1024).toFixed(1)} KB)`);
}

const sfxDir = path.resolve('public/sfx');
fs.mkdirSync(sfxDir, { recursive: true });

// 1. sfx_whoosh.wav - 220ms filtered noise + pitch sweep (350Hz -> 110Hz)
writeWavFile(path.join(sfxDir, 'sfx_whoosh.wav'), (t, p) => {
  const env = Math.sin(p * Math.PI); // Smooth bell envelope
  const freq = 360 - 240 * p;
  const sine = Math.sin(2 * Math.PI * freq * t);
  const noise = (Math.random() * 2 - 1) * 0.45;
  return (sine * 0.55 + noise) * env * 0.85;
}, 220);

// 2. sfx_click.wav - 65ms crisp micro-click + 900Hz pop
writeWavFile(path.join(sfxDir, 'sfx_click.wav'), (t, p) => {
  const env = Math.exp(-p * 18);
  const click = Math.sin(2 * Math.PI * 920 * t) * 0.65;
  const transient = p < 0.08 ? (Math.random() * 2 - 1) * 0.8 : 0;
  return (click + transient) * env * 0.9;
}, 65);

// 3. sfx_pop.wav - 140ms warm elastic spring pop (460Hz -> 160Hz)
writeWavFile(path.join(sfxDir, 'sfx_pop.wav'), (t, p) => {
  const env = Math.exp(-p * 10);
  const freq = 460 - 300 * Math.pow(p, 0.7);
  const sine = Math.sin(2 * Math.PI * freq * t);
  return sine * env * 0.95;
}, 140);

// 4. sfx_bass_impact.wav - 450ms deep sub-bass drop (65Hz -> 32Hz)
writeWavFile(path.join(sfxDir, 'sfx_bass_impact.wav'), (t, p) => {
  const env = Math.exp(-p * 5.5);
  const freq = 65 - 33 * Math.pow(p, 0.6);
  const sub = Math.sin(2 * Math.PI * freq * t);
  const thump = p < 0.05 ? Math.sin(2 * Math.PI * 110 * t) * 0.4 : 0;
  return (sub * 0.8 + thump) * env * 0.98;
}, 450);

// 5. Extract phone playback music into public/audio/phone_music.wav
const pythonFindFfmpeg = `import imageio_ffmpeg; print(imageio_ffmpeg.get_ffmpeg_exe())`;
const ffmpegRes = spawnSync('python', ['-c', pythonFindFfmpeg], { encoding: 'utf-8' });
const ffmpegExe = ffmpegRes.stdout.trim();

if (ffmpegExe && fs.existsSync(ffmpegExe)) {
  const liveClip = path.resolve('public/captures/live_02_karaoke_sweep.mp4');
  const outAudio = path.resolve('public/audio/phone_music.wav');
  if (fs.existsSync(liveClip)) {
    console.log(`Extracting phone music audio track from ${liveClip}...`);
    spawnSync(ffmpegExe, ['-y', '-i', liveClip, '-vn', '-acodec', 'pcm_s16le', '-ar', '48000', '-ac', '2', outAudio]);
    console.log(`Extracted: ${outAudio}`);
  }
}
