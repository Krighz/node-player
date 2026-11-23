import React from 'react';
import { Img, interpolate, spring, staticFile, useCurrentFrame, useVideoConfig } from 'remotion';
import { StudioBackground } from '../components/StudioBackground';

interface SceneProps {
  layout: '16x9' | '9x16';
}

export const Scene5Outro: React.FC<SceneProps> = ({ layout }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  const isPortrait = layout === '9x16';

  // Entry spring
  const enterSpring = spring({
    frame,
    fps,
    config: {
      damping: 18,
      stiffness: 140,
    },
  });

  const logoScale = interpolate(enterSpring, [0, 1], [0.8, 1]);
  const contentY = interpolate(enterSpring, [0, 1], [40, 0]);
  const opacity = interpolate(enterSpring, [0, 1], [0, 1]);

  const logoSize = isPortrait ? 120 : 150;

  return (
    <div
      style={{
        position: 'absolute',
        inset: 0,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        opacity,
        fontFamily: 'Inter, system-ui, -apple-system, sans-serif',
      }}
    >
      <StudioBackground accentIntensity={0.2} />

      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          textAlign: 'center',
          transform: `translateY(${contentY}px)`,
          zIndex: 10,
          padding: '0 32px',
        }}
      >
        {/* Brand Squircle Logo */}
        <div
          style={{
            width: logoSize,
            height: logoSize,
            transform: `scale(${logoScale})`,
            filter: 'drop-shadow(0 20px 50px rgba(94, 234, 212, 0.35))',
            marginBottom: isPortrait ? 28 : 34,
          }}
        >
          <Img
            src={staticFile('nodeplayer-logo.svg')}
            style={{ width: '100%', height: '100%' }}
          />
        </div>

        <h1
          style={{
            margin: 0,
            fontSize: isPortrait ? 52 : 68,
            fontWeight: 900,
            color: '#FAFAFA',
            letterSpacing: '-0.03em',
            lineHeight: 1.1,
          }}
        >
          Node Player 2.0
        </h1>

        {/* Platforms Badge Row */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            marginTop: 18,
            flexWrap: 'wrap',
            justifyContent: 'center',
          }}
        >
          <div
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 8,
              padding: '8px 18px',
              borderRadius: 12,
              backgroundColor: 'rgba(255, 255, 255, 0.05)',
              border: '1px solid rgba(255, 255, 255, 0.1)',
              color: '#FAFAFA',
              fontSize: 15,
              fontWeight: 600,
            }}
          >
            🤖 Android (APK)
          </div>
          <div
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 8,
              padding: '8px 18px',
              borderRadius: 12,
              backgroundColor: 'rgba(255, 255, 255, 0.05)',
              border: '1px solid rgba(255, 255, 255, 0.1)',
              color: '#FAFAFA',
              fontSize: 15,
              fontWeight: 600,
            }}
          >
            🪟 Windows Desktop (x64)
          </div>
        </div>

        {/* Live Website URL Pill */}
        <div
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 10,
            marginTop: 32,
            padding: '12px 28px',
            borderRadius: 999,
            backgroundColor: 'rgba(94, 234, 212, 0.15)',
            border: '1.5px solid rgba(94, 234, 212, 0.45)',
            boxShadow: '0 0 35px rgba(94, 234, 212, 0.25)',
          }}
        >
          <span
            style={{
              width: 8,
              height: 8,
              borderRadius: '50%',
              backgroundColor: '#5EEAD4',
              boxShadow: '0 0 12px #5EEAD4',
            }}
          />
          <span
            style={{
              color: '#5EEAD4',
              fontSize: isPortrait ? 18 : 22,
              fontWeight: 800,
              letterSpacing: '-0.01em',
            }}
          >
            krighz.github.io/node-player
          </span>
        </div>

        <p
          style={{
            margin: '22px 0 0 0',
            color: '#71717A',
            fontSize: 14,
            fontWeight: 500,
            letterSpacing: '0.04em',
          }}
        >
          100% Free • Open Source • Apache-2.0 • Zero Telemetry
        </p>
      </div>
    </div>
  );
};
