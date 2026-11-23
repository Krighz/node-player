import React from 'react';
import { interpolate, spring, useCurrentFrame, useVideoConfig } from 'remotion';
import { KineticBadge } from '../components/KineticBadge';
import { RealisticPhoneFrame } from '../components/RealisticPhoneFrame';
import { StudioBackground } from '../components/StudioBackground';

interface SceneProps {
  layout: '16x9' | '9x16';
}

export const Scene3HybridStream: React.FC<SceneProps> = ({ layout }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  const isPortrait = layout === '9x16';

  // Entry spring
  const enterSpring = spring({
    frame,
    fps,
    config: {
      damping: 17,
      stiffness: 110,
    },
  });

  // Dynamic 3D rotation from left to center
  const phoneRotateY = interpolate(frame, [0, 330], [16, -10]);
  const phoneRotateX = interpolate(frame, [0, 330], [-6, 6]);
  const phoneScale = isPortrait
    ? interpolate(enterSpring, [0, 1], [0.85, 0.98])
    : interpolate(enterSpring, [0, 1], [0.85, 1.05]);

  // Transition from Home feed to Now Playing sheet with section markers at frame 160
  const showNowPlaying = frame > 160;

  // Scene exit transition
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
      <StudioBackground accentIntensity={0.15} glowCenterX={isPortrait ? 50 : 35} />

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
          {/* Left 3D Phone Chassis */}
          <div style={{ position: 'relative', width: 450, height: 780, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <RealisticPhoneFrame
              width={phoneWidth}
              rotateX={phoneRotateX}
              rotateY={phoneRotateY}
              scale={phoneScale}
              imageSrc={showNowPlaying ? 'captures/02_now_playing.png' : 'captures/01_home.png'}
            />
          </div>

          {/* Right Text Callout */}
          <KineticBadge
            category="Hybrid Architecture"
            title="YouTube Music & Bit-Perfect Storage"
            subtitle="Switch between infinite online catalogs with ~1.1s startup and bit-perfect local files. Features Smart Offline Mixtape and interactive Song Section Markers."
            highlights={[
              '~1.1s Instant Stream',
              'Smart Offline Mixtape',
              'Song Section Markers',
            ]}
            delay={10}
            maxWidth={640}
          />
        </div>
      ) : (
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
            category="Hybrid Architecture"
            title="Instant Streaming & Mixtape"
            subtitle="~1.1s YouTube stream startup with autonomous offline caching."
            highlights={['~1.1s Startup', 'Offline Mixtape', 'Section Markers']}
            delay={8}
            align="center"
            maxWidth={520}
          />

          <div style={{ position: 'relative', marginTop: 40, width: '100%', height: 950, display: 'flex', justifyContent: 'center' }}>
            <RealisticPhoneFrame
              width={phoneWidth}
              rotateX={phoneRotateX}
              rotateY={phoneRotateY}
              scale={phoneScale}
              imageSrc={showNowPlaying ? 'captures/02_now_playing.png' : 'captures/01_home.png'}
            />
          </div>
        </div>
      )}
    </div>
  );
};
