import React from 'react';
import { interpolate, spring, useCurrentFrame, useVideoConfig } from 'remotion';
import { RealisticPhoneChassis } from '../components/RealisticPhoneChassis';
import { StudioBackground } from '../components/StudioBackground';

interface SceneProps {
  layout: '16x9' | '9x16';
}

export const Scene6ArtistDspParallax: React.FC<SceneProps> = ({ layout }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  const isPortrait = layout === '9x16';

  // Entry spring
  const enterSpring = spring({
    frame,
    fps,
    config: { damping: 18, stiffness: 120 },
  });

  const entryProgress = interpolate(enterSpring, [0, 1], [0.85, 1]);

  // Parallax subtle drift across 240 frames
  const driftProgress = interpolate(frame, [0, 240], [-20, 20]);

  // Exit transition glide towards frame 240
  const exitProgress = interpolate(frame, [210, 240], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const exitScale = interpolate(exitProgress, [0, 1], [1, 1.3]);
  const exitOpacity = interpolate(exitProgress, [0, 1], [1, 0]);

  const phoneWidth = isPortrait ? 250 : 320;

  return (
    <div
      style={{
        position: 'absolute',
        inset: 0,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        overflow: 'hidden',
        fontFamily: 'Inter, system-ui, -apple-system, sans-serif',
        opacity: exitOpacity,
        transform: `scale(${exitScale})`,
      }}
    >
      <StudioBackground accentIntensity={0.12} />

      {/* Top Title Safe Zone (Strictly y: 54px to 175px) */}
      <div
        style={{
          position: 'absolute',
          top: 54,
          left: 0,
          right: 0,
          height: 110,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 40,
          pointerEvents: 'none',
        }}
      >
        <div
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 10,
            padding: '7px 20px',
            borderRadius: 999,
            background: 'rgba(18, 18, 21, 0.85)',
            border: '1px solid rgba(255, 255, 255, 0.14)',
            boxShadow: '0 8px 24px rgba(0, 0, 0, 0.6)',
            backdropFilter: 'blur(16px)',
          }}
        >
          <span
            style={{
              width: 7,
              height: 7,
              borderRadius: '50%',
              backgroundColor: '#5EEAD4',
            }}
          />
          <span
            style={{
              color: '#FAFAFA',
              fontSize: isPortrait ? 13 : 15,
              fontWeight: 800,
              letterSpacing: '0.04em',
              textTransform: 'uppercase',
            }}
          >
            Artist Hubs & Audiophile DSP
          </span>
        </div>

        <div
          style={{
            color: '#A1A1AA',
            fontSize: isPortrait ? 12 : 14,
            fontWeight: 500,
            marginTop: 8,
            textAlign: 'center',
          }}
        >
          380dp dynamic hero discography + 32-bit float 10-band parametric EQ
        </div>
      </div>

      {/* Center Stage Zone (Strictly top >= 215px) — Two Overlapping 3D Phones in Parallax */}
      <div
        style={{
          position: 'absolute',
          top: 220,
          bottom: 70,
          left: 0,
          right: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          perspective: 1600,
        }}
      >
        {/* Left Phone: Artist Page Hub */}
        <div
          style={{
            position: 'absolute',
            zIndex: 15,
          }}
        >
          <RealisticPhoneChassis
            width={phoneWidth}
            videoSrc="captures/live_04_artist_scroll.mp4"
            rotateX={8}
            rotateY={16}
            rotateZ={-2}
            scale={entryProgress * 0.95}
            translateX={isPortrait ? -120 + driftProgress : -340 + driftProgress * 1.4}
            translateY={isPortrait ? -60 : -10}
          />
        </div>

        {/* Right Phone: 10-Band Parametric EQ */}
        <div
          style={{
            position: 'absolute',
            zIndex: 20,
          }}
        >
          <RealisticPhoneChassis
            width={phoneWidth}
            videoSrc="captures/live_05_dsp_eq.mp4"
            rotateX={6}
            rotateY={-14}
            rotateZ={2}
            scale={entryProgress * 1.0}
            translateX={isPortrait ? 120 - driftProgress : 340 - driftProgress * 1.4}
            translateY={isPortrait ? 80 : 20}
          />
        </div>
      </div>

      {/* Floating Glass Chips Around the Parallax Scene */}
      <div
        style={{
          position: 'absolute',
          bottom: isPortrait ? 40 : 50,
          display: 'flex',
          gap: 12,
          flexWrap: 'wrap',
          justifyContent: 'center',
          padding: '0 20px',
          zIndex: 45,
        }}
      >
        {[
          { label: '4,000+ AutoEQ Presets', icon: '🎧' },
          { label: 'EBU R128 Loudness', icon: '📊' },
          { label: 'True Equal-Power Crossfade', icon: '⚡' },
        ].map((chip) => (
          <div
            key={chip.label}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              padding: isPortrait ? '6px 14px' : '8px 18px',
              borderRadius: 999,
              background: '#121215',
              border: '1px solid rgba(255, 255, 255, 0.12)',
              boxShadow: '0 10px 24px rgba(0, 0, 0, 0.6)',
              backdropFilter: 'blur(16px)',
              color: '#FAFAFA',
              fontSize: isPortrait ? 11 : 13,
              fontWeight: 700,
            }}
          >
            <span>{chip.icon}</span>
            <span>{chip.label}</span>
          </div>
        ))}
      </div>
    </div>
  );
};
