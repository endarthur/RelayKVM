# PeerJS / WebRTC Video Mode

PeerJS provides WebRTC-based screen sharing that works across networks, making it perfect for remote access scenarios. This is the **simplest software-only video solution**.

## Overview

**What is PeerJS?**
- Free WebRTC signaling service (peerjs.com)
- Handles NAT traversal, peer discovery
- Actual video streams P2P (not through cloud)
- ~50 lines of code total implementation

**When to use PeerJS:**
- ✅ Need access across different networks (home ↔ office)
- ✅ Want software-only solution (no hardware)
- ✅ Host PC can run browser tab
- ❌ Need BIOS access (PeerJS only works after OS boots)
- ❌ Need lowest latency (USB capture is better)

## How It Works

```
1. Host PC opens sender page in browser
2. Sender page gets random peer ID from PeerJS cloud
3. Share peer ID with controller (manual or auto-type via HID)
4. Controller connects to peer ID
5. WebRTC establishes P2P connection
6. Target streams screen via getDisplayMedia()
7. Controller receives video stream

Signaling: PeerJS cloud (internet required)
Video/data: Direct P2P (stays local if on same LAN)
```

## Architecture

### Signaling vs Data Paths

```
Signaling (small, goes through internet):
Target ──┐                    ┌──> Controller
         │                    │
         └──> PeerJS Cloud ──┘
              (peerjs.com)

Data (large, tries to stay local):
Target ═════════════════════════> Controller
         (P2P direct connection)
         (or TURN relay if needed)
```

**On same LAN:**
- Signaling: Internet (just for peer discovery)
- Video: Direct P2P (never leaves your network)
- Latency: ~100ms

**Across networks:**
- Signaling: Internet
- Video: Internet (or TURN relay)
- Latency: ~180-300ms (depends on distance)

## Implementation

### Sender Page (Host PC)

Create `sender.html` to run on host PC:

