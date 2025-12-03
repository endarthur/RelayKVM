package org.gentropic.relaykvm

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.util.Log
import java.util.concurrent.Executors

/**
 * Bluetooth HID Device Controller
 *
 * Makes the phone appear as a keyboard + mouse to the target PC.
 * Uses the BluetoothHidDevice API (Android 9+).
 */
@SuppressLint("MissingPermission")
class HidController(private val context: Context) {

    companion object {
        private const val TAG = "HidController"

        // HID Report IDs (must match descriptors)
        const val REPORT_ID_KEYBOARD: Byte = 1
        const val REPORT_ID_MOUSE: Byte = 2
        const val REPORT_ID_CONSUMER: Byte = 3

        // Standard HID descriptor for keyboard + mouse + consumer control
        // This tells the host what kind of reports to expect
        private val HID_DESCRIPTOR = byteArrayOf(
            // Keyboard (Report ID 1)
            0x05.toByte(), 0x01.toByte(),  // Usage Page (Generic Desktop)
            0x09.toByte(), 0x06.toByte(),  // Usage (Keyboard)
            0xA1.toByte(), 0x01.toByte(),  // Collection (Application)
            0x85.toByte(), 0x01.toByte(),  //   Report ID (1)
            0x05.toByte(), 0x07.toByte(),  //   Usage Page (Key Codes)
            0x19.toByte(), 0xE0.toByte(),  //   Usage Minimum (224) - Left Ctrl
            0x29.toByte(), 0xE7.toByte(),  //   Usage Maximum (231) - Right GUI
            0x15.toByte(), 0x00.toByte(),  //   Logical Minimum (0)
            0x25.toByte(), 0x01.toByte(),  //   Logical Maximum (1)
            0x75.toByte(), 0x01.toByte(),  //   Report Size (1)
            0x95.toByte(), 0x08.toByte(),  //   Report Count (8)
            0x81.toByte(), 0x02.toByte(),  //   Input (Data, Variable, Absolute) - Modifier byte
            0x95.toByte(), 0x01.toByte(),  //   Report Count (1)
            0x75.toByte(), 0x08.toByte(),  //   Report Size (8)
            0x81.toByte(), 0x01.toByte(),  //   Input (Constant) - Reserved byte
            0x95.toByte(), 0x06.toByte(),  //   Report Count (6)
            0x75.toByte(), 0x08.toByte(),  //   Report Size (8)
            0x15.toByte(), 0x00.toByte(),  //   Logical Minimum (0)
            0x25.toByte(), 0x65.toByte(),  //   Logical Maximum (101)
            0x05.toByte(), 0x07.toByte(),  //   Usage Page (Key Codes)
            0x19.toByte(), 0x00.toByte(),  //   Usage Minimum (0)
            0x29.toByte(), 0x65.toByte(),  //   Usage Maximum (101)
            0x81.toByte(), 0x00.toByte(),  //   Input (Data, Array) - Key array
            0xC0.toByte(),                  // End Collection

            // Mouse (Report ID 2) - matches Pico firmware exactly
            0x05.toByte(), 0x01.toByte(),  // Usage Page (Generic Desktop)
            0x09.toByte(), 0x02.toByte(),  // Usage (Mouse)
            0xA1.toByte(), 0x01.toByte(),  // Collection (Application)
            0x09.toByte(), 0x01.toByte(),  //   Usage (Pointer)
            0xA1.toByte(), 0x00.toByte(),  //   Collection (Physical)
            0x85.toByte(), 0x02.toByte(),  //     Report ID (2)
            0x05.toByte(), 0x09.toByte(),  //     Usage Page (Buttons)
            0x19.toByte(), 0x01.toByte(),  //     Usage Minimum (1)
            0x29.toByte(), 0x03.toByte(),  //     Usage Maximum (3) - 3 buttons like Pico
            0x15.toByte(), 0x00.toByte(),  //     Logical Minimum (0)
            0x25.toByte(), 0x01.toByte(),  //     Logical Maximum (1)
            0x95.toByte(), 0x03.toByte(),  //     Report Count (3)
            0x75.toByte(), 0x01.toByte(),  //     Report Size (1)
            0x81.toByte(), 0x02.toByte(),  //     Input (Data, Variable, Absolute) - Buttons (3 bits)
            0x95.toByte(), 0x01.toByte(),  //     Report Count (1)
            0x75.toByte(), 0x05.toByte(),  //     Report Size (5)
            0x81.toByte(), 0x01.toByte(),  //     Input (Constant) - Padding (5 bits)
            0x05.toByte(), 0x01.toByte(),  //     Usage Page (Generic Desktop)
            0x09.toByte(), 0x30.toByte(),  //     Usage (X)
            0x09.toByte(), 0x31.toByte(),  //     Usage (Y)
            0x09.toByte(), 0x38.toByte(),  //     Usage (Wheel)
            0x15.toByte(), 0x81.toByte(),  //     Logical Minimum (-127)
            0x25.toByte(), 0x7F.toByte(),  //     Logical Maximum (127)
            0x75.toByte(), 0x08.toByte(),  //     Report Size (8)
            0x95.toByte(), 0x03.toByte(),  //     Report Count (3)
            0x81.toByte(), 0x06.toByte(),  //     Input (Data, Variable, Relative) - X, Y, Wheel
            0xC0.toByte(),                  //   End Collection
            0xC0.toByte(),                  // End Collection

            // Consumer Control (Report ID 3) - Media keys
            0x05.toByte(), 0x0C.toByte(),  // Usage Page (Consumer)
            0x09.toByte(), 0x01.toByte(),  // Usage (Consumer Control)
            0xA1.toByte(), 0x01.toByte(),  // Collection (Application)
            0x85.toByte(), 0x03.toByte(),  //   Report ID (3)
            0x15.toByte(), 0x00.toByte(),  //   Logical Minimum (0)
            0x26.toByte(), 0xFF.toByte(), 0x03.toByte(),  // Logical Maximum (1023)
            0x19.toByte(), 0x00.toByte(),  //   Usage Minimum (0)
            0x2A.toByte(), 0xFF.toByte(), 0x03.toByte(),  // Usage Maximum (1023)
            0x75.toByte(), 0x10.toByte(),  //   Report Size (16)
            0x95.toByte(), 0x01.toByte(),  //   Report Count (1)
            0x81.toByte(), 0x00.toByte(),  //   Input (Data, Array)
            0xC0.toByte()                   // End Collection
        )
    }

