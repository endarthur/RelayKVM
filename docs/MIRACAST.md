# Miracast Video Mode

Miracast (WiFi Display/WiDi) allows wireless screen transmission from the host PC to your controller PC. This is useful when you forgot your USB HDMI capture card or want a fully wireless setup.

## Overview

**What is Miracast?**
- Wireless display protocol (essentially "HDMI over WiFi")
- Uses WiFi Direct (P2P) for connection
- Transmits H.264 encoded video via RTP/RTSP
- Latency: ~100-200ms (acceptable for KVM use)

**When to use Miracast:**
- ✅ Don't have USB HDMI capture card with you
- ✅ Want fully wireless setup (no cables between PCs)
- ✅ Both PCs have compatible WiFi adapters
- ❌ Need to access BIOS (Miracast only works after OS boots)
- ❌ Need low latency (<50ms)

## Requirements

### Host PC (Computer to Control)

**Windows 10/11:**
- Intel WiFi adapter (AX210, AX201, AC9560, AC8265, etc.)
- Or any WiFi adapter with "Miracast transmitter" support
- Check support: `Settings → System → Display → Multiple displays → "Connect to a wireless display"`

**Linux:**
- WiFi adapter with P2P mode support (`iw list | grep "P2P-client"`)
- `gnome-network-displays` package (GNOME)
- Or manual setup with `wpa_supplicant` + `gstreamer`

**macOS:**
- Does not support Miracast (use AirPlay instead, or USB capture/PeerJS)

### Controller PC (Your Laptop)

**Windows 10/11:**
- Intel WiFi adapter with "Miracast receiver" support
- Framework 13 with Intel AX210/AX201: ✅ Supported
- Check: `Settings → System → Projecting to this PC → "Available everywhere"`

**Linux:**
- `gnome-network-displays` (GNOME) - can act as receiver
- Or use `miraclecast` (unmaintained, but works on some systems)

### Optional: HDMI Dummy Plug

When running host PC with **lid closed**, you need an HDMI dummy plug ($5):
- Tricks GPU into rendering (laptops disable display when lid closed)
- Any cheap HDMI dummy with 1920x1080 support works
- Plug into host PC's HDMI port

## Setup Guide

### Step 1: Enable Miracast Receiver on Controller PC

**Windows:**
```powershell
# Open Settings → System → Projecting to this PC

1. Set "Available everywhere" or "Available everywhere on secure networks"
2. Set "First time only" or "Never" for PIN requirement
3. Note the connection name (e.g., "DESKTOP-ABC123")
```

**Linux (GNOME):**
```bash
# Install gnome-network-displays
sudo apt install gnome-network-displays

# Launch receiver
gnome-network-displays
# Click "Enable" to start listening
```

### Step 2: Connect from Host PC

**Windows:**
```
1. Press Win+K (or Win+P → "Connect to wireless display")
2. Wait for controller PC to appear in list
3. Click controller PC name to connect
4. Enter PIN if prompted (shown on controller screen)
5. Target screen now appears in "Connect" app window on controller
```

**Linux:**
```bash
# Using gnome-network-displays
gnome-network-displays
# Click on detected receiver and "Connect"
```

### Step 3: Capture Miracast Window with OBS

Since browsers can't directly display the Miracast window, we use OBS Virtual Camera:

**Install OBS Studio:**
```bash
# Windows: Download from https://obsproject.com
# Linux:
sudo apt install obs-studio
```

**Configure OBS:**
```
1. Launch OBS Studio

2. Add Window Capture source:
   - Sources → Add → Window Capture
   - Select "Connect" window (Miracast receiver)
   - Click OK

3. Start Virtual Camera:
   - Tools → VirtualCam → Start
   - This creates a virtual webcam that outputs the Miracast window
```

### Step 4: Use Virtual Camera in Browser

**In NanoKVM web interface:**
```javascript
// Select OBS Virtual Camera as video source
const stream = await navigator.mediaDevices.getUserMedia({
  video: {
    deviceId: 'OBS-Camera'  // or 'OBS Virtual Camera'
  }
});

videoElement.srcObject = stream;
```

**Or manually in Chrome:**
```
1. Open NanoKVM web interface
2. When prompted for camera permission, select "OBS-Camera"
3. Host PC's screen now visible in web interface!
```

## Lid Closed Setup

If you want to run host PC with lid closed:

**Requirements:**
- HDMI dummy plug ($5)
- Configure Windows to NOT sleep when lid closed

**Windows setup:**
```powershell
# 1. Insert HDMI dummy into host PC

# 2. Set lid close action
Control Panel → Power Options → Choose what closing the lid does
→ Set to "Do nothing" when plugged in

# 3. Configure display settings
Settings → System → Display
→ Ensure HDMI dummy is detected as "Display 2"
→ Set as "Extend" or "Duplicate"

# 4. Connect Miracast (will transmit HDMI dummy's display)
Win+K → Connect to controller PC
```

**Linux setup:**
```bash
# 1. Insert HDMI dummy

# 2. Disable lid close sleep
sudo systemctl mask sleep.target suspend.target

# 3. Or use logind.conf
sudo nano /etc/systemd/logind.conf
# Set: HandleLidSwitch=ignore

sudo systemctl restart systemd-logind

# 4. Verify display detected
xrandr  # Should show HDMI-1 or similar

# 5. Connect Miracast
gnome-network-displays
```

