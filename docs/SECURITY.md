# Security Considerations for RelayKVM

## Overview

RelayKVM transmits keyboard and mouse input wirelessly via Bluetooth Low Energy (BLE). This document covers security considerations for the **Pico 2W** (primary platform) and the web interface.

**Supported Platforms:**
- **Raspberry Pi Pico 2W** (recommended) - MicroPython + BTstack
- **M5Stack Cardputer** (legacy) - Arduino + NimBLE

## Threat Model

### Potential Threats

**BLE/Firmware:**
1. **Eavesdropping:** Attacker intercepts Bluetooth traffic to capture keystrokes
2. **Man-in-the-Middle (MITM):** Attacker impersonates either device during pairing
3. **Unauthorized Access:** Attacker connects to unpaired/unprotected device
4. **Physical Access:** Attacker gains physical access to the Pico/Cardputer
5. **Replay Attacks:** Attacker records and replays captured commands

**Web Interface:**
6. **Local Storage Exposure:** Attacker with browser access reads stored scripts/macros
7. **Shared Computer Risk:** Multiple users on same browser profile share config
8. **Clipboard Leakage:** Clipboard bridge sends sensitive data to wrong host
9. **Script Injection:** Malicious script content executed via text injection

### Assets to Protect

- Keyboard input (passwords, sensitive data)
- Mouse movements (could reveal workflow/behavior)
- Target computer control (unauthorized command execution)
- Pairing credentials (Bluetooth bonding keys)
- **Stored scripts/macros** (may contain sensitive sequences)
- **Controller clipboard** (when using clipboard bridge)

## Current Security Status

### Pico 2W (Primary Platform)

**Current state: Minimal security**

The MicroPython BLE firmware currently uses "Just Works" pairing - no PIN, no confirmation. This is convenient but vulnerable to MITM attacks.

```python
# Current: No explicit security configuration
self._ble = bluetooth.BLE()
self._ble.active(True)
# BLE stack uses default security (Just Works)
```

**What this means:**
- ✅ Traffic is encrypted (BLE 4.2+ always encrypts after pairing)
- ✅ Bonding stores keys for reconnection
- ❌ No MITM protection (attacker can intercept pairing)
- ❌ No user confirmation (any device can pair)
- ❌ No application-layer authentication

**Why it's like this:**
- Pico 2W has no display for PIN confirmation
- MicroPython's `bluetooth` module has limited security API
- BTstack underneath supports security, but needs explicit configuration

### Cardputer (Legacy Platform)

**Better security due to display + keyboard:**

```cpp
NimBLEDevice::setSecurityAuth(true, true, true);
// - Bonding: true (stores keys for reconnection)
// - MITM protection: true (requires PIN confirmation)
// - Secure Connections: true (uses ECDH key exchange)

NimBLEDevice::setSecurityIOCap(BLE_HS_IO_DISPLAY_YESNO);
// Numeric Comparison: Both devices show same 6-digit PIN
```

**Benefits:**
- ECDH P-256 elliptic curve cryptography
- AES-128 encryption for all traffic
- User must confirm 6-digit PIN on Cardputer screen
- Emergency disconnect via ESC key

### Comparison

| Feature | Pico 2W | Cardputer |
|---------|---------|-----------|
| Encryption | ✅ AES (default) | ✅ AES-128 |
| MITM Protection | ❌ Just Works | ✅ Numeric Comparison |
| User Confirmation | ❌ None | ✅ PIN + Y/N |
| Bonding | ✅ Yes | ✅ Yes |
| Emergency Disconnect | ❌ No button | ✅ ESC key |
| Physical Indicator | 🟢 LED only | 🖥️ Full display |

## Web Interface Security

The RelayKVM web interface (`index.html`) runs entirely in the browser. No server, no accounts - but this means security depends on browser/OS security.

### localStorage Data

All configuration is stored in browser localStorage:

| Data | Risk Level | Contents |
|------|------------|----------|
| **Scripts** | 🔴 High | May contain typed passwords, login sequences |
| **Macros** | 🟡 Medium | Key sequences (Ctrl+Alt+Del, etc.) |
| **Custom Themes** | 🟢 Low | CSS variables (color values) |
| **Settings** | 🟢 Low | Preferences (sensitivity, device name) |

**Risks:**
- Anyone with access to your browser profile can read localStorage
- Browser extensions can access localStorage
- Shared computers expose all stored data
- No encryption - data is plain JSON

**Mitigations (current):**
- Don't store passwords in scripts (use `{{prompt:Label}}` when implemented)
- Use separate browser profile for RelayKVM
- Clear localStorage when done on shared computers

