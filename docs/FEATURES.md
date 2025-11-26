## 🚀 Additional Features Roadmap

### Implemented ✅
1. **USB HID Emulation** - Keyboard (6KRO) and Mouse (abs/rel)
2. **BLE Security** - SSP with PIN display and confirmation
3. **Protocol Parser** - Full NanoKVM protocol support
4. **Status Display** - Connection, battery, command count
5. **Emergency Controls** - ESC key disconnect
6. **Mass Storage Mode** - Hold 'M' at boot to access SD card
7. **Display Control** - Remote brightness control (off/dim/on)
8. **Web Interface** - Industrial-style control panel with capture mode

### Distribution & Release 📦

#### GitHub Actions CI/CD
**Status:** 🔲 TODO

Automatically compile firmware on release:
- Trigger on version tags (e.g., `v1.0.0`)
- Build with PlatformIO
- Attach `RelayKVM.bin` to GitHub Release
- Users can download pre-built firmware directly

```yaml
# .github/workflows/build.yml
on:
  release:
    types: [published]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/cache@v4
        with:
          path: ~/.platformio
          key: pio
      - run: pip install platformio
      - run: cd firmware && pio run -e m5launcher
      - uses: actions/upload-release-asset@v1
        with:
          asset_path: firmware/.pio/build/m5launcher/RelayKVM.bin
```

#### M5Launcher Community Repository
**Status:** 🔲 TODO (after more testing)

Submit to M5Stack's community app repository for M5Launcher:
- Users can browse and install directly from Cardputer
- No manual .bin download needed
- Auto-updates when new versions released

**Requirements before submission:**
- [ ] Stable for 2+ weeks of daily use
- [ ] Test on multiple Cardputer units
- [ ] Test with various host PCs (Windows/Mac/Linux)
- [ ] Clean app icon and metadata

### Implemented ✅

#### USB Wake (Remote Wakeup)
**Status:** ✅ **IMPLEMENTED**

Press 'W' on Cardputer to wake host from sleep/suspend mode.

```cpp
// Press W key to show wake menu
// Select U for USB wake
bool wakeTargetComputer() {
  return tud_remote_wakeup();
}
```

**Features:**
- ✅ USB resume signal (USB 2.0 spec compliant)
- ✅ Works for Sleep (S3) and Hibernate (S4)
- ✅ Interactive menu with status feedback
- ✅ Error handling and diagnostics
- ✅ No network/WiFi required

**Benefit:** Wake host computer instantly from sleep mode

**See:** `docs/WAKE.md` for complete setup guide

### High Priority (Easy Wins) 🟢

#### 1. System Control HID (Sleep/Power/Wake Keys)
**Benefit:** Universal sleep/wake/power keys that work across all OSes

USB HID has a separate "System Control" usage page (0x01) with dedicated keys:
- 0x81 = System Power Down
- 0x82 = System Sleep
- 0x83 = System Wake Up

```cpp
// Requires adding System Control HID interface
// Different from regular keyboard - separate report type
#include <USB.h>
#include <USBHIDSystemControl.h>

USBHIDSystemControl SystemControl;

void sendSleep() {
    SystemControl.press(SYSTEM_CONTROL_SLEEP);
    delay(50);
    SystemControl.release();
}
```

**Impact:** Universal power management without OS-specific macros
**Complexity:** ⭐⭐ (need to add new HID interface)

#### 2. Battery Optimization
**Benefit:** Extend runtime from ~4 hours to ~8+ hours

```cpp
// Power management modes
void enablePowerSaving() {
  // Reduce BLE TX power when signal is strong
  NimBLEDevice::setPower(ESP_PWR_LVL_N12); // -12dBm vs default 0dBm

  // Enable light sleep between commands
  esp_sleep_enable_timer_wakeup(100000); // 100ms
  esp_light_sleep_start();

  // Dim display when idle
  M5Cardputer.Display.setBrightness(50);
}
```

