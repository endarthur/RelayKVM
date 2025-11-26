# Video Mode Comparison Guide

This project supports **four video modes** for different use cases. You can mix and match any video mode with any HID relay device (ESP32-S3 Cardputer or Android phone).

## Quick Comparison

| Mode | Cost | Setup | Latency | Works in BIOS | Lid Closed | Across Networks | Best For |
|------|------|-------|---------|---------------|------------|-----------------|----------|
| **No Video** | $0 | 0 min | N/A | N/A | N/A | N/A | Desk setup, can see both screens |
| **USB Capture** | $15 | 2 min | ~30ms | ✅ Yes | ✅ Yes | ❌ No | Most reliable, BIOS access |
| **Miracast** | $0-5 | 10 min | ~150ms | ❌ No | ⚠️ Needs dummy | ❌ No | Wireless, forgot capture card |
| **PeerJS** | $0-5 | 1 min | ~100ms | ❌ No | ⚠️ Needs dummy | ✅ Yes | Remote access, cross-network |

*$5 for HDMI dummy plug if running host with lid closed*

---

## Mode 1: No Video (HID Only)

**Use case:** You can physically see the host PC's screen.

This turns the project into a **wireless hardware KVM** similar to Synergy, Barrier, or Mouse Without Borders, but with key advantages:

**Advantages over software KVM:**
- ✅ No network configuration needed
- ✅ Works when host network is down
- ✅ Works during boot/BIOS (can access firmware settings)
- ✅ More secure (Bluetooth pairing vs network exposure)
- ✅ Can upgrade to video modes when needed

**Setup:**
```
1. Pair Bluetooth HID device with host PC
2. Connect controller to HID device via Bluetooth/Web
3. Start typing/clicking - no video needed!
```

**Perfect for:**
- Desk with two monitors side-by-side
- Laptop + desktop on same desk
- Host PC with monitor, you're sitting nearby
- Software KVM replacement

**Example setup:**
```
Your desk:
┌─────────────┐      ┌─────────────┐
│ Controller  │      │  Host PC  │
│  (laptop)   │      │  (desktop)  │
│             │      │             │
│ ┌─────────┐ │      │ ┌─────────┐ │
│ │ Monitor │ │      │ │ Monitor │ │
│ └─────────┘ │      │ └─────────┘ │◄── You can see this!
└──────┬──────┘      └──────┬──────┘
       │ Bluetooth HID      │
       └────────────────────┘
       (ESP32-S3 or Android)
```

**Cost:** $0 (if using Android) or $40 (M5Stack Cardputer)

---

## Mode 2: USB HDMI Capture Card

**Use case:** Most reliable, professional, works in BIOS.

**How it works:**
```
Host PC ──HDMI──> Capture Card ──USB──> Controller PC
                                          │
                                          └─> Web browser displays video
Controller PC ──Bluetooth──> HID Device ──USB──> Host PC
```

**Hardware needed:**
- USB HDMI capture card ($10-15)
  - Any UVC-compatible card works
  - Recommended: EVGA XR1 Lite, Elgato Cam Link 4K, or cheap $10 ones
- USB-C cable (HID device → Target)
- USB-C/A cable (Capture card → Controller)
- HDMI cable (Target → Capture)

**Setup:**
1. Connect HDMI from target to capture card
2. Connect capture card USB to controller
3. Open NanoKVM web interface
4. Select capture card as video source
5. Connect Bluetooth to HID device

**Advantages:**
- ✅ Works during BIOS/boot/POST
- ✅ Works when target is sleeping (display keeps transmitting)
- ✅ Lowest latency (~30-50ms)
- ✅ No software setup on host PC
- ✅ Most reliable (hardware-based)
- ✅ Works offline (no network needed)

**Disadvantages:**
- ❌ Requires carrying capture card + cables
- ❌ Not fully wireless (2 cables to target)

**Latency breakdown:**
- HDMI encode: ~5ms
- USB transfer: ~10ms
- Browser decode: ~15ms
- **Total: ~30ms** (imperceptible for KVM use)

**Cost:** $15 (capture card)

**See:** [WIRING.md](WIRING.md) for detailed setup

---

## Mode 3: Miracast (WiFi Display)

**Use case:** Wireless setup, forgot capture card, both PCs support Miracast.

**How it works:**
```
Host PC ──WiFi Direct/Miracast──> Controller PC
                                    │
                                    └─> Windows "Connect" app
                                    └─> OBS captures window
                                    └─> OBS Virtual Camera
                                    └─> Web browser

Controller PC ──Bluetooth──> HID Device ──USB──> Host PC
```

**Requirements:**
- Host PC with Miracast transmitter (Windows 10+, Intel WiFi)
- Controller PC with Miracast receiver (Windows 10+, Intel AX210/AX201)
- OBS Studio with Virtual Camera
- Optional: HDMI dummy plug ($5) for lid-closed operation

**Setup:**
1. Controller: Enable Miracast receiver (`Win+K → Projecting settings`)
2. Target: Connect to controller (`Win+K → Select controller`)
3. Controller: Open OBS, capture "Connect" window
4. Controller: Start OBS Virtual Camera
5. Browser: Select "OBS-Camera" as video source

