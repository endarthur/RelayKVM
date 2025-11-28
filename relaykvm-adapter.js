/**
 * RelayKVM Web Bluetooth Adapter
 *
 * Connects to RelayKVM device via Web Bluetooth and sends
 * NanoKVM protocol commands for keyboard/mouse control.
 *
 * Usage:
 *   const kvm = new RelayKVMAdapter();
 *   await kvm.connect();
 *   kvm.sendKey('a');
 *   kvm.sendKeyCombo(['ctrl', 'alt', 'delete']);
 *   kvm.moveMouse(10, -5);
 *   kvm.click('left');
 */

class RelayKVMAdapter {
    // Nordic UART Service UUIDs
    static SERVICE_UUID = '6e400001-b5a3-f393-e0a9-e50e24dcca9e';
    static RX_CHARACTERISTIC_UUID = '6e400002-b5a3-f393-e0a9-e50e24dcca9e';  // Write to device
    static TX_CHARACTERISTIC_UUID = '6e400003-b5a3-f393-e0a9-e50e24dcca9e';  // Notifications from device

    // NanoKVM Protocol constants
    static HEAD1 = 0x57;
    static HEAD2 = 0xAB;
    static ADDR = 0x00;

    // Command codes
    static CMD_GET_INFO = 0x01;
    static CMD_SEND_KB_GENERAL_DATA = 0x02;
    static CMD_SEND_KB_MEDIA_DATA = 0x03;
    static CMD_SEND_MS_ABS_DATA = 0x04;
    static CMD_SEND_MS_REL_DATA = 0x05;

    // Custom command codes (0x80+)
    static CMD_DISPLAY_CONTROL = 0x81;
    static CMD_DISPLAY_TIMEOUT = 0x82;
    static CMD_USB_WAKE = 0x83;
    static CMD_USB_RECONNECT = 0x84;
    static CMD_DEVICE_RESET = 0x85;

    // Display brightness levels
    static DISPLAY_OFF = 0;
    static DISPLAY_DIM = 64;
    static DISPLAY_ON = 255;

    // HID Keyboard modifier bits
    static MODIFIER = {
        NONE: 0x00,
        LEFT_CTRL: 0x01,
        LEFT_SHIFT: 0x02,
        LEFT_ALT: 0x04,
        LEFT_GUI: 0x08,
        RIGHT_CTRL: 0x10,
        RIGHT_SHIFT: 0x20,
        RIGHT_ALT: 0x40,
        RIGHT_GUI: 0x80
    };

