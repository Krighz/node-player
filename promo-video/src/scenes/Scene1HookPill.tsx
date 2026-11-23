import React from 'react';
import { interpolate, spring, useCurrentFrame, useVideoConfig } from 'remotion';
import { AnimatedCursor } from '../components/AnimatedCursor';
import { StudioBackground } from '../components/StudioBackground';

interface SceneProps {
  layout: '16x9' | '9x16';
}

export const Scene1HookPill: React.FC<SceneProps> = ({ layout }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  const isPortrait = layout === '9x16';

  // 4 Kinetic Words flashing on 124 BPM beat grid (58 frames per step)
  // 0-58: Stream.
  // 58-116: Local.
  // 116-174: Lossless.
  // 174-205: Every Beat.
  let currentWord = 'Stream.';
  let wordColor = '#FAFAFA';

  if (frame >= 58 && frame < 116) {
    currentWord = 'Local.';
    wordColor = '#FAFAFA';
  } else if (frame >= 116 && frame < 174) {
    currentWord = 'Lossless.';
    wordColor = '#FAFAFA';
  } else if (frame >= 174 && frame < 205) {
    currentWord = 'Every Beat.';
    wordColor = '#5EEAD4';
  }

  // Snappy spring per word (zero blur!)
  const wordCycleFrame = frame < 174 ? frame % 58 : frame - 174;
  const wordSpring = spring({
    frame: wordCycleFrame,
    fps,
    config: { damping: 16, stiffness: 240 },
  });

  // Sharp scale & clip-path reveal (100% in focus at all times)
  const wordScale = interpolate(wordSpring, [0, 1], [0.92, 1.0]);
  const wordOpacity = interpolate(wordSpring, [0, 0.3, 1], [0, 0.9, 1]);
  const clipBottom = interpolate(wordSpring, [0, 1], [100, 0]);

  // Pill reveal at frame 198
  const pillSpring = spring({
    frame: Math.max(0, frame - 198),
    fps,
    config: { damping: 18, stiffness: 160 },
  });

  const showPill = frame >= 198;
  const pillScale = interpolate(pillSpring, [0, 1], [0.85, 1]);
  const pillOpacity = interpolate(pillSpring, [0, 1], [0, 1]);

  // Click on "Device Files" occurs at beat frame 232
  const isDeviceActive = frame >= 232;

  // Zoom through pill into Scene 2 (frames 246 to 270)
  const zoomProgress = interpolate(frame, [246, 270], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const zoomScale = interpolate(zoomProgress, [0, 1], [1, 4.0]);
  const zoomOpacity = interpolate(zoomProgress, [0, 1], [1, 0]);

  const pillWidth = isPortrait ? 340 : 420;
  const pillHeight = isPortrait ? 60 : 72;

  // Cursor coordinates
  const pillCenterX = isPortrait ? 540 : 960;
  const pillCenterY = isPortrait ? 960 : 540;
  const targetX = pillCenterX + (isPortrait ? 85 : 105);
  const targetY = pillCenterY;

  return (
    <div
      style={{
        position: 'absolute',
        inset: 0,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        opacity: zoomOpacity,
        transform: `scale(${zoomScale})`,
        overflow: 'hidden',
        fontFamily: 'Inter, system-ui, -apple-system, sans-serif',
      }}
    >
      <StudioBackground accentIntensity={0.12} />

      {/* Kinetic Text Words with Zero Blur Mask-Clip Reveal (0 - 200 frames) */}
      {frame < 205 && (
        <div
          style={{
            position: 'absolute',
            textAlign: 'center',
            transform: `scale(${wordScale})`,
            opacity: wordOpacity,
            clipPath: `inset(0 0 ${clipBottom}% 0)`,
            zIndex: 10,
          }}
        >
          <div
            style={{
              fontSize: isPortrait ? 64 : 96,
              fontWeight: 900,
              letterSpacing: '-0.04em',
              color: wordColor,
              textShadow: '0 12px 36px rgba(0,0,0,0.8)',
            }}
          >
            {currentWord}
          </div>
        </div>
      )}

      {/* Floating Mode Switcher Pill */}
      {showPill && (
        <div
          style={{
            position: 'relative',
            width: pillWidth,
            height: pillHeight,
            borderRadius: 999,
            backgroundColor: '#121215',
            border: '1px solid rgba(255, 255, 255, 0.16)',
            boxShadow: '0 24px 60px rgba(0, 0, 0, 0.75)',
            display: 'flex',
            alignItems: 'center',
            padding: 4,
            boxSizing: 'border-box',
            transform: `scale(${pillScale})`,
            opacity: pillOpacity,
            zIndex: 20,
          }}
        >
          {/* Active selection indicator sliding background */}
          <div
            style={{
              position: 'absolute',
              top: 4,
              bottom: 4,
              left: isDeviceActive ? '50%' : 4,
              width: 'calc(50% - 4px)',
              borderRadius: 999,
              backgroundColor: '#5EEAD4',
              transition: 'left 0.18s cubic-bezier(0.16, 1, 0.3, 1)',
              zIndex: 1,
            }}
          />

          {/* YT Music Option */}
          <div
            style={{
              flex: 1,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 8,
              zIndex: 5,
              color: !isDeviceActive ? '#09090B' : '#A1A1AA',
              fontWeight: 800,
              fontSize: isPortrait ? 15 : 18,
            }}
          >
            <span
              style={{
                width: 7,
                height: 7,
                borderRadius: '50%',
                backgroundColor: !isDeviceActive ? '#09090B' : '#5EEAD4',
              }}
            />
            YT Music
          </div>

          {/* Device Files Option */}
          <div
            style={{
              flex: 1,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              zIndex: 5,
              color: isDeviceActive ? '#09090B' : '#A1A1AA',
              fontWeight: 800,
              fontSize: isPortrait ? 15 : 18,
            }}
          >
            Device Files
          </div>
        </div>
      )}

      {/* Animated Cursor clicking Device Files at quantized beat frame 232 */}
      {showPill && (
        <AnimatedCursor
          startX={pillCenterX + 250}
          startY={pillCenterY + 180}
          targetX={targetX}
          targetY={targetY + 8}
          moveStartFrame={206}
          clickFrame={232}
        />
      )}
    </div>
  );
};
