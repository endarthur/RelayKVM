# RelayKVM

> **Named after geological relay ramps** - structures that accommodate displacement between fault segments. This device "relays" HID input between your computers, bridging the gap wirelessly.

Wireless KVM solution using ESP32-S3 (M5Stack Cardputer) as a Bluetooth-to-USB HID bridge. Control any computer's keyboard and mouse from a web browser over Bluetooth.

## How It Works

```
┌─────────────┐     Bluetooth      ┌─────────────┐      USB HID      ┌─────────────┐
│  Browser    │ ──────────────────>│  Cardputer  │ ─────────────────>│  Host PC  │
│  (Any PC)   │   Web Bluetooth    │  ESP32-S3   │  Keyboard/Mouse   │             │
└─────────────┘                    └─────────────┘                    └─────────────┘
```

**Controller PC**: Opens the web interface, captures keyboard/mouse input
**Cardputer**: Receives commands via Bluetooth, sends USB HID to host
**Host PC**: Sees a standard USB keyboard and mouse

## Features

- **Web Bluetooth interface** - Works in Chrome/Edge, no software install needed
- **Full keyboard support** - All keys including modifiers, media keys
- **Mouse support** - Relative movement, scroll, all buttons
- **Capture mode** - Lock mouse/keyboard to the host
- **Macros** - Save and replay key sequences
- **Scripts** - Multi-step automation (type, delay, key combos)
- **Mouse jiggler** - Prevent screen lock
- **Wake/Sleep** - USB wake signal + Windows sleep macro
- **Display control** - Dim/off the Cardputer screen remotely
- **Mass Storage mode** - Hold 'M' at boot to access SD card

## Hardware

**Required:**
- [M5Stack Cardputer](https://shop.m5stack.com/products/m5stack-cardputer-kit-w-m5stamps3) ($40) - ESP32-S3 with screen, keyboard, battery
- USB-C cable

**Optional:**
- MicroSD card - For storing web interface offline
- USB HDMI capture card ($15) - For video feedback

**Alternatives:** Any ESP32-S3 board with USB OTG should work, though we only tested with the M5Stack Cardputer v1.1. See [docs/PICOW.md](docs/PICOW.md) for Raspberry Pi Pico W option.

## Quick Start

### 1. Flash Firmware

```bash
cd firmware
pio run -e m5launcher -t upload
```

Or download pre-built `.bin` from [Releases](https://github.com/endarthur/RelayKVM/releases) and flash via M5Launcher.

> **Important:** Always use the `m5launcher` environment (`-e m5launcher`). Do NOT use direct upload without specifying the environment. The m5launcher build includes the correct partition scheme and OTA support for returning to the M5Launcher menu.

### 2. Connect Hardware

1. Plug Cardputer into **host PC** via USB-C
2. Host PC recognizes "RelayKVM Controller" as keyboard/mouse

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
| **Jiggler** | Prevent screen timeout |
| **Wake/Power** | Wake from sleep, sleep macro |
| **Macros** | Quick key combinations |
| **Scripts** | Multi-step automation |
| **Cardputer** | Display brightness, connection status |

## SD Card Mode

Hold **'M'** during boot to enter Mass Storage mode:

1. Cardputer mounts as USB drive
2. Copy `index.html` and `relaykvm-adapter.js` to SD card
3. Reset to return to normal mode
4. Now you can use the interface offline from any PC!

## Directory Structure

```
RelayKVM/
├── index.html              # Web interface (GitHub Pages root)
├── relaykvm-adapter.js     # BLE communication adapter
├── firmware/               # ESP32-S3 Arduino firmware
│   ├── RelayKVM/           # Main sketch
│   └── platformio.ini      # Build config
└── docs/                   # Documentation
    ├── SETUP.md            # Detailed setup guide
    ├── FEATURES.md         # Roadmap
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