    interface Listener {
        fun onHostConnected(device: BluetoothDevice)
        fun onHostDisconnected()
        fun onWaitingForHost()
    }

    var listener: Listener? = null

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private var hidDevice: BluetoothHidDevice? = null
    private var hidHost: BluetoothDevice? = null
    private val executor = Executors.newSingleThreadExecutor()

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.i(TAG, "App status changed: registered=$registered, device=${pluggedDevice?.name}")
            if (registered) {
                listener?.onWaitingForHost()
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            Log.i(TAG, "Connection state: ${device.name} -> $state")
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    hidHost = device
                    listener?.onHostConnected(device)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    hidHost = null
                    listener?.onHostDisconnected()
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            Log.d(TAG, "onGetReport: type=$type, id=$id")
            // Respond with empty report
            hidDevice?.replyReport(device, type, id, ByteArray(bufferSize))
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            Log.d(TAG, "onSetReport: type=$type, id=$id, data=${data.contentToString()}")
            hidDevice?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            Log.i(TAG, "HID Device service connected")
            hidDevice = proxy as BluetoothHidDevice

            // Register our HID application
            val sdp = BluetoothHidDeviceAppSdpSettings(
                "RelayKVM",
                "KVM Relay Device",
                "Gentropic",
                BluetoothHidDevice.SUBCLASS1_COMBO,
                HID_DESCRIPTOR
            )

            val qos = BluetoothHidDeviceAppQosSettings(
                BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
                800, 9, 0, 11250, 11250
            )

            hidDevice?.registerApp(sdp, null, qos, executor, hidCallback)
        }

