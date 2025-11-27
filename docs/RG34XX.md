# RG34XX SP as RelayKVM Device

The Anbernic RG34XX SP can function as a Bluetooth HID relay on **stock firmware** - no custom OS required!

## Hardware

- **Device:** Anbernic RG34XX SP
- **SoC:** Allwinner H700 (quad-core Cortex-A53)
- **Bluetooth:** Realtek BT 4.1 (supports peripheral mode)
- **OS:** Stock Anbernic Linux (kernel 4.9.170, aarch64)

## What Works

| Feature | Status | Notes |
|---------|--------|-------|
| SSH Access | ✅ | Enabled by default |
| Python 3.10 | ✅ | Pre-installed |
| dbus-python | ✅ | Pre-installed |
| BlueZ | ✅ | Pre-installed (needs `--compat` flag) |
| BT Peripheral Mode | ✅ | `Roles: peripheral` supported |
| HID Keyboard SDP | ✅ | Registers and pairs with Windows |
| USB Gadget | ⚠️ | UDC exists, untested for HID |

## Setup Instructions

### 1. Enable BlueZ Compatibility Mode

The stock BlueZ runs without `--compat`, which breaks SDP registration.

```bash
# SSH into the device (default has root access)
ssh root@<device-ip>

# Edit the bluetooth service
vi /lib/systemd/system/bluetooth.service

# Change:
#   ExecStart=/usr/libexec/bluetooth/bluetoothd
# To:
#   ExecStart=/usr/libexec/bluetooth/bluetoothd --compat

# Reload and restart
systemctl daemon-reload
systemctl restart bluetooth
```

### 2. Register HID Keyboard Service

```bash
# Register keyboard HID profile
sdptool add KEYB

# Verify it registered
sdptool records local | grep -A5 "HID Keyboard"

# Should show:
# Service Name: HID Keyboard
# Service Class ID List:
#   "Human Interface Device" (0x1124)
```

### 3. Make Device Discoverable

```bash
# Set device class to keyboard
hciconfig hci0 class 0x002540

# Enable discovery and pairing
bluetoothctl discoverable on
bluetoothctl pairable on
```

### 4. Pair from Host PC

1. Open Bluetooth settings on your PC
2. Scan for devices
3. Select "ANBERNIC"
4. Device should pair successfully

## Architecture

```
┌─────────────┐      BLE       ┌─────────────┐    BT HID    ┌─────────────┐
│   Browser   │ ─────────────► │  RG34XX SP  │ ───────────► │   Host PC   │
│ (Any PC)    │  Nordic UART   │  (Python)   │  Keyboard/   │             │
└─────────────┘                └─────────────┘    Mouse      └─────────────┘
```

A Python daemon would:
1. Advertise BLE Nordic UART service (receive commands from web interface)
2. Maintain BT HID connection to host PC
3. Translate received commands to HID reports

## Pre-installed Python Packages

Key packages available without pip:
- `dbus` / `dbus-python` - BlueZ communication
- `gi` (PyGObject) - GLib main loop
- `PyYAML` - Configuration
- `PIL` / `Pillow` - Image processing

## USB Gadget Mode (Not Supported)

The device has a USB gadget controller, but **HID gadget is not compiled into the kernel**:

```bash
# UDC exists
ls /sys/class/udc/
# Output: 5100000.udc-controller

# Configfs mounts fine
mount -t configfs none /sys/kernel/config

# But HID function fails
mkdir /sys/kernel/config/usb_gadget/test/functions/hid.usb0
# Error: No such file or directory (kernel lacks CONFIG_USB_CONFIGFS_F_HID)
```

This is fine - **BT HID is actually better** (wireless, no cable needed)!

## Known Issues

- **SDP registration fails without `--compat`** - Must edit bluetooth.service
- **`sdptool add HID` fails** - Use `sdptool add KEYB` instead
- **No pip installed** - Use pre-installed packages or bootstrap pip manually

## App Integration

The stock Anbernic menu supports custom apps in `/mnt/mmc/Roms/APPS/`:

### File Structure

```
/mnt/mmc/Roms/APPS/
├── RelayKVM.sh              # Launcher script
├── relaykvm/                # App folder
│   ├── main.py              # Entry point
│   ├── app.py               # Main app logic
│   ├── graphic.py           # SDL2 graphics (see Clock app)
│   └── input.py             # Button input handling
└── Imgs/
    └── RelayKVM.png         # 240x180 PNG icon, RGBA
```

### Launcher Script Template

