# Audio Chirp KVM - The Cursed Edition

Galvanically isolated wired KVM using audio frequency-shift keying (AFSK) over 3.5mm audio cable.

**Status:** Cursed idea. Documented for rainy days in São Paulo.

## Why?

The RP2040-PiZero wired version bridges USB grounds between two computers. Usually fine, but not guaranteed safe. Solutions:

| Approach | Cost | Cursed Level |
|----------|------|--------------|
| USB isolator | $20-50 | 0 - Boring |
| Bluetooth (Pico 2W) | $10 | 0 - Already works |
| Fiber USB extender | $50-200 | 1 - Overkill |
| Audio chirp | ~$5 + dignity | 10 - Peak cursed |

**Why audio provides isolation:**
- Audio transformers in sound cards provide galvanic isolation
- Even without transformers, capacitor-coupled outputs block DC
- No ground reference shared between devices
- It's literally how modems worked for decades

## Architecture

```
┌─────────────┐                              ┌─────────────────┐
│ Controller  │   USB Audio    3.5mm cable   │  RP2040-PiZero  │
│     PC      │──[Adapter]────────────────►──│    + RC input   │
└─────────────┘   (audio out)   isolated!    └────────┬────────┘
                                                      │
      Web interface generates FSK tones               │ FSK demodulation
      for keyboard/mouse commands                     │
                                                      ▼
                                             ┌─────────────────┐
                                             │    Target PC    │
                                             │    (USB HID)    │
                                             └─────────────────┘
```

## Hardware

### Controller Side

**Need:** Audio output (3.5mm line out or USB audio adapter)

Most computers have audio out. If not (like Framework 13 without audio module):
- USB audio adapter: ~$5-15
- Any cheap USB sound card works
- We only need mono output

### Bridge Device

**Base:** RP2040-PiZero (or any RP2040 board with USB)

**Audio input circuit:**
```
3.5mm TIP ────┤├──────┬────── ADC Pin (GPIO26/27/28)
            10µF      │
           (AC       ┌┴┐
          couple)    │ │ 10kΩ (bias to 1.65V)
                     └┬┘
                      │
3.5mm GND ───────────┴─────── GND
                      │
                     ┌┴┐
                     │ │ 10kΩ
                     └┬┘
                      │
                     3.3V
```

**BOM:**
| Part | Value | Purpose | Cost |
|------|-------|---------|------|
| C1 | 10µF electrolytic | AC coupling (block DC) | $0.02 |
| R1 | 10kΩ | Voltage divider (top) | $0.01 |
| R2 | 10kΩ | Voltage divider (bottom) | $0.01 |
| J1 | 3.5mm jack | Audio input | $0.50 |

**Total:** ~$0.55 + whatever's in your parts drawer

**Optional additions:**
- 100nF ceramic cap parallel to ADC pin (low-pass filter)
- 1kΩ resistor in series (input protection)
- TVS diode (overvoltage protection)

## Modulation Protocol

### AFSK (Audio Frequency-Shift Keying)

Classic modem technique. Two frequencies represent 0 and 1:

| Symbol | Frequency | Note |
|--------|-----------|------|
| Mark (1) | 1200 Hz | ~D5 |
| Space (0) | 2200 Hz | ~C#7 |

This is the Bell 202 standard - same as old 1200 baud modems, packet radio, NOAA weather satellites.

**Why these frequencies:**
- Well above 300Hz (telephone/audio low-cut)
- Well below 3400Hz (telephone/audio high-cut)
- Work through any audio path that handles voice
- 1000 Hz separation = easy to distinguish

### Baud Rate

Start conservative: **300 baud**

At 300 baud:
- 300 bits/second = 37.5 bytes/second
- A keystroke packet (~5 bytes) = 133ms
- Perceptible latency but usable for typing
- Very robust against audio quality issues

Can increase to 1200 baud once working:
- 1200 bits/second = 150 bytes/second
- Keystroke = 33ms
- Feels responsive

### Packet Format

