/**
 * RelayKVM: Bluetooth-Controlled USB HID for M5Stack Cardputer
 *
 * Receives NanoKVM protocol commands via Bluetooth and translates them
 * to USB HID keyboard/mouse events for the target computer.
 *
 * Hardware: M5Stack Cardputer v1.1 (ESP32-S3FN8)
 * License: GNU GPL v3
 * Author: Arthur Endlein
 *
 * USB Identifiers (Development):
 * - VID: 0xFEED (DIY keyboard community)
 * - PID: 0xAE01 (Arthur Endlein initials)
 */

#include <M5Cardputer.h>
#include <USB.h>
#include <USBCDC.h>
#include <USBHIDKeyboard.h>
#include <USBHIDMouse.h>
#include <USBMSC.h>
#include <SD.h>
#include <SPI.h>
#include <NimBLEDevice.h>
#include "tusb.h"  // TinyUSB for USB state queries

// Debug output - comment out to disable serial debug (reduces USB issues)
// #define DEBUG

#ifdef DEBUG
  #define DEBUG_PRINT(x) USBSerial.print(x)
  #define DEBUG_PRINTLN(x) USBSerial.println(x)
  #define DEBUG_PRINTF(...) USBSerial.printf(__VA_ARGS__)
#else
  #define DEBUG_PRINT(x)
  #define DEBUG_PRINTLN(x)
  #define DEBUG_PRINTF(...)
#endif

// M5Launcher compatibility
#ifdef M5LAUNCHER_COMPATIBLE
#include <esp_ota_ops.h>
#endif

// USB HID + CDC devices
USBHIDKeyboard Keyboard;
USBHIDMouse Mouse;
USBCDC USBSerial;

// BLE server and characteristics
NimBLEServer *pServer = nullptr;
NimBLECharacteristic *pTxCharacteristic = nullptr;
NimBLECharacteristic *pRxCharacteristic = nullptr;

// Connection state - use volatile for variables modified in callbacks
volatile bool deviceConnected = false;
volatile bool connectionChanged = false;  // Flag to trigger display update in main loop

// NanoKVM Protocol constants (from browser/src/libs/device/proto.ts)
#define HEAD1 0x57
#define HEAD2 0xAB
#define ADDR_DEFAULT 0x00

// Command codes
#define CMD_GET_INFO 0x01
#define CMD_SEND_KB_GENERAL_DATA 0x02
#define CMD_SEND_KB_MEDIA_DATA 0x03
#define CMD_SEND_MS_ABS_DATA 0x04
#define CMD_SEND_MS_REL_DATA 0x05
#define CMD_SEND_MY_HID_DATA 0x06

// Custom command codes (0x80+)
#define CMD_RELEASE_CAPTURE 0x80      // Sent when G0 button pressed
#define CMD_DISPLAY_CONTROL 0x81      // Display off/dim/on
#define CMD_DISPLAY_TIMEOUT 0x82      // Set display timeout (seconds)
#define CMD_USB_WAKE 0x83             // Trigger USB wake signal
#define CMD_USB_RECONNECT 0x84        // Soft USB reconnect (re-enumerate)
#define CMD_DEVICE_RESET 0x85         // Full device reset

// Display brightness levels
#define DISPLAY_OFF 0
#define DISPLAY_DIM 64
#define DISPLAY_ON 255

// UUIDs for BLE Serial service (Nordic UART Service compatible)
#define SERVICE_UUID           "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_UUID_RX "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_UUID_TX "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"

// Display state
uint32_t lastStatusUpdate = 0;
uint32_t commandCount = 0;

// Display control state
uint8_t displayBrightness = DISPLAY_ON;
uint16_t displayTimeout = 30;          // Seconds, 0 = disabled
uint32_t lastActivityTime = 0;         // For screen timeout
bool displayAsleep = false;

// USB keepalive to prevent Windows from suspending the device
uint32_t lastUsbKeepalive = 0;
#define USB_KEEPALIVE_INTERVAL 30000   // 30 seconds

// USB state tracking (set by TinyUSB callbacks)
volatile bool usbMounted = false;
volatile bool usbSuspended = false;
volatile bool usbStateChanged = false;  // Flag to trigger display update

// Pending display operations (set in BLE callback, processed in main loop)
volatile bool pendingDisplayChange = false;
volatile uint8_t pendingBrightness = 0;

// Forward declarations
void processNanoKVMPacket(uint8_t* packet, size_t len);
void updateDisplay();
void handleKeyboardCommand(uint8_t* data, size_t len);
void handleMouseAbsCommand(uint8_t* data, size_t len);
void handleMouseRelCommand(uint8_t* data, size_t len);
bool wakeTargetComputer();
void showWakeMenu();
void softReset();
void sendReleaseCaptureNotification();
void setDisplayBrightness(uint8_t brightness);
void setDisplayTimeout(uint16_t seconds);
void wakeDisplay();
void checkDisplayTimeout();
void runMassStorageMode();
bool checkMassStorageKey();
void usbKeepalive();
void checkUsbState();
void handleUsbReconnect();
void handleDeviceReset();