```bash
#!/bin/bash

. /mnt/mod/ctrl/configs/functions &>/dev/null 2>&1
progdir="$(cd $(dirname "$0") || exit; pwd)"/relaykvm

program="python3 ${progdir}/main.py"
log_file="${progdir}/log.txt"

$program > "$log_file" 2>&1
```

### Device Detection

```python
from pathlib import Path

board_mapping = {
    'RGcubexx': 1, 'RG34xx': 2, 'RG34xxSP': 2,
    'RG28xx': 3, 'RG35xx+_P': 4, 'RG35xxH': 5,
    'RG35xxSP': 6, 'RG40xxH': 7, 'RG40xxV': 8, 'RG35xxPRO': 9
}

board_info = Path("/mnt/vendor/oem/board.ini").read_text().splitlines()[0]
hw_info = board_mapping.get(board_info, 0)
```

### Graphics (Framebuffer + PIL)

No SDL2 needed! Direct framebuffer with PIL (from Clock app):

```python
from PIL import Image, ImageDraw, ImageFont
import mmap
import os

# Screen resolutions by hw_info (from board.ini)
screen_resolutions = {
    1: (720, 720, 18),   # RGcubexx
    2: (720, 480, 11),   # RG34xx / RG34xxSP
    3: (480, 640, 11),   # RG28xx
    4: (640, 480, 11),   # RG35xx+_P
}

screen_width, screen_height, max_elem = screen_resolutions.get(hw_info, (640, 480, 11))
bytes_per_pixel = 4  # BGRA format
screen_size = screen_width * screen_height * bytes_per_pixel

# Open framebuffer
fb = os.open('/dev/fb0', os.O_RDWR)
mm = mmap.mmap(fb, screen_size, mmap.MAP_SHARED, mmap.PROT_WRITE | mmap.PROT_READ)

# Draw with PIL, then write to framebuffer
image = Image.new('RGBA', (screen_width, screen_height))
draw = ImageDraw.Draw(image)
draw.text((10, 10), "RelayKVM", fill="#00ff00")
mm.seek(0)
mm.write(image.tobytes())

# System font available at:
font_file = "/usr/share/fonts/TTF/DejaVuSansMono.ttf"
```

### Input (evdev)

Direct `/dev/input/event1` reading (from Clock app):

```python
import struct

button_mapping = {
    304: "A", 305: "B", 306: "Y", 307: "X",
    308: "L1", 309: "R1", 314: "L2", 315: "R2",
    17: "DY", 16: "DX",  # D-pad axes (value: -1/0/1)
    310: "SELECT", 311: "START", 312: "MENUF",
    114: "V+", 115: "V-"
}

code = 0
codeName = ""
value = 0

def check():
    """Blocking read for next button event"""
    global code, codeName, value
    with open("/dev/input/event1", "rb") as f:
        while True:
            event = f.read(24)
            if event:
                (tv_sec, tv_usec, type, kcode, kvalue) = struct.unpack('llHHI', event)
                if kvalue != 0:  # Ignore release events
                    if kvalue != 1:
                        kvalue = -1  # D-pad negative direction
                    code = kcode
                    codeName = button_mapping.get(code, str(code))
                    value = kvalue
                    return

def key(keyCodeName, keyValue=99):
    """Check if specific button was pressed"""
    if codeName == keyCodeName:
        if keyValue != 99:
            return value == keyValue
        return True
    return False

def reset_input():
    """Clear input state after handling"""
    global codeName, value
    codeName = ""
    value = 0
```

### Other Resources

- `font/` - Custom TTF fonts
- `sound/` - Audio files
- `lang/` - Localization JSON

## Software TODO

- [ ] Python BLE GATT server (Nordic UART service via dbus)
- [ ] Python BT HID report sender (L2CAP to paired host)
- [ ] Framebuffer GUI showing connection status (PIL, no SDL2 needed)
- [ ] App icon (240x180 PNG)
- [ ] Startup script to register services on boot
- [ ] Handle MENUF button to exit app cleanly

## Resources

- [EmuBTHID](https://github.com/Alkaid-Benetnash/EmuBTHID) - Reference BT HID implementation
- [BlueZ D-Bus API](https://git.kernel.org/pub/scm/bluetooth/bluez.git/tree/doc) - Official docs
- [Linux HID Gadget](https://docs.kernel.org/usb/gadget_hid.html) - USB gadget HID docs

## Discovery Log

Tested on 2025-11-27:
1. Stock Anbernic firmware has SSH enabled
2. Python 3.10 + dbus-python pre-installed
3. BlueZ present but needs `--compat` for SDP
4. `sdptool add KEYB` successfully registers HID keyboard
5. Windows 11 discovers and pairs with device as "ANBERNIC"
6. Proof of concept confirmed - BT HID relay is viable!
