# Security Considerations for RelayKVM

## Overview

RelayKVM transmits keyboard and mouse input wirelessly, which poses security risks if not properly secured. This document outlines the security measures implemented and recommendations for safe use.

## Threat Model

### Potential Threats

1. **Eavesdropping:** Attacker intercepts Bluetooth traffic to capture keystrokes
2. **Man-in-the-Middle (MITM):** Attacker impersonates either device during pairing
3. **Unauthorized Access:** Attacker connects to unpaired device
4. **Physical Access:** Attacker gains physical access to Cardputer
5. **Replay Attacks:** Attacker records and replays captured commands

### Assets to Protect

- Keyboard input (passwords, sensitive data)
- Mouse movements (could reveal workflow/behavior)
- Target computer control (unauthorized command execution)
- Pairing credentials (Bluetooth bonding keys)

## Implemented Security Measures

### 1. Bluetooth LE Secure Simple Pairing (SSP)

**Implementation:**
```cpp
NimBLEDevice::setSecurityAuth(true, true, true);
// - Bonding: true (stores keys for reconnection)
// - MITM protection: true (requires PIN confirmation)
// - Secure Connections: true (uses ECDH key exchange)
```

**Benefits:**
- ECDH P-256 elliptic curve cryptography
- AES-128 encryption for all traffic
- Protection against passive eavesdropping

### 2. Display + Keyboard I/O Capability

**Implementation:**
```cpp
NimBLEDevice::setSecurityIOCap(BLE_HS_IO_DISPLAY_YESNO);
```

**Benefits:**
- Numeric Comparison: Both devices show same 6-digit PIN
- User verification: Must physically confirm on both sides
- Prevents "Just Works" pairing (vulnerable to MITM)

### 3. Pairing Confirmation on Cardputer

**Implementation:**
- PIN displayed on Cardputer screen
- User must press 'Y' or 'N' on physical keyboard
- No automatic acceptance

**Benefits:**
- Requires physical access to Cardputer
- User awareness of pairing attempts
- Prevents unauthorized pairing

### 4. Emergency Disconnect

**Implementation:**
```cpp
// Press ESC key to force disconnect
if (key == "ESC") {
    pServer->disconnect(pServer->getConnId());
}
```

**Benefits:**
- Quick disconnect if suspicious activity
- Physical button on device (requires physical access)

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

**Last Updated:** 2025-11-23
**Security Contact:** [TBD]
**GPG Key:** [TBD]
