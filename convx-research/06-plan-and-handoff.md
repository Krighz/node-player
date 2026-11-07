# 06-plan-and-handoff.md — Implementation Roadmap & Future Session Handoff

> **Project Reference:** Convx (`D:\Convx-main`)  
> **Target Codebase:** Node Player (`D:\AI MAKING\node-player`)  
> **Audience:** Next Development Session / Autonomous Agent  
> **Status:** Implementation Plan Only (Strictly No Code Modified This Session)

---

## 1. Goal & Objectives for Next Session

### Primary Goal:
Eliminate the frame drops and render-thread lag in Node Player caused by continuous full-resolution backdrop re-blurring under animated procedural backgrounds, while upgrading Node Player's optical liquid glass aesthetic on both **Android (AGSL)** and **Windows Desktop (Skia / SkSL)**.

### Measurable Acceptance Criteria:
1. **Frame Stability:** Scrolling a LazyColumn with an active animated background (`LiquidGlassBackground`) and visible glass dock (`FrostedBottomBar` & `FrostedMiniPlayer`) must sustain $\ge 110$ fps on 120Hz Android displays and a solid 60/120 fps on Windows Desktop.
2. **GPU Workload Reduction:** Screen blur pixel fill reduced by $\ge 85\%$ via shared 0.35x downscaling.
3. **Multiplatform Parity:** Optical SDF lens refraction, specular highlights, and chromatic aberration functional on both Android and Windows with zero platform-specific leaks into `core-ui`.
4. **Zero Regression:** Existing glass settings (opacity, blur radius, grain, chromatic fringe, lens strength) and the automated `GlassShaderSelfTest` fallback remain fully operational.

---

## 2. Techniques to Adapt (In Original Words & Architecture)

1. **Shared 0.35x Downscaled Blur Buffer (`SharedGlassPool`):**  
   Instead of each UI component (mini-player, bottom dock, search pill) taking independent full-resolution Haze/Skia snapshots, capture the active background layer once into an offscreen texture downscaled to $0.35\times$ resolution, apply Gaussian blur at this smaller size, and share it across all glass chrome.
2. **Multi-Shape Single-Pass SDF Shader (`MultiShapeLens`):**  
   Combine all active dock surfaces (e.g. mini-player rect, navigation bar rect, search circle) into an array of up to 4–8 geometric bounding boxes passed to one single shader, evaluating optical displacement in a single GPU draw pass.
3. **Gesture Motion Freezing (`ScrollFreeze`):**  
   Intercept list scrolls and sheet drags via a Compose `NestedScrollConnection`. While a fling or scroll is in flight, freeze the background capture and reuse the existing texture, unfreezing only after momentum settles.
4. **Quantized Loop Bucketing for Background Motion (`LoopBucketCache`):**  
   Quantize continuous background animation time (e.g. into 100ms buckets) and cache blurred frames in an LRU graphics layer cache so repeating background loops require zero GPU blur passes after initial warm-up.

---

## 3. Ordered Implementation Task Breakdown

### Phase 1: Shared Blur Pool & Downscaling in `core-ui`
- **Task 1.1:** Create `core-ui/src/commonMain/kotlin/com/nodeplayer/ui/components/SharedGlassPool.kt`. Define an expect/actual or Compose `GraphicsLayer` container that records background content at `0.35f` scale.
- **Task 1.2:** Update `LiquidGlassBackground.kt` to expose a stable backdrop holder.
- **Task 1.3:** Implement upscale rendering with bilinear filtering so the 0.35x blurred texture seamlessly fills the dock area.

### Phase 2: Multi-Shape SDF Lens Shader (Android AGSL & Windows Skia)
- **Task 2.1 (Android):** Update `GlassRefractionShader.kt` to support multi-rect bounding uniforms (`rects[]`, `radii[]`, `count`) using AGSL.
- **Task 2.2 (Desktop):** Port the same multi-shape SDF shader to SkSL in `app-desktop` using `org.jetbrains.skia.RuntimeEffect`.
- **Task 2.3:** Bind existing theme tokens (`lensStrength`, `rimSpecular`, `chromaticFringe`, `grainStrength`) to the shader uniforms.