    // HID Keyboard scan codes (USB HID Usage Tables)
    static KEYCODE = {
        // Letters
        'a': 0x04, 'b': 0x05, 'c': 0x06, 'd': 0x07, 'e': 0x08, 'f': 0x09,
        'g': 0x0A, 'h': 0x0B, 'i': 0x0C, 'j': 0x0D, 'k': 0x0E, 'l': 0x0F,
        'm': 0x10, 'n': 0x11, 'o': 0x12, 'p': 0x13, 'q': 0x14, 'r': 0x15,
        's': 0x16, 't': 0x17, 'u': 0x18, 'v': 0x19, 'w': 0x1A, 'x': 0x1B,
        'y': 0x1C, 'z': 0x1D,

        // Numbers
        '1': 0x1E, '2': 0x1F, '3': 0x20, '4': 0x21, '5': 0x22,
        '6': 0x23, '7': 0x24, '8': 0x25, '9': 0x26, '0': 0x27,

        // Special keys
        'enter': 0x28, 'return': 0x28,
        'escape': 0x29, 'esc': 0x29,
        'backspace': 0x2A,
        'tab': 0x2B,
        'space': 0x2C, ' ': 0x2C,

        // Punctuation
        '-': 0x2D, '=': 0x2E, '[': 0x2F, ']': 0x30, '\\': 0x31,
        ';': 0x33, "'": 0x34, '`': 0x35, ',': 0x36, '.': 0x37, '/': 0x38,

        // Function keys
        'f1': 0x3A, 'f2': 0x3B, 'f3': 0x3C, 'f4': 0x3D, 'f5': 0x3E, 'f6': 0x3F,
        'f7': 0x40, 'f8': 0x41, 'f9': 0x42, 'f10': 0x43, 'f11': 0x44, 'f12': 0x45,
        'f13': 0x68, 'f14': 0x69, 'f15': 0x6A, 'f16': 0x6B, 'f17': 0x6C, 'f18': 0x6D,
        'f19': 0x6E, 'f20': 0x6F, 'f21': 0x70, 'f22': 0x71, 'f23': 0x72, 'f24': 0x73,

        // Control keys
        'printscreen': 0x46, 'scrolllock': 0x47, 'pause': 0x48,
        'insert': 0x49, 'home': 0x4A, 'pageup': 0x4B,
        'delete': 0x4C, 'end': 0x4D, 'pagedown': 0x4E,

        // Arrow keys
        'right': 0x4F, 'left': 0x50, 'down': 0x51, 'up': 0x52,

        // Numpad
        'numlock': 0x53,
        'num/': 0x54, 'num*': 0x55, 'num-': 0x56, 'num+': 0x57,
        'numenter': 0x58,
        'num1': 0x59, 'num2': 0x5A, 'num3': 0x5B, 'num4': 0x5C, 'num5': 0x5D,
        'num6': 0x5E, 'num7': 0x5F, 'num8': 0x60, 'num9': 0x61, 'num0': 0x62,
        'num.': 0x63,

        // Modifiers (for reference, usually handled separately)
        'ctrl': 0xE0, 'lctrl': 0xE0,
        'shift': 0xE1, 'lshift': 0xE1,
        'alt': 0xE2, 'lalt': 0xE2,
        'gui': 0xE3, 'lgui': 0xE3, 'win': 0xE3, 'meta': 0xE3, 'cmd': 0xE3,
        'rctrl': 0xE4, 'rshift': 0xE5, 'ralt': 0xE6, 'rgui': 0xE7
    };

    // Modifier key name to bit mapping
    static MODIFIER_MAP = {
        'ctrl': 0x01, 'lctrl': 0x01, 'leftctrl': 0x01,
        'shift': 0x02, 'lshift': 0x02, 'leftshift': 0x02,
        'alt': 0x04, 'lalt': 0x04, 'leftalt': 0x04,
        'gui': 0x08, 'lgui': 0x08, 'win': 0x08, 'meta': 0x08, 'cmd': 0x08, 'super': 0x08,
        'rctrl': 0x10, 'rightctrl': 0x10,
        'rshift': 0x20, 'rightshift': 0x20,
        'ralt': 0x40, 'rightalt': 0x40, 'altgr': 0x40,
        'rgui': 0x80, 'rightgui': 0x80
    };

    constructor() {
        this.device = null;
        this.server = null;
        this.service = null;
        this.rxCharacteristic = null;
        this.txCharacteristic = null;
        this.connected = false;
        this.onConnectionChange = null;
        this.onDataReceived = null;

        // Write queue to prevent "GATT operation already in progress" errors
        this._writeQueue = [];
        this._writeInProgress = false;

        // Mouse button state for drag operations
        this._heldButtons = 0;
    }

    /**
     * Check if Web Bluetooth is available
     */
    static isSupported() {
        return navigator.bluetooth !== undefined;
    }

    /**
     * Get previously paired devices (no picker required)
     * Returns array of devices that can be reconnected to directly
     */
    static async getKnownDevices() {
        if (!RelayKVMAdapter.isSupported()) return [];

        try {
            // getDevices() returns previously permitted devices
            if (!navigator.bluetooth.getDevices) {
                console.log('getDevices() not supported in this browser');
                return [];
            }

            const devices = await navigator.bluetooth.getDevices();
            // Filter for RelayKVM devices
            return devices.filter(d => d.name && d.name.startsWith('Relay'));
        } catch (e) {
            console.warn('Failed to get known devices:', e);
            return [];
        }
    }

    /**
     * Helper to wait
     */
    async delay(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }

    /**
     * Reconnect to a previously paired device (no picker)
     */
    async reconnect(device, maxRetries = 3) {
        if (!device) {
            throw new Error('No device provided');
        }

        this.device = device;

        this.device.addEventListener('gattserverdisconnected', () => {
            console.log('Device disconnected');
            this.connected = false;
            if (this.onConnectionChange) {
                this.onConnectionChange(false);
            }
        });

        return await this._connectToDevice(maxRetries);
    }

