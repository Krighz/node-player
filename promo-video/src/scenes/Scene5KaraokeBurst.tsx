import React from 'react';
import { Img, interpolate, spring, staticFile, useCurrentFrame, useVideoConfig } from 'remotion';
import { AnimatedCursor } from '../components/AnimatedCursor';
import { RealisticPhoneChassis } from '../components/RealisticPhoneChassis';
import { StudioBackground } from '../components/StudioBackground';

interface SceneProps {
  layout: '16x9' | '9x16';
}

export const Scene5KaraokeBurst: React.FC<SceneProps> = ({ layout }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  const isPortrait = layout === '9x16';

  // Entry spring
  const enterSpring = spring({
    frame,
    fps,
    config: { damping: 18, stiffness: 120 },
  });

  const baseScale = interpolate(enterSpring, [0, 1], [0.85, 1.0]);

  // Click share at frame 150
  const isAfterClick = frame >= 150;

  // Burst spring starting at frame 180
  const burstSpring = spring({
    frame: Math.max(0, frame - 180),
    fps,
    config: { damping: 14, stiffness: 160 },
  });

  const burstSpread = interpolate(burstSpring, [0, 1], [0, 1]);
  const burstOpacity = interpolate(burstSpring, [0, 0.2, 1], [0, 0.95, 1]);

  // Exit transition glide towards frame 408
  const exitProgress = interpolate(frame, [370, 408], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const exitScale = interpolate(exitProgress, [0, 1], [1, 1.35]);
  const exitOpacity = interpolate(exitProgress, [0, 1], [1, 0]);

  // Phone dimensions strictly conforming to safe zone:
  // Width 320px -> height 704px (strictly <= 750px)
  const phoneWidth = isPortrait ? 290 : 320;

  // Cursor coordinates to click "Share Lyric Card" button
  const cursorTargetX = isPortrait ? 680 : 1080;
  const cursorTargetY = isPortrait ? 520 : 380;

  // 4 Authentic Clean Song Lyric Cards (Midnight City, Starlight, After Hours, Neverender)
  const lyricCards = [
    {
      id: 'tl',
      title: 'Midnight City',
      artist: 'M83',
      lyric: 'The city is my church\nIt wraps me in the blinding twilight\nWaiting in a car...',
      gradient: 'linear-gradient(135deg, #1E1B4B 0%, #0F172A 100%)',
      accent: '#818CF8',
      angle: -8,
      targetX: isPortrait ? -240 : -460,
      targetY: isPortrait ? -340 : -200,
    },
    {
      id: 'tr',
      title: 'Starlight',
      artist: 'Muse',
      lyric: 'Hold you in my arms\nI just wanted to hold you in my arms\nMy starlight...',
      gradient: 'linear-gradient(135deg, #312E81 0%, #18181B 100%)',
      accent: '#A78BFA',
      angle: 9,
      targetX: isPortrait ? 240 : 460,
      targetY: isPortrait ? -340 : -200,
    },
    {
      id: 'bl',
      title: 'After Hours',
      artist: 'The Weeknd',
      lyric: 'My darkest hours\nGirl, I felt so alive with you\nI’m falling in too deep...',
      gradient: 'linear-gradient(135deg, #450A0A 0%, #09090B 100%)',
      accent: '#F87171',
      angle: 7,
      targetX: isPortrait ? -240 : -460,
      targetY: isPortrait ? 340 : 200,
    },
    {
      id: 'br',
      title: 'Neverender',
      artist: 'Justice, Tame Impala',
      lyric: 'All in all, I need someone\nTo lean on\nAll that’s left is what we are...',
      gradient: 'linear-gradient(135deg, #134E4A 0%, #09090B 100%)',
      accent: '#5EEAD4',
      angle: -7,
      targetX: isPortrait ? 240 : 460,
      targetY: isPortrait ? 340 : 200,
    },
  ];

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
            120 FPS Word-by-Word Karaoke
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
          Sub-millisecond synced lyrics with instant 9:16 social Lyric Card export
        </div>
      </div>

      {/* Center Stage Zone (Strictly top >= 215px, height <= 750px) */}
      <div
        style={{
          position: 'absolute',
          top: 220,
          bottom: 50,
          left: 0,
          right: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 20,
        }}
      >
        <RealisticPhoneChassis
          width={phoneWidth}
          videoSrc="captures/live_02_karaoke_sweep.mp4"
          rotateX={interpolate(frame, [0, 200, 408], [6, 3, 6])}
          rotateY={interpolate(frame, [0, 200, 408], [-8, 0, 8])}
          rotateZ={0}
          scale={baseScale}
        />
      </div>

      {/* 4 Clean Authentic Lyric Cards Bursting to the 4 Corners at frame 180 */}
      {frame >= 180 &&
        lyricCards.map((card) => {
          const cardX = card.targetX * burstSpread;
          const cardY = card.targetY * burstSpread;
          const cardRot = card.angle * burstSpread;

          return (
            <div
              key={card.id}
              style={{
                position: 'absolute',
                width: isPortrait ? 210 : 250,
                height: isPortrait ? 340 : 400,
                borderRadius: 22,
                background: card.gradient,
                border: '1px solid rgba(255, 255, 255, 0.16)',
                boxShadow: '0 24px 60px rgba(0, 0, 0, 0.75)',
                padding: '20px 18px',
                boxSizing: 'border-box',
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'space-between',
                zIndex: 35,
                transform: `translate3d(${cardX}px, ${cardY}px, 0) rotate(${cardRot}deg) scale(${burstSpread})`,
                opacity: burstOpacity,
                pointerEvents: 'none',
              }}
            >
              {/* Card Header with Node Player logo badge */}
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <Img
                    src={staticFile('nodeplayer-logo.svg')}
                    style={{ width: 20, height: 20, borderRadius: 5 }}
                  />
                  <span style={{ fontSize: 11, fontWeight: 700, color: '#A1A1AA', letterSpacing: '0.05em' }}>
                    NODE PLAYER
                  </span>
                </div>
                <span
                  style={{
                    fontSize: 10,
                    fontWeight: 800,
                    color: card.accent,
                    backgroundColor: 'rgba(255, 255, 255, 0.08)',
                    padding: '2px 6px',
                    borderRadius: 4,
                  }}
                >
                  9:16 CARD
                </span>
              </div>

              {/* Clean Authentic Lyric Lines */}
              <div style={{ margin: 'auto 0' }}>
                <div
                  style={{
                    color: '#FAFAFA',
                    fontSize: isPortrait ? 15 : 17,
                    fontWeight: 800,
                    lineHeight: 1.45,
                    whiteSpace: 'pre-line',
                  }}
                >
                  {card.lyric}
                </div>
              </div>

              {/* Card Footer with Track Metadata */}
              <div
                style={{
                  borderTop: '1px solid rgba(255, 255, 255, 0.1)',
                  paddingTop: 10,
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                }}
              >
                <div>
                  <div style={{ color: '#FAFAFA', fontWeight: 700, fontSize: 12 }}>
                    {card.title}
                  </div>
                  <div style={{ color: '#A1A1AA', fontWeight: 500, fontSize: 11 }}>
                    {card.artist}
                  </div>
                </div>
                <div
                  style={{
                    width: 6,
                    height: 6,
                    borderRadius: '50%',
                    backgroundColor: card.accent,
                  }}
                />
              </div>
            </div>
          );
        })}

      {/* Animated Cursor clicking Share Lyric Card at frame 150 */}
      <AnimatedCursor
        startX={cursorTargetX + 200}
        startY={cursorTargetY + 160}
        targetX={cursorTargetX}
        targetY={cursorTargetY}
        moveStartFrame={110}
        clickFrame={150}
      />
    </div>
  );
};
