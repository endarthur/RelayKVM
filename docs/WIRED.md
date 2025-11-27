# Wired KVM - RP2040-PiZero

Wired alternative to Bluetooth using dual USB-C ports for direct connection to both controller and host PCs.

## Hardware

**Board:** [Waveshare RP2040-PiZero](https://www.waveshare.com/rp2040-pizero.htm)
- RP2040 dual-core Cortex M0+ @ 133MHz
- 264KB SRAM, 16MB Flash
- **Two USB-C ports:**
  - Native USB (RP2040 USB peripheral)
  - PIO-USB (software USB via PIO)
- ~70 BRL / $14 USD

## Architecture

```
┌─────────────┐   USB-C (Native)   ┌─────────────────┐   USB-C (PIO)   ┌─────────────┐
│ Controller  │ ◄─────────────────► │  RP2040-PiZero  │ ◄─────────────► │   Host PC   │
│     PC      │     WebSerial       │                 │     USB HID     │             │
└─────────────┘    (CDC Serial)     └─────────────────┘  (Keyboard/Mouse)└─────────────┘
```

**Data flow:**
1. Web interface captures keyboard/mouse on Controller PC
2. Sends HID commands via WebSerial (USB CDC)
3. RP2040 receives commands, translates to USB HID
4. Sends HID reports to Host PC via PIO-USB

## Advantages Over Bluetooth

| Aspect | Bluetooth (Cardputer/Pico 2W) | Wired (RP2040-PiZero) |
|--------|-------------------------------|------------------------|
| Pairing | Required each time | None |
| Latency | ~10-50ms | <1ms |
| Range | ~10m | Cable length |
| Interference | Possible | None |
| Power | Battery/USB | USB powered |
| Reliability | Good | Excellent |
| Setup | Pair + connect | Just plug in |

## USB Identifiers

| Port | VID | PID | Class | Product Name |
|------|-----|-----|-------|--------------|
| Native USB (Controller) | `0xFEED` | `0xAE03` | CDC | RelayKVM Serial |
| PIO-USB (Host) | `0xFEED` | `0xAE01` | HID | RelayKVM Controller |

## WebSerial API

### Permission Persistence

WebSerial remembers previously granted devices. No picker needed after first authorization:

```javascript
// Check for previously authorized devices on page load
async function autoConnect() {
    const ports = await navigator.serial.getPorts();
    
    for (const port of ports) {
        const info = port.getInfo();
        // Check if this is our device
        if (info.usbVendorId === 0xFEED && info.usbProductId === 0xAE03) {
            try {
                await port.open({ baudRate: 115200 });
                console.log('Auto-connected to RelayKVM');
                return port;
            } catch (e) {
                console.log('Device found but not connected');
            }
        }
    }
    return null;
}
```

### Device Filtering

Filter the picker to only show our device:

```javascript
async function manualConnect() {
    const port = await navigator.serial.requestPort({
        filters: [{ 
            usbVendorId: 0xFEED, 
            usbProductId: 0xAE03 
        }]
    });
    
    await port.open({ baudRate: 115200 });
    return port;
}
```

### Reading and Writing

```javascript
// Send data
async function send(port, data) {
    const writer = port.writable.getWriter();
    await writer.write(new Uint8Array(data));
    writer.releaseLock();
}

// Receive data
async function startReading(port, onData) {
    const reader = port.readable.getReader();
    
    try {
        while (true) {
            const { value, done } = await reader.read();
            if (done) break;
            onData(value);
        }
    } finally {
        reader.releaseLock();
    }
}
```

### Disconnect Detection

```javascript
navigator.serial.addEventListener('disconnect', (event) => {
    const port = event.target;
    console.log('Device disconnected:', port);
    // Update UI, attempt reconnect, etc.
});

navigator.serial.addEventListener('connect', (event) => {
    const port = event.target;
    console.log('Device connected:', port);
    // Could auto-connect if previously authorized
});
```

## Firmware Architecture

### Dependencies
- TinyUSB (native USB CDC)
- Pico-PIO-USB (PIO USB HID device)

### Main Loop Pseudocode

```c
// Native USB: CDC Serial for receiving commands
// PIO USB: HID Device for sending to host

void main() {
    // Initialize native USB as CDC
    tud_init(BOARD_TUD_RHPORT);
    
    // Initialize PIO USB as HID device
    pio_usb_configuration_t pio_cfg = PIO_USB_DEFAULT_CONFIG;
    pio_cfg.pin_dp = PIO_USB_DP_PIN;
    tuh_configure(1, TUH_CFGID_RPI_PIO_USB_CONFIGURATION, &pio_cfg);
    
    while (1) {
        tud_task();  // Native USB task
        tuh_task();  // PIO USB task
        
        // Check for incoming serial data
        if (tud_cdc_available()) {
            uint8_t buf[64];
            uint32_t count = tud_cdc_read(buf, sizeof(buf));
            process_command(buf, count);
        }
    }
}

void process_command(uint8_t* buf, uint32_t len) {
    // Parse command (same protocol as Bluetooth version)
    // Send HID report via PIO USB
}
```

## Ground Loop Considerations

Both USB ports share the same ground on the RP2040-PiZero. When connected to two different computers:

**Usually fine when:**
- Both PCs on same electrical circuit
- Same power strip
- Laptop on battery (isolated)

**Potential issues when:**
- PCs on different circuits/breakers
- Different buildings
- Long cable runs

**Solutions if needed:**
1. Same power strip for both PCs
2. USB isolator (~$15-30) on controller side
3. One PC is laptop on battery

## Protocol

Same HID protocol as Bluetooth version - just different transport:

| Byte | Description |
|------|-------------|
| 0 | Command type (0x01=keyboard, 0x02=mouse, etc.) |
| 1-N | Command data (keycodes, mouse delta, etc.) |

See main protocol documentation for full command reference.

## Implementation Checklist

- [ ] Firmware: Basic CDC serial on native USB
- [ ] Firmware: Basic HID on PIO-USB
- [ ] Firmware: Command parser (reuse from Cardputer)
- [ ] Firmware: USB descriptors with correct VID/PID
- [ ] Web: WebSerial adapter (parallel to BLE adapter)
- [ ] Web: Auto-connect on page load
- [ ] Web: Transport abstraction (BLE vs Serial)
- [ ] Web: Connection status indicator for wired mode
- [ ] Test: Verify no ground loop issues
- [ ] Test: Latency comparison with Bluetooth

## Resources

- [Pico-PIO-USB](https://github.com/sekigon-gonnoc/Pico-PIO-USB) - PIO USB implementation
- [TinyUSB](https://github.com/hathach/tinyusb) - USB stack
- [WebSerial API](https://developer.mozilla.org/en-US/docs/Web/API/Web_Serial_API) - Browser serial API
- [RP2040-PiZero Wiki](https://www.waveshare.com/wiki/RP2040-PiZero) - Board documentation
