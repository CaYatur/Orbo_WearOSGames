# Orbo — Wrist Arcade

> **85 games · 13 tools** — a polished Wear OS arcade suite built on a custom Canvas engine.

**Developer:** [CaYaDev](https://cayadev.com) · **Package:** `com.cayadev.orbo`  
**Platform:** Wear OS 3+ (API 30) · **Language:** Kotlin · **No external dependencies**

---

## Features

- **Custom Canvas engine** — Kotlin + Android Canvas + `Choreographer` game loop with automatic coordinate scaling (`ScreenInfo`) so every game looks native on any watch size, round or square.
- **Zero runtime dependencies** — pure Android framework + Kotlin stdlib; the build is fully self-contained.
- **Procedural audio** (`Sfx`) — real-time synthesized sounds, no audio files.
- **Full sensory feedback** — haptics, particles, screen shake & flash.
- **Round-screen menus** — curved, fling-able, rotary-aware menus tuned for circular dials.
- **Persistent state** — per-game high scores, "orbs" currency, sound/haptic settings.
- **i18n ready** — `Loc.t()` dictionary supports EN / TR out of the box.

---

## Game Catalog

### Arcade (19)
Wing It, Dodge, Bubble Pop, Stack, Catch, Sky Hop, Spiral Drop,
**Brick Breaker**, **Geo Leap** ⭐, **Nebula Survivor** ⭐, Plinko, **Bubble Shooter**,
Pinball, Tunnel Dash, Color Switch, **Hex Dash**, Sky Dive, PinWheel, Timber

### Puzzle (16)
Minesweeper, Gem Swap, Slide Puzzle, **Twos**, **Block Blast**, **Maze Runner**,
Push It, Flow Connect, Pipes, Slide Out, Hanoi, Nonogram, Sudoku,
Peg Solitaire, **Cascade**, **Water Sort**

### Reflex (12)
Tap Reflex, Reaction, Mole Pop, Rhythm Tap, Orbit Dash, Bullseye,
Key Tiles, Color Rush, Orbit Tap, Quick Stop, Swipe Frenzy, Sharp Shooter

### Brain (14)
Color Match, Memory, Number Order, Quick Math, Pairs, Odd One,
**Shell Game**, Mastermind, Digit Span, Number Hunt, Memory Matrix,
Anagram, Sum Ten, Flash Count

### Classic (9)
Tic-Tac-Toe, Pong, Light Cycle, **Snake Neo**, Blackjack, Higher Lower,
Pig, Rock Paper Scissors, Dice Poker

### Action (9)
**Fruit Slash**, Cross Dash, Star Defender, Jet Dash, Missile Guard,
Air Hockey, Cave Flyer, Lane Runner, Meteor Shield

### Strategy (6)
Lights Out, **Reversi**, Connect Four, Dots & Boxes, Gomoku, Nim

### Tools (13)
Sketch, Timer Lab, Beat Pad, Dice Roller, Coin Flip, Spinner,
Tally, Metronome, Breathe, Level, Compass, Flashlight, World Clock

> **Bold** = big title · ⭐ = flagship title

---

## Tech Stack

| Layer | Detail |
|---|---|
| Language | Kotlin 2.1.10 |
| Build | Gradle 8.12 / AGP 8.9.1 |
| Min SDK | 30 (Wear OS 3) |
| Target SDK | 34 · Compile SDK 35 |
| JVM target | 17 |
| Dependencies | **None** (pure Android + Kotlin stdlib) |

---

## Build

The toolchain is pinned for a fully **offline** build — no network access required.

```powershell
# Debug APK
.\gradlew.bat assembleDebug --offline

# Signed release APK  (keystore: orbo-release.jks, alias "orbo")
.\gradlew.bat assembleRelease --offline
```

**Output paths:**
- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk`

### Install on device / emulator

```powershell
adb install -r app\build\outputs\apk\release\app-release.apk
```

> For Google Play distribution, re-sign with your own Play App Signing key.

---

## Project Structure

```
app/src/main/java/com/cayadev/orbo/
├── engine/          # Core: Screen, ScreenInfo, Sfx, Haptics, Particles, Backdrop, …
├── games/           # 85 individual game implementations
├── tools/           # 13 utility tools
└── data/            # Catalog, GameRegistry, GameMeta
```

---

## License

MIT License — © 2026 **CaYaDev (ÇAĞAN TURGUT)**  
See [LICENSE](LICENSE) for full text.

---

<p align="center">Made with ❤️ by <a href="https://cayadev.com">CaYaDev</a></p>
