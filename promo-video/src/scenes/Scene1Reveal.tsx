import React from 'react';
import { Img, interpolate, spring, staticFile, useCurrentFrame, useVideoConfig } from 'remotion';
import { StudioBackground } from '../components/StudioBackground';

interface SceneProps {
  layout: '16x9' | '9x16';
}

export const Scene1Reveal: React.FC<SceneProps> = ({ layout }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  // Critically damped spring for the logo pop
  const logoSpring = spring({
    frame,
    fps,
    config: {
      damping: 18,
      stiffness: 160,
    },
  });

  const logoScale = interpolate(logoSpring, [0, 1], [0.7, 1]);
  const logoOpacity = interpolate(logoSpring, [0, 1], [0, 1]);

  // Text reveal delayed by 18 frames
  const textSpring = spring({
    frame: Math.max(0, frame - 18),
    fps,
    config: {
      damping: 18,
      stiffness: 140,
    },
  });

  const textY = interpolate(textSpring, [0, 1], [35, 0]);
  const textOpacity = interpolate(textSpring, [0, 1], [0, 1]);

  // Exit transition towards end of scene (frame 180 to 210)
  const exitProgress = interpolate(frame, [180, 210], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const exitOpacity = 1 - exitProgress;
  const exitScale = 1 + exitProgress * 0.08;

  const isPortrait = layout === '9x16';
  const logoSize = isPortrait ? 130 : 160;

  return (
    <div
      style={{
        position: 'absolute',
        inset: 0,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        opacity: exitOpacity,
        transform: `scale(${exitScale})`,
        fontFamily: 'Inter, system-ui, -apple-system, sans-serif',
      }}
    >
      <StudioBackground accentIntensity={0.16} />

      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          textAlign: 'center',
          zIndex: 10,
          padding: '0 32px',
        }}
      >
        {/* Brand Squircle Logo Mark */}
        <div
          style={{
            width: logoSize,
            height: logoSize,
            transform: `scale(${logoScale})`,
            opacity: logoOpacity,
            filter: 'drop-shadow(0 20px 45px rgba(94, 234, 212, 0.25))',
            marginBottom: isPortrait ? 28 : 36,
          }}
        >
          <Img
            src={staticFile('nodeplayer-logo.svg')}
            style={{ width: '100%', height: '100%' }}
          />
        </div>

        {/* Brand Typography */}
        <div
          style={{
            transform: `translateY(${textY}px)`,
            opacity: textOpacity,
          }}
        >
          <div
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 8,
              padding: '6px 16px',
              borderRadius: 999,
              backgroundColor: 'rgba(94, 234, 212, 0.12)',
              border: '1px solid rgba(94, 234, 212, 0.3)',
              color: '#5EEAD4',
              fontSize: isPortrait ? 13 : 15,
              fontWeight: 700,
              letterSpacing: '0.1em',
              textTransform: 'uppercase',
              marginBottom: 18,
            }}
          >
            <span
              style={{
                width: 6,
                height: 6,
                borderRadius: '50%',
                backgroundColor: '#5EEAD4',
                boxShadow: '0 0 10px #5EEAD4',
              }}
            />
            Official v2.0 Release
          </div>

          <h1
            style={{
              margin: 0,
              fontSize: isPortrait ? 56 : 74,
              fontWeight: 900,
              color: '#FAFAFA',
              letterSpacing: '-0.04em',
              lineHeight: 1.08,
            }}
          >
            Node Player
          </h1>

          <p
            style={{
              margin: '18px 0 0 0',
              fontSize: isPortrait ? 20 : 26,
              fontWeight: 500,
              color: '#A1A1AA',
              maxWidth: isPortrait ? 420 : 680,
              lineHeight: 1.45,
            }}
          >
            Hybrid Streaming. Bit-Perfect 32-Bit Local Audio.
          </p>
        </div>
      </div>
    </div>
  );
};
