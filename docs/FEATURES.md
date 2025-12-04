# Features & Roadmap

## Current Features (v1.0)

### Core
- Web Bluetooth interface (Chrome/Edge)
- Full keyboard support with all modifiers
- Relative mouse movement with scroll
- USB HID output via ESP32-S3

### Video
- USB capture card support
- Resolution/FPS selection
- Fullscreen capture mode
- Auto-save device preferences

### Input
- CapsLock command mode (20+ shortcuts)
- Mouse/scroll sensitivity adjustment
- Macros (4 slots)
- Scripts (multi-step automation)
- Mouse jiggler with configurable range

### UI
- 10 built-in themes (Catppuccin, etc.)
- 5 custom theme slots with JSON editor
- Settings modal with export/import
- Floating log panel during capture
- Toast notifications for commands

### Hardware

**Raspberry Pi Pico 2W** ⭐ *Recommended*
- ~$10, tiny, reliable
- MicroPython firmware - rock solid stability
- Supports seamless mode (absolute mouse/digitizer)
- Single green LED for status
- "Set it and forget it" - just works

**M5Stack Cardputer**
- Built-in display, keyboard, SD card
- More features but more complexity
- Good for development and demos
- May need occasional reset
- Swiss army knife aesthetic

*The Pico 2W is the reliable daily driver. The Cardputer is the feature-rich dev platform.*

---

## Roadmap

