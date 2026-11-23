import React from 'react';
import { interpolate, spring, useCurrentFrame, useVideoConfig } from 'remotion';

interface KineticBadgeProps {
  category?: string;
  title: string;
  subtitle?: string;
  highlights?: string[];
  delay?: number;
  align?: 'left' | 'center' | 'right';
  maxWidth?: number;
}

export const KineticBadge: React.FC<KineticBadgeProps> = ({
  category,
  title,
  subtitle,
  highlights = [],
  delay = 0,
  align = 'left',
  maxWidth = 620,
}) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  const adjustedFrame = Math.max(0, frame - delay);

  // Critical damped spring for snappy typography reveal
  const animProgress = spring({
    frame: adjustedFrame,
    fps,
    config: {
      damping: 18,
      stiffness: 160,
    },
  });

  const translateY = interpolate(animProgress, [0, 1], [30, 0]);
  const opacity = interpolate(animProgress, [0, 1], [0, 1]);

  const alignmentStyle: React.CSSProperties = {
    textAlign: align,
    alignItems: align === 'center' ? 'center' : align === 'right' ? 'flex-end' : 'flex-start',
  };

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        ...alignmentStyle,
        maxWidth,
        opacity,
        transform: `translateY(${translateY}px)`,
        fontFamily: 'Inter, system-ui, -apple-system, sans-serif',
      }}
    >
      {category && (
        <div
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 8,
            padding: '6px 14px',
            borderRadius: 999,
            backgroundColor: 'rgba(94, 234, 212, 0.1)',
            border: '1px solid rgba(94, 234, 212, 0.25)',
            color: '#5EEAD4',
            fontSize: 13,
            fontWeight: 700,
            letterSpacing: '0.08em',
            textTransform: 'uppercase',
            marginBottom: 16,
          }}
        >
          <span
            style={{
              width: 6,
              height: 6,
              borderRadius: '50%',
              backgroundColor: '#5EEAD4',
              boxShadow: '0 0 10px #5EEAD4',
            }}
          />
          {category}
        </div>
      )}

      <h2
        style={{
          margin: 0,
          color: '#FAFAFA',
          fontSize: 48,
          fontWeight: 800,
          lineHeight: 1.15,
          letterSpacing: '-0.03em',
        }}
      >
        {title}
      </h2>

      {subtitle && (
        <p
          style={{
            margin: '14px 0 0 0',
            color: '#A1A1AA',
            fontSize: 20,
            lineHeight: 1.5,
            fontWeight: 500,
          }}
        >
          {subtitle}
        </p>
      )}

      {highlights.length > 0 && (
        <div
          style={{
            display: 'flex',
            flexWrap: 'wrap',
            gap: 10,
            marginTop: 22,
            justifyContent: align === 'center' ? 'center' : 'flex-start',
          }}
        >
          {highlights.map((h, i) => (
            <div
              key={i}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                padding: '8px 16px',
                borderRadius: 12,
                backgroundColor: 'rgba(255, 255, 255, 0.04)',
                border: '1px solid rgba(255, 255, 255, 0.08)',
                color: '#E4E4E7',
                fontSize: 14,
                fontWeight: 600,
                backdropFilter: 'blur(8px)',
              }}
            >
              {h}
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