/**
 * BLE Server Callbacks - With Windows-friendly connection parameters
 */
class ServerCallbacks: public NimBLEServerCallbacks {
    void onConnect(NimBLEServer* pServer, ble_gap_conn_desc* desc) override {
        deviceConnected = true;
        connectionChanged = true;

        // Don't update connection params immediately - let the connection settle
        // Log the negotiated parameters instead
        DEBUG_PRINTF("Client connected, handle: %d\n", desc->conn_handle);
        DEBUG_PRINTF("  Interval: %d, Latency: %d, Timeout: %d\n",
            desc->conn_itvl, desc->conn_latency, desc->supervision_timeout);
    };

    void onDisconnect(NimBLEServer* pServer, ble_gap_conn_desc* desc) override {
        deviceConnected = false;
        connectionChanged = true;
        DEBUG_PRINTF("Client disconnected, handle: %d\n", desc->conn_handle);
    }

    // Called when MTU is updated - important for Windows
    void onMTUChange(uint16_t MTU, ble_gap_conn_desc* desc) override {
        DEBUG_PRINTF("MTU updated: %d, handle: %d\n", MTU, desc->conn_handle);
    }
};

// Security callbacks removed for stability - using "Just Works" pairing

/**
 * BLE Characteristic Callbacks
 */
class CharacteristicCallbacks: public NimBLECharacteristicCallbacks {
    void onWrite(NimBLECharacteristic* pCharacteristic) {
        std::string value = pCharacteristic->getValue();

        if (value.length() > 0) {
            commandCount++;
            processNanoKVMPacket((uint8_t*)value.data(), value.length());
        }
    }
};

void setup() {
    // Initialize M5Cardputer
    auto cfg = M5.config();
    M5Cardputer.begin(cfg);

    // Display startup screen
    M5Cardputer.Display.setRotation(1);
    M5Cardputer.Display.setTextColor(GREEN);
    M5Cardputer.Display.setTextSize(2);
    M5Cardputer.Display.setCursor(10, 10);
    M5Cardputer.Display.println("RelayKVM");
    M5Cardputer.Display.setTextSize(1);
    M5Cardputer.Display.setCursor(10, 40);

    // Check for Mass Storage mode (hold 'M' during boot)
    M5Cardputer.Display.println("Hold M for SD mode...");
    delay(500);  // Give user time to press key
    M5Cardputer.update();

    if (checkMassStorageKey()) {
        runMassStorageMode();  // Never returns
    }

    M5Cardputer.Display.println("Initializing USB...");

    // Initialize USB HID + CDC - ORDER MATTERS!
    // All USB devices must be initialized BEFORE USB.begin()
    Keyboard.begin();
    Mouse.begin();
#ifdef DEBUG
    USBSerial.begin();  // CDC Serial for debugging (only in debug mode)
#endif

    // Configure USB identity
    USB.VID(0xFEED); // DIY keyboard community
    USB.PID(0xAE01); // Arthur Endlein initials
    USB.productName("RelayKVM Controller");
    USB.manufacturerName("NanoKVM Project");

    // Start USB stack AFTER all USB devices are ready
    USB.begin();

    // Give USB time to enumerate properly
    delay(1500);

    DEBUG_PRINTLN("RelayKVM: Starting...");
    M5Cardputer.Display.println("USB HID ready!");
    M5Cardputer.Display.println("Initializing BLE...");

    // Initialize BLE with Windows-compatible settings
    NimBLEDevice::init("RelayKVM");

    // Set MTU size - 185 works well across platforms
    NimBLEDevice::setMTU(185);

    // Security settings - "Just Works" pairing
    NimBLEDevice::setSecurityAuth(false, false, false);
    NimBLEDevice::setSecurityIOCap(BLE_HS_IO_NO_INPUT_OUTPUT);

    // Set connection TX power for better stability
    NimBLEDevice::setPower(ESP_PWR_LVL_P9);

    // Create BLE Server
    pServer = NimBLEDevice::createServer();
    pServer->setCallbacks(new ServerCallbacks());

    // Create BLE Service
    NimBLEService *pService = pServer->createService(SERVICE_UUID);

    // Create BLE Characteristics
    pTxCharacteristic = pService->createCharacteristic(
        CHARACTERISTIC_UUID_TX,
        NIMBLE_PROPERTY::NOTIFY
    );

    pRxCharacteristic = pService->createCharacteristic(
        CHARACTERISTIC_UUID_RX,
        NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_NR
    );

    pRxCharacteristic->setCallbacks(new CharacteristicCallbacks());

    // Start service
    pService->start();

    // Configure advertising for Web Bluetooth + Windows compatibility
    NimBLEAdvertising *pAdvertising = NimBLEDevice::getAdvertising();

    // Clear any previous config
    pAdvertising->reset();

    // Set advertising data
    pAdvertising->setName("RelayKVM");
    pAdvertising->addServiceUUID(SERVICE_UUID);

    // Important: Set as connectable (undirected)
    pAdvertising->setAdvertisementType(BLE_GAP_CONN_MODE_UND);

    // Enable scan response for full name
    pAdvertising->setScanResponse(true);

    // Set preferred connection parameters in advertising
    // This helps Windows negotiate faster
    pAdvertising->setMinPreferred(12);  // 12 * 1.25ms = 15ms min
    pAdvertising->setMaxPreferred(24);  // 24 * 1.25ms = 30ms max

    // Start advertising
    pAdvertising->start();

    DEBUG_PRINTLN("BLE advertising started with Windows-compatible parameters");

    M5Cardputer.Display.println("BLE ready!");
    M5Cardputer.Display.println("");
    M5Cardputer.Display.println("Waiting for connection...");
    M5Cardputer.Display.println("Device: RelayKVM");

    DEBUG_PRINTLN("RelayKVM: Ready!");
    DEBUG_PRINTF("VID:PID = 0x%04X:0x%04X\n", 0xFEED, 0xAE01);
}

