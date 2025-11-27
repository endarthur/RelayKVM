# RelayKVM

KVM solution using microcontrollers as USB HID bridges. Supports wireless (Bluetooth) and wired (dual USB) modes.

## Architecture

**Wireless (BLE to USB HID):**
```
Browser (Controller PC) --[Web Bluetooth]--> Cardputer/Pico 2W --[USB HID]--> Host PC
```

**Wireless (BLE to BT HID):**
```
Browser (Controller PC) --[Web Bluetooth]--> RG34XX/Android --[BT HID]--> Host PC
```

**Wired (Dual USB):**
```
Browser (Controller PC) --[WebSerial]--> RP2040-PiZero --[USB HID]--> Host PC
```

## Hardware Platforms

| Platform | Connection | Status |
|----------|------------|--------|
| M5Stack Cardputer | BLE → USB HID | ✅ Working |
| Raspberry Pi Pico 2W | BLE → USB HID | ✅ Working ([firmware](firmware/pico2w/)) |
| Anbernic RG34XX SP | BLE → BT HID | 🧪 PoC Working ([docs](docs/RG34XX.md)) |
| Waveshare RP2040-PiZero | WebSerial → USB HID | 🚧 Planned ([docs](docs/WIRED.md)) |
| Android App | BLE → BT HID | 🚧 Planned |

- **Web interface** (`index.html`): Captures keyboard/mouse, sends via BLE or WebSerial
- **Firmware** (`firmware/RelayKVM/`): ESP32-S3 Arduino sketch (Cardputer)
- **Protocol**: Modified NanoKVM HID protocol over BLE UART or USB Serial

## Key Files

| File | Purpose |
|------|---------|
| `index.html` | Main web interface (single-file, self-contained) |
| `relaykvm-adapter.js` | BLE communication adapter |
| `firmware/RelayKVM/RelayKVM.ino` | Main firmware sketch (Cardputer) |
| `firmware/pico2w/code.py` | CircuitPython firmware (Pico 2W) |
| `firmware/platformio.ini` | PlatformIO build config |
| `docs/WIRED.md` | Wired mode documentation (RP2040-PiZero) |
| `docs/RG34XX.md` | Anbernic RG34XX SP setup guide |
| `docs/FEATURES.md` | Features and roadmap |
| `icons/icon.svg` | Logo (overlapping teal/coral rectangles) |

## Build Commands

**IMPORTANT:** Always use the `m5launcher` environment. **NEVER use direct upload/flash** (`-t upload`) - this crashes the Cardputer.

```bash
cd firmware
pio run -e m5launcher              # Build only
```

**Deployment workflow:**
1. Build with `pio run -e m5launcher`
2. Copy `.pio/build/m5launcher/RelayKVM.bin` to SD card
3. Install via M5Launcher on the device

The m5launcher build includes correct partition scheme and OTA support for returning to M5Launcher menu.

## Web Interface Features

- Full theming system with CSS variables (10 themes including Catppuccin)
- Theme selector in top-right corner
- Compact vertical LED indicators (L/T/R for Link/TX/RX)
- Modules: Text input, special keys, mouse pad, jiggler, wake/power, macros, scripts

## USB Identifiers

| Mode | VID | PID | Product Name |
|------|-----|-----|--------------|
| HID | `0xFEED` | `0xAE01` | RelayKVM Controller |
| MSC | `0xFEED` | `0xAE02` | RelayKVM SD Card |
| CDC Serial | `0xFEED` | `0xAE03` | RelayKVM Serial |

## Known Issues & Solutions

- **USB disconnects on Windows after idle**: Firmware has USB keepalive (Mouse.move(0,0,0) every 30s). If still happening, disable USB Selective Suspend in Windows power settings.
- **SD Card mode**: Hold 'M' during boot to enter Mass Storage mode.

## Dependencies

- Arduino framework for ESP32-S3
- NimBLE-Arduino (BLE stack)
- M5Stack libraries (display, keyboard)
- TinyUSB (USB HID/MSC)

## License

GPL v3 (inherited from NanoKVM protocol)
