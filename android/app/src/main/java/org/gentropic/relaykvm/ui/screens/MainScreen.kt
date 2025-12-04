package org.gentropic.relaykvm.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.gentropic.relaykvm.ui.components.*
import org.gentropic.relaykvm.ui.theme.RelayKVM

/**
 * Main screen state
 */
data class MainScreenState(
    val isRunning: Boolean = false,
    val bleConnected: Boolean = false,
    val bleDeviceName: String? = null,
    val hidConnected: Boolean = false,
    val hidDeviceName: String? = null,
    val isActive: Boolean = false,
    val logs: List<String> = emptyList(),
    val pairedDevices: List<String> = emptyList(),
    val selectedDeviceIndex: Int = -1
)

/**
 * Main screen for RelayKVM
 */
@Composable
fun MainScreen(
    state: MainScreenState,
    currentTheme: String,
    onThemeSelected: (String) -> Unit,
    onStartStop: () -> Unit,
    onDeviceSelected: (Int) -> Unit,
    onConnectHost: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RelayKVM.colors.background)
            .padding(16.dp)
    ) {
        // Header with title and theme selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "RelayKVM",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = RelayKVM.colors.textBright
                )
                Text(
                    text = if (state.isRunning) "Running" else "Stopped",
                    fontSize = 14.sp,
                    color = RelayKVM.colors.textDim
                )
            }

            ThemeSelector(
                currentTheme = currentTheme,
                onThemeSelected = onThemeSelected
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status LEDs
        StatusRow(
            bleConnected = state.bleConnected,
            hidConnected = state.hidConnected,
            isActive = state.isActive
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Connection status texts
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatusText(
                label = "BLE",
                value = when {
                    state.bleConnected -> state.bleDeviceName ?: "Connected"
                    state.isRunning -> "Advertising..."
                    else -> "-"
                },
                valueColor = if (state.bleConnected) RelayKVM.colors.accentPrimary else RelayKVM.colors.textDim
            )
            StatusText(
                label = "HID",
                value = when {
                    state.hidConnected -> state.hidDeviceName ?: "Connected"
                    state.isRunning -> "Ready"
                    else -> "-"
                },
                valueColor = if (state.hidConnected) RelayKVM.colors.accentSecondary else RelayKVM.colors.textDim
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Start/Stop button
        ActionButton(
            text = if (state.isRunning) "Stop" else "Start",
            onClick = onStartStop,
            isPrimary = true,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Host selector section
        if (state.isRunning) {
            SectionTitle(text = "Target Host")
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Select a paired Bluetooth device",
                fontSize = 11.sp,
                color = RelayKVM.colors.textDim
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                DeviceSelector(
                    devices = state.pairedDevices,
                    selectedDevice = if (state.selectedDeviceIndex >= 0 && state.selectedDeviceIndex < state.pairedDevices.size) {
                        state.pairedDevices[state.selectedDeviceIndex]
                    } else null,
                    onDeviceSelected = onDeviceSelected,
                    modifier = Modifier.weight(1f)
                )

                ActionButton(
                    text = "Connect",
                    onClick = onConnectHost,
                    isPrimary = false,
                    enabled = state.selectedDeviceIndex >= 0
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Log section
        SectionTitle(text = "Log")
        Spacer(modifier = Modifier.height(8.dp))

        LogDisplay(
            logs = state.logs,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}
