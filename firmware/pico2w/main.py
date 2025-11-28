"""
RelayKVM for Raspberry Pi Pico 2W - MicroPython
BLE Nordic UART + USB HID implementation

Requires MicroPython 1.22+ with USB HID support.
"""

import bluetooth
import time
from micropython import const
from machine import Pin

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

        # USB HID setup (will be initialized if available)
        self._hid = None
        self._setup_usb_hid()

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

    def _irq(self, event, data):
        """BLE interrupt handler"""
        if event == 1:  # _IRQ_CENTRAL_CONNECT
            conn_handle, _, _ = data
            self._connections.add(conn_handle)
            self._led.on()
            print(f"Connected: {conn_handle}")

        elif event == 2:  # _IRQ_CENTRAL_DISCONNECT
            conn_handle, _, _ = data
            self._connections.discard(conn_handle)
            self._led.off()
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

        return 5 + length + 1  # Packet size

    def run(self):
        """Main loop"""
        print("RelayKVM-Pico starting...")
        print(f"USB HID available: {self._hid is not None}")

        self._advertise()

        while True:
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

            time.sleep_ms(1)


# Main entry point
if __name__ == "__main__":
    hid = BLEHID()
    hid.run()