**Impact:** +100% battery life

#### 2. OTA (Over-The-Air) Updates
**Benefit:** Update firmware without USB cable

```cpp
#include <ArduinoOTA.h>

void setupOTA() {
  ArduinoOTA.setHostname("kmputer");
  ArduinoOTA.setPassword("your-secure-password");
  ArduinoOTA.begin();
}

void loop() {
  ArduinoOTA.handle();
}
```

**Requirements:**
- Controller and Cardputer on same WiFi
- Or OTA via Bluetooth (slower but wireless)

#### 3. Connection Quality Indicator
**Benefit:** See signal strength in real-time

```cpp
void updateDisplay() {
  int8_t rssi = pServer->getRssi();

  if (rssi > -50) M5.Display.print("████"); // Excellent
  else if (rssi > -60) M5.Display.print("███ "); // Good
  else if (rssi > -70) M5.Display.print("██  "); // Fair
  else M5.Display.print("█   ");              // Poor
}
```

#### 4. Auto-Reconnect
**Benefit:** Reconnect automatically after disconnect

```cpp
bool autoReconnect = true;
unsigned long lastReconnectAttempt = 0;

void loop() {
  if (!deviceConnected && autoReconnect) {
    if (millis() - lastReconnectAttempt > 5000) {
      NimBLEDevice::startAdvertising();
      lastReconnectAttempt = millis();
    }
  }
}
```

#### 5. Keyboard Layout Support
**Benefit:** Support non-US layouts (QWERTY, AZERTY, etc.)

```cpp
enum KeyboardLayout {
  LAYOUT_US,
  LAYOUT_UK,
  LAYOUT_DE,
  LAYOUT_FR
};

uint8_t translateKeycode(uint8_t key, KeyboardLayout layout) {
  // Remap keys based on layout
}
```

### Medium Priority 🟡

#### 6. Macro Support
**Benefit:** Record and replay common sequences

```cpp
struct Macro {
  String name;
  uint8_t commands[256];
  size_t length;
};

Macro macros[10];

// Press special key combo to trigger macro
void executeMacro(int index) {
  for (size_t i = 0; i < macros[index].length; i++) {
    // Execute stored commands
  }
}
```

#### 7. Multi-Computer Support
**Benefit:** Switch between multiple host computers

```cpp
struct Computer {
  String name;
  uint8_t usbPort; // If using USB hub
};

Computer computers[4] = {
  {"Workstation", 0},
  {"Laptop", 1},
  {"Server", 2}
};

// Press key combo to switch
void switchComputer(int index) {
  // Re-enumerate USB on different port
}
```

#### 8. Web Configuration Interface
**Benefit:** Configure settings via browser

```cpp
#include <WebServer.h>

WebServer server(80);

void setupWebServer() {
  server.on("/", handleRoot);
  server.on("/settings", handleSettings);
  server.begin();
}

// Access via http://kmputer.local
```

#### 9. Gesture Mouse Support
**Benefit:** Use Cardputer as air mouse (IMU-based)

```cpp
#include <BMI270_Sensor.h>

void updateMouseFromIMU() {
  float ax, ay, az;
  M5.Imu.getAccel(&ax, &ay, &az);

  // Tilt = mouse movement
  int dx = (int)(ax * 50);
  int dy = (int)(ay * 50);
  Mouse.move(dx, dy);
}
```

#### 10. Clipboard Sync
**Benefit:** Sync clipboard between controller and host

```cpp
// Via BLE service
void syncClipboard(String text) {
  // Controller sends clipboard text
  // RelayKVM types it out on host
  Keyboard.print(text);
}
```

### Medium Priority 🟡 (continued)

#### 10. Wake-on-LAN
**Benefit:** Wake host from complete shutdown (S5)