## Workflow Summary

**Daily use:**
```
1. Controller PC: Open "Connect" app (Win+K receiver)
2. Host PC: Connect to controller (Win+K)
3. Controller PC: OBS Virtual Camera running
4. Controller PC: Open web interface, select OBS-Camera
5. Controller PC: Connect Bluetooth to RelayKVM device
6. Start controlling host PC!
```

**First-time setup time:** ~10 minutes
**Daily connection time:** ~30 seconds

## Advantages vs USB Capture

| Feature | Miracast | USB Capture |
|---------|----------|-------------|
| **Cables needed** | 0 | 2 (HDMI + USB) |
| **Works during BIOS** | ❌ No | ✅ Yes |
| **Works when sleeping** | ❌ No | ✅ Yes |
| **Latency** | ~150ms | ~30ms |
| **Cost** | $0-5* | $15 |
| **Setup complexity** | Medium | Low |
| **Reliability** | Medium | High |

*$5 for HDMI dummy if using lid closed

## Troubleshooting

### "No wireless displays found"

**Check WiFi adapter support:**
```powershell
# Windows
dxdiag → Save All Information → Search for "Miracast"
# Should say "Available" (not "Available, with HDCP")

# Or check in Settings
Settings → System → Display → Multiple displays
→ Should see "Connect to wireless display"
```

**Enable WiFi Direct:**
```powershell
# Windows: Ensure WiFi Direct service is running
services.msc → Find "WiFi Direct Services Access Manager"
→ Set to Automatic, Start
```

### "Can't connect to [PC Name]"

1. **Both PCs on same WiFi band:**
   - Miracast uses WiFi Direct, but both adapters should support same bands
   - Try forcing 5GHz: `netsh wlan set autoconfig enabled=no interface="Wi-Fi"`

2. **Firewall blocking:**
   ```powershell
   # Allow RTSP (port 7236) and RTP (dynamic ports)
   netsh advfirewall firewall add rule name="Miracast" dir=in action=allow protocol=TCP localport=7236
   netsh advfirewall firewall add rule name="Miracast RTP" dir=in action=allow protocol=UDP localport=15000-15100
   ```

3. **Driver issues:**
   - Update WiFi driver to latest from Intel/manufacturer
   - Restart WiFi adapter: `Device Manager → Network adapters → Disable/Enable`

### "Connection drops frequently"

1. **Interference:** Move away from other WiFi networks, microwaves, Bluetooth
2. **Power saving:** Disable WiFi adapter power saving
   ```
   Device Manager → Network adapters → Intel WiFi → Properties
   → Power Management → Uncheck "Allow computer to turn off this device"
   ```
3. **Use 5GHz:** 5GHz WiFi Direct is more stable than 2.4GHz

### "Miracast window is black"

1. **Graphics driver issue:** Update GPU driver
2. **HDCP protection:** Some apps (Netflix, etc.) block Miracast
3. **Lid closed without dummy:** Insert HDMI dummy plug

### "High latency / stuttering video"

1. **Reduce resolution:** Lower host display to 1280x720
   ```
   Settings → System → Display → Display resolution → 1280x720
   ```
2. **Close background apps:** Free up WiFi bandwidth
3. **Check WiFi signal:** Use `netsh wlan show interfaces` (Windows)
   - Signal should be >80%
4. **Use USB capture instead:** Miracast is inherently higher latency

## Advanced: Linux Miracast Receiver (without GNOME)

For headless or non-GNOME systems:

**Using miraclecast (experimental):**
```bash
# Install dependencies
sudo apt install build-essential git autoconf libtool libglib2.0-dev

# Clone and build
git clone https://github.com/albfan/miraclecast.git
cd miraclecast
./autogen.sh
./configure --prefix=/usr
make
sudo make install

# Run receiver
sudo miracle-wifid &  # WiFi daemon
miracle-sinkctl       # Sink controller

# In miracle-sinkctl prompt:
run
# Wait for host PC to connect
```

**Using gstreamer + wpa_supplicant (expert):**
```bash
# This requires manual WFD protocol implementation
# Not recommended unless you're comfortable with:
# - wpa_supplicant P2P configuration
# - RTSP session setup
# - gstreamer pipeline debugging

# Example pipeline (after RTSP session established):
gst-launch-1.0 rtspsrc location=rtsp://192.168.173.1:7236/wfd1.0 ! \
    rtph264depay ! avdec_h264 ! videoconvert ! autovideosink
```

For most users, **stick with GNOME Network Displays** or **Windows Connect app**.

## Integration with Web Interface

See [BROWSER_INTEGRATION.md](BROWSER_INTEGRATION.md) for modifying the web interface to:
- Auto-detect OBS Virtual Camera
- Provide one-click Miracast setup button
- Show connection status and latency

## See Also

- [VIDEO_MODES.md](VIDEO_MODES.md) - Comparison of all 4 video modes
- [WIRING.md](WIRING.md) - Hardware setup for USB capture mode
- [PEERJS.md](PEERJS.md) - WebRTC alternative to Miracast
