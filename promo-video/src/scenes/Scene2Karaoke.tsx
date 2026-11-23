import React from 'react';
import { interpolate, spring, useCurrentFrame, useVideoConfig } from 'remotion';
import { KineticBadge } from '../components/KineticBadge';
import { RealisticPhoneFrame } from '../components/RealisticPhoneFrame';
import { StudioBackground } from '../components/StudioBackground';

interface SceneProps {
  layout: '16x9' | '9x16';
}

export const Scene2Karaoke: React.FC<SceneProps> = ({ layout }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  const isPortrait = layout === '9x16';

  // Entry spring for 3D Phone
  const enterSpring = spring({
    frame,
    fps,
    config: {
      damping: 17,
      stiffness: 110,
    },
  });

  // Camera 3D movement
  const phoneX = isPortrait
    ? 0
    : interpolate(enterSpring, [0, 1], [400, 180]);
  const phoneY = isPortrait
    ? interpolate(enterSpring, [0, 1], [300, 120])
    : interpolate(enterSpring, [0, 1], [150, 0]);
  const phoneRotateY = interpolate(frame, [0, 330], [-18, 12]);
  const phoneRotateX = interpolate(frame, [0, 330], [8, -4]);
  const phoneScale = isPortrait
    ? interpolate(enterSpring, [0, 1], [0.85, 0.98])
    : interpolate(enterSpring, [0, 1], [0.85, 1.05]);

  // Transition from live karaoke sweep to lyric card midway (around frame 170)
  const showLyricCard = frame > 175;

  // Scene exit transition (last 25 frames)
  const exitProgress = interpolate(frame, [305, 330], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const exitOpacity = 1 - exitProgress;

  const phoneWidth = isPortrait ? 380 : 360;

  return (
    <div
      style={{
        position: 'absolute',
        inset: 0,
        opacity: exitOpacity,
        overflow: 'hidden',
        fontFamily: 'Inter, system-ui, -apple-system, sans-serif',
      }}
    >
      <StudioBackground accentIntensity={0.14} glowCenterX={isPortrait ? 50 : 65} />

      {/* 16:9 Landscape Layout */}
      {!isPortrait ? (
        <div
          style={{
            position: 'absolute',
            inset: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '0 120px',
            boxSizing: 'border-box',
          }}
        >
          {/* Left Text Callout */}
          <KineticBadge
            category="Word-by-Word Lyrics"
            title="120 FPS Real-Time Syllable Karaoke"
            subtitle="Sub-pixel hardware-accelerated sweep with active-word illumination, TTML syllable timing, and instant shareable Lyric Cards."
            highlights={[
              '5 Lyric Providers',
              'Sub-Pixel ClipRect',
              '1080×1920 Lyric Cards',
            ]}
            delay={10}
            maxWidth={640}
          />

          {/* Right 3D Phone Chassis */}
          <div style={{ position: 'relative', width: 450, height: 780, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <RealisticPhoneFrame
              width={phoneWidth}
              rotateX={phoneRotateX}
              rotateY={phoneRotateY}
              translateX={phoneX - 180}
              translateY={phoneY}
              scale={phoneScale}
              imageSrc={showLyricCard ? 'captures/04_lyric_card.png' : 'captures/03_karaoke.png'}
              videoSrc={!showLyricCard ? 'captures/clip_karaoke.mp4' : undefined}
            />
          </div>
        </div>
      ) : (
        /* 9:16 Portrait / Shorts Layout */
        <div
          style={{
            position: 'absolute',
            inset: 0,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            padding: '90px 40px 0 40px',
            boxSizing: 'border-box',
          }}
        >
          <KineticBadge
            category="Word-by-Word Lyrics"
            title="120 FPS Syllable Karaoke"
            subtitle="Sub-pixel active word sweep and 1-tap shareable Lyric Cards."
            highlights={['5 Lyric Engines', '1080×1920 Cards']}
            delay={8}
            align="center"
            maxWidth={520}
          />

          <div style={{ position: 'relative', marginTop: 40, width: '100%', height: 950, display: 'flex', justifyContent: 'center' }}>
            <RealisticPhoneFrame
              width={phoneWidth}
              rotateX={phoneRotateX}
              rotateY={phoneRotateY}
              translateY={phoneY - 120}
              scale={phoneScale}
              imageSrc={showLyricCard ? 'captures/04_lyric_card.png' : 'captures/03_karaoke.png'}
              videoSrc={!showLyricCard ? 'captures/clip_karaoke.mp4' : undefined}
            />
          </div>
        </div>
      )}
    </div>
  );
};
