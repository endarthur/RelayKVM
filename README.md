# RelayKVM

> **Named after geological relay ramps** - structures that accommodate displacement between fault segments. This device "relays" HID input between your computers, bridging the gap wirelessly.

Wireless KVM solution using a tiny BLE-to-USB HID bridge. Control any computer's keyboard and mouse from a web browser over Bluetooth. No software install on the target - it just sees a standard USB keyboard and mouse.

## How It Works

```
┌─────────────┐     Bluetooth      ┌─────────────┐      USB HID      ┌─────────────┐
│  Browser    │ ──────────────────>│  Pico 2W /  │ ─────────────────>│  Target PC  │
│  (Any PC)   │   Web Bluetooth    │  Cardputer  │  Keyboard/Mouse   │             │
└─────────────┘                    └─────────────┘                    └─────────────┘
```

**Controller PC**: Opens the web interface, captures keyboard/mouse input
**BLE Device**: Receives commands via Bluetooth, sends USB HID to target
**Target PC**: Sees a standard USB keyboard and mouse

## Features

- **Web Bluetooth interface** - Works in Chrome/Edge, no software install needed
- **Full keyboard support** - All keys including modifiers, media keys
- **Mouse support** - Relative movement, scroll, all buttons
- **Capture mode** - Lock mouse/keyboard to the host
- **Video capture** - USB capture card support with fullscreen display
- **CapsLock command mode** - 20+ shortcuts for quick actions while captured
- **Macros** - Save and replay key sequences
- **Scripts** - Multi-step automation (type, delay, key combos)
- **Mouse jiggler** - Prevent screen lock
- **Wake/Sleep** - USB wake signal + Windows sleep macro
- **Display control** - Dim/off the Cardputer screen remotely (Cardputer only)
- **Theming** - 10 built-in themes + 5 custom slots with JSON editor
- **Settings** - Export/import configuration, all preferences in one modal
- **Mass Storage mode** - Hold 'M' at boot to access SD card (Cardputer only)
- **Seamless mode** - Mouse flows between controller and target PCs (Pico 2W only)

## Seamless Mode

Seamless mode allows your mouse to flow naturally between your controller PC and the target PC, similar to how multi-monitor setups work. This requires:

1. **Pico 2W device** - Cardputer does not support absolute mouse positioning
2. **Dummy HDMI plug** - Creates a virtual monitor on your controller PC
3. **Portal window** - Drag to the virtual monitor, click to activate

**How it works:**
- The portal window represents the target PC's screen on your controller
- Move your mouse onto the portal to control the target
- Move it off to return to your controller
- Uses absolute mouse positioning (digitizer HID) for accurate cursor placement

> **Note:** Seamless mode only works with the **Raspberry Pi Pico 2W** firmware. The M5Stack Cardputer's USB stack doesn't support custom HID descriptors required for absolute mouse positioning. See [docs/PICOW.md](docs/PICOW.md) for Pico 2W setup.

## Hardware

**Raspberry Pi Pico 2W** ⭐ *Recommended*
- ~$10, tiny, reliable
- MicroPython firmware - rock solid stability
- Supports seamless mode (absolute mouse/digitizer)
- "Set it and forget it" - just works
- See [docs/PICOW.md](docs/PICOW.md) for setup

**M5Stack Cardputer** - *Feature-rich alternative*
- ~$40 - ESP32-S3 with screen, keyboard, battery
- Built-in display shows connection status
- SD card for offline web interface storage
- Good for development and demos
- May need occasional reset

**Optional:**
- USB HDMI capture card (~$15) - For video feedback from target
- Dummy HDMI plug (~$5) - Required for seamless mode

## Quick Start

### 1. Flash Firmware

**Pico 2W (recommended):**
```bash
# Copy MicroPython firmware (one-time)
# Hold BOOTSEL, plug in, copy .uf2 to RPI-RP2 drive

# Then copy RelayKVM script
mpremote cp firmware/pico2w/main.py :main.py
```

