# RelayKVM for Raspberry Pi Pico 2W

MicroPython firmware that turns a Pico 2W into a BLE-to-USB HID relay.

## Requirements

- Raspberry Pi Pico 2W (or Pico W)
- MicroPython 1.23+
- USB cable

## Installation

### 1. Install MicroPython

1. Download MicroPython for your board:
   - **Pico 2W**: https://micropython.org/download/RPI_PICO2_W/
   - **Pico W**: https://micropython.org/download/RPI_PICO_W/

2. Hold **BOOTSEL** button while plugging in the Pico
3. Drag the `.uf2` file to the **RPI-RP2** drive
4. Pico reboots with MicroPython installed

### 2. Install USB HID Library

#### Option A: Using Thonny (Recommended)

1. Open [Thonny IDE](https://thonny.org/)
2. Connect to your Pico (bottom-right corner → select interpreter)
3. Go to **Tools → Manage packages...**
4. Search for `usb-device-hid`
5. Click **Install**

#### Option B: Using mpremote

```bash
# Install mpremote if you don't have it
pip install mpremote

# Install the USB HID library on the Pico
mpremote connect COM9 mip install usb-device-hid
```

(Replace `COM9` with your Pico's serial port)

### 3. Deploy Firmware

#### Option A: Using Thonny

1. Open `main.py` in Thonny
2. Go to **File → Save as...**
3. Select **Raspberry Pi Pico** when prompted
4. Save as `main.py`

#### Option B: Using mpremote

```bash
mpremote connect COM9 cp main.py :main.py
```

### 4. Reboot

Unplug and replug the Pico. It will:
1. Initialize USB HID (keyboard/mouse/media)
2. Start advertising as "RelayKVM-Pico"
3. LED turns on when BLE connected

## Usage

1. Plug Pico 2W into the **target PC** (the one you want to control)
2. Open `index.html` on the **controller PC** (any device with Chrome/Edge)
3. Click "Connect" and select "RelayKVM-Pico"
4. Control the target PC!

## LED Status

- **Off**: Advertising (waiting for BLE connection)
- **On**: Connected

## Troubleshooting

### "RelayKVM-Pico" not appearing in Bluetooth picker

- Ensure MicroPython is running (not in BOOTSEL mode)
- Check serial console for errors
- Try rebooting the Pico

### "USB HID not available" error

- Make sure you installed `usb-device-hid` library (step 2)
- Verify MicroPython version is 1.23 or newer

### Keys/mouse not working on target PC

- The first boot after installing USB HID may need a second reboot
- Check that the Pico shows as a HID device in Device Manager

### Serial console disconnects on boot

This is **normal** - when USB HID initializes, the USB configuration changes and the serial port reconnects. Just reconnect in Thonny or mpremote.

## Files

| File | Purpose |
|------|---------|
| `main.py` | Main firmware (BLE + USB HID) |

## Features

- Full keyboard support (all keys + modifiers)
- Relative mouse movement + scroll
- Mouse buttons (left, right, middle)
- Media keys (volume, play/pause, etc.)
- Same protocol as Cardputer (works with same web interface)

## Protocol

Uses NanoKVM protocol over BLE Nordic UART Service (NUS):
- Service UUID: `6e400001-b5a3-f393-e0a9-e50e24dcca9e`
- RX (write): `6e400002-b5a3-f393-e0a9-e50e24dcca9e`
- TX (notify): `6e400003-b5a3-f393-e0a9-e50e24dcca9e`

## Compared to Cardputer

| Feature | Pico 2W | Cardputer |
|---------|---------|-----------|
| Cost | $7 | $40 |
| Display | None | Yes |
| Keyboard | None | Yes |
| Language | MicroPython | C++ |
| Setup | Copy files | Compile & flash |
| Size | Tiny | Pocket-sized |
| Media keys | Yes | Yes |

## See Also

- [Main README](../../README.md) - Project overview
- [PICOW.md](../../docs/PICOW.md) - Additional Pico W documentation
- [Cardputer firmware](../RelayKVM/) - ESP32-S3 version