    /**
     * Connect to RelayKVM device with retries (shows picker)
     */
    async connect(maxRetries = 3) {
        if (!RelayKVMAdapter.isSupported()) {
            throw new Error('Web Bluetooth is not supported in this browser');
        }

        try {
            console.log('Requesting Bluetooth device...');
            this.device = await navigator.bluetooth.requestDevice({
                filters: [{ namePrefix: 'Relay' }],
                optionalServices: [RelayKVMAdapter.SERVICE_UUID]
            });

            this.device.addEventListener('gattserverdisconnected', () => {
                console.log('Device disconnected');
                this.connected = false;
                if (this.onConnectionChange) {
                    this.onConnectionChange(false);
                }
            });

            return await this._connectToDevice(maxRetries);
        } catch (error) {
            console.error('Connection failed:', error);
            this.connected = false;
            throw error;
        }
    }

    /**
     * Internal: Connect to the already-selected device
     */
    async _connectToDevice(maxRetries = 3) {
        let lastError = null;
        for (let attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                console.log(`Connection attempt ${attempt}/${maxRetries}...`);

                console.log('Connecting to GATT server...');
                this.server = await this.device.gatt.connect();
                console.log('GATT connected, server:', this.server.connected);

                // Immediately start service discovery - no delay
                // Windows drops idle connections quickly
                console.log('Getting NUS service...');
                this.service = await this.server.getPrimaryService(RelayKVMAdapter.SERVICE_UUID);
                console.log('Service found');

                console.log('Getting RX characteristic...');
                this.rxCharacteristic = await this.service.getCharacteristic(RelayKVMAdapter.RX_CHARACTERISTIC_UUID);
                console.log('RX characteristic found');

                console.log('Getting TX characteristic...');
                this.txCharacteristic = await this.service.getCharacteristic(RelayKVMAdapter.TX_CHARACTERISTIC_UUID);
                console.log('TX characteristic found');

                // Set up notifications for TX characteristic (data from device)
                console.log('Starting notifications...');
                await this.txCharacteristic.startNotifications();
                console.log('Notifications started');
                this.txCharacteristic.addEventListener('characteristicvaluechanged', (event) => {
                    const value = new Uint8Array(event.target.value.buffer);
                    console.log('Received from device:', value);
                    if (this.onDataReceived) {
                        this.onDataReceived(value);
                    }
                });

                this.connected = true;
                console.log('Connected to RelayKVM!');

                if (this.onConnectionChange) {
                    this.onConnectionChange(true);
                }

                return true;

            } catch (attemptError) {
                console.warn(`Attempt ${attempt} failed:`, attemptError.message);
                lastError = attemptError;

                // Disconnect if partially connected
                if (this.device && this.device.gatt.connected) {
                    this.device.gatt.disconnect();
                }

                if (attempt < maxRetries) {
                    console.log(`Waiting before retry...`);
                    await this.delay(1000);
                }
            }
        }