**Advantages:**
- ✅ Fully wireless (no cables between PCs)
- ✅ No external hardware needed (uses built-in WiFi)
- ✅ Good quality (H.264 encoding)

**Disadvantages:**
- ❌ Does NOT work in BIOS (only after OS boots)
- ❌ Requires compatible WiFi adapters on both PCs
- ❌ Higher latency (~100-200ms)
- ❌ Connection can drop if WiFi interference
- ❌ Requires OBS Virtual Camera setup
- ❌ HDMI dummy needed for lid-closed operation

**Latency breakdown:**
- Display capture: ~10ms
- H.264 encode: ~30ms
- WiFi Direct: ~50ms
- Decode: ~30ms
- OBS virtual camera: ~20ms
- **Total: ~150ms** (acceptable for KVM, not gaming)

**Cost:** $0 (if WiFi supports it) or $5 (HDMI dummy)

**See:** [MIRACAST.md](MIRACAST.md) for detailed setup

---

## Mode 4: PeerJS / WebRTC

**Use case:** Remote access across networks, software-only solution, simplest setup.

**How it works:**
```
Host PC (browser) ──getDisplayMedia()──> PeerJS Cloud ──Signaling──> Controller PC
                                                                        │
                                                                        └─> Web browser

(Actual video streams P2P, signaling via internet)

Controller PC ──Bluetooth──> HID Device ──USB──> Host PC
```

**Requirements:**
- Modern browser on both PCs (Chrome, Edge, Firefox)
- Internet connection for signaling (P2P works locally if on same network)
- Optional: HDMI dummy plug ($5) for lid-closed operation

**Setup:**
1. Target: Open sender page (creates peer ID)
2. Controller: Connect to peer ID via web interface
3. Target: Share screen via `getDisplayMedia()` prompt
4. Automatic P2P connection established

**Auto-setup with HID device:**
```
1. Controller: Click "Connect via PeerJS"
2. Controller: Sends peer ID to HID device via Bluetooth
3. HID device: Auto-types URL on host PC
4. Target: Browser opens sender page automatically
5. Connection established!
```

**Advantages:**
- ✅ Works across networks (even different cities!)
- ✅ No hardware needed (software only)
- ✅ Simplest setup (~1 minute)
- ✅ Uses standard WebRTC (well-supported)
- ✅ Auto-setup possible with HID auto-type
- ✅ P2P direct connection (low latency if local)

**Disadvantages:**
- ❌ Does NOT work in BIOS
- ❌ Requires internet for signaling
- ❌ Target must run browser with sender page
- ❌ Medium latency (~100-200ms)
- ❌ HDMI dummy needed for lid-closed operation

**Latency breakdown:**
- `getDisplayMedia()`: ~20ms
- WebRTC encode: ~30ms
- Network transfer: ~20ms (local) or ~50-100ms (internet)
- WebRTC decode: ~30ms
- **Total: ~100ms (local)** or **~180ms (internet)**

**Privacy note:**
- Signaling goes through PeerJS cloud (peerjs.com) - only peer discovery
- Actual video streams P2P directly between your PCs
- If on same LAN, traffic never leaves your network
- Open source: Can host your own PeerJS server

**Cost:** $0 (free PeerJS cloud) or $5 (HDMI dummy)

**See:** [PEERJS.md](PEERJS.md) for implementation details

---

## Decision Tree

**Choose your video mode:**

```
Can you see host PC's screen physically?
├─ Yes → Mode 1: No Video (HID only)
└─ No → Do you need BIOS/boot access?
    ├─ Yes → Mode 2: USB Capture (only mode that works)
    └─ No → Are both PCs on same network?
        ├─ Yes → Do you have USB capture card?
            ├─ Yes → Mode 2: USB Capture (best latency)
            └─ No → Do both PCs support Miracast?
                ├─ Yes → Mode 3: Miracast (wireless)
                └─ No → Mode 4: PeerJS (works everywhere)
        └─ No (different networks) → Mode 4: PeerJS (only option)
```

---

## Use Case Examples

### Travel / Backpack Setup

**Scenario:** Emergency access to server in backpack

**Best choice:** Mode 1 (No Video) or Mode 4 (PeerJS)
```
Hardware needed:
- Android phone (already have) OR M5Stack Cardputer ($40)
- Optional: USB-C cable for charging

No capture card needed! Use PeerJS if you need to see screen.
```

**Total cost:** $0 (Android) or $40 (Cardputer)

---

### Home Office / Desk Setup

**Scenario:** Two computers on desk, both visible

**Best choice:** Mode 1 (No Video)
```
Hardware needed:
- Android phone OR M5Stack Cardputer

Just pair and control - you can see both screens!
Acts as hardware Synergy/Barrier.
```

**Total cost:** $0 (Android) or $40 (Cardputer)

---

### Professional IT / Server Rack

**Scenario:** Need BIOS access, reliable KVM