void loop() {
    M5Cardputer.update();

    // Handle connection state changes (from BLE callback flags)
    if (connectionChanged) {
        connectionChanged = false;

        if (deviceConnected) {
            DEBUG_PRINTLN("Loop: Connection established");
            wakeDisplay();  // Wake display on connection
        } else {
            DEBUG_PRINTLN("Loop: Connection lost, restarting advertising");
            delay(100);  // Brief delay before restart
            NimBLEDevice::startAdvertising();
        }
        updateDisplay();
    }

    // Handle pending display brightness change (from BLE callback)
    if (pendingDisplayChange) {
        pendingDisplayChange = false;
        uint8_t brightness = pendingBrightness;
        DEBUG_PRINTF("Loop: Applying display brightness=%d\n", brightness);
        setDisplayBrightness(brightness);
    }

    // Handle USB state changes (from TinyUSB callbacks)
    if (usbStateChanged) {
        usbStateChanged = false;
        DEBUG_PRINTF("USB state: mounted=%d suspended=%d\n", usbMounted, usbSuspended);
        wakeDisplay();  // Wake display to show USB status change
        updateDisplay();
    }

    // Check display timeout
    checkDisplayTimeout();

    // USB keepalive to prevent Windows from suspending device
    usbKeepalive();

    // Check USB connection state
    checkUsbState();

    // Update display periodically (only if not asleep)
    if (!displayAsleep && millis() - lastStatusUpdate > 1000) {
        lastStatusUpdate = millis();
        if (deviceConnected) {
            updateDisplay();
        }
    }

    // Handle keyboard shortcuts
    if (M5Cardputer.Keyboard.isChange() && M5Cardputer.Keyboard.isPressed()) {
        // Wake display on any key press
        wakeDisplay();

        auto keys = M5Cardputer.Keyboard.keysState().word;

        if (!keys.empty()) {
            char key = keys[0];

            // ESC = Emergency disconnect (check for ESC key code)
            if (M5Cardputer.Keyboard.keysState().del) {  // DEL/ESC key
                DEBUG_PRINTLN("Emergency disconnect requested");
                if (pServer && pServer->getConnectedCount() > 0) {
                    // Disconnect all clients
                    pServer->disconnect(0);
                }
            }

            // G = Release capture (G0 button alternative)
            // Send notification to web interface to exit capture mode
            if (key == 'g' || key == 'G') {
                DEBUG_PRINTLN("Release capture requested (G0)");
                sendReleaseCaptureNotification();
            }

            // W = Wake target computer
            if (key == 'w' || key == 'W') {
                DEBUG_PRINTLN("Wake target requested");
                showWakeMenu();
            }

            // R = Soft reset
            if (key == 'r' || key == 'R') {
                DEBUG_PRINTLN("Soft reset requested");
                softReset();
            }
        }
    }

    delay(10);
}

/**
 * Update status display
 */
