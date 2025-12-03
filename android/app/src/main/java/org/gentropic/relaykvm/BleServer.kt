package org.gentropic.relaykvm

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import java.util.*

/**
 * BLE GATT Server implementing Nordic UART Service (NUS).
 *
 * This makes the phone act as a BLE peripheral that the RelayKVM web interface
 * can connect to - same protocol as the Pico 2W firmware.
 *
 * UUIDs must match relaykvm-adapter.js exactly.
 */
@SuppressLint("MissingPermission")  // Permissions handled in Activity
class BleServer(private val context: Context) {

    companion object {
        private const val TAG = "BleServer"

        // Nordic UART Service UUIDs (matching web interface)
        val SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val RX_CHAR_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")  // Browser writes here
        val TX_CHAR_UUID: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")  // We notify here

        // Client Characteristic Configuration Descriptor (standard UUID for notifications)
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    /** Callback interface for BLE events */
    interface Listener {
        fun onConnectionStateChanged(connected: Boolean, deviceName: String?)
        fun onDataReceived(data: ByteArray)
    }

    var listener: Listener? = null

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var connectedDevice: BluetoothDevice? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null

    /** GATT server callbacks - handles connections and data */
    private val gattCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            Log.i(TAG, "Connection state change: status=$status, newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "Device connected: ${device.name ?: device.address}")
                    connectedDevice = device
                    // Stop advertising while connected (single connection mode)
                    advertiser?.stopAdvertising(advertiseCallback)
                    listener?.onConnectionStateChanged(true, device.name ?: device.address)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "Device disconnected, status=$status")
                    connectedDevice = null
                    listener?.onConnectionStateChanged(false, null)
                    // Resume advertising
                    startAdvertising()
                }
            }
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            Log.i(TAG, "Service added: status=$status, uuid=${service.uuid}")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Only start advertising after service is fully registered
                startAdvertising()
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (characteristic.uuid == RX_CHAR_UUID) {
                // This is HID data from the browser!
                Log.d(TAG, "Received ${value.size} bytes")
                listener?.onDataReceived(value)

                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            // Client enabling/disabling notifications on TX characteristic
            if (descriptor.uuid == CCCD_UUID) {
                val enabled = value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                Log.d(TAG, "Notifications ${if (enabled) "enabled" else "disabled"}")

                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            Log.d(TAG, "MTU changed to $mtu")
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            Log.d(TAG, "Read request for ${characteristic.uuid}")
            if (characteristic.uuid == TX_CHAR_UUID) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, byteArrayOf())
            }
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor
        ) {
            Log.d(TAG, "Descriptor read request for ${descriptor.uuid}")
            if (descriptor.uuid == CCCD_UUID) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0,
                    BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
            }
        }
    }

    /** Start the BLE server and begin advertising */
    fun start(): Boolean {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth not available or not enabled")
            return false
        }

        // Create GATT server
        gattServer = bluetoothManager.openGattServer(context, gattCallback)
        if (gattServer == null) {
            Log.e(TAG, "Failed to create GATT server")
            return false
        }

        // Build the Nordic UART Service
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        // RX characteristic - browser writes HID commands here
        val rxChar = BluetoothGattCharacteristic(
            RX_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(rxChar)

        // TX characteristic - we send responses/status via notifications
        txCharacteristic = BluetoothGattCharacteristic(
            TX_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        // CCCD allows client to subscribe to notifications
        val cccd = BluetoothGattDescriptor(
            CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        txCharacteristic!!.addDescriptor(cccd)
        service.addCharacteristic(txCharacteristic!!)

        gattServer!!.addService(service)
        // Advertising will start in onServiceAdded callback

        Log.i(TAG, "BLE server started, waiting for service registration...")
        return true
    }

    private fun startAdvertising() {
        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.e(TAG, "BLE advertising not supported on this device")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)  // Advertise forever
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)  // Shows as phone's Bluetooth name
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        advertiser!!.startAdvertising(settings, data, advertiseCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.i(TAG, "Advertising started")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "Advertising failed with error: $errorCode")
        }
    }

    /** Send data back to browser via notification */
    fun sendData(data: ByteArray): Boolean {
        val device = connectedDevice ?: return false
        val char = txCharacteristic ?: return false

        char.value = data
        return gattServer?.notifyCharacteristicChanged(device, char, false) ?: false
    }

    /** Stop server and clean up */
    fun stop() {
        advertiser?.stopAdvertising(advertiseCallback)
        gattServer?.close()
        gattServer = null
        connectedDevice = null
        Log.i(TAG, "BLE server stopped")
    }

    val isConnected: Boolean
        get() = connectedDevice != null
}
