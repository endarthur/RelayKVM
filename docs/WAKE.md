# Wake Computer Guide for RelayKVM

Complete guide to waking the host computer from sleep or shutdown using RelayKVM.

## 🎯 Quick Reference

| Power State | Name | USB Wake | Wake-on-LAN | Recommended |
|-------------|------|----------|-------------|-------------|
| **S0** | Running | N/A | N/A | N/A |
| **S3** | Sleep/Suspend | ✅ **Yes** | ✅ Yes | **USB Wake** |
| **S4** | Hibernate | ⚠️ Sometimes | ✅ Yes | Try USB first |
| **S5** | Shutdown | ❌ Rarely | ✅ **Yes** | **Wake-on-LAN** |
| **G3** | Mechanical off | ❌ Never | ❌ Never | Use power button |

## ⚡ Method 1: USB Wake (Implemented)

**Best for: Sleep (S3) and Hibernate (S4)**

### How to Use

1. **Press 'W' key** on Cardputer (when not controlling target)
2. **Select 'U'** for USB Wake
3. Cardputer sends USB resume signal
4. Target wakes in 1-3 seconds

### Technical Details

- Uses USB Remote Wakeup feature (USB 2.0 spec section 7.1.7.7)
- Sends 1-15ms resume signal on USB data lines
- Handled automatically by ESP32-S3 TinyUSB stack
- Requires target to be in sleep/suspend mode (S3)

### Host PC Setup Required

#### Windows 10/11

1. **Device Manager:**
   - Right-click Start → Device Manager
   - Expand "Keyboards"
   - Find "RelayKVM Controller" (VID_FEED&PID_AE01)
   - Right-click → Properties
   - Power Management tab
   - ☑ "Allow this device to wake the computer"
   - Click OK

2. **Power Options:**
   - Control Panel → Power Options
   - Choose power plan → Change plan settings
   - Change advanced power settings
   - USB settings → USB selective suspend setting
   - Set to "Disabled" (both battery and plugged in)

3. **Sleep Settings:**
   - Settings → System → Power & sleep
   - When PC is idle: "Go to sleep after 30 minutes"
   - NOT "Shutdown" or "Hibernate" (initially)

#### Linux (Ubuntu/Debian)

```bash
# Find RelayKVM USB device
lsusb | grep -i kmputer
# Output: Bus 001 Device 005: ID feed:ae01 RelayKVM Controller

# Find device path
ls -l /sys/bus/usb/devices/*/idVendor | xargs grep -l feed
# Example output: /sys/bus/usb/devices/1-4/idVendor

# Enable wakeup for this device
echo enabled | sudo tee /sys/bus/usb/devices/1-4/power/wakeup

# Make permanent (create udev rule)
echo 'ACTION=="add", SUBSYSTEM=="usb", ATTR{idVendor}=="feed", ATTR{idProduct}=="ae01", ATTR{power/wakeup}="enabled"' | \
  sudo tee /etc/udev/rules.d/90-kmputer-wakeup.rules

# Reload udev rules
sudo udevadm control --reload-rules
sudo udevadm trigger

# Verify wakeup is enabled
cat /sys/bus/usb/devices/1-4/power/wakeup
# Should output: enabled
```

#### macOS

```bash
# System Preferences
# → Energy Saver (or Battery on newer versions)
# ☑ "Wake for network access"
# ☑ "Start up automatically after a power failure"

# Allow USB devices to wake
sudo pmset -a ttyskeepawake 1
sudo pmset -a usb 1

# Verify settings
pmset -g
```

### BIOS/UEFI Configuration

Most modern motherboards support USB wake by default, but verify:

1. **Enter BIOS/UEFI:**
   - Restart PC
   - Press Del / F2 / F10 (varies by manufacturer)

2. **Find USB Wake Setting:**
   - Look in "Power Management" or "Advanced" section
   - Names vary:
     - "USB Wake Support"
     - "Wake on USB"
     - "USB Device Wake"
     - "Resume by USB Device"
     - "USB Power Delivery in S3/S4/S5"

3. **Enable the setting**

4. **Save and Exit**

### Common USB Wake Settings by Manufacturer