        // All retries failed
        throw lastError;
    }

    /**
     * Disconnect from device
     */
    disconnect() {
        // Clear write queue and held buttons
        this._writeQueue = [];
        this._writeInProgress = false;
        this._heldButtons = 0;

        if (this.device && this.device.gatt.connected) {
            this.device.gatt.disconnect();
        }
        this.connected = false;
    }

    /**
     * Build NanoKVM protocol packet
     */
    buildPacket(cmd, data) {
        const packet = new Uint8Array(6 + data.length);
        packet[0] = RelayKVMAdapter.HEAD1;
        packet[1] = RelayKVMAdapter.HEAD2;
        packet[2] = RelayKVMAdapter.ADDR;
        packet[3] = cmd;
        packet[4] = data.length;
        packet.set(data, 5);

        // Calculate checksum
        let sum = 0;
        for (let i = 0; i < packet.length - 1; i++) {
            sum += packet[i];
        }
        packet[packet.length - 1] = sum & 0xFF;

        return packet;
    }

    /**
     * Send raw packet to device (queued to prevent GATT conflicts)
     */
    async sendPacket(packet) {
        if (!this.connected || !this.rxCharacteristic) {
            throw new Error('Not connected to device');
        }

        // Queue the write operation
        return new Promise((resolve, reject) => {
            this._writeQueue.push({ packet, resolve, reject });
            this._processWriteQueue();
        });
    }

    /**
     * Process queued write operations sequentially
     */
    async _processWriteQueue() {
        if (this._writeInProgress || this._writeQueue.length === 0) {
            return;
        }

        this._writeInProgress = true;

        while (this._writeQueue.length > 0) {
            // Check connection before each write
            if (!this.connected || !this.rxCharacteristic) {
                // Reject all remaining queued writes
                while (this._writeQueue.length > 0) {
                    const { reject } = this._writeQueue.shift();
                    reject(new Error('Disconnected'));
                }
                break;
            }

            const { packet, resolve, reject } = this._writeQueue.shift();

            try {
                await this.rxCharacteristic.writeValueWithoutResponse(packet);
                resolve();
            } catch (error) {
                console.error('BLE write error:', error);
                reject(error);
            }
        }

        this._writeInProgress = false;
    }

    /**
     * Send keyboard HID report
     * @param {number} modifier - Modifier bits
     * @param {number[]} keys - Array of up to 6 key codes
     */
    async sendKeyboardReport(modifier, keys = []) {
        // Pad keys array to 6 elements
        const keyArray = [...keys, 0, 0, 0, 0, 0, 0].slice(0, 6);

        const data = new Uint8Array([
            modifier,
            0x00,  // Reserved
            keyArray[0], keyArray[1], keyArray[2],
            keyArray[3], keyArray[4], keyArray[5]
        ]);

        const packet = this.buildPacket(RelayKVMAdapter.CMD_SEND_KB_GENERAL_DATA, data);
        await this.sendPacket(packet);
    }

    /**
     * Send a single key press and release
     * @param {string} key - Key name (e.g., 'a', 'enter', 'f1')
     * @param {string[]} modifiers - Array of modifier names (e.g., ['ctrl', 'shift'])
     */
    async sendKey(key, modifiers = []) {
        const keyLower = key.toLowerCase();
        const keyCode = RelayKVMAdapter.KEYCODE[keyLower];

        if (keyCode === undefined) {
            console.warn(`Unknown key: ${key}`);
            return;
        }

        // Calculate modifier byte
        let modifierByte = 0;
        for (const mod of modifiers) {
            const modBit = RelayKVMAdapter.MODIFIER_MAP[mod.toLowerCase()];
            if (modBit) {
                modifierByte |= modBit;
            }
        }

        // Press key
        await this.sendKeyboardReport(modifierByte, [keyCode]);

        // Small delay
        await new Promise(resolve => setTimeout(resolve, 10));

        // Release key
        await this.sendKeyboardReport(0, []);
    }

    /**
     * Send a key combination (e.g., Ctrl+Alt+Delete)
     * @param {string[]} keys - Array of key names including modifiers
     */
    async sendKeyCombo(keys) {
        const modifiers = [];
        const regularKeys = [];

        for (const key of keys) {
            const keyLower = key.toLowerCase();
            if (RelayKVMAdapter.MODIFIER_MAP[keyLower]) {
                modifiers.push(keyLower);
            } else {
                regularKeys.push(keyLower);
            }
        }

        // Calculate modifier byte
        let modifierByte = 0;
        for (const mod of modifiers) {
            modifierByte |= RelayKVMAdapter.MODIFIER_MAP[mod];
        }

        // Get key codes for regular keys (max 6)
        const keyCodes = regularKeys
            .map(k => RelayKVMAdapter.KEYCODE[k])
            .filter(k => k !== undefined)
            .slice(0, 6);

        // Press combo
        await this.sendKeyboardReport(modifierByte, keyCodes);

        // Hold briefly
        await new Promise(resolve => setTimeout(resolve, 50));

        // Release all
        await this.sendKeyboardReport(0, []);
    }

    /**
     * Send a media/consumer control key
     * @param {number} code - Consumer control code (e.g., 0xE9 for volume up)
     */
    async sendMediaKey(code) {
        // Send as little-endian 16-bit value
        const data = new Uint8Array([
            code & 0xFF,         // Low byte
            (code >> 8) & 0xFF   // High byte
        ]);
        const packet = this.buildPacket(RelayKVMAdapter.CMD_SEND_KB_MEDIA_DATA, data);
        await this.sendPacket(packet);
    }

    /**
     * Type a string of text
     * @param {string} text - Text to type
     * @param {number} delay - Delay between keys in ms
     */
    async typeText(text, delay = 20) {
        for (const char of text) {
            const needsShift = char !== char.toLowerCase() || '!@#$%^&*()_+{}|:"<>?~'.includes(char);

            // Map shifted characters
            const charMap = {
                '!': '1', '@': '2', '#': '3', '$': '4', '%': '5',
                '^': '6', '&': '7', '*': '8', '(': '9', ')': '0',
                '_': '-', '+': '=', '{': '[', '}': ']', '|': '\\',
                ':': ';', '"': "'", '<': ',', '>': '.', '?': '/',
                '~': '`'
            };

            let keyChar = charMap[char] || char.toLowerCase();

            if (RelayKVMAdapter.KEYCODE[keyChar]) {
                await this.sendKey(keyChar, needsShift ? ['shift'] : []);
                await new Promise(resolve => setTimeout(resolve, delay));
            }
        }
    }

    /**
     * Send relative mouse movement
     * @param {number} dx - X delta (-127 to 127)
     * @param {number} dy - Y delta (-127 to 127)
     * @param {number} scroll - Scroll delta (-127 to 127)
     * @param {number} buttons - Button mask (1=left, 2=right, 4=middle)
     */
    async moveMouse(dx, dy, scroll = 0, buttons = null) {
        // Clamp values to int8 range
        dx = Math.max(-127, Math.min(127, Math.round(dx)));
        dy = Math.max(-127, Math.min(127, Math.round(dy)));
        scroll = Math.max(-127, Math.min(127, Math.round(scroll)));

        // Use held buttons if not explicitly specified
        if (buttons === null) {
            buttons = this._heldButtons || 0;
        }

        const data = new Uint8Array([
            0x01,  // Relative mode indicator
            buttons,
            dx & 0xFF,
            dy & 0xFF,
            scroll & 0xFF
        ]);

        const packet = this.buildPacket(RelayKVMAdapter.CMD_SEND_MS_REL_DATA, data);
        await this.sendPacket(packet);
    }

    /**
     * Click a mouse button
     * @param {string} button - 'left', 'right', or 'middle'
     */
    async click(button = 'left') {
        const buttonMap = { left: 1, right: 2, middle: 4 };
        const buttonBit = buttonMap[button.toLowerCase()] || 1;

        // Press
        await this.moveMouse(0, 0, 0, buttonBit);
        await new Promise(resolve => setTimeout(resolve, 20));
        // Release
        await this.moveMouse(0, 0, 0, 0);
    }

    /**
     * Double click
     * @param {string} button - 'left', 'right', or 'middle'
     */
    async doubleClick(button = 'left') {
        await this.click(button);
        await new Promise(resolve => setTimeout(resolve, 100));
        await this.click(button);
    }

    /**
     * Press and hold a mouse button
     * @param {number} buttonBit - Button bit (1=left, 2=right, 4=middle)
     */
    async mouseDown(buttonBit) {
        this._heldButtons = (this._heldButtons || 0) | buttonBit;
        await this.moveMouse(0, 0, 0, this._heldButtons);
    }

    /**
     * Release a mouse button
     * @param {number} buttonBit - Button bit (1=left, 2=right, 4=middle)
     */
    async mouseUp(buttonBit) {
        this._heldButtons = (this._heldButtons || 0) & ~buttonBit;
        await this.moveMouse(0, 0, 0, this._heldButtons);
    }

    /**
     * Scroll the mouse wheel
     * @param {number} amount - Scroll amount (positive = up, negative = down)
     */
    async scroll(amount) {
        await this.moveMouse(0, 0, amount, this._heldButtons || 0);
    }

    /**
     * Send absolute mouse position (for seamless mode)
     * @param {number} x - X coordinate (0-32767)
     * @param {number} y - Y coordinate (0-32767)
     * @param {number} scroll - Scroll delta (-127 to 127)
     * @param {number} buttons - Button mask (1=left, 2=right, 4=middle)
     */
    async moveMouseAbsolute(x, y, scroll = 0, buttons = null) {
        // Clamp coordinates to HID absolute range (0-32767)
        x = Math.max(0, Math.min(32767, Math.round(x)));
        y = Math.max(0, Math.min(32767, Math.round(y)));
        scroll = Math.max(-127, Math.min(127, Math.round(scroll)));

        // Use held buttons if not explicitly specified
        if (buttons === null) {
            buttons = this._heldButtons || 0;
        }

        const data = new Uint8Array([
            0x02,  // Absolute mode indicator
            buttons,
            x & 0xFF,          // X low byte
            (x >> 8) & 0xFF,   // X high byte
            y & 0xFF,          // Y low byte
            (y >> 8) & 0xFF,   // Y high byte
            scroll & 0xFF
        ]);

        const packet = this.buildPacket(RelayKVMAdapter.CMD_SEND_MS_ABS_DATA, data);
        await this.sendPacket(packet);
    }

    /**
     * Scale screen coordinates to HID absolute range (0-32767)
     * @param {number} x - Screen X coordinate
     * @param {number} y - Screen Y coordinate
     * @param {number} screenWidth - Target screen width
     * @param {number} screenHeight - Target screen height
     * @returns {{x: number, y: number}} - Scaled coordinates
     */
    static scaleToAbsolute(x, y, screenWidth, screenHeight) {
        return {
            x: Math.round((x / screenWidth) * 32767),
            y: Math.round((y / screenHeight) * 32767)
        };
    }

    /**
     * Set Cardputer display brightness
     * @param {string} mode - 'off', 'dim', or 'on'
     */
    async setDisplayBrightness(mode) {
        const brightnessMap = {
            'off': RelayKVMAdapter.DISPLAY_OFF,
            'dim': RelayKVMAdapter.DISPLAY_DIM,
            'on': RelayKVMAdapter.DISPLAY_ON
        };

        const brightness = brightnessMap[mode.toLowerCase()] ?? RelayKVMAdapter.DISPLAY_ON;
        console.log('setDisplayBrightness:', mode, '-> brightness:', brightness);
        const data = new Uint8Array([brightness]);
        const packet = this.buildPacket(RelayKVMAdapter.CMD_DISPLAY_CONTROL, data);
        console.log('Display packet:', Array.from(packet).map(b => '0x' + b.toString(16).padStart(2, '0')).join(' '));
        await this.sendPacket(packet);
        console.log('Display packet sent successfully');
    }

    /**
     * Set Cardputer display timeout
     * @param {number} seconds - Timeout in seconds (0 = disabled)
     */
    async setDisplayTimeout(seconds) {
        const data = new Uint8Array([
            seconds & 0xFF,
            (seconds >> 8) & 0xFF
        ]);
        const packet = this.buildPacket(RelayKVMAdapter.CMD_DISPLAY_TIMEOUT, data);
        await this.sendPacket(packet);
    }

    /**
     * Send USB wake signal to target computer
     */
    async sendUsbWake() {
        const data = new Uint8Array([]);
        const packet = this.buildPacket(RelayKVMAdapter.CMD_USB_WAKE, data);
        await this.sendPacket(packet);
    }

    /**
     * Trigger USB recovery on Cardputer
     * Attempts to recover USB connection by sending HID activity.
     *
     * How it works:
     * - If USB is suspended: HID activity wakes it up
     * - If USB stack is corrupted: HID calls may crash, triggering device reset
     *   which re-initializes everything and fixes USB
     *
     * Either way, USB should work again. May cause device reset if USB is
     * in a bad state - this is expected and actually fixes the problem.
     */
    async recoverUsb() {
        const data = new Uint8Array([]);
        const packet = this.buildPacket(RelayKVMAdapter.CMD_USB_RECONNECT, data);
        await this.sendPacket(packet);
    }

    // Alias for backwards compatibility
    async reconnectUsb() {
        return this.recoverUsb();
    }

    /**
     * Reset the Cardputer device
     * This performs a full ESP32 restart
     * Note: BLE connection will be lost after this
     */
    async resetDevice() {
        const data = new Uint8Array([]);
        const packet = this.buildPacket(RelayKVMAdapter.CMD_DEVICE_RESET, data);
        await this.sendPacket(packet);
    }
}

// Export for module usage
if (typeof module !== 'undefined' && module.exports) {
    module.exports = RelayKVMAdapter;
}
