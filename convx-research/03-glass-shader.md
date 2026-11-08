# 03-glass-shader.md — Deep Deconstruction of the Convx Optical Glass Shader

> **Project Reference:** Convx (`D:\Convx-main`)  
> **Audience:** Node Player Engine & Graphics Pipeline Team  
> **Mode:** Pure Research & Analysis (Read-Only)

---

## 1. Overview & Location of Shader Infrastructure

The optical liquid glass system in Convx is located in:
- `app/src/main/kotlin/com/convx/music/ui/component/GlassEffect.kt`
- `app/src/main/kotlin/com/convx/music/ui/component/backdrop/`
  - `GlassPool.kt`
  - `DrawBackdropModifier.kt`
  - `effects/Lens.kt`
  - `effects/Blur.kt`
  - `internal/Shaders.kt`
  - `backdrops/LayerBackdrop.kt`
  - `backdrops/BackdropFreeze.kt`

The shader language is **AGSL (Android Graphics Shading Language)** compiled dynamically at runtime into `android.graphics.RuntimeShader` instances and wrapped into Compose `RenderEffect` pipelines.

---

## 2. Backdrop Capture & Pixel Acquisition

A primary bottleneck in real-time glass effects is how background pixels are obtained. Many naive implementations use `View.drawToBitmap()` or capture the whole window, causing massive GPU readbacks.

Convx uses a zero-copy GPU layer capture mechanism:

```
[Screen Content Tree] 
        │ (RenderNode recorded into graphicsLayer)
        ▼
[LayerBackdrop / LayerRecorder]
        │ (Downscaled 0.35x & translated to pool origin)
        ▼
[GlassPoolNode] ──> Base RenderEffect: ColorFilter + Gaussian Blur (0.35x size)
        │
        ▼ MultiRefraction AGSL Shader (One single pass over all shapes)
[Single Offscreen Texture (Masked)]
        │
        ▼ (Drawn by each sampling surface: MiniPlayer, Dock, Search Button)
[Screen Composite]
```