### Phase 3: Gesture Freezing & Draw-Phase Optimization
- **Task 3.1:** Implement `rememberBackdropScrollFreeze()` in `core-ui` using `NestedScrollConnection`.
- **Task 3.2:** Verify that all timing and freeze variables use primitive arrays (`longArrayOf`) rather than Compose `mutableStateOf` to prevent draw-phase recomposition loops.
- **Task 3.3:** Attach the scroll connection to `LibraryScreen` and `HomeScreen` lazy lists.

### Phase 4: Integration with Self-Test & Fallbacks
- **Task 4.1:** Verify that `GlassShaderSelfTest.kt` passes with the updated shader code.
- **Task 4.2:** Ensure that when Performance Mode is ON or the self-test fails, the system cleanly drops back to plain frosted blur without crashes.

---

## 4. Verification & Measurement Plan for Future Session

To measure success using real frame times:
1. **On Android:**
   - Connect a physical test device via USB debugging (`adb`).
   - Run `adb shell dumpsys gfxinfo com.nodeplayer.music framestats reset`.
   - Scroll through the library list for 10 seconds with the glass dock visible and background animating.
   - Run `adb shell dumpsys gfxinfo com.nodeplayer.music framestats` and calculate:
     - Median frame time (target: $< 8.3\text{ ms}$ for 120Hz, $< 16.6\text{ ms}$ for 60Hz)
     - 95th and 99th percentile frame times
     - Total percentage of janky frames (target: $< 2.0\%$)
2. **On Windows Desktop:**
   - Profile via Java VisualVM or Skia frame logger.
   - Verify zero Garbage Collection (GC) pauses from shader allocations during continuous playback.

## 6. User Decisions & Architectural Directives (Captured Round 1)

1. **Target Platform Strategy:** **Multiplatform First.** Implement the shared downscaled blur pool and multi-shape SDF shader in `core-ui` for both Android (AGSL) and Windows Desktop (Skia / SkSL) concurrently.
2. **Visual Dock Styling:** **Adopt Convx's full iOS-style floating tab bar with draggable selection puck** alongside the mini-player.
3. **GPU Resolution & Quality Scaling:** **Balanced by default (`0.35x` resolution scale), paired with a user-facing resolution scale slider** in Settings so users can tune between battery conservation and absolute sharpness.
4. **Code Reuse & Licensing Strategy:** **Hybrid Approach.** Re-implement the parts that require cross-platform adaptation (`core-ui`, Skia, state management) as clean-room code; reuse/vendor lower-level math/SDF primitives where appropriate, with licensing compliance verified prior to commit.

- **Problem Identified:** Node Player's lag is caused by full-resolution ($1080 \times 2400$) backdrop blur re-computations occurring on every single frame due to continuous procedural animation in `LiquidGlassBackground.kt`.
- **Reference Solution in Convx:** Convx maintains ultra-fluid 120Hz performance using a 4-tier optimization pipeline:
  1. *Downscaling:* Blurs the scene at `0.35x` resolution (saving 88% GPU pixel fill).
  2. *Single-Pass Pooling:* Evaluates multiple glass shapes in one AGSL pass using SDFs.
  3. *Scroll Freezing:* Holds previous frame textures during scroll flings, cutting frame times from 47ms to 1.9ms.
  4. *Loop Bucketing:* Caches blurred frames over repeating background cycles.
- **Feasibility:** Fully portable to both Android (AGSL) and Windows Desktop (Skia / SkSL).
- **Deliverables Created:**
  - `D:\convx-research\01-app-overview.md` (Architecture, dependencies, pipelines)
  - `D:\convx-research\02-ui-analysis.md` (Design tokens, components, springs)
  - `D:\convx-research\03-glass-shader.md` (SDF shader math, multi-shape refraction)
  - `D:\convx-research\04-comparison.md` (Comparison matrix, multiplatform plan)
  - `D:\convx-research\05-explained.md` (Educational guide, sequence diagrams, exercises)
  - `D:\convx-research\06-plan-and-handoff.md` (Implementation roadmap for next session)
