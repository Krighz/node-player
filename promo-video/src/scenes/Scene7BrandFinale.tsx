import React from 'react';
import { Img, interpolate, spring, staticFile, useCurrentFrame, useVideoConfig } from 'remotion';
import { StudioBackground } from '../components/StudioBackground';

interface SceneProps {
  layout: '16x9' | '9x16';
}

export const Scene7BrandFinale: React.FC<SceneProps> = ({ layout }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  const isPortrait = layout === '9x16';

  // Entry energy convergence spring
  const enterSpring = spring({
    frame,
    fps,
    config: { damping: 18, stiffness: 140 },
  });

  // Logo impact lockup at relative frame 30
  const isLogoLocked = frame >= 30;
  const lockupSpring = spring({
    frame: Math.max(0, frame - 30),
    fps,
    config: { damping: 14, stiffness: 220 },
  });

  const logoScale = isLogoLocked
    ? interpolate(lockupSpring, [0, 1], [0.65, 1.0])
    : interpolate(frame, [0, 30], [0.3, 0.65]);

  const logoGlow = isLogoLocked
    ? interpolate(lockupSpring, [0, 1], [0, 1])
    : 0;

  // Title reveal spring starting at frame 50
  const titleSpring = spring({
    frame: Math.max(0, frame - 50),
    fps,
    config: { damping: 16, stiffness: 180 },
  });

  const titleY = interpolate(titleSpring, [0, 1], [30, 0]);
  const titleOpacity = interpolate(titleSpring, [0, 1], [0, 1]);

  // Badges reveal spring starting at frame 70
  const badgeSpring = spring({
    frame: Math.max(0, frame - 70),
    fps,
    config: { damping: 18, stiffness: 160 },
  });

  const badgeY = interpolate(badgeSpring, [0, 1], [25, 0]);
  const badgeOpacity = interpolate(badgeSpring, [0, 1], [0, 1]);

  // Master fade out in final 40 frames (140 to 180)
  const masterFade = interpolate(frame, [140, 180], [1, 0], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });

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
        overflow: 'hidden',
        fontFamily: 'Inter, system-ui, -apple-system, sans-serif',
        opacity: masterFade,
      }}
    >
      <StudioBackground />

      {/* Radiating Soundwave Rings converging/expanding */}
      <div
        style={{
          position: 'absolute',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          pointerEvents: 'none',
        }}
      >
        {[1, 2, 3].map((ringIndex) => {
          const ringScale = interpolate(
            frame,
            [0, 30, 180],
            [ringIndex * 0.4, ringIndex * 0.8, ringIndex * 1.6]
          );
          const ringOpacity = isLogoLocked
            ? interpolate(frame, [30, 90, 180], [0.6, 0.2, 0.05])
            : 0.15;

          return (
            <div
              key={ringIndex}
              style={{
                position: 'absolute',
                width: 320,
                height: 320,
                borderRadius: '50%',
                border: `1.5px solid rgba(94, 234, 212, ${ringOpacity})`,
                transform: `scale(${ringScale})`,
                boxShadow: `0 0 30px rgba(94, 234, 212, ${ringOpacity * 0.5})`,
              }}
            />
          );
        })}
      </div>

      {/* Main Brand Container */}
      <div
        style={{
          position: 'relative',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          zIndex: 30,
        }}
      >
        {/* Node Player Squircle Logo with High-Specular Halo */}
        <div
          style={{
            position: 'relative',
            width: logoSize,
            height: logoSize,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            transform: `scale(${logoScale})`,
            marginBottom: isPortrait ? 28 : 36,
          }}
        >
          {/* Cyan Glow Halo Behind Logo */}
          <div
            style={{
              position: 'absolute',
              inset: -30,
              background: 'radial-gradient(circle, rgba(94, 234, 212, 0.45) 0%, rgba(94, 234, 212, 0) 70%)',
              filter: 'blur(25px)',
              opacity: logoGlow,
            }}
          />

          <Img
            src={staticFile('nodeplayer-logo.svg')}
            style={{
              width: '100%',
              height: '100%',
              borderRadius: Math.round(logoSize * 0.22),
              boxShadow: `
                0 0 0 1.5px rgba(255, 255, 255, 0.18),
                0 25px 60px rgba(0, 0, 0, 0.9),
                0 0 40px rgba(94, 234, 212, ${0.4 * logoGlow})
              `,
            }}
          />
        </div>

        {/* Hero Title & Subtitle */}
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            transform: `translateY(${titleY}px)`,
            opacity: titleOpacity,
            textAlign: 'center',
            padding: '0 24px',
          }}
        >
          <div
            style={{
              color: '#FAFAFA',
              fontSize: isPortrait ? 40 : 54,
              fontWeight: 900,
              letterSpacing: '-0.03em',
              textShadow: '0 10px 40px rgba(0, 0, 0, 0.8)',
            }}
          >
            Node Player <span style={{ color: '#5EEAD4' }}>2.0</span>
          </div>

          <div
            style={{
              color: '#A1A1AA',
              fontSize: isPortrait ? 16 : 20,
              fontWeight: 500,
              marginTop: 10,
              maxWidth: 680,
            }}
          >
            The Audiophile Music Player for Android & Windows
          </div>
        </div>

        {/* Platform Pills & Website Callout */}
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: 16,
            marginTop: isPortrait ? 32 : 40,
            transform: `translateY(${badgeY}px)`,
            opacity: badgeOpacity,
          }}
        >
          {/* Android and Windows Platform Pills */}
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', justifyContent: 'center' }}>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 10,
                padding: '10px 22px',
                borderRadius: 999,
                background: 'rgba(24, 24, 27, 0.85)',
                border: '1px solid rgba(255, 255, 255, 0.14)',
                boxShadow: '0 15px 35px rgba(0, 0, 0, 0.6)',
                backdropFilter: 'blur(16px)',
                color: '#FAFAFA',
                fontSize: isPortrait ? 13 : 15,
                fontWeight: 700,
              }}
            >
              <span>🤖</span>
              <span>Android (APK / Releases)</span>
            </div>

            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 10,
                padding: '10px 22px',
                borderRadius: 999,
                background: 'rgba(24, 24, 27, 0.85)',
                border: '1px solid rgba(255, 255, 255, 0.14)',
                boxShadow: '0 15px 35px rgba(0, 0, 0, 0.6)',
                backdropFilter: 'blur(16px)',
                color: '#FAFAFA',
                fontSize: isPortrait ? 13 : 15,
                fontWeight: 700,
              }}
            >
              <span>🪟</span>
              <span>Windows (MSI / EXE)</span>
            </div>
          </div>

          {/* Website Link Pill with Pulsing Teal Status */}
          <div
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 10,
              padding: '10px 24px',
              borderRadius: 999,
              background: 'rgba(94, 234, 212, 0.1)',
              border: '1px solid rgba(94, 234, 212, 0.45)',
              boxShadow: '0 0 25px rgba(94, 234, 212, 0.25)',
              backdropFilter: 'blur(16px)',
              marginTop: 6,
            }}
          >
            <div
              style={{
                width: 8,
                height: 8,
                borderRadius: '50%',
                backgroundColor: '#5EEAD4',
                boxShadow: '0 0 10px #5EEAD4',
              }}
            />
            <span
              style={{
                color: '#FAFAFA',
                fontSize: isPortrait ? 14 : 16,
                fontWeight: 800,
                letterSpacing: '0.02em',
              }}
            >
              krighz.github.io/node-player
            </span>
          </div>
        </div>
      </div>
    </div>
  );
};
