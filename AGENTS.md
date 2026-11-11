# Agent Collaboration Guidelines

> **Mandatory Rule File:** Every agent MUST read and adhere to [RULES.md](file:///RULES.md) before executing tasks, planning, or editing code.

## Repository Structure & Rules
- **Rule Reference**: Follow [RULES.md](file:///RULES.md) for lookup protocols ([FUNCTION_INDEX.md](file:///.planning/codebase/FUNCTION_INDEX.md)), mandatory pre-fix diagnostic gates, surgical edit boundaries, and verification requirements.
- **Core Modules (`:core-model`, `:core-dsp`, `:core-library`, `:core-common`, `:integration-api`)**: Pure Kotlin modules without Android framework dependencies.
- **Shared UI Goal**: Single Compose Multiplatform UI shared by Android portrait, Android landscape (16:9), and Windows desktop (16:9).
- **Rule**: Move, don't rewrite.
- **Rule**: No hallucinations. Verify every claim with real command output, tests, or logs.
- **Branch**: `feature/liquid-dock` (or active feature branch). Never push to `main` until "go live".

