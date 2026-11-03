# Agent Collaboration Guidelines

## Repository Structure & Rules
- **Core Modules (`:core-model`, `:core-dsp`, `:core-library`, `:core-common`, `:integration-api`)**: Pure Kotlin modules without Android framework dependencies.
- **Shared UI Goal**: Single Compose Multiplatform UI shared by Android portrait, Android landscape (16:9), and Windows desktop (16:9).
- **Rule**: Move, don't rewrite.
- **Rule**: No hallucinations. Verify every claim with real command output, tests, or logs.
- **Branch**: `feature/shared-ui` for shared UI development. Never push to `main` until "go live".