```html
<!DOCTYPE html>
<html>
<head>
    <title>PeerJS KVM Sender</title>
    <script src="https://unpkg.com/peerjs@1.5.2/dist/peerjs.min.js"></script>
    <style>
        body {
            font-family: monospace;
            padding: 20px;
            background: #1e1e1e;
            color: #00ff00;
        }
        #peer-id {
            font-size: 24px;
            background: #000;
            padding: 10px;
            border: 2px solid #00ff00;
            margin: 20px 0;
            word-break: break-all;
        }
        #status {
            font-size: 18px;
            margin: 10px 0;
        }
        .connected { color: #00ff00; }
        .disconnected { color: #ff0000; }
        #qr-code {
            margin: 20px 0;
        }
    </style>
</head>
<body>
    <h1>🖥️ KVM Sender (Host PC)</h1>

    <div id="status" class="disconnected">● Waiting for connection...</div>

    <div>
        <strong>Your Peer ID:</strong>
        <div id="peer-id">Initializing...</div>
    </div>

    <div>
        <strong>Share this URL with controller:</strong>
        <div id="share-url" style="margin: 10px 0; background: #000; padding: 10px;">
            Waiting for peer ID...
        </div>
    </div>

    <div id="qr-code"></div>

    <div style="margin-top: 20px; font-size: 12px; opacity: 0.7;">
        Keep this page open. Controller will connect and view your screen.
    </div>

    <script>
        // Initialize PeerJS
        const peer = new Peer({
            host: 'peerjs.com',
            port: 443,
            secure: true,
            debug: 2  // Show connection info in console
        });

        let localStream = null;
        let currentCall = null;

        // When we get our peer ID
        peer.on('open', (id) => {
            console.log('My peer ID:', id);
            document.getElementById('peer-id').textContent = id;

            // Generate share URL
            const baseUrl = 'https://your-nanokvm-interface.com/kvm.html';  // TODO: Update this
            const shareUrl = `${baseUrl}#${id}`;
            document.getElementById('share-url').textContent = shareUrl;

            // Optional: Generate QR code for easy mobile scanning
            // new QRCode(document.getElementById('qr-code'), shareUrl);
        });

        // When controller calls us
        peer.on('call', async (call) => {
            console.log('Incoming call from:', call.peer);

            // Get screen capture
            try {
                localStream = await navigator.mediaDevices.getDisplayMedia({
                    video: {
                        cursor: 'always',     // Include cursor in capture
                        displaySurface: 'monitor',  // Prefer monitor over window
                        logicalSurface: false,
                        width: { ideal: 1920 },
                        height: { ideal: 1080 },
                        frameRate: { ideal: 30, max: 60 }
                    },
                    audio: false  // No audio for KVM
                });

                // Answer call with our screen stream
                call.answer(localStream);
                currentCall = call;

                document.getElementById('status').textContent = '● Connected to controller';
                document.getElementById('status').className = 'connected';

                console.log('Streaming screen to controller');

            } catch (err) {
                console.error('Failed to capture screen:', err);
                document.getElementById('status').textContent = `● Error: ${err.message}`;

                // If user cancelled, show helpful message
                if (err.name === 'NotAllowedError') {
                    alert('Please allow screen sharing to use KVM');
                }
            }
        });

        // Connection events
        peer.on('connection', (conn) => {
            console.log('Data connection from:', conn.peer);

            conn.on('data', (data) => {
                console.log('Received:', data);
                // Could receive commands from controller here
            });
        });

        peer.on('disconnected', () => {
            console.log('Disconnected from PeerJS server');
            document.getElementById('status').textContent = '● Disconnected from server';
            document.getElementById('status').className = 'disconnected';

            // Try to reconnect
            setTimeout(() => peer.reconnect(), 5000);
        });

        peer.on('error', (err) => {
            console.error('PeerJS error:', err);
            document.getElementById('status').textContent = `● Error: ${err.type}`;
        });

        // When user closes screen sharing
        if (localStream) {
            localStream.getVideoTracks()[0].addEventListener('ended', () => {
                console.log('Screen sharing stopped by user');
                document.getElementById('status').textContent = '● Screen sharing stopped';
                document.getElementById('status').className = 'disconnected';

                if (currentCall) {
                    currentCall.close();
                    currentCall = null;
                }
            });
        }

        // Cleanup on page unload
        window.addEventListener('beforeunload', () => {
            if (localStream) {
                localStream.getTracks().forEach(track => track.stop());
            }
            if (currentCall) {
                currentCall.close();
            }
            peer.destroy();
        });
    </script>
</body>
</html>
```

**Usage:**
```
1. Save as sender.html
2. Open on host PC: firefox sender.html
3. Copy peer ID or share URL
4. Keep page open (must stay open to stream)
```

---

### Receiver Integration (Controller PC)

Modify NanoKVM web interface to connect to PeerJS sender:

```javascript
// Add to existing web interface
class PeerJSVideoAdapter {
    constructor() {
        this.peer = null;
        this.connection = null;
        this.call = null;
        this.videoElement = document.getElementById('video');
    }

    async connect(remotePeerId) {
        // Initialize our peer
        this.peer = new Peer({
            host: 'peerjs.com',
            port: 443,
            secure: true
        });

        return new Promise((resolve, reject) => {
            this.peer.on('open', (id) => {
                console.log('Our peer ID:', id);

                // Establish data connection
                this.connection = this.peer.connect(remotePeerId);

                this.connection.on('open', () => {
                    console.log('Data connection established');

                    // Call remote peer (request their screen)
                    this.call = this.peer.call(remotePeerId, null);  // We're not sending video

                    this.call.on('stream', (remoteStream) => {
                        console.log('Receiving remote stream');

                        // Display remote screen
                        this.videoElement.srcObject = remoteStream;
                        this.videoElement.play();

                        resolve();
                    });

                    this.call.on('error', (err) => {
                        console.error('Call error:', err);
                        reject(err);
                    });
                });

                this.connection.on('error', (err) => {
                    console.error('Connection error:', err);
                    reject(err);
                });
            });

            this.peer.on('error', (err) => {
                console.error('Peer error:', err);
                reject(err);
            });

            // Timeout after 30 seconds
            setTimeout(() => reject(new Error('Connection timeout')), 30000);
        });
    }