| Manufacturer | BIOS Location | Setting Name |
|--------------|---------------|--------------|
| **ASUS** | Advanced → APM | "Power On By PCI-E/PCI" |
| **MSI** | Settings → Advanced → Wake Up | "Resume by USB Device" |
| **Gigabyte** | Power Management | "USB Wake Up From S3/S4/S5" |
| **ASRock** | Advanced → ACPI | "USB Keyboard/Mouse Wake" |
| **Dell** | Power Management | "USB Wake Support" |
| **HP** | Advanced → Power-On Options | "USB Wake on Device Insertion" |
| **Lenovo** | Config → Power | "USB Wake" |

### Troubleshooting USB Wake

#### "USB not connected" Error

**Cause:** Cardputer not plugged into target, or USB cable faulty

**Solution:**
- Check USB-C cable is securely connected
- Try different USB port on host
- Use data-capable USB cable (not charge-only)
- Check Device Manager shows RelayKVM

#### "Wake signal FAILED"

**Causes:**
1. Target is shutdown (S5), not sleep (S3)
2. USB wake not enabled in Device Manager
3. BIOS USB wake setting disabled
4. USB port loses power in sleep mode

**Solutions:**
1. **Test sleep mode first:**
   ```
   Windows: Start → Power → Sleep
   Linux: systemctl suspend
   macOS: Apple menu → Sleep
   ```
   Then press 'W' on Cardputer

2. **Verify Device Manager settings** (Windows)

3. **Check BIOS setting** is enabled

4. **Try different USB port** (ports near PS/2 or on motherboard I/O usually work best)

#### Target Wakes for 1 Second Then Sleeps Again

**Cause:** Fast Startup or Modern Standby interference

**Solution (Windows):**
1. Control Panel → Power Options
2. Choose what the power buttons do
3. "Change settings that are currently unavailable"
4. ☐ Uncheck "Turn on fast startup"
5. Restart PC

### Power State Reference

**S3 (Sleep/Suspend to RAM):**
- RAM stays powered
- USB may stay powered (motherboard-dependent)
- Fastest wake (1-3 seconds)
- ✅ **USB wake works reliably**

**S4 (Hibernate/Suspend to Disk):**
- RAM saved to disk, then powered off
- USB usually loses power
- Slower wake (5-10 seconds)
- ⚠️ **USB wake sometimes works** (BIOS-dependent)

**S5 (Soft Shutdown):**
- Everything powered off except wake circuits
- USB usually loses power
- ❌ **USB wake rarely works** (use WoL instead)

## 🌐 Method 2: Wake-on-LAN (To Be Implemented)

**Best for: Complete shutdown (S5)**

### How It Will Work (Future)

1. Press 'W' on Cardputer
2. Select 'W' for Wake-on-LAN
3. Cardputer connects to WiFi
4. Sends "magic packet" to target's MAC address
5. Target wakes from shutdown

### Implementation Plan

#### Firmware Changes Needed

```cpp
#include <WiFi.h>
#include <WiFiUdp.h>

// Configuration (store in EEPROM/Preferences)
struct WoLConfig {
  char wifi_ssid[32];
  char wifi_password[64];
  uint8_t target_mac[6];
};

WoLConfig wolConfig;

void sendWakeOnLAN(uint8_t mac[6]) {
  // Connect to WiFi if not connected
  if (WiFi.status() != WL_CONNECTED) {
    M5Cardputer.Display.println("Connecting to WiFi...");
    WiFi.begin(wolConfig.wifi_ssid, wolConfig.wifi_password);

    int timeout = 20; // 10 seconds
    while (WiFi.status() != WL_CONNECTED && timeout > 0) {
      delay(500);
      M5Cardputer.Display.print(".");
      timeout--;
    }

    if (WiFi.status() != WL_CONNECTED) {
      M5Cardputer.Display.println("\nWiFi failed!");
      return;
    }
  }

  // Build magic packet
  uint8_t magicPacket[102];

  // First 6 bytes: 0xFF
  for (int i = 0; i < 6; i++) {
    magicPacket[i] = 0xFF;
  }

  // Next 96 bytes: MAC address repeated 16 times
  for (int i = 0; i < 16; i++) {
    memcpy(&magicPacket[6 + i * 6], mac, 6);
  }

  // Send UDP broadcast
  WiFiUDP udp;
  IPAddress broadcastIP(255, 255, 255, 255);
  udp.beginPacket(broadcastIP, 9); // Port 9 for WoL
  udp.write(magicPacket, sizeof(magicPacket));
  udp.endPacket();

  M5Cardputer.Display.println("Magic packet sent!");
  Serial.println("WoL: Magic packet sent");
}

void configureWoL() {
  // TODO: UI to enter WiFi SSID/password and MAC address
  // Store in Preferences for persistence
}
```

