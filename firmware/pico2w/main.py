"""
RelayKVM for Raspberry Pi Pico 2W - MicroPython
BLE Nordic UART + USB HID implementation

Requires MicroPython 1.22+ with USB HID support.
"""

import bluetooth
import time
import json
import os
import machine
import rp2
import hmac
import hashlib
from micropython import const
from machine import Pin

# =============================================================================
# Configuration System
# =============================================================================

CONFIG_FILE = "/config.json"

DEFAULT_CONFIG = {
    "pairing_mode": False,
    "security_level": "open",  # "open" or "paired_only"
    "paired_browsers": {}  # browser_id -> hex_encoded_key
}

def load_config():
    """Load config from flash, return defaults if not found"""
    try:
        with open(CONFIG_FILE, "r") as f:
            config = json.load(f)
            # Merge with defaults for any missing keys
            for key, value in DEFAULT_CONFIG.items():
                if key not in config:
                    config[key] = value
            return config
    except (OSError, ValueError):
        return DEFAULT_CONFIG.copy()

def save_config(config):
    """Save config to flash"""
    try:
        with open(CONFIG_FILE, "w") as f:
            json.dump(config, f)
        return True
    except OSError as e:
        print(f"Config save error: {e}")
        return False

def get_unique_id():
    """Get Pico's unique flash ID as hex string"""
    return machine.unique_id().hex()

# =============================================================================
# Boot Mode Detection
# =============================================================================

# Load config early to determine USB mode
_config = load_config()
PAIRING_MODE = _config.get("pairing_mode", False)

if PAIRING_MODE:
    # Clear the flag immediately so next boot is normal
    _config["pairing_mode"] = False
    save_config(_config)
    print("=== PAIRING MODE ===")
    print("USB CDC active for WebSerial pairing")

    # Initialize USB CDC (serial) instead of HID
    try:
        from usb.device.cdc import CDCInterface
        import usb.device

        cdc = CDCInterface()
        usb.device.get().init(cdc, builtin_driver=True)
        USB_MODE = "cdc"
        USB_HID_AVAILABLE = False
        print("USB CDC initialized")
    except Exception as e:
        print(f"USB CDC init failed: {e}")
        USB_MODE = "none"
        USB_HID_AVAILABLE = False
else:
    # Normal mode - USB HID
    USB_MODE = "hid"
    # Try to import USB HID - may need different import depending on MicroPython version
    try:
        from usb.device.hid import HIDInterface
        import usb.device
        USB_HID_AVAILABLE = True
    except ImportError:
        try:
            import usb_hid
            USB_HID_AVAILABLE = True
        except ImportError:
            USB_HID_AVAILABLE = False
            print("WARNING: USB HID not available in this MicroPython build")

