# Known Issues & Workarounds (v1.6.0 Development)

1. **Windows Authenticode Signing**:
   - *Issue*: Unsigned MSI and portable EXE trigger Windows Defender SmartScreen "Unknown Publisher" protection modal.
   - *Workaround*: Click "More info" &rarr; "Run anyway". EV Code Signing certificate will eliminate this in formal production release.
2. **Windows Firewall Inbound Prompt**:
   - *Issue*: Node Connect listening on port 47200 may prompt for Windows Defender Firewall permission.
   - *Status*: Handled with an informative in-app guidance modal explaining the private network firewall rule.
3. **Mali / MediaTek GPU Shader Spill**:
   - *Issue*: Certain older mobile GPUs struggle with unclipped domain-warping shaders.
   - *Status*: Resolved via automated offscreen shader self-test and graceful fallback to Haze blur with rim highlight.
4. **Simple Mode Security Boundary**:
   - *Context*: Simple Mode uses unauthenticated TLS (accept any cert, zero PIN) for zero-friction local LAN/hotspot connectivity.
   - *Guidance*: Switch to Strict Mode in Settings on untrusted or public Wi-Fi networks (detailed in `NODE_CONNECT_SECURITY.md`).
5. **ADB USB Loopback Tunnel Port Forwarding**:
   - *Context*: Direct USB loopback link uses port `54825` (PC forward) and `54826` (Phone reverse) to avoid collision with local `NodeServer` port `54820`.
   - *Guidance*: Accessible via 1-click "Setup USB Debugging Link" in Connection Doctor.
6. **ExoPlayer Application Thread Restriction for Audio Cast (Resolved)**:
   - *Issue*: Accessing `activePlayer.currentPosition` from background dispatcher (`Dispatchers.IO`) threw `IllegalStateException: Player is accessed on the wrong thread`.
   - *Status*: Resolved by capturing position on main application thread before handing off PCM tap streaming to IO scope.
7. **Android Emulator GPU SkSL Shader Precision Variances vs Physical ART Hardware (D-07)**:
   - *Issue*: Software/Host GPU emulation in Android Emulator (QEMU / Goldfish / SwiftShader / host GPU passthrough) can exhibit slight subpixel coordinate quantization, precision rounding variances, or missing hardware blending stages for runtime SkSL/AGSL shaders (`RenderEffect.createRuntimeShaderEffect`).
   - *Status*: Expected emulator virtualization behavior. Pixel-perfect AGSL refraction, Fresnel rim lighting, chromatic dispersion, and blur falloff must be validated on physical Android ART hardware (API 26+) or via the in-app hardware diagnostic self-test (`GlassShaderSelfTest`).

