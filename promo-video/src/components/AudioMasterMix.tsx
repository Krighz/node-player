import React from 'react';
import { Audio, interpolate, staticFile, useCurrentFrame } from 'remotion';

export const AudioMasterMix: React.FC = () => {
  const frame = useCurrentFrame();

  // Master fade-out in final 90 frames (frames 1980 to 2070)
  const masterFade = interpolate(frame, [1980, 2070], [1, 0], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });

  // Dynamic master bed volume (clean -1.0 dBFS ceiling, zero phone noise)
  const effectiveBgBeatVolume = 0.88 * masterFade;

  // Key SFX trigger frames quantized to 124 BPM beat grid (~29 frames per beat)
  // Sub-Bass Drops
  const subDropFrames = [0, 912, 1920];
  // Micro-Clicks
  const clickFrames = [232, 912, 1392];
  // Silky Whooshes
  const whooshFrames = [270, 630, 822, 1242, 1650, 1890];
  // Warm Spring Pops
  const popFrames = [58, 116, 174, 667, 1422];

  return (
    <>
      {/* 1. Master 124 BPM Minimalist Electronic Music Bed (Clean -1.0 dBFS, Zero Phone Noise) */}
      <Audio
        src={staticFile('audio/bg_beat.wav')}
        volume={effectiveBgBeatVolume}
        startFrom={0}
      />

      {/* 2. Studio Sub-Bass Drop SFX */}
      {subDropFrames.map((trigFrame) =>
        frame >= trigFrame && frame < trigFrame + 30 ? (
          <Audio
            key={`sub-${trigFrame}`}
            src={staticFile('sfx/sfx_sub_drop.wav')}
            volume={0.92 * masterFade}
          />
        ) : null
      )}

      {/* 3. Crisp Mechanical Micro-Click SFX */}
      {clickFrames.map((trigFrame) =>
        frame >= trigFrame && frame < trigFrame + 6 ? (
          <Audio
            key={`click-${trigFrame}`}
            src={staticFile('sfx/sfx_click.wav')}
            volume={0.95 * masterFade}
          />
        ) : null
      )}

      {/* 4. Silky Whoosh Transition SFX */}
      {whooshFrames.map((trigFrame) =>
        frame >= trigFrame && frame < trigFrame + 18 ? (
          <Audio
            key={`whoosh-${trigFrame}`}
            src={staticFile('sfx/sfx_whoosh.wav')}
            volume={0.82 * masterFade}
          />
        ) : null
      )}

      {/* 5. Warm Spring Pop SFX */}
      {popFrames.map((trigFrame) =>
        frame >= trigFrame && frame < trigFrame + 10 ? (
          <Audio
            key={`pop-${trigFrame}`}
            src={staticFile('sfx/sfx_pop.wav')}
            volume={0.88 * masterFade}
          />
        ) : null
      )}
    </>
  );
};
