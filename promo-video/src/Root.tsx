import React from 'react';
import { Composition } from 'remotion';
import { PromoVideo } from './PromoVideo';

export const RemotionRoot: React.FC = () => {
  return (
    <>
      <Composition
        id="NodePlayerPromo16x9"
        component={PromoVideo}
        durationInFrames={2070}
        fps={60}
        width={1920}
        height={1080}
        defaultProps={{
          layout: '16x9' as const,
        }}
      />
      <Composition
        id="NodePlayerPromo9x16"
        component={PromoVideo}
        durationInFrames={2070}
        fps={60}
        width={1080}
        height={1920}
        defaultProps={{
          layout: '9x16' as const,
        }}
      />
    </>
  );
};