### 2.1 The Capture Mechanics
1. **Source Recording:** The entire app content sits inside a `rememberLayerBackdrop()` ([AppBackdropState.kt:54-62](file:///D:/Convx-main/app/src/main/kotlin/com/convx/music/AppBackdropState.kt#L54-L62)). The content is drawn into a dedicated `androidx.compose.ui.graphics.layer.GraphicsLayer`.
2. **GPU Native References:** Because `GraphicsLayer` wraps an Android `RenderNode`, drawing it does **not** download pixels to CPU RAM. The Skia/HWUI pipeline retains the GPU display list.
3. **Downscaling (`GlassPoolScale = 0.35f`):** The shared glass pool records the source at only 35% of the screen dimension ([GlassPool.kt:42](file:///D:/Convx-main/app/src/main/kotlin/com/convx/music/ui/component/backdrop/GlassPool.kt#L42)). Because the backdrop is immediately blurred, the blur naturally masks the low resolution while reducing fill-rate by **~88%**.

---

## 3. Mathematical Pipeline of the Liquid Glass Shader

The core shader math is defined in [Shaders.kt:30-205](file:///D:/Convx-main/app/src/main/kotlin/com/convx/music/ui/component/backdrop/internal/Shaders.kt#L30-L205).

### 3.1 Signed Distance Fields (SDF) for Rounded Rectangles
To perform accurate optical refraction along curved borders, the shader evaluates the exact distance from each pixel to the rounded rectangle boundary:

```glsl
float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y; // Top-Right
        else return radii.z;                // Bottom-Right
    } else {
        if (coord.y <= 0.0) return radii.x; // Top-Left
        else return radii.w;                // Bottom-Left
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}
```

### 3.2 Gradient / Normal Vector Calculation
To refract light inwards, the surface normal (gradient of the distance field) is calculated analytically without numerical sampling:

```glsl
float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}
```

### 3.3 Lens Refraction Mapping
The refraction profile uses a circular arc mapping function:
$$\text{circleMap}(x) = 1.0 - \sqrt{1.0 - x^2}$$

1. If the distance from the edge $-sd \ge \text{refractionHeight}$, the pixel is inside the flat core of the glass and evaluates content normally: `content.eval(coord)`.
2. Inside the rim border ($0 \le -sd < \text{refractionHeight}$), the displacement distance $d$ is:
$$d = \text{circleMap}\left(1.0 - \frac{-sd}{\text{refractionHeight}}\right) \times \text{refractionAmount}$$
3. The refraction displacement moves along the gradient vector:
$$\text{refractedCoord} = \text{coord} + d \times \text{grad}$$

### 3.4 Chromatic Dispersion (Fringing)
When `chromaticAberration` is active ([Shaders.kt:198-204](file:///D:/Convx-main/app/src/main/kotlin/com/convx/music/ui/component/backdrop/internal/Shaders.kt#L198-L204)):
- Dispersion intensity increases quadratically toward the corners:
$$\text{dispersionIntensity} = \text{chromaticAberration} \times \left(\frac{\text{centeredCoord}.x \times \text{centeredCoord}.y}{\text{halfSize}.x \times \text{halfSize}.y}\right)$$
- The red and blue channels are offset along the normal:
  - $\text{Red} \gets \text{content.eval}(\text{refractedCoord} + \text{dispersedCoord}).r$
  - $\text{Green} \gets \text{content.eval}(\text{refractedCoord}).g$
  - $\text{Blue} \gets \text{content.eval}(\text{refractedCoord} - \text{dispersedCoord}).b$

### 3.5 Specular Rim Highlight (Pluck)
The edge specular highlight is computed using directional lighting:
$$\text{intensity} = \left|\text{dot}(\text{grad}, \text{lightDir})\right|^{\text{falloff}}$$
A hairline stroke (`0.8.dp`) with this intensity is composited along the upper perimeter ([GlassEffect.kt:200](file:///D:/Convx-main/app/src/main/kotlin/com/convx/music/ui/component/GlassEffect.kt#L200)).

---

## 4. Multi-Shape Single-Pass Glass Pool (`MultiRefraction`)

The most brilliant optimization in Convx is [MultiRefractionShaderString](file:///D:/Convx-main/app/src/main/kotlin/com/convx/music/ui/component/backdrop/internal/Shaders.kt#L115-L153).

### The Multi-Surface Problem
In standard glass docks with multiple floating buttons and pills (e.g. Nav Bar, Mini Player, Search Circle), having each component apply its own `RenderEffect` forces the GPU to create separate offscreen buffers for every element:
$$N \text{ components} \implies 2N \text{ render passes per frame}$$

### The Convx Solution
Convx combines up to 8 rounded rectangles into **one single AGSL shader pass**:
- The uniforms pass arrays:
  - `uniform float4 rects[8];` (Bounding boxes: $x_1, y_1, x_2, y_2$)
  - `uniform float4 radii[8];` (Corner radii for each rect)
  - `uniform float4 lens[8];` (Height, amount, depth effect per rect)
  - `uniform int count;`
- The fragment shader loops over $i < count$, finds which bounding box contains `coord`, computes the SDF refraction for that specific shape, and evaluates the pixel in **one single draw call**.

---

## 5. Caching, Resolution Scaling & Animated Content Handling

Convx solves the "animated background re-blur lag" through a 4-tier caching and throttling architecture:

### 5.1 Region-Bounded Capture
Rather than capturing the full screen, `GlassPoolNode` monitors `LayerBackdrop.coverageRequest` from visible samplers and clips the capture strictly to the union of visible glass surfaces plus a `32.dp` blur margin ([GlassPool.kt:208-230](file:///D:/Convx-main/app/src/main/kotlin/com/convx/music/ui/component/backdrop/GlassPool.kt#L208-L230)). Skia then rejects all offscreen scene graph nodes.

### 5.2 Motion & Gesture Freezing (`BackdropFreeze`)
During active user gestures (scrolling a lazy list, dragging a sheet, or pushing a NavHost destination):
- `BackdropFreeze` marks `frozen = true` via a `NestedScrollConnection` ([BackdropFreeze.kt:50-60](file:///D:/Convx-main/app/src/main/kotlin/com/convx/music/ui/component/backdrop/backdrops/BackdropFreeze.kt#L50-L60)).
- While frozen, `DrawBackdropNode.drawBackdropLayer` **completely skips re-recording the layer** ([DrawBackdropModifier.kt:489-497](file:///D:/Convx-main/app/src/main/kotlin/com/convx/music/ui/component/backdrop/DrawBackdropModifier.kt#L489-L497)), replaying the previous valid GPU texture.
- The cost drops from **47ms per scroll frame to 1.9ms** on Android RenderThread. Once the gesture settles (`onPostFling`), a single capture refresh is scheduled.

### 5.3 Static Region Protection (`backdropStaticRegion`)
If parts of the underlying screen change (e.g. a small playhead indicator or text timer), but the area underneath the glass dock is declared inside a `backdropStaticRegion` ([BackdropStaticRegion.kt:23-25](file:///D:/Convx-main/app/src/main/kotlin/com/convx/music/ui/component/backdrop/backdrops/BackdropStaticRegion.kt#L23-L25)), the glass node checks `isFullyStatic(sampledRect)` and skips re-rendering.

### 5.4 Video & Animated Background Loop Bucketing (`VideoLoopClock`)
When an animated canvas or looping video plays beneath glass:
- `VideoLoopClock` quantizes video timeline position into discrete buckets (e.g., 100ms chunks) ([VideoLoopClock.kt:36-41](file:///D:/Convx-main/app/src/main/kotlin/com/convx/music/ui/player/VideoLoopClock.kt#L36-L41)).
- `DrawBackdropNode` maintains a graphics layer pool of up to 64 pre-recorded frames ([DrawBackdropModifier.kt:434-447](file:///D:/Convx-main/app/src/main/kotlin/com/convx/music/ui/component/backdrop/DrawBackdropModifier.kt#L434-L447)).
- Once the background loops once, **zero GPU re-blurring occurs**; the shader simply draws cached `GraphicsLayer` textures corresponding to the active bucket.

---

## 6. GPU Differences, Fallbacks & Device Compatibility

- **MediaTek / Mali Workaround:** Mobile GPUs with low-precision floating point units often exhibit NaN / INF artifacts when dividing by tiny coordinates. Convx enforces:
  - Coordinate clamping (`clamp(..., 0.0, 1.0)`)
  - Boundary guards before math execution
  - Discarding zero and NaN layer sizes before setting `renderEffect`
- **Fallback Hierarchy:**
  1. **Full Liquid Glass:** API 33+ (AGSL RuntimeShader + MultiRefraction).
  2. **Plain Blur (Frosted):** API 31–32 (RenderEffect Blur + ColorFilter, no lens shader).
  3. **Translucent Tint Fallback:** API < 31 or low-RAM devices (pure solid alpha fill with specular border stroke, zero GPU blur passes).