void updateDisplay() {

    M5Cardputer.Display.clear();
    M5Cardputer.Display.setTextColor(GREEN);
    M5Cardputer.Display.setTextSize(2);
    M5Cardputer.Display.setCursor(10, 10);
    M5Cardputer.Display.println("RelayKVM");

    M5Cardputer.Display.setTextSize(1);
    M5Cardputer.Display.setCursor(10, 40);

    // Show USB status - prominent warning if disconnected
    if (!usbMounted) {
        M5Cardputer.Display.setTextColor(RED);
        M5Cardputer.Display.println("USB: DISCONNECTED!");
    } else if (usbSuspended) {
        M5Cardputer.Display.setTextColor(ORANGE);
        M5Cardputer.Display.println("USB: Suspended");
    } else {
        M5Cardputer.Display.setTextColor(GREEN);
        M5Cardputer.Display.println("USB: Connected");
    }

    // Show BLE status
    if (deviceConnected) {
        M5Cardputer.Display.setTextColor(GREEN);
        M5Cardputer.Display.println("BLE: CONNECTED");
        M5Cardputer.Display.printf("Commands: %d\n", commandCount);
        M5Cardputer.Display.printf("Battery: %d%%\n", M5Cardputer.Power.getBatteryLevel());
    } else {
        M5Cardputer.Display.setTextColor(YELLOW);
        M5Cardputer.Display.println("BLE: Waiting...");
    }

    M5Cardputer.Display.setTextColor(DARKGREY);
    M5Cardputer.Display.setCursor(10, 100);
    M5Cardputer.Display.println("G=Release W=Wake R=Reset");
}

/**
 * Process NanoKVM protocol packet
 */
void processNanoKVMPacket(uint8_t* packet, size_t len) {
    // Validate minimum packet length
    if (len < 6) {
        DEBUG_PRINTF("Invalid packet: too short (%d bytes)\n", len);
        return;
    }

    // Validate header
    if (packet[0] != HEAD1 || packet[1] != HEAD2) {
        DEBUG_PRINTF("Invalid packet: bad header (0x%02X 0x%02X)\n", packet[0], packet[1]);
        return;
    }

    uint8_t cmd = packet[3];
    uint8_t dataLen = packet[4];
    uint8_t* data = &packet[5];

    // Validate data length
    if (len < 6 + dataLen) {
        DEBUG_PRINTF("Invalid packet: data length mismatch\n");
        return;
    }

    // Process command
    switch (cmd) {
        case CMD_SEND_KB_GENERAL_DATA:
            handleKeyboardCommand(data, dataLen);
            break;

        case CMD_SEND_MS_ABS_DATA:
            handleMouseAbsCommand(data, dataLen);
            break;

        case CMD_SEND_MS_REL_DATA:
            handleMouseRelCommand(data, dataLen);
            break;

        case CMD_GET_INFO:
            DEBUG_PRINTLN("CMD: GET_INFO (not implemented)");
            break;

        case CMD_DISPLAY_CONTROL:
            // Data: [brightness] - 0=off, 64=dim, 255=on
            // Don't call setDisplayBrightness here - it blocks BLE!
            // Set pending flag and handle in main loop
            if (dataLen >= 1) {
                pendingBrightness = data[0];
                pendingDisplayChange = true;
                DEBUG_PRINTF("Display: queued brightness=%d\n", data[0]);
            }
            break;

        case CMD_DISPLAY_TIMEOUT:
            // Data: [seconds_low, seconds_high] - 0 = disabled
            if (dataLen >= 2) {
                uint16_t seconds = data[0] | (data[1] << 8);
                setDisplayTimeout(seconds);
            } else if (dataLen >= 1) {
                setDisplayTimeout(data[0]);
            }
            break;

        case CMD_USB_WAKE:
            // No data needed - just trigger wake
            handleUsbWakeCommand();
            break;

        case CMD_USB_RECONNECT:
            // Soft USB reconnect - re-enumerate without full reset
            handleUsbReconnect();
            break;

        case CMD_DEVICE_RESET:
            // Full device reset
            handleDeviceReset();
            break;

        default:
            DEBUG_PRINTF("Unknown command: 0x%02X\n", cmd);
            break;
    }
}

/**
 * Handle keyboard command (0x02)
 * Data format: [modifier, 0x00, key1, key2, key3, key4, key5, key6]
 */

// Track previously pressed keys to handle release
static uint8_t prevKeys[6] = {0, 0, 0, 0, 0, 0};
static uint8_t prevModifier = 0;

// Modifier bit to key code mapping
static const uint8_t modifierKeys[] = {
    0xE0, // Left Ctrl  (bit 0)
    0xE1, // Left Shift (bit 1)
    0xE2, // Left Alt   (bit 2)
    0xE3, // Left GUI   (bit 3)
    0xE4, // Right Ctrl (bit 4)
    0xE5, // Right Shift (bit 5)
    0xE6, // Right Alt  (bit 6)
    0xE7  // Right GUI  (bit 7)
};