### Clipboard Bridge

CapsLock+P types the controller's clipboard content on the host.

**Risks:**
- Accidentally typing sensitive clipboard content (passwords, API keys)
- Clipboard history may contain old sensitive data
- No confirmation before typing

**Mitigations:**
- Clear clipboard before using bridge
- Be aware of what's in clipboard
- Consider: Add confirmation dialog for large clipboard content

### Script Execution

Scripts type arbitrary text and key sequences on the host.

**Risks:**
- Malicious script could type commands (`rm -rf /`, `format c:`)
- Imported scripts from untrusted sources
- No sandboxing - scripts have full HID access

**Mitigations:**
- Review scripts before running
- Don't import scripts from untrusted sources
- Consider: Script signing/verification (future)

### Custom Themes

Custom themes are JSON with CSS variable values.

**Risks:**
- CSS injection limited (only color/size values used)
- Theme import could theoretically contain malicious JSON
- XSS unlikely but possible if theme parsing is flawed

**Mitigations:**
- Theme values are sanitized to CSS color/size values
- Don't import themes from untrusted sources

### Recommendations for Web Interface

1. **Use a dedicated browser profile** for RelayKVM
2. **Never store passwords** in scripts - use runtime prompts
3. **Review imported scripts/themes** before using
4. **Clear localStorage** on shared computers
5. **Be mindful of clipboard** content before using bridge

## Application-Layer Security (Future)

The Pico 2W lacks a display for PIN confirmation, making traditional BLE pairing vulnerable to MITM. A future security enhancement could use USB Bootstrap Pairing - using the physical USB connection as a trusted out-of-band (OOB) channel.

### The Challenge

```
Attacker within BLE range (~10m) can:
1. Intercept "Just Works" pairing
2. Become MITM between browser and Pico
3. See/modify all keystrokes
```

Traditional solutions require:
- Display for PIN (Pico has none)
- Pre-shared passkey (annoying to enter)
- Physical button confirmation ← **Pico has BOOTSEL!**

### Potential Approach: USB Bootstrap

The concept: Use USB WebSerial to exchange a shared secret during initial setup, then require challenge-response authentication over BLE.

**Key points:**
- BOOTSEL button (`rp2.bootsel_button()`) provides physical presence confirmation
- USB CDC mode allows WebSerial access for key exchange
- Challenge-response (HMAC-SHA256) proves possession of shared secret

### Current Status

**Not implemented.** The current firmware operates in "open" mode - any paired BLE device can connect and send HID commands. Application-layer security may be added in a future version.

**For now, rely on:**
- BLE encryption (always enabled after pairing)
- Physical proximity requirement (~10m range)
- Physical security of the Pico device

## Security Recommendations

### For General Use

1. **Always verify PIN during pairing**
   - Check that PIN matches on both devices
   - Reject pairing if PIN is unexpected

2. **Only pair in secure locations**
   - Avoid pairing in public spaces
   - Ensure no one can see the PIN

3. **Use wired connection for sensitive operations**
   - Banking, password entry, etc.
   - Or disable Bluetooth during these tasks

4. **Keep Cardputer physically secure**
   - Don't leave unattended
   - Store in secure location when not in use

5. **Monitor connection status**
   - Check Cardputer display regularly
   - Look for unexpected "CONNECTED" status

### For High-Security Environments

⚠️ **RelayKVM is NOT suitable for:**
- Military/government classified systems
- Financial trading terminals
- Healthcare systems with PHI/PII
- Industrial control systems (ICS/SCADA)

For these use cases, use wired KVM only.

### Additional Hardening (Optional)

#### MAC Address Whitelist

Add to firmware:
```cpp
bool isAuthorizedDevice(const char* address) {
    const char* whitelist[] = {
        "AA:BB:CC:DD:EE:FF", // Controller 1
        "11:22:33:44:55:66"  // Controller 2
    };
    // Check if address is in whitelist
}

// In ServerCallbacks::onConnect():
if (!isAuthorizedDevice(addr)) {
    pServer->disconnect(connId);
}
```

#### Connection Timeout

Auto-disconnect after inactivity:
```cpp
unsigned long lastCommand = millis();

void loop() {
    if (deviceConnected && (millis() - lastCommand > 300000)) { // 5 min
        pServer->disconnect(pServer->getConnId());
    }
}
```

#### Challenge-Response Authentication

