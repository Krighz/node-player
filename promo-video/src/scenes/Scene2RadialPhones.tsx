import React from 'react';
import { interpolate, spring, useCurrentFrame, useVideoConfig } from 'remotion';
import { RealisticPhoneChassis } from '../components/RealisticPhoneChassis';
import { StudioBackground } from '../components/StudioBackground';

interface SceneProps {
  layout: '16x9' | '9x16';
}

export const Scene2RadialPhones: React.FC<SceneProps> = ({ layout }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  const isPortrait = layout === '9x16';

  // Entry spring expansion
  const enterSpring = spring({
    frame,
    fps,
    config: { damping: 18, stiffness: 140 },
  });

  const radialSpread = interpolate(enterSpring, [0, 1], [0.5, 1]);

  // Center typography morphs quantized to 124 BPM beats
  // 0 - 116 frames (beats 0-4): YT Music.
  // 116 - 232 frames (beats 4-8): 32-Bit FLAC.
  // 232 - 360 frames (beats 8-12): One Fluid Engine.
  let centerText = 'YT Music.';
  let textIndex = 0;
  if (frame >= 116 && frame < 232) {
    centerText = '32-Bit FLAC.';
    textIndex = 1;
  } else if (frame >= 232) {
    centerText = 'One Fluid Engine.';
    textIndex = 2;
  }

  const textCycleFrame = frame < 232 ? (frame < 116 ? frame : frame - 116) : frame - 232;
  const textSpring = spring({
    frame: textCycleFrame,
    fps,
    config: { damping: 16, stiffness: 220 },
  });
  const textScale = interpolate(textSpring, [0, 1], [0.92, 1.0]);
  const textOpacity = interpolate(textSpring, [0, 0.3, 1], [0, 0.9, 1]);
  const clipBottom = interpolate(textSpring, [0, 1], [100, 0]);

  // Exit color-wave wipe at frames 320 to 360
  const wipeProgress = interpolate(frame, [320, 360], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const exitOpacity = interpolate(wipeProgress, [0, 1], [1, 0]);

  // Strict safe zone dimensions:
  // In 16x9: 200px width => 440px height.
  // Top phone at top: 60px (ends at 500px).
  // Bottom phone at top: 580px (ends at 1020px).
  // Gutter between top and bottom phone = 80px (Strictly >= 36px, zero overlap!).
  const phoneW = isPortrait ? 160 : 200;
  const topPhoneY = isPortrait ? 140 : 60;
  const btmPhoneY = isPortrait ? 1120 : 580;
  const leftX = isPortrait ? 40 : 100;
  const rightX = isPortrait ? 40 : 100;

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
      }}
    >
      <StudioBackground accentIntensity={0.12} />

      {/* 4 Radial Angled 3D Phones with Strict Safe-Zone Separations (Zero Overlap) */}
      {/* 1. Top-Left Phone: live_01_home_switch.mp4 */}
      <div
        style={{
          position: 'absolute',
          left: leftX,
          top: topPhoneY,
          transform: `scale(${radialSpread})`,
          zIndex: 5,
        }}
      >
        <RealisticPhoneChassis
          width={phoneW}
          videoSrc="captures/live_01_home_switch.mp4"
          rotateY={16}
          rotateX={10}
          rotateZ={-6}
          scale={0.92}
        />
      </div>

      {/* 2. Bottom-Left Phone: live_04_artist_scroll.mp4 */}
      <div
        style={{
          position: 'absolute',
          left: leftX,
          top: btmPhoneY,
          transform: `scale(${radialSpread})`,
          zIndex: 5,
        }}
      >
        <RealisticPhoneChassis
          width={phoneW}
          videoSrc="captures/live_04_artist_scroll.mp4"
          rotateY={14}
          rotateX={-8}
          rotateZ={5}
          scale={0.92}
        />
      </div>

      {/* 3. Top-Right Phone: live_05_dsp_eq.mp4 */}
      <div
        style={{
          position: 'absolute',
          right: rightX,
          top: topPhoneY,
          transform: `scale(${radialSpread})`,
          zIndex: 5,
        }}
      >
        <RealisticPhoneChassis
          width={phoneW}
          videoSrc="captures/live_05_dsp_eq.mp4"
          rotateY={-16}
          rotateX={10}
          rotateZ={6}
          scale={0.92}
        />
      </div>

      {/* 4. Bottom-Right Phone: live_03_section_drop.mp4 */}
      <div
        style={{
          position: 'absolute',
          right: rightX,
          top: btmPhoneY,
          transform: `scale(${radialSpread})`,
          zIndex: 5,
        }}
      >
        <RealisticPhoneChassis
          width={phoneW}
          videoSrc="captures/live_03_section_drop.mp4"
          rotateY={-14}
          rotateX={-8}
          rotateZ={-5}
          scale={0.92}
        />
      </div>

      {/* Central Kinetic Typography (Zero Blur Mask-Clip Reveal) */}
      <div
        style={{
          position: 'relative',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 20,
          textAlign: 'center',
          pointerEvents: 'none',
        }}
      >
        <div
          style={{
            transform: `scale(${textScale})`,
            opacity: textOpacity,
            clipPath: `inset(0 0 ${clipBottom}% 0)`,
          }}
        >
          <div
            style={{
              fontSize: isPortrait ? 44 : 76,
              fontWeight: 900,
              letterSpacing: '-0.04em',
              color: textIndex === 2 ? '#5EEAD4' : '#FAFAFA',
              textShadow: '0 16px 48px rgba(0,0,0,0.9)',
            }}
          >
            {centerText}
          </div>
          <div
            style={{
              color: '#A1A1AA',
              fontSize: isPortrait ? 15 : 20,
              fontWeight: 600,
              marginTop: 12,
              letterSpacing: '0.02em',
            }}
          >
            Dual local & cloud playback pipeline
          </div>
        </div>
      </div>
    </div>
  );
};