void handleKeyboardCommand(uint8_t* data, size_t len) {
    if (len < 8) {
        DEBUG_PRINTLN("KB: Invalid data length");
        return;
    }

    uint8_t modifier = data[0];
    uint8_t keys[6] = {data[2], data[3], data[4], data[5], data[6], data[7]};

    // Handle modifier changes
    for (int i = 0; i < 8; i++) {
        uint8_t mask = (1 << i);
        bool wasPressed = (prevModifier & mask) != 0;
        bool isPressed = (modifier & mask) != 0;

        if (isPressed && !wasPressed) {
            Keyboard.pressRaw(modifierKeys[i]);
        } else if (!isPressed && wasPressed) {
            Keyboard.releaseRaw(modifierKeys[i]);
        }
    }
    prevModifier = modifier;

    // Release keys that are no longer pressed
    for (int i = 0; i < 6; i++) {
        if (prevKeys[i] != 0) {
            bool stillPressed = false;
            for (int j = 0; j < 6; j++) {
                if (keys[j] == prevKeys[i]) {
                    stillPressed = true;
                    break;
                }
            }
            if (!stillPressed) {
                Keyboard.releaseRaw(prevKeys[i]);
            }
        }
    }

    // Press newly pressed keys
    for (int i = 0; i < 6; i++) {
        if (keys[i] != 0) {
            bool alreadyPressed = false;
            for (int j = 0; j < 6; j++) {
                if (prevKeys[j] == keys[i]) {
                    alreadyPressed = true;
                    break;
                }
            }
            if (!alreadyPressed) {
                Keyboard.pressRaw(keys[i]);
            }
        }
    }

    // Save current state
    memcpy(prevKeys, keys, 6);

    DEBUG_PRINTF("KB: mod=0x%02X keys=[0x%02X 0x%02X 0x%02X 0x%02X 0x%02X 0x%02X]\n",
                  modifier, keys[0], keys[1], keys[2], keys[3], keys[4], keys[5]);
}

/**
 * Handle absolute mouse command (0x04)
 * Data format: [0x02, buttons, x_low, x_high, y_low, y_high, scroll]
 */
void handleMouseAbsCommand(uint8_t* data, size_t len) {
    if (len < 7) {
        DEBUG_PRINTLN("MS_ABS: Invalid data length");
        return;
    }

    uint8_t buttons = data[1];
    uint16_t x = data[2] | (data[3] << 8);
    uint16_t y = data[4] | (data[5] << 8);
    int8_t scroll = (int8_t)data[6];

    // Map 0-4095 to screen coordinates (this is approximate)
    // Note: Absolute positioning requires proper coordinate mapping
    Mouse.move(x, y);
    Mouse.click(buttons);
    if (scroll != 0) {
        Mouse.move(0, 0, scroll);
    }

    DEBUG_PRINTF("MS_ABS: btn=0x%02X x=%d y=%d scroll=%d\n", buttons, x, y, scroll);
}

/**
 * Handle relative mouse command (0x05)
 * Data format: [0x01, buttons, x_delta, y_delta, scroll]
 */
static uint8_t prevMouseButtons = 0;

void handleMouseRelCommand(uint8_t* data, size_t len) {
    if (len < 5) {
        DEBUG_PRINTLN("MS_REL: Invalid data length");
        return;
    }

    uint8_t buttons = data[1];
    int8_t dx = (int8_t)data[2];
    int8_t dy = (int8_t)data[3];
    int8_t scroll = (int8_t)data[4];

    // Handle button changes separately
    // Button bits: 1=left, 2=right, 4=middle
    if (buttons != prevMouseButtons) {
        // Check each button
        for (int i = 0; i < 3; i++) {
            uint8_t mask = (1 << i);
            bool wasPressed = (prevMouseButtons & mask) != 0;
            bool isPressed = (buttons & mask) != 0;

            if (isPressed && !wasPressed) {
                Mouse.press(mask);
            } else if (!isPressed && wasPressed) {
                Mouse.release(mask);
            }
        }
        prevMouseButtons = buttons;
    }

    // Send movement and scroll separately
    if (dx != 0 || dy != 0) {
        Mouse.move(dx, dy);
    }
    if (scroll != 0) {
        Mouse.move(0, 0, scroll);
    }

    DEBUG_PRINTF("MS_REL: btn=0x%02X dx=%d dy=%d scroll=%d\n", buttons, dx, dy, scroll);
}

/**
 * Wake target computer via keyboard activity
 * Works for Sleep (S3) - sends a harmless key combo to wake
 */
bool wakeTargetComputer() {
    M5Cardputer.Display.clear();
    M5Cardputer.Display.setTextSize(2);
    M5Cardputer.Display.setCursor(10, 10);
    M5Cardputer.Display.println("USB Wake");
    M5Cardputer.Display.setTextSize(1);
    M5Cardputer.Display.setCursor(10, 40);

    DEBUG_PRINTLN("Sending wake signal via keyboard activity...");
    M5Cardputer.Display.println("Sending wake signal...");
    M5Cardputer.Display.println("");

    // Method 1: Send a harmless key press/release
    // Most systems wake on any HID activity
    // Using Left Shift which doesn't trigger anything harmful
    Keyboard.pressRaw(0xE1);  // Left Shift
    delay(50);
    Keyboard.releaseRaw(0xE1);

    // Method 2: Small mouse movement as backup
    delay(100);
    Mouse.move(1, 0);
    delay(50);
    Mouse.move(-1, 0);

    DEBUG_PRINTLN("Wake signals sent");
    M5Cardputer.Display.println("Wake signal sent!");
    M5Cardputer.Display.println("");
    M5Cardputer.Display.println("Target should wake up");
    M5Cardputer.Display.println("in 1-3 seconds...");
    M5Cardputer.Display.println("");
    M5Cardputer.Display.setTextColor(DARKGREY);
    M5Cardputer.Display.println("Note: Only works if");
    M5Cardputer.Display.println("target is in Sleep mode");
    M5Cardputer.Display.println("and USB wake is enabled");

    delay(3000);
    updateDisplay();
    return true;
}