#### Battery Impact

| WiFi Usage | Battery Drain | Runtime |
|------------|---------------|---------|
| Off | ~450mA | ~3.5 hours |
| Connecting (10s) | ~600mA | -2 min |
| Idle connected | ~500mA | ~3 hours |

**Recommendation:** Keep WiFi off normally, enable only when needed for WoL.

### Host PC Setup for Wake-on-LAN

#### BIOS/UEFI Settings

1. Enter BIOS
2. Find settings (usually in "Power Management"):
   - **"Wake-on-LAN"** → Enabled
   - **"PCI-E/PCI Wake"** → Enabled
   - **"PME Event Wake Up"** → Enabled
   - **"Power On by PCI-E"** → Enabled

3. Save and exit

#### Windows 10/11

1. **Device Manager:**
   - Expand "Network adapters"
   - Right-click your Ethernet adapter → Properties
   - "Advanced" tab:
     - "Wake on Magic Packet" → Enabled
     - "Wake on Pattern Match" → Enabled
   - "Power Management" tab:
     - ☑ "Allow this device to wake the computer"
     - ☑ "Only allow a magic packet to wake the computer"
   - Click OK

2. **Power Options:**
   - Control Panel → Power Options
   - "Choose what closing the lid does" (laptops)
   - "Change settings that are currently unavailable"
   - ☐ Uncheck "Turn on fast startup" (optional, but recommended)

3. **Find MAC Address:**
   ```cmd
   ipconfig /all
   ```
   Look for "Physical Address" under your Ethernet adapter
   Example: `AA-BB-CC-DD-EE-FF`

#### Linux

```bash
# Install ethtool
sudo apt install ethtool

# Enable WoL on interface (replace eth0 with your interface)
sudo ethtool -s eth0 wol g

# Make permanent (systemd service)
sudo tee /etc/systemd/system/wol.service << EOF
[Unit]
Description=Enable Wake-on-LAN
Requires=network.target
After=network.target

[Service]
Type=oneshot
ExecStart=/usr/sbin/ethtool -s eth0 wol g
RemainAfterExit=yes

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl enable wol
sudo systemctl start wol

# Verify WoL is enabled
sudo ethtool eth0 | grep Wake-on
# Should show: Wake-on: g

# Get MAC address
ip link show eth0
# Look for "link/ether aa:bb:cc:dd:ee:ff"
```

#### macOS

```bash
# System Preferences → Energy Saver
# ☑ "Wake for network access"

# Get MAC address
ifconfig en0 | grep ether
# Output: ether aa:bb:cc:dd:ee:ff
```

### Testing Wake-on-LAN (Current Methods)

Until WoL is implemented in RelayKVM, test with these tools:

#### From Linux/macOS

```bash
# Install wakeonlan
sudo apt install wakeonlan  # Ubuntu/Debian
brew install wakeonlan      # macOS

# Send magic packet
wakeonlan AA:BB:CC:DD:EE:FF

# Or use Python
python3 << EOF
import socket

mac = 'AABBCCDDEEFF'  # No separators
data = 'FF' * 6 + mac * 16
sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
sock.sendto(bytes.fromhex(data), ('<broadcast>', 9))
EOF
```

#### From Windows

```powershell
# PowerShell WoL function
function Send-WOL {
    param([string]$Mac)
    $MacByteArray = $Mac -split '[:-]' | ForEach-Object { [byte]('0x' + $_) }
    $MagicPacket = ([byte[]]@(0xFF * 6)) + ($MacByteArray * 16)
    $UdpClient = New-Object System.Net.Sockets.UdpClient
    $UdpClient.Connect(([IPAddress]::Broadcast), 9)
    $UdpClient.Send($MagicPacket, $MagicPacket.Length)
    $UdpClient.Close()
}

# Usage
Send-WOL -Mac "AA-BB-CC-DD-EE-FF"
```