    sendData(data) {
        if (this.connection && this.connection.open) {
            this.connection.send(data);
        }
    }

    disconnect() {
        if (this.call) this.call.close();
        if (this.connection) this.connection.close();
        if (this.peer) this.peer.destroy();

        this.videoElement.srcObject = null;
    }
}

// Usage in web interface
let peerJSAdapter = null;

async function connectViaPeerJS() {
    // Get peer ID from URL hash: https://controller.com/kvm.html#peer-id-here
    const remotePeerId = window.location.hash.slice(1);

    if (!remotePeerId) {
        const manualId = prompt('Enter target peer ID:');
        if (!manualId) return;
        window.location.hash = manualId;
        remotePeerId = manualId;
    }

    peerJSAdapter = new PeerJSVideoAdapter();

    try {
        await peerJSAdapter.connect(remotePeerId);
        console.log('Connected to host PC!');

        // Now hook up keyboard/mouse events
        setupInputHandlers(peerJSAdapter);

    } catch (err) {
        alert(`Failed to connect: ${err.message}`);
    }
}

// Connect automatically if peer ID in URL
if (window.location.hash) {
    connectViaPeerJS();
}
```

**HTML UI additions:**

```html
<button onclick="connectViaPeerJS()">Connect via PeerJS</button>

<div id="peerjs-status" style="display: none;">
    <span id="peerjs-latency">Latency: --</span>
    <span id="peerjs-bandwidth">Bandwidth: --</span>
</div>
```

---

## Auto-Type Setup

Since we already have HID relay (Cardputer or Android), we can auto-type the sender URL!

### Cardputer Firmware Addition

```cpp
// Add to RelayKVM.ino
void autotypePeerJSUrl(const char* peerId) {
    M5Cardputer.Display.println("Auto-typing PeerJS URL...");

    delay(1000);  // Give user time to focus browser

    // Open browser (Windows)
    keyboard.press(KEY_LEFT_GUI);  // Win key
    keyboard.print("r");
    keyboard.releaseAll();
    delay(500);

    // Type URL
    keyboard.print("https://your-sender-page.html");
    keyboard.write(KEY_RETURN);

    delay(2000);  // Wait for page load

    M5Cardputer.Display.println("Done! Sender page should be open.");
}

// Bluetooth command handler
void onBluetoothCommand(const char* command, const char* data) {
    if (strcmp(command, "AUTOTYPE_PEERJS") == 0) {
        autotypePeerJSUrl(data);  // data contains peer ID
    }
}
```

### Web Interface Auto-Setup

```javascript
async function setupPeerJSAutoConnect() {
    // 1. Generate our peer ID
    const peer = new Peer();

    peer.on('open', async (id) => {
        console.log('Our peer ID:', id);

        // 2. Send command to HID device via Bluetooth
        await bluetoothAdapter.send({
            command: 'AUTOTYPE_PEERJS',
            data: id
        });

        // 3. HID device auto-types sender URL on host PC
        // 4. Host PC opens sender page automatically
        // 5. Wait for connection...

        showNotification('Auto-setup initiated. Waiting for target...');

        // 6. Connect to target when they come online
        peer.on('call', (call) => {
            // Target is calling us!
            setupPeerJSConnection(call);
        });
    });
}
```

**User experience:**
```
1. Click "Auto-Connect PeerJS" button
2. Controller sends peer ID to Cardputer via Bluetooth
3. Cardputer auto-types sender URL on host PC
4. Target browser opens sender page
5. Connection established automatically!

Total time: ~5 seconds
```

---

## Hosting Sender Page

### Option 1: GitHub Pages (Free)

```bash
# Create repo
git init peerjs-kvm-sender
cd peerjs-kvm-sender

# Add sender.html
cp sender.html index.html
git add index.html
git commit -m "Add PeerJS sender page"

