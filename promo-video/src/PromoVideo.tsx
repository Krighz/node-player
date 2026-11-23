import React from 'react';
import { Series } from 'remotion';
import { AudioMasterMix } from './components/AudioMasterMix';
import { Scene1HookPill } from './scenes/Scene1HookPill';
import { Scene2RadialPhones } from './scenes/Scene2RadialPhones';
import { Scene3MacroMixtape } from './scenes/Scene3MacroMixtape';
import { Scene4SectionDrop } from './scenes/Scene4SectionDrop';
import { Scene5KaraokeBurst } from './scenes/Scene5KaraokeBurst';
import { Scene6ArtistDspParallax } from './scenes/Scene6ArtistDspParallax';
import { Scene7BrandFinale } from './scenes/Scene7BrandFinale';

export interface PromoVideoProps {
  layout?: '16x9' | '9x16';
}

export const PromoVideo: React.FC<PromoVideoProps> = ({ layout = '16x9' }) => {
  return (
    <div
      style={{
        flex: 1,
        backgroundColor: '#09090B',
        width: '100%',
        height: '100%',
        position: 'relative',
        overflow: 'hidden',
      }}
    >
      {/* Global Layered Audio Design: Clean 124 BPM Music Bed + Studio SFX (Zero Phone Noise) */}
      <AudioMasterMix />

      {/* Visual Scene Timeline: Exactly 2070 frames @ 60 FPS = 34.5s */}
      <Series>
        {/* Scene 1: Kinetic Brand Hook & Audio Switch (Frames 0–270 / 4.5s) */}
        <Series.Sequence durationInFrames={270}>
          <Scene1HookPill layout={layout} />
        </Series.Sequence>

        {/* Scene 2: 4-Phone Radial Array with Non-Overlapping Gutters (Frames 270–630 / 6.0s) */}
        <Series.Sequence durationInFrames={360}>
          <Scene2RadialPhones layout={layout} />
        </Series.Sequence>

        {/* Scene 3: Fast 2-Card Smart Offline Mixtape & Engine (Frames 630–822 / 3.2s = 192 frames) */}
        <Series.Sequence durationInFrames={192}>
          <Scene3MacroMixtape layout={layout} />
        </Series.Sequence>

        {/* Scene 4: Now Playing 3D Macro & Smart Section Drop (Frames 822–1242 / 7.0s = 420 frames) */}
        <Series.Sequence durationInFrames={420}>
          <Scene4SectionDrop layout={layout} />
        </Series.Sequence>

        {/* Scene 5: 120 FPS Karaoke Sweep & 4-Corner Authentic Lyric Cards (Frames 1242–1650 / 6.8s = 408 frames) */}
        <Series.Sequence durationInFrames={408}>
          <Scene5KaraokeBurst layout={layout} />
        </Series.Sequence>

        {/* Scene 6: Parallax Split Hubs — 380dp Artist Page & 32-Bit 10-Band EQ (Frames 1650–1890 / 4.0s = 240 frames) */}
        <Series.Sequence durationInFrames={240}>
          <Scene6ArtistDspParallax layout={layout} />
        </Series.Sequence>

        {/* Scene 7: Soundwave Convergence, Squircle Brand Impact & Outro (Frames 1890–2070 / 3.0s = 180 frames) */}
        <Series.Sequence durationInFrames={180}>
          <Scene7BrandFinale layout={layout} />
        </Series.Sequence>
      </Series>
    </div>
  );
};