Reuse existing RelayKVM HID protocol, wrapped in AFSK:

```
[Preamble] [Sync] [Length] [Command] [Data...] [Checksum]
   0xAA    0x7E    1 byte   1 byte    N bytes   XOR
```

**Preamble:** 8x 0xAA (10101010) for clock recovery
**Sync:** 0x7E (01111110) marks start of frame
**Checksum:** XOR of all bytes (simple but catches most errors)

## Software Implementation

### Controller Side (Web Audio API)

Generate FSK tones in the browser:

```javascript
class AFSKModulator {
    constructor(audioContext) {
        this.ctx = audioContext;
        this.freqMark = 1200;  // 1 bit
        this.freqSpace = 2200; // 0 bit
        this.baudRate = 300;
        this.bitDuration = 1 / this.baudRate;
    }

    async sendByte(byte) {
        const oscillator = this.ctx.createOscillator();
        const gain = this.ctx.createGain();

        oscillator.connect(gain);
        gain.connect(this.ctx.destination);
        gain.gain.value = 0.5;

        let time = this.ctx.currentTime;

        // Start bit (space)
        oscillator.frequency.setValueAtTime(this.freqSpace, time);
        time += this.bitDuration;

        // Data bits (LSB first)
        for (let i = 0; i < 8; i++) {
            const bit = (byte >> i) & 1;
            const freq = bit ? this.freqMark : this.freqSpace;
            oscillator.frequency.setValueAtTime(freq, time);
            time += this.bitDuration;
        }

        // Stop bit (mark)
        oscillator.frequency.setValueAtTime(this.freqMark, time);
        time += this.bitDuration;

        oscillator.start();
        oscillator.stop(time);

        return new Promise(resolve => setTimeout(resolve, (time - this.ctx.currentTime) * 1000));
    }

    async sendPacket(data) {
        // Preamble
        for (let i = 0; i < 8; i++) await this.sendByte(0xAA);

        // Sync
        await this.sendByte(0x7E);

        // Length
        await this.sendByte(data.length);

        // Data
        let checksum = data.length;
        for (const byte of data) {
            await this.sendByte(byte);
            checksum ^= byte;
        }

        // Checksum
        await this.sendByte(checksum);
    }
}
```

### Bridge Side (RP2040 Firmware)

Demodulate FSK using Goertzel algorithm (efficient single-frequency detection):

```c
// Pseudocode - actual implementation needs tuning

#define SAMPLE_RATE 8000
#define BAUD_RATE 300
#define SAMPLES_PER_BIT (SAMPLE_RATE / BAUD_RATE)

#define FREQ_MARK 1200
#define FREQ_SPACE 2200

float goertzel(int16_t* samples, int n, int target_freq) {
    float omega = 2.0 * M_PI * target_freq / SAMPLE_RATE;
    float coeff = 2.0 * cos(omega);
    float s0 = 0, s1 = 0, s2 = 0;

    for (int i = 0; i < n; i++) {
        s0 = samples[i] + coeff * s1 - s2;
        s2 = s1;
        s1 = s0;
    }

    return s1*s1 + s2*s2 - coeff*s1*s2;
}

int demodulate_bit(int16_t* samples) {
    float mark_power = goertzel(samples, SAMPLES_PER_BIT, FREQ_MARK);
    float space_power = goertzel(samples, SAMPLES_PER_BIT, FREQ_SPACE);

    return mark_power > space_power ? 1 : 0;
}
```

## Implementation Checklist

- [ ] Hardware: Build audio input circuit on breadboard
- [ ] Hardware: Test with oscilloscope/logic analyzer
- [ ] Firmware: ADC sampling at 8kHz
- [ ] Firmware: Goertzel demodulator
- [ ] Firmware: Packet parser with checksums
- [ ] Firmware: Integration with USB HID output
- [ ] Web: AFSK modulator using Web Audio API
- [ ] Web: Audio output device selection
- [ ] Web: Fallback adapter alongside BLE/WebSerial
- [ ] Test: Latency measurement
- [ ] Test: Error rate at different baud rates
- [ ] Test: Various audio cables and adapters

