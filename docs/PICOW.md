# Raspberry Pi Pico W for RelayKVM

The **Raspberry Pi Pico W** is an excellent, budget-friendly alternative to ESP32-S3 for RelayKVM. At $6, it's the cheapest option and uses CircuitPython for easy development.

## Why Pico W Works (Correction!)

I initially stated Pico W "doesn't have USB OTG" - this was **misleading**. Here's the clarification:

| Capability | Pico W | Needed for RelayKVM? |
|------------|--------|----------------------|
| **USB Device Mode** (act as HID) | ✅ **YES** | ✅ **YES** (this is what we need!) |
| **USB Host Mode** (connect devices to it) | ❌ No | ❌ No (we don't need this) |

**Bottom line:** Pico W CAN be a USB keyboard/mouse, which is exactly what RelayKVM needs!

## Hardware Specs

**Raspberry Pi Pico W:**
- **MCU:** RP2040 (Dual Cortex-M0+ @ 133MHz)
- **RAM:** 264KB SRAM
- **Flash:** 2MB
- **USB:** Micro-USB, supports USB 1.1 Device mode
- **Wireless:** WiFi 802.11n (2.4GHz) + Bluetooth 5.2 (BLE only)
- **GPIO:** 26 pins, 3 ADC
- **Cost:** $6 USD

**Pico 2 W (newer, faster):**
- **MCU:** RP2350 (Dual Cortex-M33 @ 150MHz)
- **RAM:** 520KB SRAM
- **Flash:** 4MB
- **Same wireless:** WiFi + BLE
- **Cost:** $7 USD

## Architecture

### Option 1: WiFi-Based (Simpler)

```
Controller PC (web browser)
    │
    └─ HTTP/WebSocket over WiFi ─> Pico W
                                     │
                                     └─ USB HID ─> Host PC
```

**Advantages:**
- ✅ No Bluetooth pairing needed
- ✅ Easier to debug (HTTP logs)
- ✅ Works with any WiFi-enabled controller

**Disadvantages:**
- ❌ Requires WiFi network
- ❌ Less secure than Bluetooth (unless using HTTPS)

---

### Option 2: BLE-Based (Closer to ESP32-S3)

```
Controller PC (web browser)
    │
    └─ Web Bluetooth (BLE) ─> Pico W
                               │
                               └─ USB HID ─> Host PC
```

**Advantages:**
- ✅ Similar to ESP32-S3 approach
- ✅ More secure (Bluetooth pairing)
- ✅ No WiFi network needed

**Disadvantages:**
- ❌ BLE only (not Bluetooth Classic)
- ❌ Web Bluetooth API limitations

**Note:** Pico W has Bluetooth 5.2 **BLE only**, not Bluetooth Classic. This means it can work with Web Bluetooth API but not with traditional Bluetooth HID profile.

---

## Implementation: CircuitPython

CircuitPython makes USB HID incredibly easy compared to ESP32-S3 TinyUSB.

### Installation

**1. Install CircuitPython:**
```bash
# Download CircuitPython for Pico W from:
# https://circuitpython.org/board/raspberry_pi_pico_w/

# Hold BOOTSEL button while plugging in Pico W
# Drag .uf2 file to RPI-RP2 drive
# Pico reboots as CIRCUITPY drive
```

**2. Install libraries:**
```bash
# Download CircuitPython library bundle:
# https://circuitpython.org/libraries

# Copy these folders to CIRCUITPY/lib/:
cp adafruit_hid/ /Volumes/CIRCUITPY/lib/
cp adafruit_httpserver/ /Volumes/CIRCUITPY/lib/  # For WiFi option
```

---

### Option 1 Implementation: WiFi + HTTP Server

**File: `code.py` (runs on boot)**

```python
"""
RelayKVM for Raspberry Pi Pico W
WiFi + HTTP Server implementation
"""

import board
import usb_hid
from adafruit_hid.keyboard import Keyboard
from adafruit_hid.mouse import Mouse
from adafruit_hid.keycode import Keycode
import wifi
import socketpool
import time

# Initialize USB HID devices
keyboard = Keyboard(usb_hid.devices)
mouse = Mouse(usb_hid.devices)

# WiFi credentials (update these!)
SSID = "YourWiFiSSID"
PASSWORD = "YourWiFiPassword"

# Connect to WiFi
print("Connecting to WiFi...")
wifi.radio.connect(SSID, PASSWORD)
print(f"Connected! IP: {wifi.radio.ipv4_address}")

# Create socket pool
pool = socketpool.SocketPool(wifi.radio)
server = pool.socket(pool.AF_INET, pool.SOCK_STREAM)
server.bind(('0.0.0.0', 8080))
server.listen(1)

print("RelayKVM server listening on port 8080")
print(f"Connect to: http://{wifi.radio.ipv4_address}:8080")

# NanoKVM protocol constants
HEAD1 = 0x57
HEAD2 = 0xAB
CMD_SEND_KB_GENERAL_DATA = 0x02
CMD_SEND_MS_REL_DATA = 0x05

def parse_nanokvm_packet(data):
    """Parse NanoKVM protocol packet"""
    if len(data) < 6:
        return None

    if data[0] != HEAD1 or data[1] != HEAD2:
        return None

    cmd = data[3]
    length = data[4]
    payload = data[5:5+length]

    return {'cmd': cmd, 'payload': payload}

def handle_keyboard(payload):
    """Handle keyboard command"""
    if len(payload) < 8:
        return

    # Payload format: [modifier, reserved, key1-key6]
    modifier = payload[0]
    keys = payload[2:8]

    # Press modifier keys
    if modifier & 0x01:  # Left Ctrl
        keyboard.press(Keycode.LEFT_CONTROL)
    if modifier & 0x02:  # Left Shift
        keyboard.press(Keycode.LEFT_SHIFT)
    if modifier & 0x04:  # Left Alt
        keyboard.press(Keycode.LEFT_ALT)
    if modifier & 0x08:  # Left GUI (Win/Cmd)
        keyboard.press(Keycode.LEFT_GUI)

    # Press regular keys
    for key in keys:
        if key != 0:
            keyboard.press(key)

    # Release all
    keyboard.release_all()

def handle_mouse(payload):
    """Handle mouse movement"""
    if len(payload) < 4:
        return

    buttons = payload[0]
    dx = payload[1] if payload[1] < 128 else payload[1] - 256  # Convert to signed
    dy = payload[2] if payload[2] < 128 else payload[2] - 256
    wheel = payload[3] if payload[3] < 128 else payload[3] - 256

    # Handle buttons
    if buttons & 0x01:  # Left click
        mouse.press(Mouse.LEFT_BUTTON)
    if buttons & 0x02:  # Right click
        mouse.press(Mouse.RIGHT_BUTTON)
    if buttons & 0x04:  # Middle click
        mouse.press(Mouse.MIDDLE_BUTTON)

    # Move mouse
    if dx != 0 or dy != 0:
        mouse.move(dx, dy, wheel)

    # Release buttons
    mouse.release_all()

# Main server loop
while True:
    try:
        client, addr = server.accept()
        print(f"Client connected from {addr}")

        while True:
            data = client.recv(1024)
            if not data:
                break

            # Parse NanoKVM packet
            packet = parse_nanokvm_packet(data)
            if packet:
                if packet['cmd'] == CMD_SEND_KB_GENERAL_DATA:
                    handle_keyboard(packet['payload'])
                elif packet['cmd'] == CMD_SEND_MS_REL_DATA:
                    handle_mouse(packet['payload'])

                # Send OK response
                client.send(b'OK\n')

        client.close()
        print("Client disconnected")

    except Exception as e:
        print(f"Error: {e}")
        time.sleep(1)
```

**Usage:**
```
1. Save code.py to CIRCUITPY drive
2. Update WiFi credentials
3. Reboot Pico W (unplug/replug)
4. Note IP address from serial console
5. Configure web interface to connect to http://<IP>:8080
```

---

### Option 2 Implementation: BLE + Nordic UART

**File: `code_ble.py`**

```python
"""
RelayKVM for Raspberry Pi Pico W
BLE + Nordic UART implementation
"""

import board
import usb_hid
from adafruit_hid.keyboard import Keyboard
from adafruit_hid.mouse import Mouse
import _bleio
from adafruit_ble import BLERadio
from adafruit_ble.advertising.standard import ProvideServicesAdvertisement
from adafruit_ble.services.nordic import UARTService

# Initialize USB HID
keyboard = Keyboard(usb_hid.devices)
mouse = Mouse(usb_hid.devices)

# Initialize BLE
ble = BLERadio()
uart = UARTService()
advertisement = ProvideServicesAdvertisement(uart)

print("RelayKVM BLE starting...")
ble.name = "RelayKVM"

# NanoKVM protocol parser (same as WiFi version)
# ... (copy from above)

# Main BLE loop
while True:
    print("Advertising as 'RelayKVM'...")
    ble.start_advertising(advertisement)

    while not ble.connected:
        pass

    print("Connected!")
    ble.stop_advertising()

    while ble.connected:
        if uart.in_waiting:
            data = uart.read(uart.in_waiting)
            packet = parse_nanokvm_packet(data)

            if packet:
                if packet['cmd'] == CMD_SEND_KB_GENERAL_DATA:
                    handle_keyboard(packet['payload'])
                elif packet['cmd'] == CMD_SEND_MS_REL_DATA:
                    handle_mouse(packet['payload'])

                # Send response
                uart.write(b'OK\n')

    print("Disconnected")
```

**Note:** BLE on Pico W uses `adafruit_ble` library, simpler than ESP32-S3 NimBLE.

---

## Comparison: Pico W vs ESP32-S3

| Feature | Pico W | ESP32-S3 |
|---------|--------|----------|
| **Cost** | $6 | $7-40 |
| **Language** | CircuitPython (easy!) | C++ (complex) |
| **USB HID** | ✅ Simple (adafruit_hid) | ✅ Complex (TinyUSB) |
| **Wireless** | WiFi + BLE | BLE only |
| **Performance** | 133MHz dual-core | 240MHz dual-core |
| **RAM** | 264KB | 512KB |
| **Development** | Drag-drop .py files | Compile + flash |
| **Debugging** | Serial REPL (easy) | Serial monitor |
| **Community** | Huge CircuitPython | Large Arduino |
| **Screen/Keyboard** | None (bare board) | Cardputer has both |

**Winner for ease:** Pico W (CircuitPython is much simpler!)
**Winner for features:** ESP32-S3 Cardputer (built-in screen/keyboard)
**Winner for cost:** Pico W ($6 vs $7+ bare ESP32-S3)

---

## USB Wake on Pico W

**Can Pico W wake host PC from sleep?**

CircuitPython doesn't directly expose USB remote wakeup API, but you can implement it in C:

```c
// Custom C module for CircuitPython
#include "tusb.h"

bool pico_usb_wake(void) {
    return tud_remote_wakeup();
}
```

**Easier alternative:** Use Pi Pico SDK (C/C++) instead of CircuitPython if USB wake is critical.

---

## Advantages of Pico W

1. **Cheapest option:** $6 (vs $7+ ESP32-S3, $40 Cardputer)
2. **Easiest development:** CircuitPython drag-drop, no compilation
3. **Great documentation:** CircuitPython has excellent tutorials
4. **WiFi option:** Can use WiFi instead of BLE (more flexible)
5. **Live debugging:** REPL allows testing code interactively
6. **Mature HID library:** `adafruit_hid` is very polished

---

## Disadvantages of Pico W

1. **No built-in screen/keyboard** (bare board only)
2. **USB wake harder** (not exposed in CircuitPython)
3. **BLE only** (no Bluetooth Classic HID profile)
4. **Less powerful** (133MHz vs 240MHz ESP32-S3)
5. **Micro-USB** (vs USB-C on ESP32-S3 boards)

---

## When to Use Pico W

**Best for:**
- Learning/prototyping (CircuitPython is easier)
- Budget builds ($6 is unbeatable)
- WiFi-based setups (easier than BLE)
- Python developers (familiar syntax)

**Not ideal for:**
- Needing built-in display (use Cardputer)
- USB wake critical (use ESP32-S3)
- Want traditional Bluetooth HID (Pico W is BLE only)

---

## Quick Start Guide

### 1. Hardware Setup

```
Buy: Raspberry Pi Pico W ($6)
Buy: Micro-USB cable
Optional: Breadboard + LED for status indicator
```

### 2. Install CircuitPython

```bash
# Download from: https://circuitpython.org/board/raspberry_pi_pico_w/
# Latest version: 9.x

# Install:
1. Hold BOOTSEL button on Pico W
2. Plug into computer (still holding BOOTSEL)
3. Release BOOTSEL - appears as RPI-RP2 drive
4. Drag .uf2 file to RPI-RP2
5. Pico reboots as CIRCUITPY drive
```

### 3. Install Libraries

```bash
# Download library bundle:
# https://circuitpython.org/libraries

# Copy to Pico:
cp -r adafruit_hid /Volumes/CIRCUITPY/lib/
cp -r adafruit_ble /Volumes/CIRCUITPY/lib/  # For BLE version
```

### 4. Deploy Code

```bash
# Copy WiFi version:
cp code.py /Volumes/CIRCUITPY/

# Or BLE version:
cp code_ble.py /Volumes/CIRCUITPY/code.py

# Edit WiFi credentials:
nano /Volumes/CIRCUITPY/code.py
# Update SSID and PASSWORD
```

### 5. Test

```bash
# Connect to serial console:
screen /dev/tty.usbmodem* 115200

# Should see:
# RelayKVM server listening on port 8080
# Connect to: http://192.168.1.100:8080

# Test from controller:
curl http://192.168.1.100:8080
# Should see "OK"
```

---

## Example: Minimal WiFi Implementation

**Simplest possible RelayKVM for Pico W (50 lines):**

```python
import board
import usb_hid
from adafruit_hid.keyboard import Keyboard
import wifi
import socketpool

keyboard = Keyboard(usb_hid.devices)

# Connect WiFi
wifi.radio.connect("YourSSID", "YourPassword")
print(f"IP: {wifi.radio.ipv4_address}")

# HTTP server
pool = socketpool.SocketPool(wifi.radio)
server = pool.socket()
server.bind(('0.0.0.0', 8080))
server.listen(1)

while True:
    client, _ = server.accept()
    data = client.recv(1024).decode()

    # Simple command parsing
    if 'type=' in data:
        text = data.split('type=')[1].split()[0]
        keyboard.write(text)

    client.send(b'HTTP/1.1 200 OK\n\nOK')
    client.close()
```

**Test:**
```bash
curl "http://192.168.1.100:8080/?type=Hello"
# Pico W types "Hello" on host PC!
```

---

## Official Firmware

The official RelayKVM firmware for Pico 2W is available at [`firmware/pico2w/`](../firmware/pico2w/):

```
firmware/pico2w/
├── main.py                    # Main BLE UART + USB HID firmware
└── README.md                  # Setup instructions
```

**Features:**
- BLE Nordic UART (same as Cardputer)
- Full NanoKVM protocol support
- Keyboard, mouse, and media keys
- Works with existing web interface

**Setup:**
1. Install MicroPython 1.23+
2. Install `usb-device-hid` via Thonny or mpremote
3. Copy `main.py` to Pico
4. Reboot and connect!

---

## See Also

- [Pico W Datasheet](https://datasheets.raspberrypi.com/picow/pico-w-datasheet.pdf)
- [CircuitPython Documentation](https://docs.circuitpython.org/)
- [Adafruit HID Library](https://docs.circuitpython.org/projects/hid/en/latest/)
- [CircuitPython BLE](https://docs.circuitpython.org/projects/ble/en/latest/)
- [SETUP.md](SETUP.md) - General RelayKVM setup
- [README.md](../README.md) - Project overview
