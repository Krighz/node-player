# Contributing to Node Player

Thank you for your interest in contributing to **Node Player**! We welcome community contributions that preserve audio fidelity, enhance performance, or refine the user experience.

---

## Code of Conduct

All contributors and participants are expected to adhere to our [Code of Conduct](CODE_OF_CONDUCT.md). Please report unacceptable behavior to the project maintainers.

---

## Development Setup

### Prerequisites
- **JDK 17** (Temurin, OpenJDK, or Zulu 17)
- **Android SDK Platform 34** (configured via `ANDROID_HOME` or `local.properties`)
- **Android NDK** (optional, pure Kotlin DSP engine runs on JVM)

### Building the Project
Clone the repository and build from source:

```bash
git clone https://github.com/Krighz/node-player.git
cd node-player

# Run DSP unit tests
./gradlew test

# Assemble debug APK
./gradlew assembleDebug
```

---

## Project Structure & Architecture Guidelines

Node Player is built with clean separation of concerns across pure-Kotlin and Android modules:

- **`:core-model`**: Pure Kotlin data models (`Track`, `Album`, `Playlist`, `Lyrics`, `AudioEffectConfig`). Keep strictly independent of Android framework dependencies.
- **`:core-dsp`**: 32-bit floating-point audio signal processing algorithms. All filters and processing blocks must include deterministic JVM unit tests in `:core-dsp:test`.
- **`:core-library`**: Pure Kotlin parsers (`.lrc`, `.cue`, `.m3u`, AutoEQ profiles).
- **`:app-android`**: Android 14 application layer (Jetpack Compose, Media3 `MediaSessionService`, Room database, AGSL runtime shaders, and AppWidgets).

---

## Pull Request Guidelines

1. **Feature Branches**: Create a descriptive branch from `main` (e.g. `feature/audiotrack-direct-output` or `fix/gapless-transition-flac`).
2. **Tests Pass**: Ensure `./gradlew test` passes with zero failures before opening a pull request.
3. **Commit Messages**: Use Conventional Commits (`feat: ...`, `fix: ...`, `docs: ...`, `perf: ...`).
4. **No Binary Keystores**: Never include keystores, private keys, or credentials in commits or pull requests.
5. **Code Style**: Format Kotlin code consistently with official Kotlin style guides.
