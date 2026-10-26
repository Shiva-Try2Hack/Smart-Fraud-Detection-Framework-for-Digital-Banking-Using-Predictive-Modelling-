package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.ServerConnectionState
import com.example.domain.model.UserAccount
import com.example.ui.components.GPayContactAvatar
import com.example.ui.components.GooglePayLogoHeader
import com.example.ui.components.ServerStatusBadge
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

@Composable
fun AuthScreen(
    serverUrl: String,
    serverState: ServerConnectionState,
    serverStatusDetail: String,
    savedEmail: String,
    liveAccounts: List<UserAccount>,
    isLoading: Boolean,
    authError: String?,
    onLoginSubmit: (email: String, password: String) -> Unit,
    onRegisterSubmit: (fullName: String, email: String, password: String, pin: String) -> Unit,
    onBiometricPromptRequest: (onVerified: () -> Unit) -> Unit,
    onOpenServerConfig: () -> Unit,
    onRetryPing: () -> Unit
) {
    var isRegisterMode by remember { mutableStateOf(false) }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(savedEmail) }
    var password by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showAccountPicker by remember { mutableStateOf(false) }

    LaunchedEffect(savedEmail) {
        if (email.isBlank() && savedEmail.isNotBlank()) {
            email = savedEmail
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GPayWhite)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // Authentic NEXUS PAY Header Emblem
                GooglePayLogoHeader(logoSize = 38.dp)

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = if (isRegisterMode) "Create your NEXUS PAY account" else "Sign in to NEXUS PAY",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = GPayPrimaryText
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isRegisterMode) {
                        "Register a live bank account & 4-digit UPI PIN on the server"
                    } else {
                        "Enter your registered bank email & password to continue"
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = GPaySecondaryText
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(22.dp))

                // Google Account Selector Pill (shows live accounts from /api/accounts if available)
                if (!isRegisterMode && liveAccounts.isNotEmpty()) {
                    val selectedAccount = liveAccounts.find { it.email.equals(email, ignoreCase = true) }
                        ?: liveAccounts.first()

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50))
                            .border(1.dp, GPayBorder, RoundedCornerShape(50))
                            .clickable { showAccountPicker = !showAccountPicker }
                            .testTag("google_account_selector_pill"),
                        color = GPayOffWhite
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GPayContactAvatar(
                                name = selectedAccount.fullName,
                                size = 32.dp,
                                fontSizeSp = 14
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedAccount.fullName,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = GPayPrimaryText
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${selectedAccount.email} • ${selectedAccount.upiId}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = GPaySecondaryText,
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = "Switch live server account",
                                tint = GPaySecondaryText
                            )
                        }
                    }

                    AnimatedVisibility(visible = showAccountPicker) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .border(1.dp, GPayBorder, RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            color = GPayWhite
                        ) {
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Text(
                                    text = "LIVE BANK ACCOUNTS ON SERVER (${liveAccounts.size})",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = GPaySecondaryText,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                                liveAccounts.take(8).forEach { acc ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                email = acc.email
                                                showAccountPicker = false
                                            }
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        GPayContactAvatar(name = acc.fullName, size = 30.dp, fontSizeSp = 13)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = acc.fullName,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.Medium,
                                                    color = GPayPrimaryText
                                                )
                                            )
                                            Text(
                                                text = acc.email.ifBlank { acc.upiId },
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = GPaySecondaryText
                                                )
                                            )
                                        }
                                        Text(
                                            text = acc.upiId,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = MonoFontFamily,
                                                color = GPayBlue
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Form Card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GPayBorder, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    color = GPayWhite
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (isRegisterMode) {
                            OutlinedTextField(
                                value = fullName,
                                onValueChange = { fullName = it },
                                label = { Text("Full Name (as per Bank)") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.PersonOutline,
                                        contentDescription = null,
                                        tint = GPayBlue
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("register_fullname_input"),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GPayBlue,
                                    unfocusedBorderColor = GPayBorder
                                )
                            )
                        }

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email or NEXUS Account") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.MailOutline,
                                    contentDescription = null,
                                    tint = GPayBlue
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_email_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GPayBlue,
                                unfocusedBorderColor = GPayBorder
                            )
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = GPayBlue
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle password visibility",
                                        tint = GPaySecondaryText
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_password_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GPayBlue,
                                unfocusedBorderColor = GPayBorder
                            )
                        )

                        if (isRegisterMode) {
                            OutlinedTextField(
                                value = pin,
                                onValueChange = { input ->
                                    val digits = input.filter { it.isDigit() }.take(4)
                                    pin = digits
                                },
                                label = { Text("Set 4-Digit Bank UPI PIN") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Pin,
                                        contentDescription = null,
                                        tint = GPayBlue
                                    )
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("register_pin_input"),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GPayBlue,
                                    unfocusedBorderColor = GPayBorder
                                )
                            )
                        }

                        if (!authError.isNullOrBlank()) {
                            Surface(
                                color = GPayRedLight,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = authError,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = GPayRed,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (isRegisterMode) {
                                    onRegisterSubmit(fullName, email, password, pin)
                                } else {
                                    onLoginSubmit(email, password)
                                }
                            },
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = GPayBlue),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("auth_submit_button")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.5.dp,
                                    color = GPayWhite
                                )
                            } else {
                                Text(
                                    text = if (isRegisterMode) "Register & Link Bank Account" else "Continue to NEXUS PAY",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = GPayWhite
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Switch between Sign In & Register
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (isRegisterMode) "Already have a bank account?" else "New to NEXUS PAY Bank?",
                        style = MaterialTheme.typography.bodyMedium.copy(color = GPaySecondaryText)
                    )
                    TextButton(
                        onClick = { isRegisterMode = !isRegisterMode },
                        modifier = Modifier.testTag("toggle_auth_mode_button")
                    ) {
                        Text(
                            text = if (isRegisterMode) "Sign in" else "Create account",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = GPayBlue,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }

            // Bottom Direct Connection Test Badge & NPCI Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
                    .padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ServerStatusBadge(
                    serverState = serverState,
                    serverUrl = serverUrl,
                    detailText = serverStatusDetail,
                    onClickConfigure = onRetryPing,
                    onRetryPing = onRetryPing,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalance,
                        contentDescription = null,
                        tint = GPaySecondaryText,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Secured by NPCI UPI & FastAPI Real-Time Fraud Engine",
                        style = MaterialTheme.typography.labelSmall.copy(color = GPaySecondaryText)
                    )
                }
            }
        }
    }
}