# BLE UUIDs for Nordic UART Service (NUS)
_NUS_UUID = bluetooth.UUID("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
_NUS_RX_UUID = bluetooth.UUID("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
_NUS_TX_UUID = bluetooth.UUID("6e400003-b5a3-f393-e0a9-e50e24dcca9e")

_FLAG_READ = const(0x0002)
_FLAG_WRITE_NO_RESPONSE = const(0x0004)
_FLAG_WRITE = const(0x0008)
_FLAG_NOTIFY = const(0x0010)

_NUS_SERVICE = (
    _NUS_UUID,
    (
        (_NUS_TX_UUID, _FLAG_READ | _FLAG_NOTIFY),
        (_NUS_RX_UUID, _FLAG_WRITE | _FLAG_WRITE_NO_RESPONSE),
    ),
)

# NanoKVM protocol constants
HEAD1 = const(0x57)
HEAD2 = const(0xAB)
CMD_GET_INFO = const(0x01)
CMD_SEND_KB_GENERAL_DATA = const(0x02)
CMD_SEND_KB_MEDIA_DATA = const(0x03)
CMD_SEND_MS_ABS_DATA = const(0x04)
CMD_SEND_MS_REL_DATA = const(0x05)
CMD_LED_CTRL = const(0xE0)  # LED control: payload[0] = mode (0=auto, 1=off, 2=on)

# Security/pairing commands (0xA0-0xAF range)
CMD_REQUEST_PAIRING = const(0xA0)   # Browser requests pairing mode
CMD_PAIRING_STATUS = const(0xA1)    # Pico responds with status
CMD_AUTH_CHALLENGE = const(0xA4)    # Pico sends challenge on connect
CMD_AUTH_RESPONSE = const(0xA5)     # Browser sends HMAC response
CMD_AUTH_RESULT = const(0xA6)       # Pico sends auth result

# Pairing status codes
PAIRING_PRESS_BOOTSEL = const(0)    # Waiting for BOOTSEL press
PAIRING_ENTERING = const(1)         # Entering pairing mode (rebooting)
PAIRING_TIMEOUT = const(2)          # BOOTSEL not pressed in time
PAIRING_ALREADY = const(3)          # Already in pairing mode

# Auth result codes
AUTH_FAIL = const(0)
AUTH_SUCCESS = const(1)
AUTH_NOT_REQUIRED = const(2)        # Security level is "open"

# LED modes
LED_MODE_OFF = const(0)
LED_MODE_ON = const(1)
LED_MODE_DOUBLE_BLINK = const(2)      # Disconnected: double blink every 3s
LED_MODE_DOUBLE_OFF_BLINK = const(3)  # Connected: double off-blink every 3s
LED_MODE_SLOW_TOGGLE = const(4)       # Command mode: 500ms toggle
LED_MODE_FAST_BLINK = const(5)        # Waiting for BOOTSEL: fast blink

# HID Report Descriptors
KEYBOARD_REPORT_DESC = bytes([
    0x05, 0x01,  # Usage Page (Generic Desktop)
    0x09, 0x06,  # Usage (Keyboard)
    0xA1, 0x01,  # Collection (Application)
    0x85, 0x01,  #   Report ID (1)
    0x05, 0x07,  #   Usage Page (Key Codes)
    0x19, 0xE0,  #   Usage Minimum (224)
    0x29, 0xE7,  #   Usage Maximum (231)
    0x15, 0x00,  #   Logical Minimum (0)
    0x25, 0x01,  #   Logical Maximum (1)
    0x75, 0x01,  #   Report Size (1)
    0x95, 0x08,  #   Report Count (8)
    0x81, 0x02,  #   Input (Data, Variable, Absolute) - Modifier byte
    0x95, 0x01,  #   Report Count (1)
    0x75, 0x08,  #   Report Size (8)
    0x81, 0x01,  #   Input (Constant) - Reserved byte
    0x95, 0x06,  #   Report Count (6)
    0x75, 0x08,  #   Report Size (8)
    0x15, 0x00,  #   Logical Minimum (0)
    0x25, 0x65,  #   Logical Maximum (101)
    0x05, 0x07,  #   Usage Page (Key Codes)
    0x19, 0x00,  #   Usage Minimum (0)
    0x29, 0x65,  #   Usage Maximum (101)
    0x81, 0x00,  #   Input (Data, Array) - Key array
    0xC0,        # End Collection
])

MOUSE_REPORT_DESC = bytes([
    0x05, 0x01,  # Usage Page (Generic Desktop)
    0x09, 0x02,  # Usage (Mouse)
    0xA1, 0x01,  # Collection (Application)
    0x09, 0x01,  #   Usage (Pointer)
    0xA1, 0x00,  #   Collection (Physical)
    0x85, 0x02,  #     Report ID (2)
    0x05, 0x09,  #     Usage Page (Buttons)
    0x19, 0x01,  #     Usage Minimum (1)
    0x29, 0x03,  #     Usage Maximum (3)
    0x15, 0x00,  #     Logical Minimum (0)
    0x25, 0x01,  #     Logical Maximum (1)
    0x95, 0x03,  #     Report Count (3)
    0x75, 0x01,  #     Report Size (1)
    0x81, 0x02,  #     Input (Data, Variable, Absolute) - Buttons
    0x95, 0x01,  #     Report Count (1)
    0x75, 0x05,  #     Report Size (5)
    0x81, 0x01,  #     Input (Constant) - Padding
    0x05, 0x01,  #     Usage Page (Generic Desktop)
    0x09, 0x30,  #     Usage (X)
    0x09, 0x31,  #     Usage (Y)
    0x09, 0x38,  #     Usage (Wheel)
    0x15, 0x81,  #     Logical Minimum (-127)
    0x25, 0x7F,  #     Logical Maximum (127)
    0x75, 0x08,  #     Report Size (8)
    0x95, 0x03,  #     Report Count (3)
    0x81, 0x06,  #     Input (Data, Variable, Relative)
    0xC0,        #   End Collection
    0xC0,        # End Collection
])

CONSUMER_REPORT_DESC = bytes([
    0x05, 0x0C,  # Usage Page (Consumer)
    0x09, 0x01,  # Usage (Consumer Control)
    0xA1, 0x01,  # Collection (Application)
    0x85, 0x03,  #   Report ID (3)
    0x15, 0x00,  #   Logical Minimum (0)
    0x26, 0xFF, 0x03,  # Logical Maximum (1023)
    0x19, 0x00,  #   Usage Minimum (0)
    0x2A, 0xFF, 0x03,  # Usage Maximum (1023)
    0x75, 0x10,  #   Report Size (16)
    0x95, 0x01,  #   Report Count (1)
    0x81, 0x00,  #   Input (Data, Array)
    0xC0,        # End Collection
])

# Absolute Mouse (Digitizer/Tablet style) - for seamless mode
# Note: "In Range" bit is required for OS to accept coordinates
ABS_MOUSE_REPORT_DESC = bytes([
    0x05, 0x0D,  # Usage Page (Digitizer)
    0x09, 0x02,  # Usage (Pen)
    0xA1, 0x01,  # Collection (Application)
    0x85, 0x04,  #   Report ID (4)

    # Buttons: In Range, Tip Switch, Barrel Switch (3 bits)
    0x09, 0x32,  #   Usage (In Range) - REQUIRED for coordinates to work
    0x09, 0x42,  #   Usage (Tip Switch) - left click
    0x09, 0x44,  #   Usage (Barrel Switch) - right click
    0x15, 0x00,  #   Logical Minimum (0)
    0x25, 0x01,  #   Logical Maximum (1)
    0x75, 0x01,  #   Report Size (1)
    0x95, 0x03,  #   Report Count (3)
    0x81, 0x02,  #   Input (Data, Variable, Absolute)

    # Padding (5 bits to make 1 byte)
    0x95, 0x05,  #   Report Count (5)
    0x81, 0x03,  #   Input (Constant)

    # X coordinate (absolute, 0-32767)
    0x05, 0x01,  #   Usage Page (Generic Desktop)
    0x09, 0x30,  #   Usage (X)
    0x15, 0x00,  #   Logical Minimum (0)
    0x26, 0xFF, 0x7F,  # Logical Maximum (32767)
    0x75, 0x10,  #   Report Size (16)
    0x95, 0x01,  #   Report Count (1)
    0x81, 0x02,  #   Input (Data, Variable, Absolute)

    # Y coordinate (absolute, 0-32767)
    0x09, 0x31,  #   Usage (Y)
    0x81, 0x02,  #   Input (Data, Variable, Absolute)

    0xC0,        # End Collection
])


class BLEHID:
    """BLE Nordic UART + USB HID handler"""

    def __init__(self):
        self._ble = bluetooth.BLE()
        self._ble.active(True)
        self._ble.irq(self._irq)

        # Register NUS service
        ((self._tx_handle, self._rx_handle),) = self._ble.gatts_register_services((_NUS_SERVICE,))

        self._connections = set()
        self._rx_buffer = bytearray()

        # LED for status
        self._led = Pin("LED", Pin.OUT)
        self._led.off()
        self._led_mode = LED_MODE_DOUBLE_BLINK  # Start in disconnected pattern
        self._led_state = False  # Current on/off state
        self._led_last_update = time.ticks_ms()
        self._led_pattern_step = 0  # For multi-step patterns

        # USB HID setup (will be initialized if available)
        self._hid = None
        self._setup_usb_hid()

        # Security state
        self._config = load_config()
        self._authenticated = False  # Set True after successful auth or if security_level is "open"
        self._pending_challenge = None  # Current challenge waiting for response
        self._bootsel_wait_until = 0  # Timestamp when BOOTSEL wait expires

    def _setup_usb_hid(self):
        """Initialize USB HID if available"""
        if not USB_HID_AVAILABLE:
            return

        try:
            # Combined report descriptor (keyboard + mouse + consumer + absolute mouse)
            report_desc = KEYBOARD_REPORT_DESC + MOUSE_REPORT_DESC + CONSUMER_REPORT_DESC + ABS_MOUSE_REPORT_DESC

            # Try newer MicroPython USB HID API
            try:
                from usb.device.hid import HIDInterface
                import usb.device

                class RelayKVMHID(HIDInterface):
                    def __init__(self):
                        super().__init__(
                            report_desc,
                            set_report_buf=bytearray(9),
                            protocol=1,  # Keyboard
                            interface_str="RelayKVM HID",
                        )

                self._hid = RelayKVMHID()
                usb.device.get().init(self._hid, builtin_driver=True)
                print("USB HID initialized (new API)")

            except Exception as e:
                print(f"New USB HID API failed: {e}")
                # Try alternative method
                self._hid = None

        except Exception as e:
            print(f"USB HID setup failed: {e}")
            self._hid = None

    def set_led_mode(self, mode):
        """Set LED mode and reset pattern state"""
        self._led_mode = mode
        self._led_pattern_step = 0
        self._led_last_update = time.ticks_ms()

        # Set initial state for static modes
        if mode == LED_MODE_OFF:
            self._led.off()
            self._led_state = False
        elif mode == LED_MODE_ON:
            self._led.on()
            self._led_state = True

    def update_led_pattern(self):
        """Update LED based on current mode pattern - call from main loop"""
        now = time.ticks_ms()
        elapsed = time.ticks_diff(now, self._led_last_update)

        if self._led_mode == LED_MODE_OFF or self._led_mode == LED_MODE_ON:
            # Static modes, nothing to update
            return

        elif self._led_mode == LED_MODE_DOUBLE_BLINK:
            # Disconnected: OFF, then double blink every 3s
            # Pattern: off(2700ms) -> on(100ms) -> off(100ms) -> on(100ms) -> repeat
            timings = [2700, 100, 100, 100]  # off, on, off, on
            if elapsed >= timings[self._led_pattern_step]:
                self._led_pattern_step = (self._led_pattern_step + 1) % 4
                self._led_last_update = now
                # Steps 1 and 3 are ON, steps 0 and 2 are OFF
                if self._led_pattern_step in (1, 3):
                    self._led.on()
                else:
                    self._led.off()

        elif self._led_mode == LED_MODE_DOUBLE_OFF_BLINK:
            # Connected: ON, then double off-blink every 3s
            # Pattern: on(2700ms) -> off(100ms) -> on(100ms) -> off(100ms) -> repeat
            timings = [2700, 100, 100, 100]  # on, off, on, off
            if elapsed >= timings[self._led_pattern_step]:
                self._led_pattern_step = (self._led_pattern_step + 1) % 4
                self._led_last_update = now
                # Steps 0 and 2 are ON, steps 1 and 3 are OFF
                if self._led_pattern_step in (0, 2):
                    self._led.on()
                else:
                    self._led.off()

        elif self._led_mode == LED_MODE_SLOW_TOGGLE:
            # Command mode: 500ms toggle
            if elapsed >= 500:
                self._led_last_update = now
                self._led_state = not self._led_state
                if self._led_state:
                    self._led.on()
                else:
                    self._led.off()

        elif self._led_mode == LED_MODE_FAST_BLINK:
            # Waiting for BOOTSEL: 100ms toggle (fast blink)
            if elapsed >= 100:
                self._led_last_update = now
                self._led_state = not self._led_state
                if self._led_state:
                    self._led.on()
                else:
                    self._led.off()

    def _irq(self, event, data):
        """BLE interrupt handler"""
        if event == 1:  # _IRQ_CENTRAL_CONNECT
            conn_handle, _, _ = data
            self._connections.add(conn_handle)
            self.set_led_mode(LED_MODE_ON)  # Connected: solid
            print(f"Connected: {conn_handle}")
            # Reset auth state and send challenge
            self._authenticated = False
            # Small delay to ensure connection is stable before sending challenge
            # Challenge will be sent from main loop after a short delay

        elif event == 2:  # _IRQ_CENTRAL_DISCONNECT
            conn_handle, _, _ = data
            self._connections.discard(conn_handle)
            self.set_led_mode(LED_MODE_DOUBLE_BLINK)  # Disconnected: double blink
            print(f"Disconnected: {conn_handle}")
            # Restart advertising
            self._advertise()

        elif event == 3:  # _IRQ_GATTS_WRITE
            conn_handle, value_handle = data
            if value_handle == self._rx_handle:
                value = self._ble.gatts_read(self._rx_handle)
                self._rx_buffer.extend(value)

    def _advertise(self):
        """Start BLE advertising"""
        name = b"RelayKVM-Pico"

        # Advertising payload
        adv_data = bytearray()
        # Flags
        adv_data += bytes([0x02, 0x01, 0x06])
        # Complete name
        adv_data += bytes([len(name) + 1, 0x09]) + name

        # Scan response with service UUID (128-bit, little-endian)
        # NUS UUID: 6e400001-b5a3-f393-e0a9-e50e24dcca9e
        uuid_bytes = bytes([
            0x9e, 0xca, 0xdc, 0x24, 0x0e, 0xe5, 0xa9, 0xe0,
            0x93, 0xf3, 0xa3, 0xb5, 0x01, 0x00, 0x40, 0x6e
        ])
        resp_data = bytearray()
        resp_data += bytes([len(uuid_bytes) + 1, 0x07]) + uuid_bytes

        self._ble.gap_advertise(100_000, adv_data=adv_data, resp_data=resp_data)
        print("Advertising as 'RelayKVM-Pico'...")

    def send_keyboard(self, modifier, keys):
        """Send keyboard HID report"""
        if self._hid is None:
            return

        # Report: [ReportID, Modifier, Reserved, Key1-Key6]
        report = bytes([0x01, modifier, 0x00] + list(keys[:6]) + [0] * (6 - len(keys)))
        try:
            self._hid.send_report(report)
        except Exception as e:
            print(f"Keyboard send error: {e}")

    def send_mouse(self, buttons, dx, dy, wheel):
        """Send mouse HID report"""
        if self._hid is None:
            return

        # Convert to signed bytes
        dx = dx if dx < 128 else dx - 256
        dy = dy if dy < 128 else dy - 256
        wheel = wheel if wheel < 128 else wheel - 256

        # Report: [ReportID, Buttons, X, Y, Wheel]
        report = bytes([0x02, buttons, dx & 0xFF, dy & 0xFF, wheel & 0xFF])
        try:
            self._hid.send_report(report)
        except Exception as e:
            print(f"Mouse send error: {e}")

    def send_consumer(self, code):
        """Send consumer control HID report"""
        if self._hid is None:
            return

        # Report: [ReportID, CodeLow, CodeHigh]
        report = bytes([0x03, code & 0xFF, (code >> 8) & 0xFF])
        try:
            self._hid.send_report(report)
        except Exception as e:
            print(f"Consumer send error: {e}")

    def send_mouse_absolute(self, buttons, x, y):
        """Send absolute mouse HID report (digitizer-style)"""
        if self._hid is None:
            return

        # Convert NanoKVM button format to HID digitizer format
        # NanoKVM: bit0=left, bit1=right
        # HID digitizer: bit0=in_range, bit1=tip(left), bit2=barrel(right)
        # In Range MUST be set for OS to accept coordinates
        hid_buttons = 0x01  # Always set In Range bit
        if buttons & 0x01:  # Left click -> Tip switch
            hid_buttons |= 0x02
        if buttons & 0x02:  # Right click -> Barrel switch
            hid_buttons |= 0x04

        # Report: [ReportID, Buttons, X_Low, X_High, Y_Low, Y_High]
        report = bytes([
            0x04,
            hid_buttons,
            x & 0xFF, (x >> 8) & 0xFF,
            y & 0xFF, (y >> 8) & 0xFF
        ])
        try:
            self._hid.send_report(report)
        except Exception as e:
            print(f"Absolute mouse send error: {e}")

    # =========================================================================
    # Security / Pairing Methods
    # =========================================================================

    def _send_ble_packet(self, cmd, payload=b""):
        """Send a NanoKVM-style packet over BLE"""
        # Format: HEAD1 HEAD2 ADDR CMD LEN PAYLOAD CHECKSUM
        addr = 0x00
        length = len(payload)
        packet = bytes([HEAD1, HEAD2, addr, cmd, length]) + payload
        checksum = sum(packet) & 0xFF
        packet += bytes([checksum])

        for conn_handle in self._connections:
            try:
                self._ble.gatts_notify(conn_handle, self._tx_handle, packet)
            except Exception as e:
                print(f"BLE send error: {e}")

    def _check_bootsel(self):
        """Check if BOOTSEL button is currently pressed"""
        return rp2.bootsel_button()

    def _start_auth_challenge(self):
        """Send authentication challenge to connected browser"""
        security_level = self._config.get("security_level", "open")

        if security_level == "open":
            # No auth required
            self._authenticated = True
            self._send_ble_packet(CMD_AUTH_RESULT, bytes([AUTH_NOT_REQUIRED]))
            print("Auth: open mode, no challenge required")
            return

        # Generate random challenge
        self._pending_challenge = os.urandom(32)
        self._authenticated = False
        self._send_ble_packet(CMD_AUTH_CHALLENGE, self._pending_challenge)
        print("Auth: challenge sent")

    def _handle_auth_response(self, response):
        """Verify HMAC response from browser"""
        if self._pending_challenge is None:
            print("Auth: no pending challenge")
            self._send_ble_packet(CMD_AUTH_RESULT, bytes([AUTH_FAIL]))
            return

        # Check all paired browsers for a matching key
        paired = self._config.get("paired_browsers", {})
        for browser_id, key_hex in paired.items():
            try:
                key = bytes.fromhex(key_hex)
                expected = hmac.new(key, self._pending_challenge, hashlib.sha256).digest()
                if response == expected:
                    self._authenticated = True
                    self._pending_challenge = None
                    self._send_ble_packet(CMD_AUTH_RESULT, bytes([AUTH_SUCCESS]))
                    print(f"Auth: success (browser: {browser_id[:8]}...)")
                    return
            except Exception as e:
                print(f"Auth check error: {e}")

        # No matching key found
        self._authenticated = False
        self._pending_challenge = None
        self._send_ble_packet(CMD_AUTH_RESULT, bytes([AUTH_FAIL]))
        print("Auth: failed - no matching key")

    def _handle_pairing_request(self):
        """Handle request to enter pairing mode - requires BOOTSEL press"""
        if PAIRING_MODE:
            # Already in pairing mode
            self._send_ble_packet(CMD_PAIRING_STATUS, bytes([PAIRING_ALREADY]))
            return

        # Start waiting for BOOTSEL
        self._bootsel_wait_until = time.ticks_add(time.ticks_ms(), 10000)  # 10 second timeout
        self.set_led_mode(LED_MODE_FAST_BLINK)
        self._send_ble_packet(CMD_PAIRING_STATUS, bytes([PAIRING_PRESS_BOOTSEL]))
        print("Pairing: waiting for BOOTSEL press (10s timeout)")

    def _check_bootsel_wait(self):
        """Check if we're waiting for BOOTSEL and handle timeout/press"""
        if self._bootsel_wait_until == 0:
            return  # Not waiting

        now = time.ticks_ms()

        if self._check_bootsel():
            # BOOTSEL pressed! Enter pairing mode
            print("Pairing: BOOTSEL pressed, entering pairing mode...")
            self._bootsel_wait_until = 0
            self.set_led_mode(LED_MODE_ON)
            self._send_ble_packet(CMD_PAIRING_STATUS, bytes([PAIRING_ENTERING]))

            # Set flag and reboot
            config = load_config()
            config["pairing_mode"] = True
            save_config(config)

            time.sleep_ms(500)  # Let the BLE packet send
            machine.reset()

        elif time.ticks_diff(now, self._bootsel_wait_until) > 0:
            # Timeout
            print("Pairing: BOOTSEL timeout")
            self._bootsel_wait_until = 0
            self.set_led_mode(LED_MODE_ON if self._connections else LED_MODE_DOUBLE_BLINK)
            self._send_ble_packet(CMD_PAIRING_STATUS, bytes([PAIRING_TIMEOUT]))

    def process_packet(self, data):
        """Parse and process NanoKVM protocol packet"""
        if len(data) < 6:
            return None

        if data[0] != HEAD1 or data[1] != HEAD2:
            return None

        cmd = data[3]
        length = data[4]

        if len(data) < 5 + length + 1:
            return None

        payload = data[5:5 + length]

        if cmd == CMD_SEND_KB_GENERAL_DATA:
            if len(payload) >= 8:
                modifier = payload[0]
                keys = list(payload[2:8])
                self.send_keyboard(modifier, keys)

        elif cmd == CMD_SEND_KB_MEDIA_DATA:
            if len(payload) >= 2:
                code = payload[0] | (payload[1] << 8)
                self.send_consumer(code)
                if code != 0:
                    time.sleep_ms(50)
                    self.send_consumer(0)  # Release

        elif cmd == CMD_SEND_MS_ABS_DATA:
            # Absolute mouse: [0x02, buttons, x_low, x_high, y_low, y_high, scroll]
            if len(payload) >= 7:
                buttons = payload[1]
                x = payload[2] | (payload[3] << 8)
                y = payload[4] | (payload[5] << 8)
                scroll = payload[6]
                self.send_mouse_absolute(buttons, x, y)
                # Handle scroll using relative mouse (absolute doesn't have scroll)
                if scroll != 0:
                    wheel = scroll if scroll < 128 else scroll - 256
                    self.send_mouse(0, 0, 0, wheel)

        elif cmd == CMD_SEND_MS_REL_DATA:
            if len(payload) >= 5:
                buttons = payload[1]
                dx = payload[2]
                dy = payload[3]
                wheel = payload[4]
                self.send_mouse(buttons, dx, dy, wheel)

        elif cmd == CMD_LED_CTRL:
            if len(payload) >= 1:
                mode = payload[0]
                if mode <= 5:  # Valid modes: 0-5
                    self.set_led_mode(mode)
                    mode_names = ['off', 'on', 'double_blink', 'double_off_blink', 'slow_toggle', 'fast_blink']
                    print(f"LED mode: {mode_names[mode]}")

        # Security commands
        elif cmd == CMD_REQUEST_PAIRING:
            self._handle_pairing_request()

        elif cmd == CMD_AUTH_RESPONSE:
            if len(payload) >= 32:
                self._handle_auth_response(bytes(payload[:32]))

        return 5 + length + 1  # Packet size

    def run(self):
        """Main loop"""
        print("RelayKVM-Pico starting...")
        print(f"USB HID available: {self._hid is not None}")
        print(f"Security level: {self._config.get('security_level', 'open')}")
        print(f"Paired browsers: {len(self._config.get('paired_browsers', {}))}")

        self._advertise()

        # Track if we need to send challenge after connect
        send_challenge_at = 0

        while True:
            now = time.ticks_ms()

            # Check if we just connected and need to send challenge
            if self._connections and not self._authenticated and self._pending_challenge is None:
                if send_challenge_at == 0:
                    # Schedule challenge send after 500ms delay for connection stability
                    send_challenge_at = time.ticks_add(now, 500)
                elif time.ticks_diff(now, send_challenge_at) >= 0:
                    self._start_auth_challenge()
                    send_challenge_at = 0

            # Reset challenge timer if disconnected
            if not self._connections:
                send_challenge_at = 0

            # Check BOOTSEL wait state
            self._check_bootsel_wait()

            # Process any received data
            while len(self._rx_buffer) >= 6:
                # Look for packet header
                if self._rx_buffer[0] != HEAD1:
                    self._rx_buffer.pop(0)
                    continue
                if len(self._rx_buffer) < 2 or self._rx_buffer[1] != HEAD2:
                    self._rx_buffer.pop(0)
                    continue

                # Check if complete packet
                if len(self._rx_buffer) < 5:
                    break

                length = self._rx_buffer[4]
                packet_size = 6 + length

                if len(self._rx_buffer) < packet_size:
                    break

                # Process packet
                packet_data = bytes(self._rx_buffer[:packet_size])
                self._rx_buffer = self._rx_buffer[packet_size:]
                self.process_packet(packet_data)

            # Update LED pattern
            self.update_led_pattern()

            time.sleep_ms(1)


# =============================================================================
# Pairing Mode Handler (CDC Serial)
# =============================================================================

def run_pairing_mode():
    """Handle pairing over USB CDC serial"""
    import sys

    print("=== PAIRING MODE ACTIVE ===")
    print(f"Pico ID: {get_unique_id()}")
    print("Waiting for pairing commands over USB serial...")
    print("Commands: ID?, PAIR:<id>:<key>, STATUS, DONE")

    led = Pin("LED", Pin.OUT)
    last_toggle = time.ticks_ms()

    # Simple line-based protocol over CDC
    # Commands:
    #   PAIR:<browser_id>:<hex_key>  - Store pairing key
    #   ID?                          - Request Pico ID
    #   DONE                         - Exit pairing mode (reboot)

    buffer = ""
    while True:
        # Non-blocking read using micropython's uart/usb polling
        try:
            # Try to read available characters
            while True:
                try:
                    # Use sys.stdin.buffer for raw access if available
                    if hasattr(sys.stdin, 'buffer'):
                        data = sys.stdin.buffer.read(1)
                    else:
                        data = sys.stdin.read(1)

                    if data is None or len(data) == 0:
                        break

                    char = data.decode() if isinstance(data, bytes) else data
                    if char == '\n' or char == '\r':
                        if buffer:
                            handle_pairing_command(buffer.strip())
                            buffer = ""
                    else:
                        buffer += char
                except Exception:
                    break
        except Exception:
            pass

        # Toggle LED to show we're alive (every 500ms)
        now = time.ticks_ms()
        if time.ticks_diff(now, last_toggle) >= 500:
            led.toggle()
            last_toggle = now

        time.sleep_ms(10)

def handle_pairing_command(cmd):
    """Process a pairing command"""
    print(f"Pairing cmd: {cmd}")

    if cmd == "ID?":
        # Return Pico's unique ID
        print(f"ID:{get_unique_id()}")

    elif cmd.startswith("PAIR:"):
        # Format: PAIR:<browser_id>:<hex_key>
        parts = cmd.split(":", 2)
        if len(parts) == 3:
            browser_id = parts[1]
            key_hex = parts[2]

            # Validate key length (should be 64 hex chars = 32 bytes)
            if len(key_hex) == 64:
                config = load_config()
                config["paired_browsers"][browser_id] = key_hex
                config["security_level"] = "paired_only"  # Enable security
                save_config(config)
                print(f"OK:paired:{browser_id[:8]}...")
            else:
                print(f"ERROR:invalid_key_length:{len(key_hex)}")
        else:
            print("ERROR:invalid_pair_format")

    elif cmd == "DONE":
        print("OK:rebooting")
        time.sleep_ms(100)
        machine.reset()

    elif cmd == "LEVEL:open":
        config = load_config()
        config["security_level"] = "open"
        save_config(config)
        print("OK:security_level:open")

    elif cmd == "LEVEL:paired_only":
        config = load_config()
        config["security_level"] = "paired_only"
        save_config(config)
        print("OK:security_level:paired_only")

    elif cmd == "STATUS":
        config = load_config()
        print(f"STATUS:level={config.get('security_level', 'open')}")
        print(f"STATUS:paired={len(config.get('paired_browsers', {}))}")
        print(f"STATUS:id={get_unique_id()}")

    else:
        print(f"ERROR:unknown_command:{cmd}")


# Main entry point
if __name__ == "__main__":
    if PAIRING_MODE:
        # In pairing mode - handle USB CDC serial
        run_pairing_mode()
    else:
        # Normal mode - BLE + USB HID
        hid = BLEHID()
        hid.run()
