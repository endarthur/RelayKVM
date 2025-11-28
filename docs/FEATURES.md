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
- M5Stack Cardputer support
- Display brightness control
- SD card mass storage mode
- USB keepalive for Windows

---

## Roadmap

### High Priority
- [x] **Raspberry Pi Pico 2W support** - MicroPython BLE dongle ([firmware](../firmware/pico2w/)) ✅
- [ ] **Anbernic RG34XX SP support** - Stock firmware BT HID relay, Python daemon ([docs](RG34XX.md)) ✨ *Proof of concept working!*
- [ ] **Wired mode (RP2040-PiZero)** - Dual USB-C, WebSerial, no pairing needed ([docs](WIRED.md))
- [ ] **Android app** - Phone as BLE-to-BT HID bridge
- [ ] **Mobile/responsive design** - Use from phone/tablet

### Medium Priority
- [ ] **Keyboard Lock API** - Capture system keys (Alt+Tab, Ctrl+W, Win key) when fullscreen - send them to host instead of controller browser
- [ ] **Latency/ping indicator** - Connection quality feedback
- [ ] **Keyboard layout support** - AZERTY, QWERTZ, etc.
- [ ] **Single-file bundle script** - Inline JS for true single-file deployment
- [ ] **WebRTC video** - Stream from another device/app

### Fun/Visual
- [ ] **CRT monitor overlay** - Professional broadcast monitor aesthetic during capture (scanlines, phosphor glow, rounded corners, RGB separation, tube curvature simulation)
- [ ] **Retro themes** - Amber/green phosphor CRT looks
- [ ] **Custom overlay layouts** - User-defined status displays

### Hardware Expansion
- [ ] **3D printed cases** - Enclosures for Pico 2W and RP2040-PiZero (design or find existing)
- [ ] **ESP32-S2 support** - Even cheaper option (no BLE, WiFi only)
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

### Long Term
- [ ] **Multi-host switching** - Control multiple PCs
- [ ] **Session recording** - Record/replay input sessions
- [ ] **Plugin system** - User-defined modules

---

## Completed

### v1.2.0 (2025)
- **Seamless mode** - Mouse flows between controller and target PCs (Pico 2W only)
- Absolute mouse positioning (digitizer HID)
- Portal window for virtual monitor capture
- PWA badge indicator when jacked in

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