        override fun onServiceDisconnected(profile: Int) {
            Log.i(TAG, "HID Device service disconnected")
            hidDevice = null
        }
    }

    fun start(): Boolean {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth not available")
            return false
        }

        // Get the HID Device profile proxy
        val success = bluetoothAdapter.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
        Log.i(TAG, "Getting HID profile proxy: $success")
        return success
    }

    fun stop() {
        hidDevice?.let { hid ->
            // Disconnect from host first (graceful)
            hidHost?.let { host ->
                Log.i(TAG, "Disconnecting from host...")
                hid.disconnect(host)
            }
            // Small delay to let disconnect complete
            Thread.sleep(100)
            // Then unregister
            hid.unregisterApp()
            bluetoothAdapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid)
        }
        hidDevice = null
        hidHost = null
        Log.i(TAG, "HID controller stopped")
    }

    /** Connect to a specific host device (must be paired) */
    fun connectToHost(device: BluetoothDevice): Boolean {
        return hidDevice?.connect(device) ?: false
    }

    /** Get list of paired devices we can connect to */
    fun getPairedDevices(): List<BluetoothDevice> {
        return bluetoothAdapter.bondedDevices.toList()
    }

    /** Send keyboard report: modifier byte + 6 key codes */
    fun sendKeyboard(modifier: Byte, keys: ByteArray): Boolean {
        val host = hidHost
        val hid = hidDevice

        if (host == null) {
            Log.w(TAG, "sendKeyboard: no host")
            return false
        }
        if (hid == null) {
            Log.w(TAG, "sendKeyboard: no hid device")
            return false
        }

        // Report format: [Modifier, Reserved, Key1-Key6] - NO report ID in data
        val report = ByteArray(8)
        report[0] = modifier
        report[1] = 0  // Reserved
        for (i in 0 until minOf(6, keys.size)) {
            report[2 + i] = keys[i]
        }

        val result = hid.sendReport(host, REPORT_ID_KEYBOARD.toInt(), report)
        if (!result && (modifier != 0.toByte() || keys.any { it != 0.toByte() })) {
            Log.w(TAG, "sendKeyboard FAILED: mod=$modifier")
        }
        return result
    }

    /** Send mouse report: buttons, X delta, Y delta, wheel */
    fun sendMouse(buttons: Byte, x: Byte, y: Byte, wheel: Byte): Boolean {
        val host = hidHost
        val hid = hidDevice

        if (host == null) {
            Log.w(TAG, "sendMouse: no host")
            return false
        }
        if (hid == null) {
            Log.w(TAG, "sendMouse: no hid device")
            return false
        }

        // Report format: [buttons, x, y, wheel] - NO report ID in data
        val report = byteArrayOf(buttons, x, y, wheel)
        val result = hid.sendReport(host, REPORT_ID_MOUSE.toInt(), report)
        if (!result) {
            Log.w(TAG, "sendMouse FAILED: btn=$buttons x=$x y=$y")
        }
        return result
    }

    /** Send consumer control report (media keys) */
    fun sendConsumer(usage: Short): Boolean {
        val host = hidHost ?: return false
        val hid = hidDevice ?: return false

        // Report format: [low, high] - NO report ID in data
        val report = byteArrayOf(
            (usage.toInt() and 0xFF).toByte(),
            ((usage.toInt() shr 8) and 0xFF).toByte()
        )
        return hid.sendReport(host, REPORT_ID_CONSUMER.toInt(), report)
    }

    val isConnected: Boolean
        get() = hidHost != null
}
