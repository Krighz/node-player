import React from 'react';
import { interpolate, spring, useCurrentFrame, useVideoConfig } from 'remotion';
import { AnimatedCursor } from '../components/AnimatedCursor';
import { RealisticPhoneChassis } from '../components/RealisticPhoneChassis';
import { StudioBackground } from '../components/StudioBackground';

interface SceneProps {
  layout: '16x9' | '9x16';
}

export const Scene4SectionDrop: React.FC<SceneProps> = ({ layout }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  const isPortrait = layout === '9x16';

  // Entry spring
  const enterSpring = spring({
    frame,
    fps,
    config: { damping: 18, stiffness: 120 },
  });

  const phoneScale = interpolate(enterSpring, [0, 1], [0.85, 1.0]);

  // Subtle 3D angle shifts
  const tiltX = interpolate(frame, [0, 90, 240, 420], [10, 6, 4, 8]);
  const tiltY = interpolate(frame, [0, 90, 240, 420], [-14, -8, -4, -12]);

  // Drop click reaction at frame 90
  const isAfterClick = frame >= 90;
  const clickReactionSpring = spring({
    frame: Math.max(0, frame - 90),
    fps,
    config: { damping: 12, stiffness: 260 },
  });

  const jumpShockScale = isAfterClick
    ? interpolate(clickReactionSpring, [0, 1], [0.97, 1.0])
    : 1.0;

  // Exit transition glide towards frame 420
  const exitProgress = interpolate(frame, [385, 420], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const exitScale = interpolate(exitProgress, [0, 1], [1, 1.3]);
  const exitOpacity = interpolate(exitProgress, [0, 1], [1, 0]);

  // Strict safe zone phone dimensions:
  // Width 330px -> height 726px (strictly <= 750px)
  const phoneWidth = isPortrait ? 290 : 330;

  // Target coordinates for clicking the "Drop" section marker
  const cursorTargetX = isPortrait ? 570 : 1010;
  const cursorTargetY = isPortrait ? 1040 : 620;

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
            Smart Section Markers
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
          Instantly jump to Drops, Choruses, and Solos with zero playback latency
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
          justifyContent: isPortrait ? 'center' : 'flex-start',
          paddingLeft: isPortrait ? 0 : 260,
          zIndex: 20,
        }}
      >
        <div
          style={{
            transform: `scale(${jumpShockScale})`,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <RealisticPhoneChassis
            width={phoneWidth}
            videoSrc="captures/live_03_section_drop.mp4"
            rotateX={tiltX}
            rotateY={tiltY}
            rotateZ={-2}
            scale={phoneScale}
          />
        </div>
      </div>

      {/* Interactive Section Markers Floating Glass HUD Card (Right side in 16x9) */}
      <div
        style={{
          position: 'absolute',
          right: isPortrait ? 'auto' : 120,
          bottom: isPortrait ? 90 : 'auto',
          top: isPortrait ? 'auto' : '52%',
          transform: isPortrait ? 'none' : 'translateY(-50%)',
          width: isPortrait ? '88%' : 390,
          padding: '24px 28px',
          borderRadius: 24,
          backgroundColor: '#121215',
          border: '1px solid rgba(255, 255, 255, 0.14)',
          boxShadow: '0 24px 60px rgba(0, 0, 0, 0.65)',
          backdropFilter: 'blur(24px)',
          zIndex: 50,
          display: 'flex',
          flexDirection: 'column',
          gap: 16,
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div style={{ color: '#FAFAFA', fontWeight: 800, fontSize: 16 }}>
            Track Structure
          </div>
          <div
            style={{
              fontSize: 12,
              fontWeight: 700,
              padding: '3px 8px',
              borderRadius: 6,
              background: 'rgba(255, 255, 255, 0.06)',
              color: '#5EEAD4',
              border: '1px solid rgba(255, 255, 255, 0.1)',
            }}
          >
            Auto-Detected
          </div>
        </div>

        {/* Section Pills */}
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {[
            { label: 'Intro', active: false },
            { label: 'Build', active: false },
            { label: '★ DROP', active: isAfterClick, isDrop: true },
            { label: 'Verse', active: false },
            { label: 'Chorus', active: false },
          ].map((sec) => (
            <div
              key={sec.label}
              style={{
                padding: '8px 14px',
                borderRadius: 12,
                fontSize: 13,
                fontWeight: 800,
                backgroundColor: sec.active
                  ? '#5EEAD4'
                  : sec.isDrop
                  ? '#1C1C21'
                  : 'rgba(255, 255, 255, 0.05)',
                color: sec.active ? '#09090B' : sec.isDrop ? '#5EEAD4' : '#A1A1AA',
                border: sec.active
                  ? '1px solid #5EEAD4'
                  : sec.isDrop
                  ? '1px solid rgba(94, 234, 212, 0.4)'
                  : '1px solid rgba(255, 255, 255, 0.08)',
                transition: 'all 0.15s ease',
              }}
            >
              {sec.label}
            </div>
          ))}
        </div>

        {/* Status ticker */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            color: isAfterClick ? '#5EEAD4' : '#71717A',
            fontSize: 13,
            fontWeight: 600,
          }}
        >
          <div
            style={{
              width: 6,
              height: 6,
              borderRadius: '50%',
              backgroundColor: isAfterClick ? '#5EEAD4' : '#71717A',
            }}
          />
          {isAfterClick
            ? 'Jumped to Drop (0.0s Playback Stutter)'
            : 'Seeking optimal transition point...'}
        </div>
      </div>

      {/* Animated Cursor clicking the Drop marker at frame 90 */}
      <AnimatedCursor
        startX={cursorTargetX + 220}
        startY={cursorTargetY + 140}
        targetX={cursorTargetX}
        targetY={cursorTargetY}
        moveStartFrame={45}
        clickFrame={90}
      />
    </div>
  );
};
