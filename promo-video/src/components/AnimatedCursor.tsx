import React from 'react';
import { interpolate, spring, useCurrentFrame, useVideoConfig } from 'remotion';

interface AnimatedCursorProps {
  startX: number;
  startY: number;
  targetX: number;
  targetY: number;
  moveStartFrame: number;
  clickFrame: number;
  durationFrames?: number;
}

export const AnimatedCursor: React.FC<AnimatedCursorProps> = ({
  startX,
  startY,
  targetX,
  targetY,
  moveStartFrame,
  clickFrame,
  durationFrames = 25,
}) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  // Glide movement spring
  const moveSpring = spring({
    frame: Math.max(0, frame - moveStartFrame),
    fps,
    config: {
      damping: 18,
      stiffness: 120,
    },
  });

  const posX = interpolate(moveSpring, [0, 1], [startX, targetX]);
  const posY = interpolate(moveSpring, [0, 1], [startY, targetY]);

  // Click recoil compression
  const isClicking = frame >= clickFrame && frame <= clickFrame + 8;
  const clickScale = isClicking ? 0.85 : 1.0;

  // Click ripple wave
  const rippleProgress = interpolate(frame - clickFrame, [0, 16], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const showRipple = frame >= clickFrame && frame <= clickFrame + 16;
  const rippleScale = interpolate(rippleProgress, [0, 1], [0.5, 2.4]);
  const rippleOpacity = interpolate(rippleProgress, [0, 1], [0.8, 0]);

  return (
    <div
      style={{
        position: 'absolute',
        left: posX,
        top: posY,
        pointerEvents: 'none',
        zIndex: 100,
        transform: `translate(-2px, -2px) scale(${clickScale})`,
        transition: 'transform 0.05s ease',
      }}
    >
      {/* Click ripple circle */}
      {showRipple && (
        <div
          style={{
            position: 'absolute',
            left: 0,
            top: 0,
            width: 32,
            height: 32,
            borderRadius: '50%',
            border: '2px solid #5EEAD4',
            transform: `translate(-50%, -50%) scale(${rippleScale})`,
            opacity: rippleOpacity,
          }}
        />
      )}

      {/* Modern Mac/Figma-style vector pointer */}
      <svg
        width="28"
        height="28"
        viewBox="0 0 24 24"
        fill="none"
        style={{
          filter: 'drop-shadow(0 4px 8px rgba(0,0,0,0.6))',
        }}
      >
        <path
          d="M3 3L10.07 20.97L13.58 13.58L20.97 10.07L3 3Z"
          fill="#FAFAFA"
          stroke="#09090B"
          strokeWidth="1.5"
          strokeLinejoin="round"
        />
      </svg>
    </div>
  );
};
