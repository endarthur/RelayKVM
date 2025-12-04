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
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import org.gentropic.relaykvm.ui.screens.MainScreen
import org.gentropic.relaykvm.ui.screens.MainScreenState
import org.gentropic.relaykvm.ui.theme.RelayKVMTheme
import org.gentropic.relaykvm.ui.theme.RelayKVMThemes
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity(), BleServer.Listener, HidController.Listener {

    // State
    private var screenState by mutableStateOf(MainScreenState())
    private var currentTheme by mutableStateOf("Default")

    // Business logic
    private var bleServer: BleServer? = null
    private var hidController: HidController? = null
    private var hidBridge: HidBridge? = null
    private var pairedDevicesList: List<BluetoothDevice> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load saved theme
        val prefs = getSharedPreferences("relaykvm", MODE_PRIVATE)
        currentTheme = prefs.getString("theme", "Default") ?: "Default"

        // Initial logs
        addLog("RelayKVM Android ready")
        addLog("1. Start server to receive from browser")
        addLog("2. Select paired host PC and connect")

        setContent {
            RelayKVMTheme(colors = RelayKVMThemes.byName(currentTheme)) {
                MainScreen(
                    state = screenState,
                    currentTheme = currentTheme,
                    onThemeSelected = { themeName ->
                        currentTheme = themeName
                        // Save theme preference
                        getSharedPreferences("relaykvm", MODE_PRIVATE)
                            .edit()
                            .putString("theme", themeName)
                            .apply()
                    },
                    onStartStop = {
                        if (screenState.isRunning) {
                            stopAll()
                        } else {
                            checkPermissionsAndStart()
                        }
                    },
                    onDeviceSelected = { index ->
                        screenState = screenState.copy(selectedDeviceIndex = index)
                    },
                    onConnectHost = {
                        connectToSelectedHost()
                    }
                )
            }
        }
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
            addLog("Requesting permissions...")
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
            addLog("Permissions denied")
        }
    }

    private fun startAll() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter

        if (adapter == null) {
            addLog("ERROR: No Bluetooth adapter")
            return
        }

        if (!adapter.isEnabled) {
            addLog("Bluetooth disabled, requesting enable...")
            enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }

        // Start HID controller first
        hidController = HidController(this).apply {
            listener = this@MainActivity
        }

        if (!hidController!!.start()) {
            addLog("ERROR: Failed to start HID controller")
            return
        }

        // Create bridge
        hidBridge = HidBridge(hidController!!).apply {
            onPacketProcessed = { msg ->
                runOnUiThread {
                    addLog(msg)
                    // Flash activity indicator
                    screenState = screenState.copy(isActive = true)
                    // Reset after brief delay
                    window.decorView.postDelayed({
                        screenState = screenState.copy(isActive = false)
                    }, 100)
                }
            }
        }

        // Start BLE server
        bleServer = BleServer(this).apply {
            listener = this@MainActivity
        }

        if (!bleServer!!.start()) {
            addLog("ERROR: Failed to start BLE server")
            return
        }

        // Start foreground service to survive screen off
        val serviceIntent = Intent(this, RelayService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)

        screenState = screenState.copy(isRunning = true)
        addLog("Services started (foreground)")

        // Populate paired devices
        populatePairedDevices()
    }

    private val enableBluetooth = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startAll()
        } else {
            addLog("Bluetooth enable denied")
        }
    }

    @SuppressLint("MissingPermission")
    private fun populatePairedDevices() {
        pairedDevicesList = hidController?.getPairedDevices() ?: emptyList()
        val names = pairedDevicesList.map { it.name ?: it.address }

        if (names.isEmpty()) {
            addLog("No paired devices. Pair your target PC first!")
        }

        screenState = screenState.copy(
            pairedDevices = names,
            selectedDeviceIndex = if (names.isNotEmpty()) 0 else -1
        )
    }

    @SuppressLint("MissingPermission")
    private fun connectToSelectedHost() {
        val position = screenState.selectedDeviceIndex
        if (position < 0) {
            addLog("No host selected")
            return
        }

        if (position >= pairedDevicesList.size) return

        val device = pairedDevicesList[position]
        addLog("Connecting to ${device.name}...")
        addLog("(If fails, try connecting FROM PC's Bluetooth settings)")

        if (hidController?.connectToHost(device) == true) {
            addLog("Connection initiated - waiting...")
        } else {
            addLog("Failed. Try: PC Settings > Bluetooth > click phone")
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

        screenState = MainScreenState(
            logs = screenState.logs  // Keep logs
        )
        addLog("Services stopped")
    }

    // BleServer.Listener
    override fun onConnectionStateChanged(connected: Boolean, deviceName: String?) {
        runOnUiThread {
            if (connected) {
                screenState = screenState.copy(
                    bleConnected = true,
                    bleDeviceName = deviceName
                )
                addLog("Browser connected: $deviceName")
            } else {
                screenState = screenState.copy(
                    bleConnected = false,
                    bleDeviceName = null
                )
                addLog("Browser disconnected")
            }
        }
    }

    override fun onDataReceived(data: ByteArray) {
        // Forward to HID bridge
        hidBridge?.processPacket(data)
    }

    // HidController.Listener
    @SuppressLint("MissingPermission")
    override fun onHostConnected(device: BluetoothDevice) {
        runOnUiThread {
            screenState = screenState.copy(
                hidConnected = true,
                hidDeviceName = device.name
            )
            addLog("Host connected: ${device.name}")
            addLog("Ready! Browser -> Phone -> ${device.name}")
        }
    }

    override fun onHostDisconnected() {
        runOnUiThread {
            screenState = screenState.copy(
                hidConnected = false,
                hidDeviceName = null
            )
            addLog("Host disconnected")
        }
    }

    override fun onWaitingForHost() {
        runOnUiThread {
            addLog("HID profile registered, ready for host")
        }
    }

    private fun addLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val line = "[$timestamp] $message"

        // Keep last 100 logs
        val newLogs = (screenState.logs + line).takeLast(100)
        screenState = screenState.copy(logs = newLogs)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAll()
    }
}
