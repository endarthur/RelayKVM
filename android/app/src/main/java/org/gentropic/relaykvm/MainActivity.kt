package org.gentropic.relaykvm

import android.annotation.SuppressLint
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity(), BleServer.Listener, HidController.Listener {

    private lateinit var statusText: TextView
    private lateinit var bleStatusText: TextView
    private lateinit var hidStatusText: TextView
    private lateinit var logText: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var startButton: Button
    private lateinit var hostSpinner: Spinner
    private lateinit var connectHostButton: Button

    private var bleServer: BleServer? = null
    private var hidController: HidController? = null
    private var hidBridge: HidBridge? = null
    private var isRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        bleStatusText = findViewById(R.id.bleStatusText)
        hidStatusText = findViewById(R.id.hidStatusText)
        logText = findViewById(R.id.logText)
        logScroll = findViewById(R.id.logScroll)
        startButton = findViewById(R.id.startButton)
        hostSpinner = findViewById(R.id.hostSpinner)
        connectHostButton = findViewById(R.id.connectHostButton)

        startButton.setOnClickListener {
            if (isRunning) {
                stopAll()
            } else {
                checkPermissionsAndStart()
            }
        }

        connectHostButton.setOnClickListener {
            connectToSelectedHost()
        }

        log("RelayKVM Android ready")
        log("1. Start server to receive from browser")
        log("2. Select paired host PC and connect")
    }

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ - need specific BT permissions
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            // Android 9-11 - need location for BLE
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        // Android 13+ - need notification permission for foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isEmpty()) {
            startAll()
        } else {
            log("Requesting permissions...")
            requestPermissions.launch(needed.toTypedArray())
        }
    }

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.all { it.value }) {
            startAll()
        } else {
            Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_SHORT).show()
            log("Permissions denied")
        }
    }

    private fun startAll() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter

        if (adapter == null) {
            log("ERROR: No Bluetooth adapter")
            return
        }

        if (!adapter.isEnabled) {
            log("Bluetooth disabled, requesting enable...")
            enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }

        // Start HID controller first
        hidController = HidController(this).apply {
            listener = this@MainActivity
        }

        if (!hidController!!.start()) {
            log("ERROR: Failed to start HID controller")
            return
        }

        // Create bridge
        hidBridge = HidBridge(hidController!!).apply {
            onPacketProcessed = { msg ->
                runOnUiThread { log(msg) }
            }
        }

        // Start BLE server
        bleServer = BleServer(this).apply {
            listener = this@MainActivity
        }

        if (!bleServer!!.start()) {
            log("ERROR: Failed to start BLE server")
            return
        }

        // Start foreground service to survive screen off
        val serviceIntent = Intent(this, RelayService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)

        isRunning = true
        statusText.text = "Running"
        startButton.text = "Stop"
        log("Services started (foreground)")

        // Populate paired devices
        populatePairedDevices()
    }

    private val enableBluetooth = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startAll()
        } else {
            log("Bluetooth enable denied")
        }
    }

    @SuppressLint("MissingPermission")
    private fun populatePairedDevices() {
        val devices = hidController?.getPairedDevices() ?: emptyList()
        val names = devices.map { it.name ?: it.address }

        if (names.isEmpty()) {
            log("No paired devices. Pair your target PC first!")
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        hostSpinner.adapter = adapter
    }

    @Suppress("MissingPermission")
    private fun connectToSelectedHost() {
        val position = hostSpinner.selectedItemPosition
        if (position < 0) {
            log("No host selected")
            return
        }

        val devices = hidController?.getPairedDevices() ?: return
        if (position >= devices.size) return

        val device = devices[position]
        log("Connecting to ${device.name}...")
        log("(If fails, try connecting FROM PC's Bluetooth settings)")

        if (hidController?.connectToHost(device) == true) {
            log("Connection initiated - waiting...")
        } else {
            log("Failed. Try: PC Settings > Bluetooth > click phone")
        }
    }

    private fun stopAll() {
        // Stop foreground service
        stopService(Intent(this, RelayService::class.java))

        bleServer?.stop()
        bleServer = null
        hidController?.stop()
        hidController = null
        hidBridge = null

        isRunning = false
        statusText.text = "Stopped"
        bleStatusText.text = "BLE: -"
        hidStatusText.text = "HID: -"
        startButton.text = "Start"
        log("Services stopped")
    }

    // BleServer.Listener
    override fun onConnectionStateChanged(connected: Boolean, deviceName: String?) {
        runOnUiThread {
            if (connected) {
                bleStatusText.text = "BLE: $deviceName"
                log("Browser connected: $deviceName")
            } else {
                bleStatusText.text = "BLE: Advertising..."
                log("Browser disconnected")
            }
        }
    }

    override fun onDataReceived(data: ByteArray) {
        // Forward to HID bridge
        hidBridge?.processPacket(data)
    }

    // HidController.Listener
    override fun onHostConnected(device: BluetoothDevice) {
        runOnUiThread {
            hidStatusText.text = "HID: ${device.name}"
            log("Host connected: ${device.name}")
            log("Ready! Browser -> Phone -> ${device.name}")
        }
    }

    override fun onHostDisconnected() {
        runOnUiThread {
            hidStatusText.text = "HID: Disconnected"
            log("Host disconnected")
        }
    }

    override fun onWaitingForHost() {
        runOnUiThread {
            hidStatusText.text = "HID: Ready"
            log("HID profile registered, ready for host")
        }
    }

    private fun log(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
        val line = "[$timestamp] $message"

        val current = logText.text.toString()
        val lines = current.lines().takeLast(100)
        logText.text = (lines + line).joinToString("\n")

        logScroll.post { logScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAll()
    }
}
