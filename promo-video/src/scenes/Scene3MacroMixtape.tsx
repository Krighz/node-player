import React from 'react';
import { Img, interpolate, spring, staticFile, useCurrentFrame, useVideoConfig } from 'remotion';
import { StudioBackground } from '../components/StudioBackground';

interface SceneProps {
  layout: '16x9' | '9x16';
}

export const Scene3MacroMixtape: React.FC<SceneProps> = ({ layout }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  const isPortrait = layout === '9x16';

  // Card 1 entry spring at frame 0
  const card1Spring = spring({
    frame,
    fps,
    config: { damping: 18, stiffness: 160 },
  });
  const card1Scale = interpolate(card1Spring, [0, 1], [0.88, 1]);
  const card1Opacity = interpolate(card1Spring, [0, 1], [0, 1]);

  // Card 2 entry spring staggered by 11 frames (~180ms)
  const card2Spring = spring({
    frame: Math.max(0, frame - 11),
    fps,
    config: { damping: 18, stiffness: 160 },
  });
  const card2Scale = interpolate(card2Spring, [0, 1], [0.88, 1]);
  const card2Opacity = interpolate(card2Spring, [0, 1], [0, 1]);

  // Rapid progress counter: 0 to 40 across frames 15 to 110
  const countProgress = interpolate(frame, [15, 110], [0, 40], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const currentCount = Math.floor(countProgress);
  const fillPercent = Math.min(100, (currentCount / 40) * 100);

  // Status snap when reaching 40/40 at frame 110
  const isComplete = frame >= 110;
  const snapSpring = spring({
    frame: Math.max(0, frame - 110),
    fps,
    config: { damping: 14, stiffness: 220 },
  });
  const snapPop = isComplete ? interpolate(snapSpring, [0, 1], [0.95, 1.0]) : 1.0;

  // Exit transition glide towards frame 192 (frames 168 to 192)
  const exitProgress = interpolate(frame, [168, 192], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const exitScale = interpolate(exitProgress, [0, 1], [1, 1.25]);
  const exitOpacity = interpolate(exitProgress, [0, 1], [1, 0]);

  const cardWidth = isPortrait ? 440 : 480;

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
        opacity: exitOpacity,
        transform: `scale(${exitScale})`,
      }}
    >
      <StudioBackground accentIntensity={0.12} />

      {/* Top Title Safe Zone (y: 54px to 175px) */}
      <div
        style={{
          position: 'absolute',
          top: 60,
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
            Smart Offline Mixtape
          </span>
        </div>

        <div
          style={{
            color: '#A1A1AA',
            fontSize: isPortrait ? 13 : 15,
            fontWeight: 500,
            marginTop: 8,
            textAlign: 'center',
          }}
        >
          Intelligent Wi-Fi background caching for zero-data local playback
        </div>
      </div>

      {/* Center Stage Zone (Strictly y >= 205px) — Two Side-by-Side Carbon/Zinc Cards */}
      <div
        style={{
          position: 'absolute',
          top: 220,
          left: 0,
          right: 0,
          bottom: 60,
          display: 'flex',
          flexDirection: isPortrait ? 'column' : 'row',
          alignItems: 'center',
          justifyContent: 'center',
          gap: isPortrait ? 24 : 36,
          zIndex: 30,
        }}
      >
        {/* CARD 1 (Left): Smart Offline Mixtape */}
        <div
          style={{
            width: cardWidth,
            padding: '28px 32px',
            borderRadius: 24,
            backgroundColor: '#121215',
            border: '1px solid rgba(255, 255, 255, 0.14)',
            boxShadow: '0 24px 60px rgba(0, 0, 0, 0.65)',
            display: 'flex',
            flexDirection: 'column',
            gap: 18,
            transform: `scale(${card1Scale * snapPop})`,
            opacity: card1Opacity,
            boxSizing: 'border-box',
          }}
        >
          {/* Card 1 Header with Node Player squircle 'N' logo mark */}
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <Img
                src={staticFile('nodeplayer-logo.svg')}
                style={{
                  width: 32,
                  height: 32,
                  borderRadius: 7,
                  boxShadow: '0 4px 12px rgba(0,0,0,0.5)',
                }}
              />
              <div>
                <div style={{ color: '#FAFAFA', fontWeight: 800, fontSize: 17 }}>
                  Smart Mixtape
                </div>
                <div style={{ color: '#71717A', fontWeight: 500, fontSize: 12 }}>
                  Automated Cache Manager
                </div>
              </div>
            </div>

            <div
              style={{
                fontSize: 11,
                fontWeight: 700,
                padding: '4px 10px',
                borderRadius: 999,
                background: 'rgba(255, 255, 255, 0.06)',
                color: '#A1A1AA',
                border: '1px solid rgba(255, 255, 255, 0.1)',
              }}
            >
              60% Discovery • 40% Heavy Rotation
            </div>
          </div>

          {/* Counter Display & Progress Bar */}
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 10 }}>
              <div style={{ color: '#FAFAFA', fontSize: 32, fontWeight: 900, letterSpacing: '-0.03em' }}>
                {currentCount} <span style={{ color: '#71717A', fontSize: 20, fontWeight: 600 }}>/ 40</span>
              </div>
              <div style={{ color: '#5EEAD4', fontSize: 14, fontWeight: 700 }}>
                {fillPercent.toFixed(0)}% Cached
              </div>
            </div>

            {/* Crisp #5EEAD4 Progress Bar (Zero Neon Halos) */}
            <div
              style={{
                width: '100%',
                height: 8,
                borderRadius: 999,
                backgroundColor: 'rgba(255, 255, 255, 0.08)',
                overflow: 'hidden',
              }}
            >
              <div
                style={{
                  width: `${fillPercent}%`,
                  height: '100%',
                  borderRadius: 999,
                  backgroundColor: '#5EEAD4',
                  transition: 'width 0.05s linear',
                }}
              />
            </div>
          </div>

          {/* Cache Metadata Tags */}
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <span
              style={{
                fontSize: 12,
                fontWeight: 600,
                padding: '4px 10px',
                borderRadius: 8,
                backgroundColor: '#1C1C21',
                color: '#D4D4D8',
                border: '1px solid rgba(255, 255, 255, 0.08)',
              }}
            >
              32-Bit Lossless FLAC
            </span>
            <span
              style={{
                fontSize: 12,
                fontWeight: 600,
                padding: '4px 10px',
                borderRadius: 8,
                backgroundColor: '#1C1C21',
                color: '#D4D4D8',
                border: '1px solid rgba(255, 255, 255, 0.08)',
              }}
            >
              Wi-Fi Background Sync
            </span>
          </div>
        </div>

        {/* CARD 2 (Right): Zero-Data Offline Engine */}
        <div
          style={{
            width: cardWidth,
            padding: '28px 32px',
            borderRadius: 24,
            backgroundColor: '#121215',
            border: '1px solid rgba(255, 255, 255, 0.14)',
            boxShadow: '0 24px 60px rgba(0, 0, 0, 0.65)',
            display: 'flex',
            flexDirection: 'column',
            gap: 18,
            transform: `scale(${card2Scale})`,
            opacity: card2Opacity,
            boxSizing: 'border-box',
          }}
        >
          {/* Card 2 Header */}
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <div style={{ color: '#FAFAFA', fontWeight: 800, fontSize: 17 }}>
                Zero-Data Offline Engine
              </div>
              <div style={{ color: '#71717A', fontWeight: 500, fontSize: 12 }}>
                Two-Tier LRU Cache • Instant Interception
              </div>
            </div>

            {/* Status Pill */}
            <div
              style={{
                fontSize: 12,
                fontWeight: 800,
                padding: '5px 12px',
                borderRadius: 999,
                backgroundColor: isComplete ? 'rgba(94, 234, 212, 0.15)' : 'rgba(255, 255, 255, 0.06)',
                color: isComplete ? '#5EEAD4' : '#A1A1AA',
                border: isComplete ? '1px solid rgba(94, 234, 212, 0.4)' : '1px solid rgba(255, 255, 255, 0.1)',
                transition: 'all 0.15s ease',
              }}
            >
              {isComplete ? '✓ Ready Offline' : '• Caching Stream'}
            </div>
          </div>

          {/* Engine Specs */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                padding: '10px 14px',
                borderRadius: 12,
                backgroundColor: '#1C1C21',
                border: '1px solid rgba(255, 255, 255, 0.06)',
              }}
            >
              <span style={{ color: '#A1A1AA', fontSize: 13, fontWeight: 500 }}>
                Memory Ring Buffer
              </span>
              <span style={{ color: '#FAFAFA', fontSize: 13, fontWeight: 700 }}>
                64 MB Pre-Roll
              </span>
            </div>

            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                padding: '10px 14px',
                borderRadius: 12,
                backgroundColor: '#1C1C21',
                border: '1px solid rgba(255, 255, 255, 0.06)',
              }}
            >
              <span style={{ color: '#A1A1AA', fontSize: 13, fontWeight: 500 }}>
                Encrypted Storage
              </span>
              <span style={{ color: '#FAFAFA', fontSize: 13, fontWeight: 700 }}>
                Direct Block I/O
              </span>
            </div>
          </div>

          {/* Subtitle notice */}
          <div style={{ color: '#71717A', fontSize: 12, fontWeight: 500 }}>
            Full offline playback without ever querying cellular data
          </div>
        </div>
      </div>
    </div>
  );
};
