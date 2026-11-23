import React from 'react';
import { Img, OffthreadVideo, staticFile } from 'remotion';

interface RealisticPhoneFrameProps {
  width: number;
  height?: number;
  rotateX?: number;
  rotateY?: number;
  rotateZ?: number;
  translateX?: number;
  translateY?: number;
  scale?: number;
  imageSrc?: string;
  videoSrc?: string;
  videoStartFrom?: number;
}

export const RealisticPhoneFrame: React.FC<RealisticPhoneFrameProps> = ({
  width,
  height,
  rotateX = 0,
  rotateY = 0,
  rotateZ = 0,
  translateX = 0,
  translateY = 0,
  scale = 1,
  imageSrc,
  videoSrc,
  videoStartFrom = 0,
}) => {
  // Device aspect ratio is strictly 1080 / 2376 (~0.4545)
  const calcHeight = height || width * (2376 / 1080);
  const borderRadius = Math.round(width * 0.088);
  const screenBorderRadius = Math.round(width * 0.076);
  const bezel = Math.round(width * 0.024);

  return (
    <div
      style={{
        width,
        height: calcHeight,
        perspective: 1400,
        transformStyle: 'preserve-3d',
        transform: `translate3d(${translateX}px, ${translateY}px, 0) scale(${scale}) rotateX(${rotateX}deg) rotateY(${rotateY}deg) rotateZ(${rotateZ}deg)`,
        transition: 'transform 0.05s linear',
      }}
    >
      {/* Dynamic floor contact shadow */}
      <div
        style={{
          position: 'absolute',
          bottom: -45,
          left: '8%',
          width: '84%',
          height: 50,
          background: 'rgba(0, 0, 0, 0.75)',
          filter: 'blur(35px)',
          borderRadius: '50%',
          transform: `scale(${1 + Math.abs(rotateY) * 0.01})`,
        }}
      />

      {/* Titanium Outer Chassis with Multi-Layer Specular Highlights */}
      <div
        style={{
          position: 'relative',
          width: '100%',
          height: '100%',
          borderRadius,
          background: 'linear-gradient(145deg, #27272A 0%, #18181B 45%, #0F0F12 100%)',
          boxShadow: `
            0 0 0 1.5px rgba(255, 255, 255, 0.16),
            0 35px 80px rgba(0, 0, 0, 0.85),
            0 15px 35px rgba(0, 0, 0, 0.65),
            inset 0 1px 2px rgba(255, 255, 255, 0.25),
            inset 0 -1px 2px rgba(0, 0, 0, 0.6)
          `,
          padding: bezel,
          boxSizing: 'border-box',
          overflow: 'hidden',
        }}
      >
        {/* Top-lit specular glass rim reflection */}
        <div
          style={{
            position: 'absolute',
            top: 0,
            left: '10%',
            right: '10%',
            height: '2px',
            background: 'linear-gradient(90deg, transparent 0%, rgba(255, 255, 255, 0.45) 50%, transparent 100%)',
            zIndex: 10,
          }}
        />

        {/* Screen Display Container */}
        <div
          style={{
            position: 'relative',
            width: '100%',
            height: '100%',
            borderRadius: screenBorderRadius,
            overflow: 'hidden',
            backgroundColor: '#09090B',
          }}
        >
          {/* Active Screen Media (Video or Crisp PNG) */}
          {videoSrc ? (
            <OffthreadVideo
              src={staticFile(videoSrc)}
              startFrom={videoStartFrom}
              muted
              volume={0}
              style={{
                width: '100%',
                height: '100%',
                objectFit: 'cover',
                display: 'block',
              }}
            />
          ) : imageSrc ? (
            <Img
              src={staticFile(imageSrc)}
              style={{
                width: '100%',
                height: '100%',
                objectFit: 'cover',
                display: 'block',
              }}
            />
          ) : null}

          {/* Realistic Camera Punch Hole */}
          <div
            style={{
              position: 'absolute',
              top: Math.round(calcHeight * 0.016),
              left: '50%',
              transform: 'translateX(-50%)',
              width: Math.round(width * 0.05),
              height: Math.round(width * 0.05),
              borderRadius: '50%',
              backgroundColor: '#000000',
              boxShadow: 'inset 0 0 2px rgba(255,255,255,0.25)',
              zIndex: 30,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            {/* Subtle inner camera lens reflection */}
            <div
              style={{
                width: '35%',
                height: '35%',
                borderRadius: '50%',
                background: 'radial-gradient(circle, rgba(94, 234, 212, 0.3) 0%, rgba(30, 41, 59, 0.8) 100%)',
              }}
            />
          </div>

          {/* Photorealistic Curved Glass Specular Sheen Overlay */}
          <div
            style={{
              position: 'absolute',
              inset: 0,
              background: 'linear-gradient(125deg, rgba(255, 255, 255, 0.08) 0%, rgba(255, 255, 255, 0.02) 28%, transparent 50%, rgba(255, 255, 255, 0.03) 100%)',
              pointerEvents: 'none',
              zIndex: 25,
            }}
          />
        </div>
      </div>
    </div>
  );
};
