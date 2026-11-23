import React from 'react';
import { interpolate, useCurrentFrame } from 'remotion';

interface StudioBackgroundProps {
  accentIntensity?: number;
  glowCenterX?: number;
  glowCenterY?: number;
}

export const StudioBackground: React.FC<StudioBackgroundProps> = ({
  accentIntensity = 0.12,
  glowCenterX = 50,
  glowCenterY = 45,
}) => {
  const frame = useCurrentFrame();

  // Gentle acoustic light breathing effect
  const breath = Math.sin(frame / 45) * 0.03;
  const effectiveIntensity = Math.max(0.05, accentIntensity + breath);

  return (
    <div
      style={{
        position: 'absolute',
        inset: 0,
        backgroundColor: '#09090B',
        overflow: 'hidden',
        pointerEvents: 'none',
      }}
    >
      {/* Radial ambient acoustic teal light falloff */}
      <div
        style={{
          position: 'absolute',
          inset: 0,
          background: `radial-gradient(circle at ${glowCenterX}% ${glowCenterY}%, rgba(94, 234, 212, ${effectiveIntensity}) 0%, rgba(20, 20, 26, 0.4) 45%, rgba(9, 9, 11, 0.98) 75%)`,
        }}
      />

      {/* Subtle floor perspective grid horizon */}
      <div
        style={{
          position: 'absolute',
          bottom: 0,
          left: '-20%',
          right: '-20%',
          height: '42%',
          background:
            'radial-gradient(ellipse at 50% 100%, rgba(94, 234, 212, 0.06) 0%, transparent 70%)',
        }}
      />

      {/* Soft floor contact reflection line */}
      <div
        style={{
          position: 'absolute',
          bottom: '12%',
          left: '15%',
          right: '15%',
          height: '1px',
          background:
            'linear-gradient(90deg, transparent 0%, rgba(94, 234, 212, 0.15) 50%, transparent 100%)',
          filter: 'blur(1px)',
        }}
      />
    </div>
  );
};