# Push to GitHub
git remote add origin https://github.com/yourusername/peerjs-kvm-sender
git push -u origin main

# Enable GitHub Pages: Settings → Pages → Source: main branch

# Your URL: https://yourusername.github.io/peerjs-kvm-sender
```

**Update sender.html:**
```javascript
const baseUrl = 'https://yourusername.github.io/nanokvm-interface';
const shareUrl = `${baseUrl}#${id}`;
```

---

### Option 2: Host Locally (Offline)

```bash
# Python simple HTTP server
python3 -m http.server 8000

# Access from host PC:
# http://controller-ip:8000/sender.html
```

**Or bookmark `file:///` URL:**
```
1. Save sender.html to Desktop
2. Open in browser: file:///C:/Users/You/Desktop/sender.html
3. Bookmark for easy access
```

---

### Option 3: Self-Hosted PeerJS Server (Advanced)

For complete control and offline operation:

```bash
# Install PeerJS server
npm install -g peer

# Run server
peerjs --port 9000 --key peerjs --path /myapp

# Now running on: http://localhost:9000/myapp
```

**Update sender.html:**
```javascript
const peer = new Peer({
    host: 'your-server.com',
    port: 9000,
    path: '/myapp',
    key: 'peerjs',
    secure: false  // or true if HTTPS
});
```

**Advantages:**
- ✅ No dependency on peerjs.com
- ✅ Works offline (LAN only)
- ✅ Full control over signaling
- ✅ Better privacy

---

## Latency Optimization

### Host PC (Sender)

```javascript
// Request lower latency capture
const stream = await navigator.mediaDevices.getDisplayMedia({
    video: {
        width: { ideal: 1280 },      // Lower resolution
        height: { ideal: 720 },
        frameRate: { ideal: 30 },    // 30fps sufficient for KVM
        cursor: 'always'
    }
});

// Use WebRTC DataChannel for control (lower latency than WebSocket)
const dataChannel = peerConnection.createDataChannel('control', {
    ordered: false,       // Don't wait for retransmits
    maxRetransmits: 0    // Send once, drop if lost
});
```

### Controller PC (Receiver)

```javascript
// Enable low-latency video playback
videoElement.playsInline = true;
videoElement.muted = true;  // No audio anyway
videoElement.volume = 0;

// Reduce buffering
const pc = new RTCPeerConnection({
    iceServers: [
        { urls: 'stun:stun.l.google.com:19302' }  // Fast STUN server
    ],
    bundlePolicy: 'max-bundle',  // Reduce ICE candidates
    rtcpMuxPolicy: 'require'
});

// Monitor latency
pc.getStats().then(stats => {
    stats.forEach(report => {
        if (report.type === 'inbound-rtp' && report.kind === 'video') {
            const latency = report.jitter * 1000;  // Convert to ms
            console.log('Current latency:', latency, 'ms');
        }
    });
});
```

**Expected latency:**
- Same LAN: 60-100ms
- Same city: 100-150ms
- Different city: 150-300ms
- Different continent: 300-500ms

---

## Bandwidth Requirements

| Resolution | FPS | Bitrate | Bandwidth |
|-----------|-----|---------|-----------|
| 1920x1080 | 30 | ~3 Mbps | 3 Mbps |
| 1920x1080 | 60 | ~6 Mbps | 6 Mbps |
| 1280x720 | 30 | ~1.5 Mbps | 1.5 Mbps |
| 1280x720 | 60 | ~3 Mbps | 3 Mbps |

**For typical KVM use:** 1280x720 @ 30fps = 1.5 Mbps

This works fine on:
- ✅ Home WiFi (>50 Mbps)
- ✅ Office LAN (>100 Mbps)
- ✅ 4G/5G mobile (>10 Mbps)
- ⚠️ 3G mobile (~5 Mbps, might stutter)

---

## Troubleshooting

### "Failed to connect to peer"

**Check internet connection:**
```bash
# Ping PeerJS cloud
ping peerjs.com

# Should respond
```