/**
 * Show wake menu with options
 */
void showWakeMenu() {
    M5Cardputer.Display.clear();
    M5Cardputer.Display.setTextColor(YELLOW);
    M5Cardputer.Display.setTextSize(2);
    M5Cardputer.Display.setCursor(10, 10);
    M5Cardputer.Display.println("Wake Target");

    M5Cardputer.Display.setTextColor(WHITE);
    M5Cardputer.Display.setTextSize(1);
    M5Cardputer.Display.setCursor(10, 40);
    M5Cardputer.Display.println("U = USB Wake (Sleep/S3)");
    M5Cardputer.Display.println("    Works if target is");
    M5Cardputer.Display.println("    in sleep mode");
    M5Cardputer.Display.println("");
    M5Cardputer.Display.setTextColor(DARKGREY);
    M5Cardputer.Display.println("W = WoL (Shutdown/S5)");
    M5Cardputer.Display.println("    Not yet implemented");
    M5Cardputer.Display.println("");
    M5Cardputer.Display.setTextColor(WHITE);
    M5Cardputer.Display.println("ESC = Cancel");

    // Wait for user input
    bool waiting = true;
    while (waiting) {
        M5Cardputer.update();

        if (M5Cardputer.Keyboard.isChange() && M5Cardputer.Keyboard.isPressed()) {
            auto keys = M5Cardputer.Keyboard.keysState().word;

            if (!keys.empty()) {
                char key = keys[0];

                if (key == 'u' || key == 'U') {
                    wakeTargetComputer();
                    waiting = false;
                }
                else if (key == 'w' || key == 'W') {
                    M5Cardputer.Display.clear();
                    M5Cardputer.Display.setCursor(10, 10);
                    M5Cardputer.Display.println("Wake-on-LAN");
                    M5Cardputer.Display.println("");
                    M5Cardputer.Display.println("Not yet implemented");
                    M5Cardputer.Display.println("");
                    M5Cardputer.Display.println("See docs/WAKE.md for");
                    M5Cardputer.Display.println("implementation guide");
                    delay(3000);
                    waiting = false;
                }
                // ESC check via del key state
                else if (M5Cardputer.Keyboard.keysState().del) {
                    waiting = false;
                }
            }
        }

        delay(10);
    }

    updateDisplay();
}

/**
 * Soft reset the device
 * R = Reset device
 */
void softReset() {
    M5Cardputer.Display.clear();
    M5Cardputer.Display.setTextColor(CYAN);
    M5Cardputer.Display.setTextSize(2);
    M5Cardputer.Display.setCursor(10, 10);
    M5Cardputer.Display.println("Resetting...");

    DEBUG_PRINTLN("Soft reset...");
    delay(500);
    esp_restart();
}

/**
 * Send release capture notification to web interface
 * Uses the TX characteristic to notify the client to exit capture mode
 */
void sendReleaseCaptureNotification() {
    if (!deviceConnected || !pTxCharacteristic) return;

    // Build a packet in NanoKVM format with custom command
    uint8_t packet[] = {
        HEAD1, HEAD2,           // Header
        ADDR_DEFAULT,           // Address
        CMD_RELEASE_CAPTURE,    // Custom command: release capture
        0x00,                   // Data length: 0
        0x00                    // Checksum (simplified)
    };

    pTxCharacteristic->setValue(packet, sizeof(packet));
    pTxCharacteristic->notify();

    DEBUG_PRINTLN("Sent release capture notification");

    // Visual feedback on display
    M5Cardputer.Display.setTextColor(YELLOW);
    M5Cardputer.Display.setCursor(10, 100);
    M5Cardputer.Display.println("Capture release sent!");
    delay(500);
    updateDisplay();
}

/**
 * Display Control Functions
 */

/**
 * Set display brightness
 * 0 = off, 64 = dim, 255 = full
 * Called from main loop (not BLE callback) to avoid blocking BLE stack
 */
void setDisplayBrightness(uint8_t brightness) {
    displayBrightness = brightness;
    displayAsleep = (brightness == DISPLAY_OFF);
    lastActivityTime = millis();

    M5Cardputer.Display.setBrightness(brightness);

    DEBUG_PRINTF("Display: brightness set to %d\n", brightness);
}

/**
 * Set display timeout in seconds (0 = disable)
 */
void setDisplayTimeout(uint16_t seconds) {
    displayTimeout = seconds;
    lastActivityTime = millis();
    DEBUG_PRINTF("Display timeout: %d seconds\n", seconds);
}