### High Priority
- [x] **Raspberry Pi Pico 2W support** - MicroPython BLE dongle ([firmware](../firmware/pico2w/)) ✅
- [x] **Document DIP control pendant** - Floating mini control panel using Document Picture-in-Picture API (always-on-top status, command feedback, quick actions - solves seamless mode feedback problem!) ✅
- [x] **Window Management API for portal** - Screen Manager with visual map, auto-position portal on target monitor, auto-fullscreen option, remember config, handle hotplug ✅ *(needs multi-monitor testing)*
- [ ] **Web firmware updater** - WebSerial-based MicroPython file upload from browser (no mpremote/Thonny needed)
- [ ] **Anbernic RG34XX SP support** - Stock firmware BT HID relay, Python daemon ([docs](RG34XX.md)) ✨ *Proof of concept working!*
- [ ] **Wired mode (RP2040-PiZero)** - Dual USB-C, WebSerial, no pairing needed ([docs](WIRED.md))
- [ ] **Android app** - Phone as BLE-to-BT HID bridge (WIP in `android/`)
- [ ] **Android deck mode** - Phone as wireless control deck (see [Deck Mode Architecture](#deck-mode-architecture))
- [ ] **Android PiP mode** - Floating status window like the DIP pendant
- [ ] **Android Companion Device Manager** - Streamlined Bluetooth pairing flow
- [ ] **Android Quick Settings tile** - One-tap start/stop from notification shade
- [ ] **Android multi-host switching** - Pair with multiple PCs, tap/swipe to switch target (software KVM switch)
- [ ] **Android local input modes** - Trackpad mode (touch→mouse), gamepad mode (virtual buttons/sticks), air mouse (gyroscope)
- [ ] **Mobile/responsive design** - Use from phone/tablet

### Medium Priority
- [x] **Screen Wake Lock API** - Prevent controller screen from sleeping while jacked in ✅
- [ ] **Gamepad API** - Use a controller plugged into controller PC as input on target (game streaming without the streaming)
- [ ] **Idle Detection API** - Auto-enable jiggler when user walks away
- [ ] **File System Access API** - Save/load macros and scripts as actual files (not just localStorage)
- [ ] **Web Locks API** - Prevent multiple browser tabs from connecting to same device simultaneously
- [ ] **Chrome extension companion** - Investigate what extras an extension could provide (global hotkeys, keyboard lock without fullscreen, native messaging, clipboard sync)
- [ ] **Keyboard Lock API** - Capture system keys (Alt+Tab, Ctrl+W, Win key) when fullscreen - send them to host instead of controller browser
- [ ] **Latency/ping indicator** - Connection quality feedback
- [ ] **Keyboard layout support** - AZERTY, QWERTZ, etc.
- [ ] **Single-file bundle script** - Inline JS for true single-file deployment
- [ ] **WebRTC video** - Stream from another device/app
- [ ] **Accessibility mode** - Screen-reader friendly minimal interface, ARIA labels, keyboard navigation. Could be separate `accessible.html` with just core functionality
- [ ] **Secrets/credential storage** - Investigate secure storage for macros/scripts. Options: Web Crypto API encryption with master password, WebAuthn biometric unlock, or just accept localStorage is insecure. Simpler idea: `{{prompt:Label}}` placeholder in scripts that asks for input at runtime - no storage needed, useful for passwords/OTPs in login scripts
- [ ] **Script: absolute positioning** - `moveto x,y` command using digitizer (Pico 2W). Also `clickat x,y` to move and click in one command
- [ ] **Script: conditionals & loops** - `if`, `while`, `goto label`, `repeat n` for more complex automation
- [ ] **Script: vision commands** - With video feed: `waitcolor x,y,w,h,color,tolerance` (wait until area matches), `ifcolor` conditional, `alert` when color changes. Simple pixel/region average comparison, not OCR. Useful for "wait until loading spinner gone" or "alert when render done"
- [x] **Quick Launch buttons** - Win+1 through Win+0 shortcuts in DIP pendant for fast app switching ✅
- [ ] **Lock key state feedback** - Host sends LED Output Reports (CapsLock/NumLock/ScrollLock state) to HID devices. Firmware can receive via `tud_hid_set_report_cb`, relay to web UI over BLE. Show indicators in UI/pendant, sync cmd mode with actual CapsLock state

### Fun/Visual
- [ ] **CRT monitor overlay** - Professional broadcast monitor aesthetic during capture (scanlines, phosphor glow, rounded corners, RGB separation, tube curvature simulation)
- [ ] **Retro themes** - Amber/green phosphor CRT looks
- [ ] **Custom overlay layouts** - User-defined status displays

### Hardware Expansion
- [ ] **3D printed cases** - Enclosures for Pico 2W and RP2040-PiZero (design or find existing)
- [ ] **ESP32-S2 support** - Even cheaper option (no BLE, WiFi only)
- [ ] **MicroPython on Cardputer** - Test if MicroPython improves stability vs Arduino (could use MicroHydra launcher)
- [x] **Absolute mouse mode** - Digitizer HID for seamless mode (Pico 2W only) ✅
- [x] **Consumer control** - Volume, mute, play/pause, media keys ✅

### Pico 2W GPIO Expansion (Software Defined KVM)
The Pico 2W has many unused GPIO pins. Future firmware could support user-configurable physical controls:

**Physical Inputs:**
- [ ] **Hardware buttons** - Jack in/out toggle, dedicated macro keys (Ctrl+Alt+Del, etc.)
- [ ] **Rotary encoder** - Mouse/scroll sensitivity adjustment
- [ ] **Potentiometers/sliders** - Analog control for sensitivity
- [ ] **Foot pedal input** - Hands-free jack in/out
- [ ] **Joystick module** - Direct mouse control

**Feedback Outputs:**
- [ ] **RGB LED status** - Connection state, jacked in indicator
- [ ] **Small OLED display** - Status info (SSD1306, etc.)
- [ ] **Buzzer/speaker** - Audio feedback on events

**Vision:** A "RelayKVM Control Box" - 3D printed enclosure with physical buttons, knobs, and status LEDs wired to the Pico. Firmware reports GPIO config via BLE, web UI allows function assignment. True Software Defined KVM where hardware is just a configurable I/O bridge.

**Bidirectional HID (future exploration):**
The Pico 2W's CYW43439 supports BLE HOGP (HID over GATT), enabling it to send HID events back to the *controller* PC - things web APIs can't do:
- Media keys (play/pause, volume) that work globally regardless of browser focus
- System commands (Win+L to lock, sleep, shutdown shortcuts)
- Arbitrary hotkeys to trigger local scripts/apps on the controller

Architecture: Controller PC pairs to Pico via Bluetooth, Pico sends HID notifications. Web UI would have "Target: Host (USB)" vs "Target: Self (BLE)" toggle. Keep it simple for the Pico - this is better suited for a dedicated macropad project with proper buttons and screen, where the complexity is justified. One Pico per host remains the reliable approach for multi-host setups.

### Android Host Management Design

**Problem:** Current UI shows all paired Bluetooth devices (earbuds, watches, etc.) - cluttered and confusing.

**Solution:** Explicit "My Hosts" list with per-host settings.

**Add Host Flow:**
1. Tap "Add Host" → Companion Device Manager dialog (filtered to computers?)
2. User selects PC → System handles pairing
3. Host added to "My Hosts" with default settings
4. Stored in local database (Room or SharedPreferences)

**Per-Host Settings:**
```kotlin
data class SavedHost(
    val address: String,           // BT MAC address (immutable)
    val name: String,              // Original BT device name
    val alias: String?,            // User-defined nickname
    val icon: HostIcon,            // 🖥️💻🖱️🎮 etc.
    val allowAutoConnect: Boolean, // Accept passive connections from this host
    val autoConnectOnLaunch: Boolean, // Connect immediately when app opens
    val priority: Int,             // If multiple hosts try to connect, pick highest
    val defaultInputMode: InputMode, // RELAY, TRACKPAD, GAMEPAD, AIR_MOUSE
    val mouseSensitivity: Float,   // Per-host sensitivity adjustment
    val lastConnected: Long,       // Timestamp for sorting
    val notes: String?             // User notes ("bedroom PC", "needs passive mode", etc.)
)

enum class InputMode { RELAY, TRACKPAD, GAMEPAD, AIR_MOUSE }
enum class HostIcon { DESKTOP, LAPTOP, SERVER, TV, QUEST, TABLET, CUSTOM }
```

**UI Flow:**
```
┌─────────────────────────────────┐
│ RelayKVM              ⚙️ [Theme]│
├─────────────────────────────────┤
│ My Hosts                        │
│ ┌─────────────────────────────┐ │
│ │ 🖥️ Work PC           ●  ⋮  │ │ ← connected (green dot)
│ │ 💻 Gaming Rig            ⋮  │ │
│ │ 📺 Living Room TV        ⋮  │ │
│ │ ➕ Add Host...               │ │
│ └─────────────────────────────┘ │
│                                 │
│ ☑️ Accept incoming connections  │
│                                 │
│ [Disconnect]     Mode: [Relay▾] │
├─────────────────────────────────┤
│ Log                             │
│ [22:30] Connected to Work PC    │
└─────────────────────────────────┘
```

**Host Settings Sheet (tap ⋮):**
```
┌─────────────────────────────────┐
│ Work PC                    Edit │
├─────────────────────────────────┤
│ Alias: Work PC                  │
│ Icon: 🖥️ ▾                      │
│                                 │
│ ○ Connect on app launch         │
│ ○ Accept incoming (passive)     │
│ Priority: [1] (highest first)   │
│                                 │
│ Default mode: [Relay ▾]         │
│ Mouse sensitivity: [====●===]   │
│                                 │
│ Notes:                          │
│ ┌─────────────────────────────┐ │
│ │ Office desktop, USB-C dock  │ │
│ └─────────────────────────────┘ │
│                                 │
│ [Forget Host]      [Connect]    │
└─────────────────────────────────┘
```

**Passive Mode Behavior:**
- On app start (or service start), register HID profile
- Incoming connection from known host with `allowAutoConnect=true` → accept
- Incoming from unknown device → reject (or prompt "Add as new host?")
- Multiple hosts with auto-connect: use `priority` to decide, or just first-come-first-served
- Show notification when passively connected

**Future Ideas:**
- Per-host macros/quick actions
- Per-host theme (work = professional, gaming = RGB)
- Sync settings via BLE from web UI
- QR code to quickly add host (encode BT address + name)
- NFC tap to connect (if host has NFC tag on desk)

### Deck Mode Architecture

The Android app can operate in two modes:

**Standalone Mode (current):**
```
Browser ──BLE──► Phone ──BT HID──► Target PC
```
Phone acts as the relay. No Pico needed. Simple but requires BT pairing on target.

**Deck Mode (planned):**
```
                    ┌──────────────┐
                    │   Browser    │
Phone ──BLE──►      │    (hub)     │ ──BLE──► Pico ──USB HID──► Target PC
  deck commands     └──────────────┘
                          │
                          ▼
                    Local actions
                    (Win+R, macros)
```

Phone becomes a wireless "Stream Deck" - trackpad, button grid, quick actions. Browser connects to BOTH phone and Pico simultaneously (Web Bluetooth supports multiple connections). Phone sends gestures/buttons over BLE, browser routes them:
- Forward to Pico as HID commands
- Execute locally on controller (open apps, trigger scripts)

**Why this topology?**
- Phone stays simple - just a BLE GATT server, same as standalone mode
- No BLE client code needed on phone (Pico doesn't need to accept connections from phone)
- Browser already handles all the routing logic
- Phone is useful even if you have a Pico

**Implementation notes:**
- Nordic UART Service (NUS) is bidirectional - phone can send notifications to browser
- Phone app adds deck UI: trackpad view, button grid (assignable macros)
- Deck mode toggle: gestures go over BLE instead of local BT HID
- Browser listens for NUS RX notifications from phone
- New message types for deck commands (gestures, button IDs, etc.)

**Alternative topology (not pursuing):**
```
Browser ──BLE──► Pico ◄──BLE──── Phone
```
Phone connects directly to Pico. Rejected because:
- Pico would need to handle two BLE connections (central + peripheral)
- More complex firmware
- Phone needs BLE client mode (more code)
- Less flexible routing

### Long Term
- [ ] **Multi-host switching** - Control multiple PCs
- [ ] **Session recording** - Record/replay input sessions
- [ ] **Plugin system** - User-defined modules
- [ ] **ReadTheDocs site** - Proper documentation site (setup guides, API reference, troubleshooting)

### Virtual Display Setup

For seamless mode, the target PC needs a display (real or virtual) that the controller can "extend into". Options:

**Hardware (HDMI/DP Dummy Plugs)**
- Cheap (~$5-10), no drivers, just plug in
- Common brands: Headless Ghost, FUERAN, generic "HDP-V104" style
- Screen Manager auto-detects these by name patterns

**Software (Windows)**
- [Virtual Display Driver by MikeTheTech](https://mikethetech.itch.io/virtual-display-driver) - easiest, GUI toggle, free
- [VirtualDrivers/Virtual-Display-Driver](https://github.com/VirtualDrivers/Virtual-Display-Driver) - HDR support, active development
- [ParsecVDD](https://github.com/nomi-san/parsec-vdd) - up to 4K@240Hz, tray app

**Software (macOS)**
- Built-in support, no extra drivers needed

All software options are free/open-source and based on Windows Indirect Display Driver (IddCx) API.

### Technical Notes & Discoveries 🔬

**Android Bluetooth HID Notes:**
- **Active vs Passive mode**: Active = phone calls `connect()` to reach PC. Passive = phone registers HID profile and waits for PC to initiate. Passive is often more reliable when active fails - tell user "go to PC Bluetooth settings, click the phone."
- **Android-to-Android HID**: Should work! Android devices are HID hosts (accept BT keyboards/mice). Untested targets: tablets, Android TV, Chromebooks, Quest headsets, car head units.
- **Companion Device Manager API**: Streamlines BT pairing - app shows system dialog listing nearby devices, user picks, system handles pairing AND grants per-device permissions. Fewer permission prompts, better UX. Worth implementing.
- **Gyroscope air mouse**: Accelerometer/gyroscope APIs could enable tilt-to-move-cursor like a Wii remote. Fun experiment.
- **Re-pairing issues**: Usually caused by changed HID descriptors (host caches old info). Clean pair from both sides fixes it. Normal users shouldn't hit this often.

**Digitizer + Relative Mouse Coexistence:**
- When a digitizer (absolute mouse) is active and sending "In Range", the OS ignores button state from relative mouse
- However, scroll wheel events from relative mouse ARE still processed
- Theory: Scroll = event/delta (aggregated from all devices), Buttons = state (exclusive to "active" pointer)
- Solution: Send clicks via digitizer in seamless mode, scroll can still go through relative mouse

### Cursed Ideas (Rainy Day Projects) 🌧️
Ideas that are wildly out of scope but too fun to forget. For when it's raining in São Paulo.

#### High Feasibility (we already have most of the pieces)

- [ ] **Pendant Forth interpreter** ⭐ - CapsLock in calc mode activates Forth. Stack already exists, just need a dictionary of words and `:` to define new ones. `2 3 + .` prints 5. `: SQUARE DUP * ;` defines a word. Could save words to localStorage. Genuine utility for automation macros. The pendant becomes a tiny programmable computer.
- [ ] **Pendant plotting** - Trackpad becomes a tiny graph (~93×105px, almost square!). Enter RPN expression with X variable, set XMIN/XMAX/YMIN/YMAX, plot. HP-48 style. Trackpad color was chosen for this! Show resolution in corner when resizing DIP. Move CLx from X key to D (or just use Delete), free X for variable. Keys: `X` push X var, `[` `]` for xmin/xmax, `{` `}` for ymin/ymax, `G` for graph/plot.
- [ ] **Stereonet mode** ⭐ - Equal-area (Schmidt) or equal-angle (Wulff) stereonet projection in the trackpad! For structural geology: plot poles, planes, lineations. Enter strike/dip from numpad, plot on net. Could toggle between lower/upper hemisphere. Tiny geology workstation in a pendant. Peak niche. Peak awesome.
- [ ] **Trig functions** - sin/cos/tan + inverses, log/exp, DEG/RAD toggle. Just more rpnUnaryOp() calls. Needed for stereonet math anyway!
- [ ] **Memory registers** - STO 0-9 / RCL 0-9 for storing intermediate results. Classic HP feature.

#### Medium Feasibility

- [ ] **Tiny BASIC** - `10 PRINT "HELLO"` / `20 GOTO 10` in the pendant. Line editor in LCD, RUN executes and sends keystrokes to host. BASIC as a macro language!
- [ ] **Conway's Game of Life** - Trackpad becomes a tiny universe. Click to toggle cells, watch it evolve. Meditative.
- [ ] **Befunge interpreter** - 2D esoteric programming language. Program counter moves in 4 directions. Perfect for tiny display. Absolutely cursed.
- [ ] **Fractals** - Mandelbrot/Julia zoom in trackpad area using canvas or CSS. Arrow keys to pan, +/- to zoom. No practical use, pure beauty.

#### Classic Cursed

- [ ] **Web MIDI relay** - Relay MIDI messages to target for music production remote setups
- [ ] **Web HID wired mode** - Use Web HID API to build wired version entirely in-browser (no firmware needed?)
- [ ] **VIA keyboard support** - Pretend to be a VIA-compatible keyboard, configure Cardputer keypad through VIA GUI, save to flash, sync layout to web UI. Basically QMK-lite for RelayKVM.
- [ ] **Aesthetic relay** - Wire an actual relay to the Pico GPIO that clicks on jack in/out. No electrical function, pure satisfaction. The "Relay" in RelayKVM becomes literal.
- [ ] **Full QMK fork** - At this point, why not?
- [ ] **Bluetooth Classic HID** - Make the controller PC think RelayKVM is a regular BT keyboard/mouse (would need ESP32 Classic BT, not just BLE)
- [ ] **Onboard scripting** - Lua/MicroPython interpreter on the device for offline automation
- [ ] **KVM-over-IP** - WebRTC relay for controlling PCs across the internet
- [ ] **WebRTC DataChannel** - Lightweight P2P data channel on Pico/ESP32? Skip the BLE middleman, direct browser-to-device
- [ ] **BT Serial TTY (Linux)** - Bluetooth SPP to /dev/rfcomm0, daemon injects to /dev/uinput. No USB on host!
- [ ] **Audio chirp KVM** - FSK/AFSK modulation over 3.5mm audio jack. For MCUs with USB but no wireless. Host demodulates → HID. Peak cursed.
- [ ] **Dual-host wired KVM** - RP2040-PiZero with USB HID to BOTH computers simultaneously. Software selects which host receives input. Hardware macropad buttons can target either host. True Software Defined KVM switch.
- [ ] **LED backchannel** - Use LED Output Report bits (ScrollLock) as host→device data channel. Helper app on host toggles ScrollLock, Pico reads state changes. Enables host→device communication without serial/BLE on host side. Could send screen resolution, PeerJS ID for WebRTC, simple state sync. ~200 bps with 1 bit.
- [ ] **WebHID host helper** - Add 4th HID interface (vendor-specific raw data). Host helper page uses WebHID API to communicate with Pico directly. No serial, no drivers, just browser permission. Could send screen resolution, clipboard text (for bridge), window info, WebRTC signaling. Like QMK's Raw HID / VIA protocol.
- [ ] **BLE security via USB bootstrap** - Use physical USB connection as trusted channel for key exchange. Plug Pico into controller, page generates shared key, sends via WebHID, both save it. BLE connections then require challenge-response with the key. No passkeys, no morse code, just plug-and-pair. OOB pairing using USB as secure channel.
- [ ] **SECURITY.md** - Document the security model: threat model (what we protect against, what we don't), USB key exchange, challenge-response auth, encrypted traffic, mutual authentication. Be honest about limitations. Preempt the Hackaday comments.

---

## Completed

### v1.2.0 (2025)
- **Seamless mode** - Mouse flows between controller and target PCs (Pico 2W only)
- Absolute mouse positioning (digitizer HID)
- Portal window for virtual monitor capture
- PWA badge indicator when jacked in
- Document DIP pendant (always-on-top status display)
- Clipboard bridge (CapsLock+P to type controller clipboard on target)
- Screen Wake Lock API
- Keyboard Lock API (fullscreen) - Capture system keys (Alt+Tab, Escape, Win key)
- Screen Manager (Window Management API) - Configure portal screen position with visual map

### v1.1.0 (2025)
- Raspberry Pi Pico 2W support (MicroPython)
- Media keys (volume, mute, play/pause, etc.)
- Fn Layer toggle (F13-F24)
- RG34XX SP proof of concept

### v1.0.0 (2024)
- Initial release
- Video capture support
- CapsLock command mode
- Theme system with custom themes
- Settings export/import
- GitHub Actions CI for firmware builds