## Known Challenges

**Clock drift:** Transmitter and receiver aren't synchronized. The preamble helps, but long packets may drift. Keep packets short.

**Audio quality:** Some USB audio adapters have terrible frequency response or add noise. May need to tune frequencies or add error correction.

**Latency:** 300 baud is slow. For typing it's okay, but mouse movement will feel laggy. Could implement mouse packets at lower precision.

**Browser audio:** Web Audio API requires user interaction to start. First click/keypress needs to initialize audio context.

**Bidirectional:** This design is unidirectional (controller→target). For bidirectional, the PiZero would need audio output too, and controller would need audio input. Twice the cursed.

## Why This Is Beautiful

1. **Perfect galvanic isolation** - Audio transformers exist for a reason
2. **Works through anything** - Phone call, radio, walkie-talkie, yelling across the room
3. **Retro computing vibes** - It's literally a modem
4. **Educational** - DSP, signal processing, protocol design
5. **Debugging is audible** - You can HEAR if it's working
6. **Immune to USB issues** - No USB enumeration, no drivers, just sound

## Acoustic Coupling (Cableless Mode)

Why use a cable when sound travels through air for free?

### The Vision

```
Framework 13 speakers          Cardputer mic
        🔊  ~~~~~~~~~~~~>  🎤
     BWEEE BWOOP           "I heard that"
```

No wires. No jack. Just vibes (literally).

### How It Works

Same AFSK protocol as cabled version, but:
- Controller uses laptop/phone speakers
- Cardputer's PDM microphone receives
- Air provides galvanic isolation (and entertainment)

### Hardware Requirements

**Controller:** Any device with speakers (laptop, phone, tablet)

**Bridge (Cardputer v1.1):**
- Built-in PDM microphone on M5StampS3
- I2S interface for audio capture
- No additional hardware needed!

### Practical Considerations

**Environment:**
- Works best in quiet room
- Background noise = packet errors
- Dog bark = random keystrokes
- Coworker sneeze = Ctrl+Alt+Del

**Range:**
- Desk distance (~30-50cm): Reliable
- Across room (~3m): Possible with volume up
- Through walls: Don't even try

### XLR Balanced Audio (Professional Cursed)

For adventurous users who want their cursed setup to be *broadcast quality*:

```
Controller ──[USB Audio Interface]──► XLR ──► [Audio Interface]──► Bridge
                                    balanced
                                    100m+ OK
```

**Why XLR:**
- Balanced differential signaling rejects interference
- Cable runs of 100m+ without degradation
- Locking connectors (no accidental disconnects)
- Phantom power available if needed (48V, very cursed)
- Looks impressively overengineered

**Hardware options:**
- Behringer UMC22 (~$50) - cheap USB audio with XLR in/out
- Focusrite Scarlett Solo (~$100) - actually good preamps
- Any mixer with USB output

**The ultimate flex:**
Run your KVM signal through a mixing board, add reverb and compression, EQ the high frequencies for "presence." Your keystrokes have never sounded so broadcast-ready.

This is what peak performance looks like. You may not like it, but this is what professional cursed engineering looks like.

**Social:**
- Everyone hears your keystrokes as modem sounds
- Great conversation starter
- Terrible for stealth
- Perfect for asserting dominance

### Volume Calibration

The Cardputer needs to auto-calibrate for ambient noise:

```c
// Sample ambient noise floor for 1 second
// Set detection threshold at noise_floor + margin
// Adjust gain dynamically

float calibrate_noise_floor() {
    float sum = 0;
    for (int i = 0; i < SAMPLE_RATE; i++) {
        int16_t sample = read_mic();
        sum += abs(sample);
    }
    return (sum / SAMPLE_RATE) * 1.5; // 50% margin
}
```

### Error Handling

Acoustic transmission is noisy. Implement:
- Forward error correction (FEC)
- Automatic repeat request (ARQ)
- Checksums on every packet
- Visual/audio feedback for failed packets