/**
 * Wake display from sleep (called on any activity)
 */
void wakeDisplay() {
    lastActivityTime = millis();

    if (displayAsleep) {
        displayAsleep = false;
        // Restore brightness (use ON if was set to OFF)
        uint8_t newBrightness = displayBrightness > 0 ? displayBrightness : DISPLAY_ON;
        displayBrightness = newBrightness;
        M5Cardputer.Display.setBrightness(newBrightness);
        updateDisplay();
        DEBUG_PRINTLN("Display woke from timeout");
    }
}

/**
 * Check display timeout and dim/off if needed
 */
void checkDisplayTimeout() {
    if (displayTimeout == 0 || displayAsleep) return;

    uint32_t elapsed = (millis() - lastActivityTime) / 1000;

    if (elapsed >= displayTimeout) {
        displayAsleep = true;
        M5Cardputer.Display.setBrightness(0);
        M5Cardputer.Display.fillScreen(BLACK);
        DEBUG_PRINTLN("Display off due to timeout");
    }
}

/**
 * Handle USB wake command from web interface
 */
void handleUsbWakeCommand() {
    DEBUG_PRINTLN("USB Wake command received from web");
    wakeDisplay();  // Also wake our display
    wakeTargetComputer();
}

/**
 * Handle USB Recovery - attempts to recover USB connection
 *
 * How it works:
 * - If USB is just suspended: HID activity wakes it up
 * - If USB stack is corrupted: HID calls may crash, triggering watchdog reset
 *   which re-initializes everything and fixes USB
 *
 * Either way, USB should work again after this. May cause device reset if
 * USB is in a bad state - this is expected and actually fixes the problem.
 */
void handleUsbReconnect() {
    DEBUG_PRINTLN("USB Recovery requested");
    wakeDisplay();

    // Show status on display
    M5Cardputer.Display.clear();
    M5Cardputer.Display.setTextColor(YELLOW);
    M5Cardputer.Display.setTextSize(2);
    M5Cardputer.Display.setCursor(10, 10);
    M5Cardputer.Display.println("USB Recovery");
    M5Cardputer.Display.setTextSize(1);
    M5Cardputer.Display.setCursor(10, 40);
    M5Cardputer.Display.println("Attempting recovery...");
    M5Cardputer.Display.setTextColor(DARKGREY);
    M5Cardputer.Display.println("(may reset if needed)");
    M5Cardputer.Display.setTextColor(WHITE);

    delay(500);

    // Send HID activity to wake suspended USB or trigger reset if corrupted
    // If USB stack is broken, these calls may crash and trigger watchdog reset
    for (int i = 0; i < 5; i++) {
        Mouse.move(1, 0);
        delay(20);
        Mouse.move(-1, 0);
        delay(20);
    }

    Keyboard.pressRaw(0xE1);  // Left Shift
    delay(50);
    Keyboard.releaseRaw(0xE1);

    delay(500);

    // If we get here, USB didn't crash - check if it's working
    if (tud_mounted()) {
        M5Cardputer.Display.setTextColor(GREEN);
        M5Cardputer.Display.println("");
        M5Cardputer.Display.println("USB Recovered!");
    } else {
        M5Cardputer.Display.setTextColor(ORANGE);
        M5Cardputer.Display.println("");
        M5Cardputer.Display.println("USB not responding");
        M5Cardputer.Display.println("Try 'Reset Device'");
    }

    delay(2000);
    updateDisplay();
}

/**
 * Handle full device reset
 * This will restart the ESP32 completely
 */
void handleDeviceReset() {
    DEBUG_PRINTLN("Device reset requested");
    wakeDisplay();

    // Show status on display
    M5Cardputer.Display.clear();
    M5Cardputer.Display.setTextColor(RED);
    M5Cardputer.Display.setTextSize(2);
    M5Cardputer.Display.setCursor(10, 10);
    M5Cardputer.Display.println("Resetting...");

    delay(500);

    // Perform software reset
    esp_restart();
}

/**
 * USB Keepalive - Prevents Windows from suspending the USB device
 * Sends a zero-movement mouse report periodically to keep the device "active"
 * This is invisible to the user but keeps Windows from thinking the device is idle
 */
void usbKeepalive() {
    if (millis() - lastUsbKeepalive < USB_KEEPALIVE_INTERVAL) {
        return;
    }
    lastUsbKeepalive = millis();

    // Send a zero-movement mouse report - invisible but keeps USB active
    // This prevents Windows USB Selective Suspend from disconnecting us
    Mouse.move(0, 0, 0);

    // Debug output (comment out for production)
    // USBSerial.println("USB keepalive sent");
}

// ============================================
// USB State Detection (polling-based)
// ============================================