**Check firewall:**
```bash
# Windows: Allow port 443 (HTTPS) for PeerJS signaling
netsh advfirewall firewall add rule name="PeerJS" dir=in action=allow protocol=TCP localport=443

# Also allow UDP for WebRTC data
netsh advfirewall firewall add rule name="WebRTC" dir=in action=allow protocol=UDP
```

**Check browser console for errors:**
```
F12 → Console → Look for PeerJS errors
```

---

### "getDisplayMedia() not available"

**HTTPS required:**
- `getDisplayMedia()` only works on HTTPS (or localhost)
- Use `http://localhost:8000/sender.html` for testing
- Or deploy to GitHub Pages (automatic HTTPS)

**Browser support:**
- Chrome/Edge: ✅
- Firefox: ✅
- Safari: ✅ (macOS 13+)

---

### "Peer ID not found"

**Expired peer ID:**
- PeerJS cloud expires IDs after 60 seconds of inactivity
- Refresh sender page to get new ID
- Or use self-hosted PeerJS server (longer timeout)

**Typo in peer ID:**
- Peer IDs are case-sensitive
- Use copy-paste instead of typing
- Or use QR code for mobile

---

### "High latency / stuttering video"

**On same LAN but high latency:**
```javascript
// Check if using TURN relay instead of direct P2P
pc.getStats().then(stats => {
    stats.forEach(report => {
        if (report.type === 'candidate-pair' && report.state === 'succeeded') {
            console.log('Connection type:', report.localCandidateId);
            // Should be "host" or "srflx", NOT "relay"
        }
    });
});
```

**Force direct connection:**
```javascript
const pc = new RTCPeerConnection({
    iceServers: [
        { urls: 'stun:stun.l.google.com:19302' }
    ],
    iceTransportPolicy: 'all'  // Try all connection types
});
```

**Lower resolution:**
```javascript
// In sender.html
width: { ideal: 1280 },  // Was 1920
height: { ideal: 720 }   // Was 1080
```

---

## Security Considerations

**Peer ID privacy:**
- Peer IDs are randomly generated (e.g., `a1b2c3d4-e5f6-7890`)
- Knowing a peer ID allows connection
- **Don't share peer IDs publicly** (only with trusted controller)

**Screen content:**
- User approves screen sharing (browser prompt)
- User can see what's being shared
- User can stop sharing anytime (Chrome shows indicator)

**Network traffic:**
- Signaling goes through PeerJS cloud (just peer discovery)
- Video streams P2P (encrypted with DTLS-SRTP)
- On same LAN, traffic never leaves your network

**Best practices:**
1. Use self-hosted PeerJS server for sensitive work
2. Rotate peer IDs frequently (new ID per session)
3. Close sender page when done (stops sharing)
4. Use VPN if accessing over internet

---

## Comparison with Other Modes

| Feature | PeerJS | USB Capture | Miracast |
|---------|--------|-------------|----------|
| **Across networks** | ✅ Yes | ❌ No | ❌ No |
| **Works in BIOS** | ❌ No | ✅ Yes | ❌ No |
| **Latency** | ~100ms | ~30ms | ~150ms |
| **Setup time** | 1 min | 2 min | 10 min |
| **Cost** | $0 | $15 | $0-5 |
| **Software only** | ✅ Yes | ❌ No | ✅ Yes |
| **Works offline** | ⚠️ Needs internet for signaling | ✅ Yes | ✅ Yes |

**PeerJS wins for:**
- Remote access across networks
- Software-only solution
- Quickest setup

**PeerJS loses for:**
- BIOS access (use USB capture)
- Lowest latency (use USB capture)
- Fully offline (use USB capture or Miracast)

---

## See Also

- [VIDEO_MODES.md](VIDEO_MODES.md) - Compare all 4 video modes
- [MIRACAST.md](MIRACAST.md) - Alternative wireless video
- [BROWSER_INTEGRATION.md](BROWSER_INTEGRATION.md) - Integrate PeerJS into web interface
- PeerJS docs: https://peerjs.com/docs/
- WebRTC samples: https://webrtc.github.io/samples/
