# M5Launcher Compatibility for RelayKVM

This guide explains how to make RelayKVM bootable from M5Launcher, allowing you to switch between different firmwares on your Cardputer.

## 🎯 What is M5Launcher?

M5Launcher is like TWRP/Grub for ESP32 devices:
- Boots every time you power on
- Shows menu of installed firmwares
- Lets you switch between apps/firmwares
- Supports both binary firmwares and MicroPython apps

## 🏗️ Architecture

```
Power On → M5Launcher (partition 0)
           ├── RelayKVM (OTA partition 1)
           ├── Bruce (OTA partition 2)
           ├── MicroHydra (FAT partition)
           └── Other Apps...
```

## 📦 Creating Launcher-Compatible Binary

### Step 1: Build OTA Binary

Add to `platformio.ini`:

```ini
[env:m5stack-cardputer-ota]
platform = espressif32
board = m5stack-stamps3
framework = arduino

; Use 16MB partition scheme with OTA
board_build.partitions = default_16MB_ota.csv

; Build as OTA binary
extra_scripts = post:create_ota_binary.py

build_flags =
    -DARDUINO_USB_MODE=1
    -DARDUINO_USB_CDC_ON_BOOT=1
    -DBOARD_HAS_PSRAM
    -DM5CARDPUTER

lib_deps =
    m5stack/M5Cardputer@^1.0.0
    h2zero/NimBLE-Arduino@^1.4.1
```

### Step 2: Create Build Script

Create `create_ota_binary.py`:

```python
#!/usr/bin/env python3
"""
Post-build script to create M5Launcher-compatible OTA binary
"""
Import("env")
import os
import shutil

def create_ota_binary(source, target, env):
    firmware_path = str(target[0])
    ota_path = firmware_path.replace('.bin', '_ota.bin')

    # Copy firmware to OTA binary
    shutil.copy(firmware_path, ota_path)

    print(f"Created OTA binary: {ota_path}")
    print(f"Copy this file to SD card: /firmware/RelayKVM.bin")

env.AddPostAction("$BUILD_DIR/${PROGNAME}.bin", create_ota_binary)
```

### Step 3: Build

```bash
cd firmware
pio run -e m5stack-cardputer-ota

# Output: .pio/build/m5stack-cardputer-ota/firmware_ota.bin
```

## 💾 Installation to M5Launcher

### Method 1: SD Card (Recommended)

1. **Format SD card** as FAT32

2. **Create firmware directory:**
   ```
   /firmware/
   ```

3. **Copy binary:**
   ```bash
   cp .pio/build/*/firmware_ota.bin /path/to/sd/firmware/RelayKVM.bin
   ```

4. **Create metadata file** `/firmware/RelayKVM.json`:
   ```json
   {
     "name": "RelayKVM",
     "version": "1.0.0",
     "description": "Bluetooth KVM Controller for M5Stack Cardputer",
     "author": "Arthur Endlein",
     "category": "Utility",
     "icon": "🎮",
     "firmware": "RelayKVM.bin",
     "size": 1234567,
     "checksum": "sha256:..."
   }
   ```

5. **Insert SD card** into Cardputer

6. **Boot M5Launcher** and select RelayKVM from menu

### Method 2: OTA Upload (Advanced)

If M5Launcher supports OTA:

```bash
# Using esptool
esptool.py --port /dev/ttyUSB0 write_flash 0x210000 firmware_ota.bin
```

## 📋 M5Launcher Menu Integration

### Create Icon (Optional)

Create `RelayKVM_icon.bmp` (64x64, 16-bit color):

```bash
# Use ImageMagick to convert
convert kmputer_logo.png -resize 64x64 -depth 16 RelayKVM_icon.bmp
```

Place in `/firmware/icons/RelayKVM_icon.bmp`

### Menu Configuration

M5Launcher auto-detects `.bin` files in `/firmware/`, but you can customize:

Create `/launcher/config.json`:

```json
{
  "firmwares": [
    {
      "name": "RelayKVM",
      "file": "/firmware/RelayKVM.bin",
      "description": "Bluetooth KVM\nWireless control",
      "icon": "/firmware/icons/RelayKVM_icon.bmp",
      "partition": "ota_0",
      "priority": 1
    }
  ]
}
```

## 🔄 Exit to Launcher

Add functionality to RelayKVM firmware to reboot to launcher:

```cpp
#include <esp_ota_ops.h>

void returnToLauncher() {
  M5Cardputer.Display.clear();
  M5Cardputer.Display.println("Returning to Launcher...");
  delay(1000);

  // Set boot partition back to factory (M5Launcher)
  const esp_partition_t* factory = esp_partition_find_first(
    ESP_PARTITION_TYPE_APP,
    ESP_PARTITION_SUBTYPE_APP_FACTORY,
    NULL
  );

  if (factory != NULL) {
    esp_ota_set_boot_partition(factory);
    esp_restart();
  } else {
    // Fallback: just restart
    esp_restart();
  }
}

// In loop(), handle special key combo
void loop() {
  M5Cardputer.update();

  // Press Fn+ESC to return to launcher
  if (M5Cardputer.Keyboard.isKeyPressed(KEY_FN) &&
      M5Cardputer.Keyboard.isKeyPressed(KEY_ESC)) {
    returnToLauncher();
  }
}
```

## 🎨 Launcher Screen Preview

```
┌────────────────────────────┐
│      M5 LAUNCHER v2.0      │
├────────────────────────────┤
│                            │
│  🎮 RelayKVM      v1.0.0   │
│     Bluetooth KVM          │
│     Press [A] to boot      │
│                            │
│  🔧 Bruce        v2.1.0   │
│     Security Testing       │
│                            │
│  🐍 MicroHydra   v1.5.0   │
│     Python Apps            │
│                            │
│  [↑↓] Navigate [A] Boot   │
└────────────────────────────┘
```

