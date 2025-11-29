# Code Patterns

Reusable patterns in the RelayKVM codebase. Use these to maintain consistency.

## localStorage

### Boolean (default false)
```javascript
// Read
let enabled = localStorage.getItem('featureEnabled') === 'true';

// Write
localStorage.setItem('featureEnabled', enabled);
```

### Boolean (default true)
```javascript
// Read - note the !== 'false' pattern
let enabled = localStorage.getItem('featureEnabled') !== 'false';

// Write
localStorage.setItem('featureEnabled', enabled);
```

### Number
```javascript
// Read with fallback
let value = parseInt(localStorage.getItem('someValue')) || 5;
let decimal = parseFloat(localStorage.getItem('sensitivity')) || 2.0;

// Write
localStorage.setItem('someValue', value);
```

### Object/Array
```javascript
// Read with fallback
let data = JSON.parse(localStorage.getItem('userData')) || { default: true };

// Write
localStorage.setItem('userData', JSON.stringify(data));
```

### Naming Conventions
| Type | Example Keys |
|------|-------------|
| Settings | `mouseSensitivity`, `scrollSensitivity`, `exitKey` |
| Feature toggles | `capsLockCmdMode`, `wakeLockEnabled`, `seamlessMode` |
| Video | `relayKvmCaptureDevice`, `relayKvmVideoRes`, `relayKvmVideoFps` |
| User data | `macros`, `scripts`, `customTheme1` |
| Theme | `relaykvm-theme` |

---

## Modals

### HTML Structure
```html
<div class="modal-overlay" id="myModal" onclick="closeMyModal(event)">
    <div class="modal" onclick="event.stopPropagation()">
        <div class="modal-header">
            <h3>Title</h3>
            <button class="modal-close" onclick="closeMyModal()">&times;</button>
        </div>
        <div class="modal-body">
            <!-- Content -->
        </div>
    </div>
</div>
```

### JavaScript Pattern
```javascript
function openMyModal() {
    document.getElementById('myModal').classList.add('active');
    syncMyModal(); // Optional: sync state from variables/localStorage
}

function closeMyModal(event) {
    if (event && event.target !== event.currentTarget) return;
    document.getElementById('myModal').classList.remove('active');
}

function syncMyModal() {
    // Sync UI with current state
    document.getElementById('myInput').value = someVariable;
}
```

### Escape to Close
```javascript
document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && document.getElementById('myModal').classList.contains('active')) {
        closeMyModal();
    }
});
```

---

## Help System

### Adding Help Content
Add entry to `helpContent` object in index.html:

```javascript
const helpContent = {
    // ... existing entries ...
    myfeature: {
        title: 'Feature Name',
        body: `
            <h3>Section</h3>
            Description text here.
            <br><br>
            <div class="cmd-row">
                <span class="cmd-name">Item</span>
                <span class="cmd-desc">Description</span>
            </div>

            <h3>Tips</h3>
            • Bullet point<br>
            • Another point
        `
    }
};
```

### Help Button
```html
<button class="fn-key" onclick="showHelp('myfeature')" style="padding: 2px 6px; font-size: 8px;">?</button>
```

---

## UI Components

### Toggle Switch
```html
<label class="toggle-switch">
    <input type="checkbox" id="myToggle" onchange="handleToggle(this.checked)">
    <span class="toggle-slider"></span>
</label>
```

Scale down if needed: `style="transform: scale(0.8);"`

### LED Indicator
```html
<div class="led dot" id="ledFeature"></div>
```

Turn on/off:
```javascript
document.getElementById('ledFeature').classList.add('on');    // Green
document.getElementById('ledFeature').classList.remove('on'); // Off
```

### Module Structure
```html
<div class="module area-name">
    <div class="module-header">
        <span>Module Name</span>
        <div style="display: flex; align-items: center; gap: 6px;">
            <button class="fn-key" onclick="showHelp('topic')" style="padding: 2px 6px; font-size: 8px;">?</button>
            <div class="led dot" id="ledModule"></div>
        </div>
    </div>
    <div class="module-body">
        <!-- Controls -->
    </div>
</div>
```