**Best choice:** Mode 2 (USB Capture)
```
Hardware needed:
- USB HDMI capture card ($15)
- M5Stack Cardputer ($40) - more durable than phone
- HDMI + USB cables

Keep in IT bag, always works, even in BIOS.
```

**Total cost:** $55

---

### Remote Server Management

**Scenario:** Server in different location/network

**Best choice:** Mode 4 (PeerJS)
```
Hardware needed:
- Android phone OR M5Stack Cardputer
- Server runs browser with sender page

Access from anywhere with internet.
PeerJS handles NAT traversal automatically.
```

**Total cost:** $0 (Android) or $40 (Cardputer)

---

### Conference Room Presentation

**Scenario:** Control presentation laptop wirelessly

**Best choice:** Mode 3 (Miracast) or Mode 1 (No Video)
```
Hardware needed:
- Android phone (discrete) OR M5Stack Cardputer

Miracast if you want preview on your device.
No Video if you're looking at projector screen.
```

**Total cost:** $0 (Android) or $40 (Cardputer)

---

## Mixing Video Modes

You can switch between modes at any time:

**Example daily workflow:**
```
Morning (desk):
→ Mode 1: No Video
  Just control desktop from laptop, both monitors visible

Afternoon (closed lid):
→ Mode 2: USB Capture
  Desktop lid closed, using capture card for display

Evening (travel):
→ Mode 4: PeerJS
  Server at home, accessing from hotel via internet

Weekend (BIOS update):
→ Mode 2: USB Capture
  Only mode that works during BIOS flash
```

**HID device stays connected throughout** - only video source changes!

---

## Hardware Checklist

**Minimal setup (No Video mode):**
```
☐ Android phone (already have)
  OR
☐ M5Stack Cardputer ($40)
```
**Cost: $0-40**

---

**Recommended setup (USB Capture mode):**
```
☐ Android phone OR M5Stack Cardputer ($0-40)
☐ USB HDMI capture card ($15)
☐ HDMI cable (probably have)
☐ USB-C cable (probably have)
```
**Cost: $15-55**

---

**Full wireless setup (Miracast mode):**
```
☐ Android phone OR M5Stack Cardputer ($0-40)
☐ HDMI dummy plug ($5) - if using lid closed
☐ Intel WiFi AX210/AX201 on both PCs (check compatibility)
☐ OBS Studio (free)
```
**Cost: $0-45**

---

**Ultimate flexibility (All modes):**
```
☐ M5Stack Cardputer ($40) - recommended over phone for durability
☐ USB HDMI capture card ($15)
☐ HDMI dummy plug ($5)
☐ HDMI cable + USB cables (~$10)
```
**Cost: $70 total**

**Enables all 4 modes** - choose based on situation!

---

## Performance Comparison

| Metric | No Video | USB Capture | Miracast | PeerJS |
|--------|----------|-------------|----------|--------|
| **Latency** | N/A | 30ms | 150ms | 100-180ms |
| **Resolution** | N/A | Up to 4K | Up to 1080p | Up to 4K |
| **Frame rate** | N/A | 60fps | 30fps | 30fps |
| **CPU usage (controller)** | 0% | 5% | 15% | 10% |
| **CPU usage (target)** | 0% | 0% | 10% | 15% |
| **Network bandwidth** | 0 | 0 | ~15 Mbps | ~10 Mbps |
| **Reliability** | ★★★★★ | ★★★★★ | ★★★☆☆ | ★★★★☆ |

---

## Troubleshooting by Mode

### Mode 1 (No Video)
**Problem:** Can't find target screen
**Solution:** Walk around desk, look for it 😄

---

### Mode 2 (USB Capture)
**Problem:** No video in browser
- Check capture card connected to controller (not target!)
- Try different USB port
- Check browser permissions for camera access
- Test with VLC: `vlc v4l2:///dev/video0` (Linux) or Camera app (Windows)

**Problem:** Video is black
- Check HDMI cable connected target → capture card
- Try different HDMI port on host
- Check host display settings (might be disabled)

---

### Mode 3 (Miracast)
**Problem:** Can't find wireless display
- See [MIRACAST.md](MIRACAST.md) troubleshooting section
- Check WiFi adapter supports Miracast: `dxdiag`
- Update Intel WiFi drivers

**Problem:** Connection drops
- Reduce WiFi interference (move away from microwave, other networks)
- Use 5GHz band if possible
- Disable WiFi power saving

---

### Mode 4 (PeerJS)
**Problem:** Can't connect to peer
- Check internet connection (signaling requires internet)
- Check firewall not blocking WebRTC
- Try different browser (Chrome/Edge work best)

**Problem:** High latency
- Check if on same LAN (should be ~100ms)
- If remote, latency is normal (~180ms+ depending on distance)
- Close background bandwidth-heavy apps

---

## See Also

- [WIRING.md](WIRING.md) - Physical setup for USB Capture mode
- [MIRACAST.md](MIRACAST.md) - Detailed Miracast setup
- [PEERJS.md](PEERJS.md) - PeerJS implementation guide
- [BROWSER_INTEGRATION.md](BROWSER_INTEGRATION.md) - Modify web interface
- [ANDROID.md](ANDROID.md) - Android phone as HID device