```cpp
void sendWakeOnLAN(uint8_t mac[6]) {
  // Connect to WiFi
  WiFi.begin(ssid, password);

  // Build magic packet
  uint8_t magicPacket[102];
  for (int i = 0; i < 6; i++) magicPacket[i] = 0xFF;
  for (int i = 0; i < 16; i++) memcpy(&magicPacket[6 + i * 6], mac, 6);

  // Send UDP broadcast
  WiFiUDP udp;
  udp.beginPacket(IPAddress(255,255,255,255), 9);
  udp.write(magicPacket, sizeof(magicPacket));
  udp.endPacket();
}
```

**Requirements:**
- Target connected via Ethernet
- BIOS Wake-on-LAN enabled
- Cardputer WiFi connection
- Target MAC address known

**Battery impact:** Moderate (WiFi use)
**Complexity:** ⭐⭐⭐
**See:** `docs/WAKE.md` for implementation guide

### Advanced Features 🔴

#### 11. Video Streaming Integration
**Benefit:** Built-in video viewer on Cardputer (low res preview)

**Challenge:** ESP32-S3 processing power limited
**Solution:** MJPEG at 320x240@15fps from capture card

```cpp
#include <esp_camera.h>

// Display preview on Cardputer screen
void displayVideoPreview() {
  // Fetch MJPEG frame from capture card
  // Scale down and display
}
```

#### 12. KVM Switching (Hardware)
**Benefit:** Physical relay switching between computers

**Requirements:**
- External relay module
- Grove connector on Cardputer
- Multiple USB cables

```cpp
#define RELAY_PIN 2

void switchToComputer(int num) {
  digitalWrite(RELAY_PIN, num == 1 ? HIGH : LOW);
  delay(100);
  // Re-enumerate USB
}
```

#### 13. Encrypted Tunnel Mode
**Benefit:** Additional encryption layer beyond BLE

```cpp
#include <mbedtls/aes.h>

// AES-256 on top of BLE encryption
void encryptCommand(uint8_t* data, size_t len) {
  mbedtls_aes_context aes;
  mbedtls_aes_setkey_enc(&aes, key, 256);
  mbedtls_aes_crypt_cbc(&aes, MBEDTLS_AES_ENCRYPT, len, iv, data, output);
}
```

#### 14. Multi-User Access
**Benefit:** Multiple controllers can connect (time-shared)

**Challenge:** Requires arbitration protocol

```cpp
struct User {
  String name;
  NimBLEAddress address;
  uint8_t priority;
};

// Queue requests, highest priority wins
```

#### 15. Screen Recording
**Benefit:** Record KVM session to SD card

```cpp
#include <SD.h>

File recordingFile;

void recordCommand(uint8_t* packet, size_t len) {
  // Write packet + timestamp to SD
  recordingFile.write(millis());
  recordingFile.write(packet, len);
}
```

#### 16. Remote Desktop Protocol (RDP/VNC) Support
**Benefit:** Act as RDP/VNC client

**Challenge:** Requires significant processing power
**Alternative:** Use as HID for existing RDP client

#### 17. FIDO2/WebAuthn Hardware Token
**Benefit:** Use Cardputer as 2FA device

```cpp
#include <FIDO2.h>

// Register RelayKVM as security key
// Use for passwordless login
```

#### 18. IR Blaster Integration
**Benefit:** Control monitors, projectors via IR

```cpp
#define IR_PIN 19 // Cardputer has IR LED

void sendIRCommand(uint32_t code) {
  // Send NEC/Sony/RC5 IR codes
  // Turn on/off monitors remotely
}
```

#### 19. SSH Terminal
**Benefit:** Built-in SSH client on Cardputer

```cpp
#include <SSH.h>

// Use Cardputer keyboard for SSH sessions
// Display on Cardputer screen
void connectSSH(String host, String user, String pass) {
  // SSH client implementation
}
```

## 📊 Feature Comparison Matrix