### Settings Row
```html
<div class="settings-row">
    <span class="settings-label">Label</span>
    <div class="settings-value">
        <!-- Input, slider, toggle, or select -->
    </div>
</div>
```

### Slider with Value Display
```html
<div style="display: flex; justify-content: space-between; margin-bottom: 2px;">
    <label class="led-label" style="font-size: 8px;">Label</label>
    <span class="seg-display" style="padding: 1px 4px; font-size: 9px;" id="valueDisplay">5</span>
</div>
<input type="range" id="mySlider" min="1" max="20" step="1" value="5" class="sensitivity-slider">
```

---

## BLE Adapter

### Sending Commands
```javascript
// Always use buildPacket + sendPacket
const data = new Uint8Array([...]);
const packet = this.buildPacket(RelayKVMAdapter.CMD_SOMETHING, data);
await this.sendPacket(packet);
```

### Adding New Commands

1. Add command constant:
```javascript
static CMD_MY_COMMAND = 0xNN;
```

2. Add method:
```javascript
async myCommand(param) {
    const data = new Uint8Array([param & 0xFF]);
    const packet = this.buildPacket(RelayKVMAdapter.CMD_MY_COMMAND, data);
    await this.sendPacket(packet);
}
```

3. Update firmware to handle the command.

---

## Event Handlers

### Pointer Lock
```javascript
document.addEventListener('pointerlockchange', () => {
    if (document.pointerLockElement === targetElement) {
        // Locked to our element
    } else if (document.pointerLockElement === null) {
        // Released
    }
});
```

Note: Multiple pointerlockchange handlers are OK if they check for different elements (e.g., trackpad vs overlay).

### Visibility Change (for Wake Lock, etc.)
```javascript
document.addEventListener('visibilitychange', async () => {
    if (document.visibilityState === 'visible') {
        // Page became visible - re-acquire resources
    }
});
```

---

## Portal Window Communication

### Accessing Parent (opener)
```javascript
// Check opener exists and has what we need
if (opener && opener.kvm && opener.kvm.connected) {
    opener.kvm.moveMouse(dx, dy, 0, buttons);
}

// Calling parent functions
if (opener && opener.setPortalJackedIn) {
    opener.setPortalJackedIn(true);
}
```

### Syncing Settings
Portal reads from both opener and localStorage as fallback:
```javascript
var setting = opener.settingVar || (localStorage.getItem('setting') === 'true');
```

---

## Known Duplications

These are intentional or unavoidable:

| Item | Locations | Reason |
|------|-----------|--------|
| `mouseSensitivity` read | syncSettingsModal, main init | Modal sync vs runtime variable |
| `capsLockCmdMode` read | index.html, portal.html | Separate windows need own copy |
| `wakeLockEnabled` read | index.html, portal.html | Separate windows need own copy |
| `pointerlockchange` handlers | Line ~4303, ~4928 | Different elements (trackpad vs overlay) |

---

## Anti-Patterns to Avoid

### Don't: Create new popup/popover systems
Use the existing `showHelp(topic)` modal system.

### Don't: Inline complex styles
Use existing CSS classes (`.fn-key`, `.hw-btn`, `.toggle-switch`, etc.).

### Don't: Duplicate localStorage reads unnecessarily
Read once at init, update variable on change, sync to storage.

### Don't: Forget to update service worker cache
Bump `CACHE_NAME` in `sw.js` when changing `index.html` or `portal.html`.

---

## Checklist for New Features

- [ ] Use existing UI patterns (module, settings-row, toggle-switch)
- [ ] Add help content if feature has settings/options
- [ ] Use consistent localStorage key naming
- [ ] Add to both index.html AND portal.html if capture-relevant
- [ ] Bump `sw.js` cache version
- [ ] Update FEATURES.md if user-facing
