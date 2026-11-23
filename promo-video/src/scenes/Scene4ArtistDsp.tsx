import React from 'react';
import { interpolate, spring, useCurrentFrame, useVideoConfig } from 'remotion';
import { KineticBadge } from '../components/KineticBadge';
import { RealisticPhoneFrame } from '../components/RealisticPhoneFrame';
import { StudioBackground } from '../components/StudioBackground';

interface SceneProps {
  layout: '16x9' | '9x16';
}

export const Scene4ArtistDsp: React.FC<SceneProps> = ({ layout }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  const isPortrait = layout === '9x16';

  // Entry spring
  const enterSpring = spring({
    frame,
    fps,
    config: {
      damping: 17,
      stiffness: 110,
    },
  });

  const phoneRotateY = interpolate(frame, [0, 330], [-12, 10]);
  const phoneRotateX = interpolate(frame, [0, 330], [5, -5]);
  const phoneScale = isPortrait
    ? interpolate(enterSpring, [0, 1], [0.85, 0.98])
    : interpolate(enterSpring, [0, 1], [0.85, 1.05]);

  // Transition from Artist Hub to 10-Band DSP / AutoEQ at frame 165
  const showDsp = frame > 165;

  // Scene exit transition
  const exitProgress = interpolate(frame, [305, 330], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const exitOpacity = 1 - exitProgress;

  const phoneWidth = isPortrait ? 380 : 360;

  return (
    <div
      style={{
        position: 'absolute',
        inset: 0,
        opacity: exitOpacity,
        overflow: 'hidden',
        fontFamily: 'Inter, system-ui, -apple-system, sans-serif',
      }}
    >
      <StudioBackground accentIntensity={0.15} glowCenterX={isPortrait ? 50 : 65} />

      {!isPortrait ? (
        <div
          style={{
            position: 'absolute',
            inset: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '0 120px',
            boxSizing: 'border-box',
          }}
        >
          {/* Left Text Callout */}
          <KineticBadge
            category="Audiophile Precision"
            title="Artist Hubs & 32-Bit Studio DSP"
            subtitle="Explore 380dp hero artist portraits, 7 categorized shelves, and calibrate sound with a 10-band parametric EQ, Harman AutoEQ IEM profiles, and DJ Blend crossfade."
            highlights={[
              'Full Artist Hubs',
              '32-Bit Float DSP & AutoEQ',
              'Node Connect P2P',
            ]}
            delay={10}
            maxWidth={640}
          />

          {/* Right 3D Phone Chassis */}
          <div style={{ position: 'relative', width: 450, height: 780, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <RealisticPhoneFrame
              width={phoneWidth}
              rotateX={phoneRotateX}
              rotateY={phoneRotateY}
              scale={phoneScale}
              imageSrc={showDsp ? 'captures/06_dsp_mixtape.png' : 'captures/05_artist.png'}
            />
          </div>
        </div>
      ) : (
        <div
          style={{
            position: 'absolute',
            inset: 0,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            padding: '90px 40px 0 40px',
            boxSizing: 'border-box',
          }}
        >
          <KineticBadge
            category="Audiophile Precision"
            title="Artist Hubs & 32-Bit DSP"
            subtitle="Full interactive artist discographies and 10-band AutoEQ."
            highlights={['Artist Hubs', '32-Bit AutoEQ', 'Node Connect P2P']}
            delay={8}
            align="center"
            maxWidth={520}
          />

          <div style={{ position: 'relative', marginTop: 40, width: '100%', height: 950, display: 'flex', justifyContent: 'center' }}>
            <RealisticPhoneFrame
              width={phoneWidth}
              rotateX={phoneRotateX}
              rotateY={phoneRotateY}
              scale={phoneScale}
              imageSrc={showDsp ? 'captures/06_dsp_mixtape.png' : 'captures/05_artist.png'}
            />
          </div>
        </div>
      )}
    </div>
  );
};