| Feature | Complexity | Battery Impact | Memory Use | Usefulness |
|---------|-----------|----------------|------------|------------|
| USB Wake | ⭐ | - | ➕ | ⭐⭐⭐⭐⭐ |
| Battery Optimization | ⭐ | ➖➖➖ | ➕ | ⭐⭐⭐⭐⭐ |
| OTA Updates | ⭐⭐ | ➕ | ➕➕ | ⭐⭐⭐⭐ |
| Signal Indicator | ⭐ | ➕ | ➕ | ⭐⭐⭐⭐ |
| Auto-Reconnect | ⭐ | ➕ | ➕ | ⭐⭐⭐⭐⭐ |
| Keyboard Layouts | ⭐⭐ | - | ➕➕ | ⭐⭐⭐ |
| Macros | ⭐⭐ | ➕ | ➕➕ | ⭐⭐⭐⭐ |
| Multi-Computer | ⭐⭐⭐ | - | ➕➕ | ⭐⭐⭐ |
| Web Config | ⭐⭐⭐ | ➕➕ | ➕➕➕ | ⭐⭐⭐⭐ |
| Gesture Mouse | ⭐⭐ | ➕ | ➕ | ⭐⭐ |
| Wake-on-LAN | ⭐⭐ | ➕➕ | ➕➕ | ⭐⭐⭐⭐ |
| Clipboard Sync | ⭐⭐ | ➕ | ➕ | ⭐⭐⭐⭐ |
| Video Preview | ⭐⭐⭐⭐ | ➕➕➕ | ➕➕➕➕ | ⭐⭐ |
| KVM Switching | ⭐⭐⭐ | - | ➕ | ⭐⭐⭐⭐ |
| Encrypted Tunnel | ⭐⭐⭐ | ➕➕ | ➕➕ | ⭐⭐ |
| Multi-User | ⭐⭐⭐⭐ | ➕➕ | ➕➕➕ | ⭐⭐ |
| Screen Recording | ⭐⭐⭐ | ➕ | ➕➕➕➕ | ⭐⭐⭐ |
| RDP/VNC | ⭐⭐⭐⭐⭐ | ➕➕➕ | ➕➕➕➕➕ | ⭐⭐ |
| FIDO2 Token | ⭐⭐⭐⭐ | ➕ | ➕➕ | ⭐⭐⭐ |
| IR Blaster | ⭐⭐ | ➕ | ➕ | ⭐⭐⭐ |
| SSH Terminal | ⭐⭐⭐⭐ | ➕➕ | ➕➕➕➕ | ⭐⭐⭐⭐ |

**Legend:**
- ⭐ Complexity (more stars = harder to implement)
- ➕ Resource usage (battery/memory)
- ➖ Resource savings

## 🎯 Recommended Implementation Order

**Phase 1 (MVP+):**
1. ✅ USB Wake (DONE!)
2. Auto-Reconnect
3. Signal Indicator
4. Battery Optimization

**Phase 2 (Power User):**
5. OTA Updates
6. Macros
7. Clipboard Sync
8. Wake-on-LAN

**Phase 3 (Advanced):**
9. Web Config
10. Multi-Computer

**Phase 4 (Experimental):**
10. Pick based on user feedback

## 💾 Memory Budget

ESP32-S3FN8 has:
- **8MB Flash** (plenty of room)
- **512KB RAM** (need to be careful)

Current usage:
- Firmware: ~1.2MB flash
- Runtime: ~150KB RAM
- **Available:** ~350KB RAM for features

## 🔋 Battery Estimates

With 1750mAh battery:

| Mode | Current Draw | Runtime |
|------|--------------|---------|
| Active (default) | ~450mA | ~3.5 hours |
| + Power Saving | ~250mA | ~7 hours |
| + Display Off | ~150mA | ~11 hours |
| Deep Sleep | ~50mA | ~35 hours |

## Which features interest you most?

I'll implement the top 3-5 you choose!
