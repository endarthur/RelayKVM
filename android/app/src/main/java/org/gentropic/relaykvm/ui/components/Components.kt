package org.gentropic.relaykvm.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.gentropic.relaykvm.ui.theme.RelayKVM
import org.gentropic.relaykvm.ui.theme.RelayKVMThemes

/**
 * LED indicator component - matches web UI style
 */
@Composable
fun LedIndicator(
    label: String,
    isOn: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val animatedColor by animateColorAsState(
        targetValue = if (isOn) color else color.copy(alpha = 0.2f),
        animationSpec = tween(150),
        label = "led"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(animatedColor)
                .border(1.dp, RelayKVM.colors.textDim, CircleShape)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = RelayKVM.colors.textDim
        )
    }
}

/**
 * Status row with LED indicators
 */
@Composable
fun StatusRow(
    bleConnected: Boolean,
    hidConnected: Boolean,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = modifier.fillMaxWidth()
    ) {
        LedIndicator(
            label = "BLE",
            isOn = bleConnected,
            color = RelayKVM.colors.accentPrimary
        )
        LedIndicator(
            label = "HID",
            isOn = hidConnected,
            color = RelayKVM.colors.accentSecondary
        )
        LedIndicator(
            label = "ACT",
            isOn = isActive,
            color = Color(0xFF00FF88)
        )
    }
}

/**
 * Main action button styled like web UI
 */
@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isPrimary: Boolean = true,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isPrimary) {
        RelayKVM.colors.accentPrimary
    } else {
        RelayKVM.colors.surface
    }

    val textColor = if (isPrimary) {
        if (RelayKVM.colors.isDark) Color.Black else Color.White
    } else {
        RelayKVM.colors.textNormal
    }

    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = textColor,
            disabledContainerColor = backgroundColor.copy(alpha = 0.5f),
            disabledContentColor = textColor.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

/**
 * Device selector dropdown
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSelector(
    devices: List<String>,
    selectedDevice: String?,
    onDeviceSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = "Target Host",
            fontSize = 12.sp,
            color = RelayKVM.colors.textDim
        )
        Spacer(modifier = Modifier.height(4.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = selectedDevice ?: "Select device...",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = RelayKVM.colors.surface,
                    unfocusedContainerColor = RelayKVM.colors.surface,
                    focusedBorderColor = RelayKVM.colors.accentPrimary,
                    unfocusedBorderColor = RelayKVM.colors.textDim,
                    focusedTextColor = RelayKVM.colors.textNormal,
                    unfocusedTextColor = RelayKVM.colors.textNormal
                ),
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(RelayKVM.colors.surface)
            ) {
                if (devices.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No paired devices", color = RelayKVM.colors.textDim) },
                        onClick = { expanded = false }
                    )
                } else {
                    devices.forEachIndexed { index, device ->
                        DropdownMenuItem(
                            text = { Text(device, color = RelayKVM.colors.textNormal) },
                            onClick = {
                                onDeviceSelected(index)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Log display with monospace font
 */
@Composable
fun LogDisplay(
    logs: List<String>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(RelayKVM.colors.logBackground)
            .padding(8.dp)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            items(logs) { log ->
                Text(
                    text = log,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = RelayKVM.colors.logText,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

/**
 * Theme selector
 */
@Composable
fun ThemeSelector(
    currentTheme: String,
    onThemeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Text(
            text = "Theme:",
            fontSize = 12.sp,
            color = RelayKVM.colors.textDim
        )
        Spacer(modifier = Modifier.width(8.dp))

        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(RelayKVM.colors.surface)
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                // Theme preview dots
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(RelayKVM.colors.accentPrimary)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(RelayKVM.colors.accentSecondary)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = currentTheme,
                    fontSize = 12.sp,
                    color = RelayKVM.colors.textNormal
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(RelayKVM.colors.surface)
            ) {
                RelayKVMThemes.all.forEach { (name, colors) ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(colors.accentPrimary)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(colors.accentSecondary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(name, color = RelayKVM.colors.textNormal)
                            }
                        },
                        onClick = {
                            onThemeSelected(name)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * Section title
 */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = RelayKVM.colors.textBright,
        modifier = modifier
    )
}

/**
 * Status text with icon
 */
@Composable
fun StatusText(
    label: String,
    value: String,
    valueColor: Color = RelayKVM.colors.textNormal,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        Text(
            text = "$label: ",
            fontSize = 12.sp,
            color = RelayKVM.colors.textDim
        )
        Text(
            text = value,
            fontSize = 12.sp,
            color = valueColor
        )
    }
}