Or just accept that sometimes 'a' becomes 'q'. It builds character.

---

## Analog Mouse Protocol

For absolute mouse positioning, why digitize at all?

### The Concept

Instead of encoding mouse coordinates as digital packets, transmit them as continuous analog signals:

```
X position → Frequency (500Hz to 2500Hz)
Y position → Amplitude (or second stereo channel)
```

Mouse movement becomes a theremin performance.

### Frequency Mapping

```
Screen left edge   (X=0)      → 500 Hz
Screen center      (X=960)    → 1500 Hz
Screen right edge  (X=1920)   → 2500 Hz
```

Resolution: 1920 positions across 2000Hz = ~1Hz per pixel. Easily distinguishable.

### Stereo Mode

If using stereo audio output:
```
Left channel:  X position as frequency
Right channel: Y position as frequency
```

Both coordinates transmitted simultaneously. True real-time mouse.

### Advantages Over Digital

| Aspect | Digital (AFSK) | Analog |
|--------|----------------|--------|
| Latency | ~100ms (packet) | ~10ms (buffer only) |
| Mouse feel | Choppy | Smooth |
| Bandwidth | Limited by baud | Continuous |
| Complexity | Packet parsing | Simple frequency detection |

### Implementation Sketch

**Controller (Web Audio):**
```javascript
function updateMouseTone(x, y, audioCtx, oscillatorX, oscillatorY) {
    // Map screen coordinates to frequencies
    const freqX = 500 + (x / screen.width) * 2000;  // 500-2500 Hz
    const freqY = 500 + (y / screen.height) * 2000;

    oscillatorX.frequency.setValueAtTime(freqX, audioCtx.currentTime);
    oscillatorY.frequency.setValueAtTime(freqY, audioCtx.currentTime);
}

// On mouse move
document.addEventListener('mousemove', (e) => {
    updateMouseTone(e.clientX, e.clientY, ctx, oscX, oscY);
});
```

**Bridge (Cardputer/Pico):**
```c
void analog_mouse_loop() {
    float freq_x = detect_frequency(LEFT_CHANNEL);
    float freq_y = detect_frequency(RIGHT_CHANNEL);

    // Map frequencies back to coordinates
    uint16_t x = ((freq_x - 500) / 2000) * SCREEN_WIDTH;
    uint16_t y = ((freq_y - 500) / 2000) * SCREEN_HEIGHT;

    send_absolute_mouse(x, y);
}
```

### Hybrid Protocol

Best of both worlds:
- **Analog** for mouse position (smooth, low-latency)
- **Digital AFSK** for keyboard and clicks (reliable, discrete events)

Keyboard events interrupt the analog tone briefly with AFSK packets, then resume.

### Requirements

- Absolute mouse mode (digitizer HID) - Pico 2W has this!
- Cardputer would need absolute mode support first
- Stereo output on controller
- Stereo input or fast channel switching on bridge

### Why This Is Peak Cursed

Your mouse movements become music. Every pixel a frequency. The cursor dances to your hand's melody. Is it practical? Barely. Is it beautiful? Absolutely.

---

## Even More Cursed Ideas

- **Optical/IR version:** LED + photodiode, even simpler circuit
- **Radio version:** 433MHz ISM band, already cursed in the roadmap
- **Ultrasonic:** Above human hearing (~20kHz+), silent but dogs hate you
- **Human relay:** One person reads hex codes, another types them. Ultimate isolation.

## References

- [Bell 202 modem standard](https://en.wikipedia.org/wiki/Bell_202_modem)
- [AFSK on Wikipedia](https://en.wikipedia.org/wiki/Frequency-shift_keying#Audio_frequency-shift_keying)
- [Goertzel algorithm](https://en.wikipedia.org/wiki/Goertzel_algorithm)
- [Web Audio API](https://developer.mozilla.org/en-US/docs/Web/API/Web_Audio_API)
- [RP2040 ADC](https://www.raspberrypi.com/documentation/microcontrollers/rp2040.html)
