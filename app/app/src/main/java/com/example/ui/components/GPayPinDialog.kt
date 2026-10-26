package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout. width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.GPayBlue
import com.example.ui.theme.GPayBorder
import com.example.ui.theme.GPayOffWhite
import com.example.ui.theme.GPayPillFill
import com.example.ui.theme.GPayPrimaryText
import com.example.ui.theme.GPayRed
import com.example.ui.theme.GPayRedLight
import com.example.ui.theme.GPaySecondaryText
import com.example.ui.theme.GPayWhite
import com.example.ui.theme.MonoFontFamily
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Genuine Google Pay / NPCI 4-Dot UPI PIN Full-Screen Dialog.
 * Includes real-time horizontal shake animation on incorrect PIN entry.
 */
@Composable
fun GPayPinDialog(
    bankName: String,
    upiId: String,
    accountMask: String,
    subtitleLabel: String,
    amountFormatted: String? = null,
    isVerifying: Boolean,
    errorMessage: String?,
    shakeTriggerCount: Int = 0,
    onBiometricRequest: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onSubmitPin: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var showDigits by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    val shakeOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var internalShakeTrigger by remember { mutableIntStateOf(0) }

    suspend fun runShakeAnimation() {
        val keyframes = listOf(-22f, 22f, -18f, 18f, -12f, 12f, -6f, 6f, 0f)
        for (target in keyframes) {
            shakeOffset.animateTo(target, animationSpec = tween(durationMillis = 42))
        }
    }

    LaunchedEffect(errorMessage, shakeTriggerCount, internalShakeTrigger) {
        if (!errorMessage.isNullOrBlank() || shakeTriggerCount > 0 || internalShakeTrigger > 0) {
            if (!errorMessage.isNullOrBlank()) {
                pin = ""
            }
            runShakeAnimation()
        }
    }

    Dialog(
        onDismissRequest = { if (!isVerifying) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("upi_pin_dialog"),
            color = GPayWhite
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top NPCI UPI Bank Header
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = bankName,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = GPayPrimaryText
                                )
                            )
                            Text(
                                text = "XXXX ${accountMask.takeLast(4)}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = MonoFontFamily,
                                    color = GPaySecondaryText
                                )
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // NPCI UPI Styled Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(GPayOffWhite)
                                    .border(1.dp, GPayBorder, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "UPI",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.5.sp,
                                        color = Color(0xFF1B325F)
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { if (!isVerifying) onDismiss() },
                                modifier = Modifier.testTag("pin_dialog_close_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel PIN entry",
                                    tint = GPaySecondaryText
                                )
                            }
                        }
                    }

                    // Transaction / Balance summary bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(GPayOffWhite)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = subtitleLabel,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = GPayPrimaryText
                                )
                            )
                            Text(
                                text = upiId,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = GPaySecondaryText
                                )
                            )
                        }
                        if (!amountFormatted.isNullOrBlank()) {
                            Text(
                                text = amountFormatted,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = GPayPrimaryText
                                )
                            )
                        }
                    }
                    HorizontalDivider(color = GPayBorder, thickness = 0.5.dp)
                }

                // Center 4-Dot PIN Display
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = GPayBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ENTER 4-DIGIT UPI PIN",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.2.sp,
                                color = GPaySecondaryText
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { showDigits = !showDigits },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (showDigits) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showDigits) "Hide PIN" else "Show PIN",
                                tint = GPayBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
                    val softwareKeyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

                    LaunchedEffect(Unit) {
                        try {
                            focusRequester.requestFocus()
                            softwareKeyboardController?.show()
                        } catch (_: Exception) {}
                    }

                    // Native invisible BasicTextField driving device default number keyboard
                    androidx.compose.foundation.text.BasicTextField(
                        value = pin,
                        onValueChange = { input ->
                            localError = null
                            val digits = input.filter { it.isDigit() }.take(4)
                            pin = digits
                            if (digits.length == 4) {
                                onSubmitPin(digits)
                            }
                        },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onDone = {
                                if (pin.length == 4) {
                                    localError = null
                                    onSubmitPin(pin)
                                } else {
                                    localError = "Please enter all 4 digits of your UPI PIN"
                                    internalShakeTrigger++
                                    scope.launch { runShakeAnimation() }
                                }
                            }
                        ),
                        modifier = Modifier
                            .size(1.dp)
                            .offset { IntOffset(-1000, -1000) }
                            .focusRequester(focusRequester)
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // 4-Dot Row with Horizontal Shake Offset - clicking anywhere requests device default numpad
                    Row(
                        modifier = Modifier
                            .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
                            .clickable {
                                try {
                                    focusRequester.requestFocus()
                                    softwareKeyboardController?.show()
                                } catch (_: Exception) {}
                            }
                            .testTag("pin_dots_row"),
                        horizontalArrangement = Arrangement.spacedBy(22.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (index in 0 until 4) {
                            val isFilled = index < pin.length
                            val isCurrent = index == pin.length
                            val hasError = !errorMessage.isNullOrBlank() || !localError.isNullOrBlank()

                            Box(
                                modifier = Modifier
                                    .size(width = 52.dp, height = 58.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isFilled) {
                                    if (showDigits) {
                                        Text(
                                            text = pin[index].toString(),
                                            style = MaterialTheme.typography.headlineLarge.copy(
                                                fontFamily = MonoFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                color = if (hasError) GPayRed else GPayPrimaryText
                                            )
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(if (hasError) GPayRed else GPayPrimaryText)
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(3.dp)
                                            .align(Alignment.BottomCenter)
                                            .background(
                                                when {
                                                    hasError -> GPayRed
                                                    isCurrent -> GPayBlue
                                                    else -> GPayBorder
                                                }
                                            )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    val activeError = errorMessage ?: localError
                    if (!activeError.isNullOrBlank()) {
                        Surface(
                            color = GPayRedLight,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = activeError,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = GPayRed,
                                    fontWeight = FontWeight.Medium
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    } else if (isVerifying) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = GPayBlue
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Verifying PIN with Bank Server...",
                                style = MaterialTheme.typography.bodySmall.copy(color = GPaySecondaryText)
                            )
                        }
                    } else {
                        Text(
                            text = "UPI PIN keeps your account secure from unauthorized access",
                            style = MaterialTheme.typography.bodySmall.copy(color = GPaySecondaryText),
                            textAlign = TextAlign.Center
                        )
                    }

                    if (onBiometricRequest != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            onClick = onBiometricRequest,
                            shape = RoundedCornerShape(50),
                            color = GPayPillFill
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = "Use Biometrics",
                                    tint = GPayBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Verify with Biometrics",
                                    style = MaterialTheme.typography.labelMedium.copy(color = GPayBlue)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }

                // Bottom spacer padded with IME & navigation insets for seamless phone keyboard integration
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.ime)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                )
            }
        }
    }
}

@Composable
private fun PinKeypadCell(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 96.dp, height = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag("pin_key_$label"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.SemiBold,
                color = GPayPrimaryText,
                fontSize = 24.sp
            )
        )
    }
}
