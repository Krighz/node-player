# Windows UI Overhaul & Reference Architecture

**Reference Inspiration:** `media_1793787814079_13a6cdd8.jpg`  
**Framework:** Compose Multiplatform on Skia (Pure Kotlin, zero CSS)  
**Layout Mode:** Expanded 16:9 widescreen layout (Windows 10/11 & Android Landscape)  
**Status:** In Progress (v1.6.0)  

---

## 1. Visual Layout Hierarchy

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                                                                        │
│   ┌──────┐   [ Search artists, tracks, or playlists...        ]    [ (·) Device Connect ]│
│   │      │                                                                             │
│   │ Lens │   FOR YOU                                                                   │
│   │ Dock │   ┌────────────────┐ ┌────────────────┐ ┌────────────────┐ ┌────────────────┐│
│   │ Rail │   │ Pop Mixes      │ │ Cinematic      │ │ Electronic     │ │ Acoustic       ││
│   │      │   └────────────────┘ └────────────────┘ └────────────────┘ └────────────────┘│
│   │ Home │                                                                             │
│   │ Lib  │   REDISCOVER (Haven't heard in a while)                                     │
│   │ Set  │   ┌──┐ ┌──┐ ┌──┐ ┌──┐ ┌──┐ ┌──┐ ┌──┐ ┌──┐ ┌──┐ ┌──┐                          │
│   │      │   └──┘ └──┘ └──┘ └──┘ └──┘ └──┘ └──┘ └──┘ └──┘ └──┘                          │
│   │      │                                                                             │
│   │      │   RECENTLY PLAYED                                                           │
│   │      │   ┌──┐ ┌──┐ ┌──┐ ┌──┐ ┌──┐ ┌──┐ ┌──┐ ┌──┐ ┌──┐ ┌──┐                          │
│   │      │   └──┘ └──┘ └──┘ └──┘ └──┘ └──┘ └──┘ └──┘ └──┘ └──┘                          │
│   └──────┘                                                                             │
│                                                                                        │
│   ┌────────────────────────────────────────────────────────────────────────────────┐   │
│   │ [Art] Title • Artist  ♡   🔀 ⏮ ⏯ ⏭ 🔁   1:27 ──||||||||||||||||── -2:33  FLAC 24/48 🔊│   │
│   └────────────────────────────────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Component Token Mapping

| UI Component | File Location | Design Tokens / Parameters |
| :--- | :--- | :--- |
| **Search Capsule** | `core-ui/.../components/SearchCapsule.kt` | Width: 480 dp, Height: 44 dp, CornerRadius: 22 dp, Glass Tint: 0.12f, Border: 1 dp White 15% |
| **Device Connect Pill** | `core-ui/.../components/DeviceConnectPill.kt` | Height: 36 dp, Pill Radius: 18 dp, Dot Color: Cyan (0xFF00F0FF) / Emerald (0xFF10B981) |
| **Optical Rail Lens Dock** | `core-ui/.../components/OpticalRailDock.kt` | Bubble Width: 72 dp, Bubble Height: 56 dp, Pop-out Scale: 1.15x, Magnification: 1.22x, Specular Rim Gradient: White &rarr; Sky Blue |
| **For You Mix Cards** | `core-ui/.../screens/HomeView.kt` | Width: 280 dp, Height: 150 dp, CornerRadius: 18 dp, Dynamic Collage Grid, Hover Lift: -4 dp |
| **Rediscover Cards** | `core-ui/.../screens/HomeView.kt` | Size: 130x130 dp, Badges: "Last played 6 months ago", "Old favorite" (pill badge at bottom of artwork) |
| **Floating Player Deck** | `core-ui/.../player/BottomPlayerDeck.kt` | Height: 76 dp, Margin: 20 dp, CornerRadius: 28 dp, Glass Opacity: 0.88f, Specular Highlight: Top Edge 1.2 dp |
| **Waveform Seekbar** | `core-ui/.../components/WaveformSeekbar.kt` | Height: 28 dp, Bar Width: 2 dp, Bar Gap: 2 dp, Unplayed Alpha: 0.25f, Played Color: Pure White |
| **Real Quality Badge** | `core-ui/.../components/RealQualityBadge.kt` | Pill shape, Background: White 8%, Text: "FLAC 24-bit / 48kHz" (read directly from track + sink) |

---

## 3. Keyboard & Mouse Hotkey Matrix

- <kbd>Space</kbd>: Play / Pause toggle
- <kbd>→</kbd> / <kbd>←</kbd>: Seek $\pm5\text{s}$
- <kbd>Ctrl</kbd> + <kbd>→</kbd> / <kbd>←</kbd>: Next / Previous track
- <kbd>Ctrl</kbd> + <kbd>F</kbd>: Focus search capsule
- <kbd>Ctrl</kbd> + <kbd>1</kbd>, <kbd>2</kbd>, <kbd>3</kbd>: Optical tab navigation (Home, Library, Settings)
- <kbd>M</kbd>: Mute toggle
- Mouse Wheel on Seekbar: Seek $\pm5\text{s}$
- Mouse Wheel on Mixes/Rediscover: Smooth horizontal scrolling