## 📝 Partition Table

M5Launcher uses custom partition table:

```csv
# Name,     Type, SubType,  Offset,   Size,     Flags
nvs,        data, nvs,      0x9000,   0x5000,
otadata,    data, ota,      0xe000,   0x2000,
factory,    app,  factory,  0x10000,  0x200000, # M5Launcher
ota_0,      app,  ota_0,    0x210000, 0x400000, # RelayKVM
ota_1,      app,  ota_1,    0x610000, 0x400000, # Other firmware
spiffs,     data, spiffs,   0xa10000, 0x100000, # Config
fat,        data, fat,      0xb10000, 0x4F0000, # MicroPython
```

**RelayKVM lives in `ota_0` (0x210000 - 0x610000 = 4MB)**

## 🔧 Development Workflow

### Testing Without M5Launcher

```bash
# Flash directly to device for testing
pio run -t upload

# Once stable, create OTA binary for launcher
pio run -e m5stack-cardputer-ota
```

### Updating RelayKVM via Launcher

1. Build new version
2. Copy `RelayKVM.bin` to SD card (overwrite old)
3. Reboot to launcher
4. Select RelayKVM → Press [U] to update
5. Launcher will flash new binary

## 🎯 Launcher Features to Leverage

### 1. Shared Configuration

M5Launcher provides shared config storage:

```cpp
#include <Preferences.h>

Preferences prefs;

void setup() {
  prefs.begin("launcher", false); // false = read/write

  // Read shared settings
  String wifiSSID = prefs.getString("wifi_ssid", "");
  String wifiPass = prefs.getString("wifi_pass", "");

  prefs.end();
}
```

### 2. Firmware Metadata

M5Launcher can read metadata from firmware:

```cpp
const char* FIRMWARE_NAME = "RelayKVM";
const char* FIRMWARE_VERSION = "1.0.0";
const char* FIRMWARE_AUTHOR = "Arthur Endlein";

// M5Launcher scans for these strings
__attribute__((section(".rodata")))
const char metadata[] = "FIRMWARE_META:{"
  "\"name\":\"RelayKVM\","
  "\"version\":\"1.0.0\","
  "\"author\":\"Arthur Endlein\""
"}";
```

### 3. Quick Boot (Optional)

Add to `setup()`:

```cpp
// If Fn key held during boot, return to launcher immediately
if (M5Cardputer.Keyboard.isKeyPressed(KEY_FN)) {
  returnToLauncher();
}
```

## 🐛 Troubleshooting

### "Firmware won't boot from launcher"

**Check:**
1. Binary size < 4MB (ota_0 partition limit)
2. Binary is compiled with correct partition table
3. SD card formatted as FAT32
4. File named exactly `RelayKVM.bin` (case-sensitive)

**Fix:**
```bash
# Check binary size
ls -lh firmware_ota.bin

# Verify partition table
pio run -t menuconfig
# → Partition Table → Custom partition CSV file
```

### "Launcher doesn't show RelayKVM"

**Check:**
1. File in `/firmware/` directory (not root)
2. Extension is `.bin` (not `.elf` or other)
3. SD card inserted properly

**Debug:**
```bash
# List SD card contents from M5Launcher serial console
ls /firmware/
```

### "Stuck in boot loop"

**Recovery:**
1. Remove SD card
2. Flash M5Launcher again:
   ```bash
   esptool.py --port /dev/ttyUSB0 erase_flash
   # Re-flash M5Launcher
   ```
3. Re-add RelayKVM binary

## 📦 Distribution

### Release Package

Create `RelayKVM_v1.0.0_M5Launcher.zip`:

```
RelayKVM_v1.0.0_M5Launcher.zip
├── README.txt (installation instructions)
├── firmware/
│   ├── RelayKVM.bin
│   ├── RelayKVM.json
│   └── icons/
│       └── RelayKVM_icon.bmp
└── LICENSE.txt
```

### Installation Instructions (for users)

```
KMPUTER FOR M5LAUNCHER
=======================

REQUIREMENTS:
- M5Stack Cardputer v1.1
- M5Launcher v2.0+ installed
- MicroSD card (any size, FAT32)

INSTALLATION:
1. Insert SD card into computer
2. Copy 'firmware' folder to SD card root
3. Eject SD card
4. Insert into Cardputer
5. Power on Cardputer
6. Navigate to RelayKVM in launcher menu
7. Press [A] to boot

USAGE:
- ESC = Emergency disconnect
- Fn+ESC = Return to launcher
```

## 🚀 Benefits of M5Launcher

| Feature | Standalone | With M5Launcher |
|---------|-----------|----------------|
| Switch firmware | Re-flash (5 min) | Menu select (5 sec) |
| Test new version | Overwrite | Install both |
| Share config | Manual | Automatic |
| Dual-boot | Not possible | Yes |
| Brick recovery | Re-flash | Boot to launcher |

## ✅ Summary

**To make RelayKVM launcher-compatible:**
1. ✅ Build with OTA partition table
2. ✅ Create metadata JSON file
3. ✅ Add "return to launcher" function
4. ✅ Copy binary to SD card `/firmware/` folder

**Total effort:** ~2 hours (mostly partition configuration)

**Benefits:**
- Easy switching between firmwares
- Safe testing of new versions
- Shared configuration with other apps
- Better user experience

Would you like me to create the complete build configuration files for M5Launcher compatibility?
