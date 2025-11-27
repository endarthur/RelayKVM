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
- [ ] **Anbernic RG34XX SP support** - Stock firmware BT HID relay, Python daemon ([docs](RG34XX.md)) ✨ *Proof of concept working!*
- [ ] **Wired mode (RP2040-PiZero)** - Dual USB-C, WebSerial, no pairing needed ([docs](WIRED.md))
- [ ] **Raspberry Pi Pico 2W support** - Cheaper wireless dongle option
- [ ] **Android app** - Phone as BLE-to-BT HID bridge
- [ ] **Mobile/responsive design** - Use from phone/tablet

### Medium Priority
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
- [ ] **Absolute mouse mode** - For VNC-style control
- [x] **Consumer control** - Volume, mute, play/pause, media keys ✅

### Long Term
- [ ] **Multi-host switching** - Control multiple PCs
- [ ] **Session recording** - Record/replay input sessions
- [ ] **Plugin system** - User-defined modules

---

## Completed

### v1.0.0 (2024)
- Initial release
- Video capture support
- CapsLock command mode
- Theme system with custom themes
- Settings export/import
- GitHub Actions CI for firmware builds
