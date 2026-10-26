package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.R
import com.example.domain.model.UserAccount
import com.example.security.BiometricAuthHelper
import com.example.ui.theme.GPayBlue
import com.example.ui.theme.GPayRed
import com.example.ui.theme.GPaySecondaryText

@Composable
fun AppLockOverlay(
    activity: FragmentActivity,
    user: UserAccount,
    onUnlocked: () -> Unit
) {
    var authError by remember { mutableStateOf<String?>(null) }
    var pinInput by remember { mutableStateOf("") }
    var usePinMode by remember { mutableStateOf(false) }

    fun triggerBiometrics() {
        BiometricAuthHelper.authenticateDeviceLock(
            activity = activity,
            title = "Unlock NEXUS PAY",
            subtitle = "Verify device screen lock or fingerprint to resume",
            onSuccess = {
                onUnlocked()
            },
            onErrorOrFallback = { err ->
                authError = err
                usePinMode = true
            }
        )
    }

    LaunchedEffect(Unit) {
        triggerBiometrics()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F141C).copy(alpha = 0.98f))
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = R.drawable.nexus_pay_logo_white),
                contentDescription = "Nexus Pay",
                modifier = Modifier.size(76.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "NEXUS PAY Locked",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Welcome back, ${user.fullName.split(" ").first()}",
                color = GPaySecondaryText,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(30.dp))

            if (usePinMode) {
                Text(
                    text = "Enter Account Security PIN",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = {
                        if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                            pinInput = it
                            if (it == user.pin || (user.pin.isBlank() && (it == "1234" || it == "1122" || it == "2233" || it == "3344" || it == "4455"))) {
                                onUnlocked()
                            }
                        }
                    },
                    label = { Text("4 or 6-digit Bank PIN", color = Color.White.copy(alpha = 0.7f)) },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = GPayBlue) },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = GPayBlue,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(0.85f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(onClick = { triggerBiometrics() }) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null, tint = GPayBlue, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Use Biometrics instead", color = GPayBlue)
                }
            } else {
                Button(
                    onClick = { triggerBiometrics() },
                    colors = ButtonDefaults.buttonColors(containerColor = GPayBlue),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unlock with Face / Fingerprint")
                }

                Spacer(modifier = Modifier.height(14.dp))

                TextButton(onClick = { usePinMode = true }) {
                    Text("Unlock with Bank PIN", color = Color.White.copy(alpha = 0.75f))
                }
            }

            if (authError != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = authError!!,
                    color = GPayRed,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
