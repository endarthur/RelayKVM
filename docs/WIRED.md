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

## Electrical Safety & Isolation

### The Issue

Both USB ports share the same ground plane on the RP2040-PiZero. When connected to two different computers, you're creating an electrical path between them:

```
Controller PC ──[USB Ground]──► RP2040 PCB ◄──[USB Ground]── Host PC
                                    │
                            Grounds bridged here
```

If the two computers have different ground reference voltages (even small differences), current can flow through the RP2040's ground plane in unintended ways.

### Is This Dangerous?

**Honest answer:** Usually not, but it's not guaranteed safe.

Most consumer USB KVM switches (including NanoKVM-USB and many others) work exactly this way - no isolation, grounds bridged. Millions of them are used daily without incident. The USB specification has enough tolerance for typical setups.

**This is generally fine when:**
- Both PCs on same electrical circuit/power strip
- One PC is a laptop on battery (battery = galvanic isolation)
- Same building, same breaker panel
- Short, quality USB cables

**This can cause problems when:**
- PCs on different circuits/breakers
- Different buildings or distant outlets
- Industrial environments with electrical noise
- Long cable runs (more antenna effect)
- Expensive equipment you don't want to risk

### What Can Go Wrong

In rough order of likelihood:

1. **Nothing** - Most common outcome
2. **USB noise/glitches** - Occasional communication errors, phantom keypresses
3. **Ground loop hum** - If either PC has audio equipment connected
4. **USB enumeration failures** - Devices not recognized consistently
5. **Component damage** - Rare, but possible in extreme cases (lightning, major faults)

### Mitigation Options

**Level 0 - Accept the risk:**
- Same as most consumer KVM switches
- Fine for typical home/office use
- Both PCs on same power strip recommended

**Level 1 - USB Isolator (~$15-50):**
- Add a USB isolator dongle on the controller side
- Provides 1-2.5kV galvanic isolation
- Examples: Adafruit USB Isolator, industrial isolators
- Adds negligible latency for HID

**Level 2 - Laptop as controller:**
- Laptop on battery = natural isolation
- The battery breaks the ground path
- Plugged-in laptop still has ground connection

**Level 3 - Built-in isolation (future):**
- Design isolation into the device itself
- Digital isolators + isolated DC-DC converter
- More complex and expensive, but "proper" engineering

### Comparison to RelayKVM Bluetooth

This is one advantage of the Bluetooth version:

| Aspect | Wired (RP2040-PiZero) | Bluetooth (Pico 2W) |
|--------|------------------------|---------------------|
| Isolation | None (grounds bridged) | **Complete** (wireless = no electrical path) |
| Ground loops | Possible | Impossible |
| Safety | Same power strip recommended | Any configuration safe |

The Bluetooth version has perfect galvanic isolation by nature - there's no wire between the controller and the bridge device.

### Disclaimer

**Use at your own risk.** This is a DIY project, not a certified commercial product. If you're connecting expensive equipment, servers, or anything where damage would be costly, either:
- Use the Bluetooth version (inherently isolated)
- Add a USB isolator
- Ensure both PCs are on the same power strip

The authors are not responsible for any damage caused by ground loops or electrical issues.

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