Add application-layer auth:
```cpp
// After BLE connection, send challenge
uint32_t challenge = esp_random();
// Expect response = HMAC(challenge, shared_secret)
// Only enable HID after correct response
```

#### Disable Pairing Mode After First Pair

```cpp
bool hasValidBond = false;

void setup() {
    // Check if bonding exists
    if (NimBLEDevice::getNumBonds() > 0) {
        hasValidBond = true;
    }
}

// In ServerCallbacks::onConnect():
if (!hasValidBond && !isFirstPairing) {
    pServer->disconnect(connId);
}
```

## Known Vulnerabilities

### 1. Bluetooth Classic CVE-2023-45866

**Status:** Not applicable (we use BLE, not Bluetooth Classic)

### 2. KNOB Attack (Key Negotiation of Bluetooth)

**Status:** Mitigated (we enforce Secure Connections)

**Details:** Attack forces weak encryption keys (1-7 bytes). Secure Connections uses 128-bit keys.

### 3. BIAS Attack (Bluetooth Impersonation AttackS)

**Status:** Partially mitigated

**Details:** Attacker with stolen long-term key can impersonate device. Mitigation: Keep Cardputer physically secure.

**Future improvement:** Implement connection attestation.

### 4. Physical Access Attacks

**Status:** Vulnerable

**Details:** Attacker with physical access to Cardputer can:
- Read serial output (shows commands)
- Flash malicious firmware
- Extract bonding keys from flash

**Mitigation:**
- Enable flash encryption (ESP32-S3 feature)
- Use secure boot
- Physical security

## Bluetooth Security Best Practices

### During Development

1. **Monitor serial output** for security events
2. **Test pairing** with different devices
3. **Verify encryption** is enabled (check logs)
4. **Test emergency disconnect**

### During Deployment

1. **Pair only once** in secure location
2. **Keep firmware updated**
3. **Monitor for CVEs** affecting ESP32 Bluetooth stack
4. **Regular security audits**

### Red Flags

🚨 **Disconnect immediately if:**
- Unexpected "Pairing..." screen appears
- Connection shows up without user action
- Strange behavior on host computer
- Cardputer screen shows unusual activity

## Comparison: Bluetooth vs WiFi

| Security Aspect | Bluetooth LE | WiFi |
|----------------|--------------|------|
| Encryption | AES-128 (SSP) | WPA2/WPA3 |
| Range | ~10m (harder to attack remotely) | ~50m (easier remote attacks) |
| Network exposure | Point-to-point only | Network traffic visible |
| Attack surface | Smaller (BLE stack) | Larger (IP stack, DNS, DHCP) |
| **Recommendation** | ✅ **Better for KVM** | ❌ More vulnerable |

## Compliance Notes

### GDPR / Data Protection

- Keyboard input may contain personal data
- Implement data retention policy (we don't store any data)
- Users must be informed of wireless transmission

### Industry Standards

- **NIST SP 800-121:** Bluetooth security guidelines
- **ISO/IEC 27001:** Information security management
- **PCI DSS:** Don't use for payment terminals

## Disclosure Policy

If you discover a security vulnerability:

1. **Do NOT publish publicly**
2. Email: [security contact]
3. Include:
   - Description of vulnerability
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if any)

We will:
- Acknowledge within 48 hours
- Provide fix within 30 days (for critical issues)
- Credit you in release notes (if desired)

## Future Security Enhancements

### Roadmap

1. **Flash encryption** (prevents firmware extraction)
2. **Secure boot** (prevents malicious firmware)
3. **Hardware-backed key storage** (ESP32-S3 eFuse)
4. **Regular security audits**
5. **Attestation protocol** (verify firmware integrity)
6. **Optional FIDO2/WebAuthn** (for web interface auth)

### Under Consideration

- **Certificate-based authentication** (instead of PIN)
- **Post-quantum cryptography** (future-proofing)
- **Tamper detection** (accelerometer-based)
- **Remote kill switch** (via web interface)

## References

- [Bluetooth Core Specification 5.3](https://www.bluetooth.com/specifications/specs/core-specification-5-3/)
- [NIST SP 800-121 Rev. 2](https://csrc.nist.gov/publications/detail/sp/800-121/rev-2/final)
- [ESP32-S3 Security Features](https://docs.espressif.com/projects/esp-idf/en/latest/esp32s3/security/security.html)
- [NimBLE Security](https://github.com/h2zero/NimBLE-Arduino/blob/master/docs/Bluetooth%20security.md)

---

**Last Updated:** 2025-12-04
**Security Contact:** [TBD]
**GPG Key:** [TBD]
