# RelayKVM Setup Guide

## Prerequisites

### Hardware
- M5Stack Cardputer v1.1 (ESP32-S3FN8)
- USB-C cable for programming
- USB HDMI capture card
- Target computer with USB port
- Controller computer with Bluetooth

### Software
- PlatformIO IDE or Arduino IDE 2.x
- ESP32 board support (v2.0.14+)
- M5Cardputer library
- NimBLE-Arduino library

## Installation

### Option 1: PlatformIO (Recommended)

1. **Install PlatformIO:**
   ```bash
   pip install platformio
   ```

2. **Clone and navigate:**
   ```bash
   cd firmware
   ```

3. **Build and upload:**
   ```bash
   pio run -t upload
   ```

4. **Monitor serial output:**
   ```bash
   pio device monitor
   ```

### Option 2: Arduino IDE

1. **Install Arduino IDE 2.x**

2. **Add ESP32 board support:**
   - File → Preferences → Additional Board Manager URLs
   - Add: `https://espressif.github.io/arduino-esp32/package_esp32_index.json`
   - Tools → Board → Boards Manager → Search "esp32" → Install

3. **Install libraries:**
   - Tools → Manage Libraries
   - Search and install:
     - M5Cardputer
     - NimBLE-Arduino

4. **Configure board:**
   - Tools → Board → ESP32 Arduino → M5Stack-STAMPS3
   - Tools → USB Mode → "Hardware CDC and JTAG"
   - Tools → USB CDC On Boot → "Enabled"

5. **Open sketch:**
   - File → Open → `NanoKVM_BT/NanoKVM_BT.ino`

6. **Upload:**
   - Plug in Cardputer
   - Select correct COM port
   - Click Upload

## First Time Setup

### 1. Flash Firmware

Follow installation steps above. On first boot, you should see:
```
RelayKVM: Starting...
USB HID ready!
BLE ready!
Waiting for connection...
Device: RelayKVM
```

### 2. Connect to Target Computer

1. Plug Cardputer into host computer via USB-C
2. Target should recognize:
   - Vendor: NanoKVM Project
   - Product: RelayKVM Controller
   - VID: 0xFEED
   - PID: 0xAE01

3. No drivers needed (uses standard HID class)

### 3. Pair with Controller

**On Linux:**
```bash
bluetoothctl
scan on
# Wait for "RelayKVM" to appear
pair [MAC_ADDRESS]
# Enter PIN shown on Cardputer screen
connect [MAC_ADDRESS]
```

**On Windows:**
1. Settings → Bluetooth & devices → Add device
2. Select "RelayKVM"
3. Enter PIN shown on Cardputer screen
4. Click "Connect"

**On macOS:**
1. System Preferences → Bluetooth
2. Select "RelayKVM"
3. Enter PIN shown on Cardputer screen

### 4. Set Up Video Capture

1. Connect HDMI capture card to host computer
2. Connect USB capture card to controller computer
3. Open video capture software (OBS, VLC, etc.)
4. Select capture device

## Using with NanoKVM Web Interface

### Modify Browser App

The browser app needs to be modified to use Web Bluetooth instead of Web Serial:

**File:** `browser/src/libs/device/serial-port.ts`

```typescript
// Replace Web Serial with Web Bluetooth
export class BluetoothPort {
  private device: BluetoothDevice | null = null;
  private characteristic: BluetoothRemoteGATTCharacteristic | null = null;

  async connect() {
    this.device = await navigator.bluetooth.requestDevice({
      filters: [{ name: 'RelayKVM' }],
      optionalServices: ['6e400001-b5a3-f393-e0a9-e50e24dcca9e']
    });

    const server = await this.device.gatt!.connect();
    const service = await server.getPrimaryService('6e400001-b5a3-f393-e0a9-e50e24dcca9e');
    this.characteristic = await service.getCharacteristic('6e400002-b5a3-f393-e0a9-e50e24dcca9e');
  }

  async send(data: Uint8Array) {
    if (this.characteristic) {
      await this.characteristic.writeValue(data);
    }
  }
}
```

## Troubleshooting

### USB HID Not Recognized

**Symptoms:** Target computer doesn't recognize keyboard/mouse

**Solutions:**
1. Check USB cable (must support data, not just charging)
2. Verify USB mode in platformio.ini: `ARDUINO_USB_CDC_ON_BOOT=1`
3. Try different USB port
4. Check Device Manager/lsusb for 0xFEED:0xAE01

### Bluetooth Won't Pair

**Symptoms:** Can't see device or pairing fails

**Solutions:**
1. Ensure Bluetooth is enabled on controller
2. Move closer (< 10m range)
3. Reset Cardputer (hold power button)
4. Check serial output for errors
5. Try removing existing pairing and re-pair

### Laggy Input

**Symptoms:** Keyboard/mouse feels slow

**Solutions:**
1. Reduce Bluetooth distance
2. Remove sources of 2.4GHz interference
3. Check battery level on Cardputer
4. Ensure host computer USB port has enough power

### Commands Not Working

**Symptoms:** Keys don't type, mouse doesn't move

**Solutions:**
1. Check serial monitor for error messages
2. Verify Bluetooth connection (should say "CONNECTED")
3. Test with simple key press
4. Check NanoKVM protocol packet format

## Testing

### Test USB HID (without Bluetooth)

Add to `setup()`:
```cpp
// Test keyboard
delay(3000);
Keyboard.print("Hello from RelayKVM!");

// Test mouse
Mouse.move(100, 100);
Mouse.click(MOUSE_LEFT);
```

### Test Bluetooth (without USB HID)

Use a BLE scanner app (nRF Connect, LightBlue) to:
1. Scan for "RelayKVM"
2. Connect to service 6E400001-...
3. Write to characteristic 6E400002-...
4. Check serial monitor for received data

## Advanced Configuration

### Change Device Name

Edit `NanoKVM_BT.ino`:
```cpp
NimBLEDevice::init("YourCustomName");
```

### Adjust Security Level

For testing without pairing:
```cpp
// In setup(), replace:
NimBLEDevice::setSecurityAuth(false, false, false);
```

⚠️ **Not recommended for production use**

### Battery Optimization

Reduce power consumption:
```cpp
// Lower Bluetooth TX power
NimBLEDevice::setPower(ESP_PWR_LVL_N12); // -12dBm

// Enable light sleep (between commands)
esp_light_sleep_start();
```

## Next Steps

- Configure video capture software
- Modify browser app for Bluetooth
- Test with real workload
- Submit to pid.codes for official VID/PID

## Support

For issues, check:
1. Serial monitor output (115200 baud)
2. GitHub issues
3. M5Stack documentation
4. ESP32 Arduino documentation