See [docs/PICOW.md](docs/PICOW.md) for detailed Pico 2W setup.

**Cardputer:**
```bash
cd firmware
pio run -e m5launcher
# Copy .bin to SD card, flash via M5Launcher
```

> **Note:** For Cardputer, always use the `m5launcher` environment. The build includes correct partition scheme and OTA support for returning to M5Launcher.

### 2. Connect Hardware

1. Plug device into **target PC** via USB-C
2. Target PC recognizes "RelayKVM Controller" as keyboard/mouse

### 3. Open Web Interface

**Online:** Go to [endarthur.github.io/RelayKVM](https://endarthur.github.io/RelayKVM)

**Offline:** Open `index.html` in Chrome/Edge on your **controller PC**

Click **Connect** and select "RelayKVM" - start controlling the host!

## Web Interface

The industrial-style control panel includes:

| Module | Function |
|--------|----------|
| **Text Input** | Type text directly to host |
| **Special Keys** | F-keys, navigation, media controls |
| **Mouse** | Click-to-move pad, sensitivity control |
| **Video** | USB capture card display, fullscreen mode |
| **Jiggler** | Prevent screen timeout |
| **Wake/Power** | Wake from sleep, sleep macro |
| **Macros** | Quick key combinations |
| **Scripts** | Multi-step automation |
| **Device** | Display brightness (Cardputer), connection status |
| **Sensitivity** | Mouse and scroll speed sliders |
| **Settings** (gear icon) | Themes, custom themes, input options, export/import |

## SD Card Mode (Cardputer only)

Hold **'M'** during boot to enter Mass Storage mode:

1. Cardputer mounts as USB drive
2. Copy `index.html` and `relaykvm-adapter.js` to SD card
3. Reset to return to normal mode
4. Use the interface offline from any PC!

## Directory Structure

```
RelayKVM/
├── index.html              # Web interface (GitHub Pages root)
├── portal.html             # Seamless mode portal window
├── relaykvm-adapter.js     # BLE communication adapter
├── firmware/
│   ├── pico2w/             # Pico 2W MicroPython firmware
│   │   └── main.py
│   ├── RelayKVM/           # Cardputer Arduino firmware
│   └── platformio.ini      # Cardputer build config
├── icons/                  # Project icons (SVG + PNGs)
├── .github/workflows/      # CI/CD (auto-build on release)
└── docs/
    ├── PICOW.md            # Pico 2W setup guide
    ├── FEATURES.md         # Features & roadmap
    └── ...
```

## USB Identifiers

| Mode | VID | PID | Product Name |
|------|-----|-----|--------------|
| HID | `0xFEED` | `0xAE01` | RelayKVM Controller |
| MSC | `0xFEED` | `0xAE02` | RelayKVM SD Card |

Planning to register with [pid.codes](https://pid.codes/) once stable.

## Protocol

Uses a modified version of the [NanoKVM](https://github.com/sipeed/NanoKVM) HID protocol over BLE UART (Nordic UART Service). Custom commands added for display control, wake signals, etc.

## Acknowledgments

- **[NanoKVM](https://github.com/sipeed/NanoKVM)** by Sipeed - Original HID protocol design (GPL v3)
- **[NimBLE-Arduino](https://github.com/h2zero/NimBLE-Arduino)** - Bluetooth Low Energy stack
- **[M5Stack](https://m5stack.com/)** - Cardputer hardware and libraries
- **[Claude Code](https://claude.ai/claude-code)** - AI-assisted development

## License

GNU General Public License v3.0 - See [LICENSE](LICENSE)

This project uses the NanoKVM protocol which is also GPL v3 licensed.

## Contributing

Contributions welcome! Priority areas:
- Android app (Bluetooth HID device mode)
- Additional hardware platform support
- Video streaming integration

## Author

Arthur Endlein ([@endarthur](https://github.com/endarthur))