### WoL Requirements Checklist

Before WoL will work:
- [ ] Host PC connected to network via **Ethernet** (WiFi WoL is unreliable)
- [ ] BIOS/UEFI "Wake-on-LAN" enabled
- [ ] OS network adapter WoL enabled
- [ ] Host PC plugged into **power** (AC power, not just battery)
- [ ] Target MAC address known
- [ ] RelayKVM and host on **same LAN** (or router configured for WoL forwarding)

### Limitations

❌ **WoL does NOT work:**
- Over the internet (without router configuration)
- With WiFi on most laptops (Ethernet required)
- If PSU is switched off (G3 state)
- If BIOS setting is disabled
- Through VPNs (usually)

✅ **WoL DOES work:**
- Same LAN/subnet
- Wired Ethernet connection
- Shutdown (S5) and Hibernate (S4)
- With router WoL forwarding configured

## 🔀 Hybrid Approach (Recommended)

Use both methods intelligently:

```cpp
void smartWake() {
  // Try USB wake first (faster, no WiFi needed)
  M5Cardputer.Display.println("Trying USB wake...");
  if (wakeTargetComputer()) {
    // Wait 3 seconds to see if target responds
    delay(3000);

    // Check if target is awake (try sending HID command)
    Keyboard.press(KEY_LEFT_CTRL);
    delay(10);
    Keyboard.release(KEY_LEFT_CTRL);

    if (USB.connected()) {
      M5Cardputer.Display.println("Wake successful!");
      return;
    }
  }

  // USB wake failed, try WoL
  M5Cardputer.Display.println("USB failed, trying WoL...");
  sendWakeOnLAN(wolConfig.target_mac);
  M5Cardputer.Display.println("WoL packet sent");
  M5Cardputer.Display.println("Target should wake in 5-10s");
}
```

## 📋 Comparison: USB Wake vs Wake-on-LAN

| Aspect | USB Wake | Wake-on-LAN |
|--------|----------|-------------|
| **Speed** | ⚡ 1-3 seconds | 🐌 5-15 seconds |
| **Range** | Cable only | Same LAN |
| **From S3 (Sleep)** | ✅ Yes | ✅ Yes |
| **From S5 (Shutdown)** | ❌ Rarely | ✅ Yes |
| **WiFi needed** | ❌ No | ✅ Yes |
| **Battery drain** | ➕ Minimal | ➕➕ Moderate |
| **Setup complexity** | ⭐⭐ Easy | ⭐⭐⭐ Medium |
| **Reliability** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| **Works over internet** | ❌ No | ⚠️ With config |

## 🎯 Best Practices

### For Home Use

1. **Configure target for Sleep (S3), not Shutdown**
   - Power Options → Sleep after 30 min
   - Use USB wake (faster, simpler)

2. **Only use WoL for complete shutdown**
   - Rare cases (power saving, security)

3. **Test both methods** to verify setup

### For Office/Server Use

1. **Enable both USB wake and WoL** in BIOS

2. **Wired Ethernet** required for reliability

3. **Document MAC addresses** of all targets

4. **Consider security:**
   - WoL packets not encrypted
   - Anyone on LAN can wake target
   - Use MAC filtering or VLANs if needed

### For Portable/Travel Use

1. **Rely on USB wake** (no network available)

2. **Set target to Sleep, not Shutdown**

3. **Battery-powered Cardputer** works fine for USB wake

## 📚 Further Reading

- [USB 2.0 Specification Section 7.1.7.7 (Resume Signaling)](https://www.usb.org/document-library/usb-20-specification)
- [Microsoft: Remote Wake-Up of USB Devices](https://learn.microsoft.com/en-us/windows-hardware/drivers/usbcon/remote-wakeup-of-usb-devices)
- [Wake-on-LAN Protocol (AMD Magic Packet)](https://en.wikipedia.org/wiki/Wake-on-LAN)
- [Linux: ethtool Wake-on-LAN](https://man7.org/linux/man-pages/man8/ethtool.8.html)

---

**Last Updated:** 2025-11-23
**Status:** USB Wake ✅ Implemented | Wake-on-LAN ⏳ Planned
