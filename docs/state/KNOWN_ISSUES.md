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