// Check USB state and update flags if changed
void checkUsbState() {
    bool currentMounted = tud_mounted();
    bool currentSuspended = tud_suspended();

    if (currentMounted != usbMounted || currentSuspended != usbSuspended) {
        usbMounted = currentMounted;
        usbSuspended = currentSuspended;
        usbStateChanged = true;
    }
}

// ============================================
// Mass Storage Mode
// ============================================

// SD card pins for M5Cardputer
#define SD_CS_PIN 12
#define SD_SCK_PIN 40
#define SD_MISO_PIN 39
#define SD_MOSI_PIN 14

USBMSC msc;
static uint32_t sdCardSectors = 0;
static uint16_t sdCardSectorSize = 512;

/**
 * USB MSC callbacks for SD card access
 */
static int32_t mscReadCallback(uint32_t lba, uint32_t offset, void* buffer, uint32_t bufsize) {
    // Read sectors from SD card
    uint32_t count = bufsize / sdCardSectorSize;
    if (!SD.readRAW((uint8_t*)buffer, lba)) {
        return -1;
    }
    return bufsize;
}

static int32_t mscWriteCallback(uint32_t lba, uint32_t offset, uint8_t* buffer, uint32_t bufsize) {
    // Write sectors to SD card
    if (!SD.writeRAW(buffer, lba)) {
        return -1;
    }
    return bufsize;
}

static bool mscStartStopCallback(uint8_t power_condition, bool start, bool load_eject) {
    // Handle eject request
    if (load_eject && !start) {
        // User clicked "Eject" - show message
        M5Cardputer.Display.fillScreen(BLACK);
        M5Cardputer.Display.setCursor(10, 60);
        M5Cardputer.Display.setTextColor(GREEN);
        M5Cardputer.Display.println("Safe to remove or reset");
    }
    return true;
}

/**
 * Check if 'M' key is held during boot
 */
bool checkMassStorageKey() {
    if (M5Cardputer.Keyboard.isChange() || M5Cardputer.Keyboard.isPressed()) {
        auto keys = M5Cardputer.Keyboard.keysState().word;
        for (char c : keys) {
            if (c == 'm' || c == 'M') {
                return true;
            }
        }
    }
    return false;
}

/**
 * Run as USB Mass Storage device - exposes SD card
 * This function never returns - user must reset to exit
 */
void runMassStorageMode() {
    M5Cardputer.Display.fillScreen(BLACK);
    M5Cardputer.Display.setTextColor(ORANGE);
    M5Cardputer.Display.setTextSize(2);
    M5Cardputer.Display.setCursor(10, 10);
    M5Cardputer.Display.println("SD Card Mode");
    M5Cardputer.Display.setTextSize(1);
    M5Cardputer.Display.setCursor(10, 40);
    M5Cardputer.Display.setTextColor(WHITE);

    // Initialize SPI for SD card
    SPI.begin(SD_SCK_PIN, SD_MISO_PIN, SD_MOSI_PIN, SD_CS_PIN);

    // Initialize SD card
    if (!SD.begin(SD_CS_PIN, SPI, 25000000)) {
        M5Cardputer.Display.setTextColor(RED);
        M5Cardputer.Display.println("SD card init failed!");
        M5Cardputer.Display.println("");
        M5Cardputer.Display.println("Insert SD card and reset");
        while (true) {
            delay(1000);
        }
    }

    // Get SD card info
    uint64_t cardSize = SD.cardSize();
    sdCardSectors = cardSize / sdCardSectorSize;

    M5Cardputer.Display.printf("Card: %llu MB\n", cardSize / (1024 * 1024));
    M5Cardputer.Display.println("Initializing USB MSC...");

    // Configure USB Mass Storage
    msc.vendorID("RelayKVM");
    msc.productID("SD Card");
    msc.productRevision("1.0");
    msc.onRead(mscReadCallback);
    msc.onWrite(mscWriteCallback);
    msc.onStartStop(mscStartStopCallback);
    msc.mediaPresent(true);

    // Start MSC with SD card size
    msc.begin(sdCardSectors, sdCardSectorSize);

    // Configure USB identity
    USB.VID(0xFEED);
    USB.PID(0xAE02);  // Different PID for MSC mode
    USB.productName("RelayKVM SD Card");
    USB.manufacturerName("NanoKVM Project");

    // Start USB
    USB.begin();

    M5Cardputer.Display.setTextColor(GREEN);
    M5Cardputer.Display.println("");
    M5Cardputer.Display.println("USB Mass Storage active!");
    M5Cardputer.Display.println("");
    M5Cardputer.Display.setTextColor(WHITE);
    M5Cardputer.Display.println("Copy web-bluetooth folder");
    M5Cardputer.Display.println("to SD card root.");
    M5Cardputer.Display.println("");
    M5Cardputer.Display.setTextColor(YELLOW);
    M5Cardputer.Display.println("Reset to exit SD mode");

    // Stay in MSC mode forever
    while (true) {
        M5Cardputer.update();
        delay(100);
    }
}
