package org.gentropic.relaykvm

import android.util.Log

/**
 * Parses RelayKVM BLE protocol packets and forwards to HID controller.
 *
 * Protocol: [57 AB 00] [CMD] [LEN] [DATA...] [CHECKSUM]
 *
 * Commands:
 * - 0x02: Keyboard [modifier, reserved, key1-key6]
 * - 0x03: Media key [lowByte, highByte]
 * - 0x04: Absolute mouse [0x02, buttons, xLo, xHi, yLo, yHi, scroll]
 * - 0x05: Relative mouse [0x01, buttons, dx, dy, scroll]
 * - 0xE0: LED control (ignored on Android)
 */
class HidBridge(private val hidController: HidController) {

    companion object {
        private const val TAG = "HidBridge"

        // Protocol header
        private const val HEAD1: Byte = 0x57
        private const val HEAD2: Byte = 0xAB.toByte()

        // Commands
        private const val CMD_KEYBOARD: Byte = 0x02
        private const val CMD_MEDIA: Byte = 0x03
        private const val CMD_MOUSE_ABS: Byte = 0x04
        private const val CMD_MOUSE_REL: Byte = 0x05
        private const val CMD_LED: Byte = 0xE0.toByte()

        // Throttling - BT HID can't handle as many reports as USB
        private const val MIN_MOUSE_INTERVAL_MS = 8L  // ~125 Hz max for mouse
    }

    var onPacketProcessed: ((String) -> Unit)? = null

    private var lastMouseTime = 0L

    /**
     * Process incoming BLE data packet
     */
    fun processPacket(data: ByteArray) {
        if (data.size < 6) {
            Log.w(TAG, "Packet too short: ${data.size}")
            return
        }

        // Verify header
        if (data[0] != HEAD1 || data[1] != HEAD2) {
            Log.w(TAG, "Invalid header: ${data[0].toHex()} ${data[1].toHex()}")
            return
        }

        val cmd = data[3]
        val len = data[4].toInt() and 0xFF

        // Check we have enough data
        if (data.size < 5 + len + 1) {
            Log.w(TAG, "Packet incomplete: have ${data.size}, need ${5 + len + 1}")
            return
        }

        // Extract payload
        val payload = data.sliceArray(5 until 5 + len)

        when (cmd) {
            CMD_KEYBOARD -> handleKeyboard(payload)
            CMD_MEDIA -> handleMedia(payload)
            CMD_MOUSE_REL -> handleMouseRelative(payload)
            CMD_MOUSE_ABS -> handleMouseAbsolute(payload)
            CMD_LED -> { /* Ignore LED commands on Android */ }
            else -> Log.d(TAG, "Unknown command: ${cmd.toHex()}")
        }
    }

    private fun handleKeyboard(payload: ByteArray) {
        if (payload.size < 8) {
            Log.w(TAG, "Keyboard payload too short")
            return
        }

        val modifier = payload[0]
        // payload[1] is reserved
        val keys = payload.sliceArray(2 until 8)

        val sent = hidController.sendKeyboard(modifier, keys)

        // Log non-empty reports
        if (modifier != 0.toByte() || keys.any { it != 0.toByte() }) {
            val keyStr = keys.filter { it != 0.toByte() }.joinToString(",") { it.toHex() }
            val status = if (sent) "OK" else "FAIL"
            onPacketProcessed?.invoke("KB[$status]: mod=${modifier.toHex()} keys=[$keyStr]")
        }
    }

    private fun handleMedia(payload: ByteArray) {
        if (payload.size < 2) {
            Log.w(TAG, "Media payload too short")
            return
        }

        val usage = ((payload[1].toInt() and 0xFF) shl 8) or (payload[0].toInt() and 0xFF)
        hidController.sendConsumer(usage.toShort())

        // Auto-release media keys after a short delay (press-release cycle)
        if (usage != 0) {
            onPacketProcessed?.invoke("Media: ${usage.toString(16)}")
            // Release the key
            Thread {
                Thread.sleep(50)
                hidController.sendConsumer(0)
            }.start()
        }
    }

    private fun handleMouseRelative(payload: ByteArray) {
        if (payload.size < 5) {
            Log.w(TAG, "Mouse relative payload too short")
            return
        }

        // payload[0] = mode indicator (0x01)
        val buttons = payload[1]
        val dx = payload[2]
        val dy = payload[3]
        val wheel = payload[4]

        // Throttle mouse movement (but always send clicks/wheel immediately)
        val now = System.currentTimeMillis()
        val isMovementOnly = buttons == 0.toByte() && wheel == 0.toByte() && (dx != 0.toByte() || dy != 0.toByte())

        if (isMovementOnly && (now - lastMouseTime) < MIN_MOUSE_INTERVAL_MS) {
            return  // Skip this movement report
        }
        lastMouseTime = now

        hidController.sendMouse(buttons, dx, dy, wheel)

        // Only log clicks or wheel, not every movement
        if (buttons != 0.toByte() || wheel != 0.toByte()) {
            onPacketProcessed?.invoke("Mouse: btn=${buttons.toHex()} wheel=$wheel")
        }
    }

    private fun handleMouseAbsolute(payload: ByteArray) {
        if (payload.size < 7) {
            Log.w(TAG, "Mouse absolute payload too short")
            return
        }

        // payload[0] = mode indicator (0x02)
        val buttons = payload[1]
        // Absolute position - we'd need digitizer HID for this
        // For now, log it but don't send (standard mouse HID doesn't support absolute)

        Log.d(TAG, "Absolute mouse not yet supported on Android")
    }

    private fun Byte.toHex(): String = "%02X".format(this)
}
