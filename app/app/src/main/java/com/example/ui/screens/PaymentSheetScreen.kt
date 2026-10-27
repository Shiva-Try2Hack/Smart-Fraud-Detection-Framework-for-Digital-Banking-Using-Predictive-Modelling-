package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.PaymentProcessResult
import com.example.domain.model.PaymentType
import com.example.domain.model.TransactionStatus
import com.example.domain.model.UserAccount
import com.example.domain.util.PaymentValidators
import com.example.ui.components.GPayContactAvatar
import com.example.ui.components.GPayPinDialog
import com.example.ui.components.GooglePayLogoHeader
import com.example.ui.theme.GPayBlue
import com.example.ui.theme.GPayBorder
import com.example.ui.theme.GPayGreen
import com.example.ui.theme.GPayGreenAccent
import com.example.ui.theme.GPayGreenLight
import com.example.ui.theme.GPayOffWhite
import com.example.ui.theme.GPayPillFill
import com.example.ui.theme.GPayPrimaryText
import com.example.ui.theme.GPayRed
import com.example.ui.theme.GPayRedLight
import com.example.ui.theme.GPaySecondaryText
import com.example.ui.theme.GPayWhite
import com.example.ui.theme.GPayYellow
import com.example.ui.theme.GPayYellowLight
import com.example.ui.theme.MonoFontFamily
import com.example.ui.viewmodel.PaymentDraft

@Composable
fun PaymentSheetScreen(
    currentUser: UserAccount,
    draft: PaymentDraft,
    liveAccounts: List<UserAccount> = emptyList(),
    isVerifyingPin: Boolean,
    paymentResult: PaymentProcessResult?,
    onBiometricRequest: (onVerified: () -> Unit) -> Unit,
    onExecutePayment: (
        amount: Double,
        pin: String,
        note: String,
        type: PaymentType,
        onError: (String) -> Unit
    ) -> Unit,
    onBackToHome: () -> Unit
) {
    BackHandler { onBackToHome() }

    val resolvedRecipientAccount = remember(draft, liveAccounts) {
        draft.recipientAccount ?: liveAccounts.find {
            it.upiId.equals(draft.recipientUpi, ignoreCase = true) ||
                (draft.recipientAccountId.isNotBlank() && it.id == draft.recipientAccountId) ||
                it.fullName.equals(draft.recipientName, ignoreCase = true)
        }
    }
    val recipientProfileImage = resolvedRecipientAccount?.profileImage ?: draft.recipientAccount?.profileImage

    var amountText by remember(draft) { mutableStateOf(draft.presetAmount) }
    var noteText by remember(draft) { mutableStateOf(draft.presetNote) }
    var selectedType by remember(draft) { mutableStateOf(draft.paymentType) }
    var showPinModal by remember { mutableStateOf(false) }
    var pinErrorText by remember { mutableStateOf<String?>(null) }
    var pinShakeCounter by remember { mutableIntStateOf(0) }
    var validationMessage by remember { mutableStateOf<String?>(null) }

    val numericAmount = amountText.toDoubleOrNull() ?: 0.0

    var isProcessingAnimation by remember { mutableStateOf(false) }
    var processingStep by remember { mutableStateOf("Connecting to Secure Payment Gateway...") }

    if (paymentResult != null) {
        PaymentResultReceiptView(
            currentUser = currentUser,
            draft = draft,
            result = paymentResult,
            paidAmount = numericAmount,
            onDone = onBackToHome
        )
        return
    }

    // Guard against stuck processing animation: auto-timeout after 12s if network drops
    LaunchedEffect(isProcessingAnimation) {
        if (isProcessingAnimation) {
            kotlinx.coroutines.delay(12000)
            if (isProcessingAnimation && paymentResult == null) {
                isProcessingAnimation = false
                showPinModal = true
                pinErrorText = "Network timeout: Payment response delayed. Check Transactions history."
            }
        }
    }

    if (isProcessingAnimation) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(GPayWhite)
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(GPayBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = GPayBlue,
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(54.dp)
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = "Processing Payment",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = GPayPrimaryText
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = processingStep,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = GPaySecondaryText,
                        fontWeight = FontWeight.Medium
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Surface(
                    shape = RoundedCornerShape(50),
                    color = GPayOffWhite,
                    border = androidx.compose.foundation.BorderStroke(1.dp, GPayBorder)
                ) {
                    Text(
                        text = "Transferring ${PaymentValidators.formatInr(numericAmount)}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = GPayBlue
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                androidx.compose.material3.TextButton(
                    onClick = {
                        isProcessingAnimation = false
                        onBackToHome()
                    }
                ) {
                    Text(
                        text = "Dismiss to Home",
                        style = MaterialTheme.typography.labelMedium.copy(color = GPaySecondaryText)
                    )
                }
            }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GPayWhite)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag("payment_sheet_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackToHome,
                    modifier = Modifier.testTag("payment_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = GPayPrimaryText
                    )
                }

                // Payment Rail Selector (UPI / QR / NFC)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(GPayOffWhite)
                        .border(0.8.dp, GPayBorder, RoundedCornerShape(50))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PaymentType.entries.forEach { mode ->
                        val selected = selectedType == mode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) GPayBlue else Color.Transparent)
                                .clickable { selectedType = mode }
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = mode.apiValue,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (selected) GPayWhite else GPaySecondaryText,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                IconButton(onClick = onBackToHome) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = GPaySecondaryText
                    )
                }
            }

            // Center Recipient Profile + Amount Input
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GPayContactAvatar(
                    name = draft.recipientName,
                    size = 68.dp,
                    fontSizeSp = 26,
                    showVerifiedBadge = true,
                    profileImage = recipientProfileImage
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Paying ${draft.recipientName}",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = GPayPrimaryText
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = "Verified Bank Name",
                        tint = GPayGreen,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Banking name: ${draft.recipientName.uppercase()}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = GPaySecondaryText,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = draft.recipientUpi,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = MonoFontFamily,
                        color = GPaySecondaryText
                    )
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Authentic Google Pay Large Centered ₹ Amount Input
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "₹",
                        style = MaterialTheme.typography.displayMedium.copy(
                            color = GPayPrimaryText,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    BasicTextField(
                        value = amountText,
                        onValueChange = { newVal ->
                            val filtered = newVal.filter { it.isDigit() || it == '.' }
                            if (filtered.count { it == '.' } <= 1 && filtered.length <= 9) {
                                amountText = filtered
                                validationMessage = null
                            }
                        },
                        textStyle = MaterialTheme.typography.displayLarge.copy(
                            color = GPayPrimaryText,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        cursorBrush = SolidColor(GPayBlue),
                        modifier = Modifier
                            .width(200.dp)
                            .testTag("payment_amount_input"),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.Center) {
                                if (amountText.isEmpty()) {
                                    Text(
                                        text = "0",
                                        style = MaterialTheme.typography.displayLarge.copy(
                                            color = GPayBorder,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quick Amount Chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(100, 500, 1000, 2000).forEach { quickAmt ->
                        Surface(
                            onClick = {
                                val current = amountText.toDoubleOrNull() ?: 0.0
                                val next = (current + quickAmt).toInt()
                                amountText = next.toString()
                                validationMessage = null
                            },
                            shape = RoundedCornerShape(50),
                            color = GPayPillFill
                        ) {
                            Text(
                                text = "+₹$quickAmt",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = GPayBlue,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // "Add a note" Pill Input
                Surface(
                    shape = RoundedCornerShape(50),
                    color = GPayOffWhite,
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, GPayBorder),
                    modifier = Modifier.width(240.dp)
                ) {
                    BasicTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = GPayPrimaryText,
                            textAlign = TextAlign.Center
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(GPayBlue),
                        modifier = Modifier
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                            .testTag("payment_note_input"),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.Center) {
                                if (noteText.isEmpty()) {
                                    Text(
                                        text = "Add a note",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = GPaySecondaryText
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }

                if (!validationMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = validationMessage!!,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = GPayRed,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            // Bottom Bank Account Selector Card + Pay CTA
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GPayBorder, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                color = GPayOffWhite
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(GPayWhite)
                                .border(0.8.dp, GPayBorder, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalance,
                                contentDescription = null,
                                tint = GPayBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "OK Axis Bank •••• ${currentUser.accountNumber.takeLast(4)}",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = GPayPrimaryText
                                )
                            )
                            Text(
                                text = "Savings Account • ${currentUser.upiId}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = GPaySecondaryText
                                )
                            )
                        }
                        if (numericAmount >= 5000.0) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = GPayPillFill
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fingerprint,
                                        contentDescription = null,
                                        tint = GPayBlue,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "High-Value Guard",
                                        style = MaterialTheme.typography.labelSmall.copy(color = GPayBlue)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (numericAmount <= 0.0) {
                                validationMessage = "Please enter an amount greater than ₹0"
                                return@Button
                            }
                            pinErrorText = null
                            // Trigger biometric prompt first for high-value payments (>= ₹5,000)
                            if (numericAmount >= 5000.0) {
                                onBiometricRequest {
                                    showPinModal = true
                                }
                                // Also open PIN modal if biometric falls back
                                showPinModal = true
                            } else {
                                showPinModal = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GPayBlue),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("proceed_to_pay_button")
                    ) {
                        Text(
                            text = if (numericAmount > 0.0) {
                                "Pay ${PaymentValidators.formatInr(numericAmount)}"
                            } else {
                                "Enter amount to pay"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = GPayWhite,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = GPayWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    if (showPinModal) {
        GPayPinDialog(
            bankName = "OK Axis Bank",
            upiId = draft.recipientUpi,
            accountMask = currentUser.accountNumber,
            subtitleLabel = "Paying ${draft.recipientName}",
            amountFormatted = PaymentValidators.formatInr(numericAmount),
            isVerifying = isVerifyingPin,
            errorMessage = pinErrorText,
            shakeTriggerCount = pinShakeCounter,
            onBiometricRequest = {
                onBiometricRequest {}
            },
            onDismiss = {
                if (!isVerifyingPin) {
                    showPinModal = false
                }
            },
            onSubmitPin = { enteredPin ->
                showPinModal = false
                isProcessingAnimation = true
                processingStep = "Processing payment with Bank..."
                
                onExecutePayment(
                    numericAmount,
                    enteredPin,
                    noteText,
                    selectedType
                ) { errMsg ->
                    isProcessingAnimation = false
                    showPinModal = true
                    pinErrorText = errMsg
                    pinShakeCounter++
                }
            }
        )
    }

    LaunchedEffect(paymentResult) {
        if (paymentResult != null) {
            isProcessingAnimation = false
        }
    }
}

@Composable
private fun PaymentResultReceiptView(
    currentUser: UserAccount,
    draft: PaymentDraft,
    result: PaymentProcessResult,
    paidAmount: Double,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val tx = result.transaction
    val status = result.status

    var animateBadge by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        animateBadge = true
        if (status == TransactionStatus.APPROVED) {
            try {
                val player = android.media.MediaPlayer.create(context, com.example.R.raw.gpay_payment_success)
                player?.setOnCompletionListener { it.release() }
                player?.start()
            } catch (_: Exception) {}
        }
    }

    val badgeScale by animateFloatAsState(
        targetValue = if (animateBadge) 1f else 0.4f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "receipt_badge_scale"
    )

    val statusColor = when (status) {
        TransactionStatus.APPROVED -> GPayGreenAccent
        TransactionStatus.FLAGGED -> GPayYellow
        TransactionStatus.BLOCKED -> GPayRed
    }
    val statusBgColor = when (status) {
        TransactionStatus.APPROVED -> GPayGreenLight
        TransactionStatus.FLAGGED -> GPayYellowLight
        TransactionStatus.BLOCKED -> GPayRedLight
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GPayWhite)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .testTag("payment_receipt_view"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GooglePayLogoHeader(logoSize = 24.dp)
            Spacer(modifier = Modifier.height(28.dp))

            // Animated Checkmark / Fraud Shield Circle
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .scale(badgeScale)
                    .clip(CircleShape)
                    .background(statusColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (status) {
                        TransactionStatus.APPROVED -> Icons.Default.Check
                        TransactionStatus.FLAGGED -> Icons.Default.GppMaybe
                        TransactionStatus.BLOCKED -> Icons.Default.GppBad
                    },
                    contentDescription = status.apiValue,
                    tint = GPayWhite,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = PaymentValidators.formatInr(tx?.amount ?: paidAmount),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = GPayPrimaryText
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = when (status) {
                    TransactionStatus.APPROVED -> "Paid to ${draft.recipientName}"
                    TransactionStatus.FLAGGED -> "Paid to ${draft.recipientName} (Flagged for Review)"
                    TransactionStatus.BLOCKED -> "Payment to ${draft.recipientName} Blocked"
                },
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = GPayPrimaryText
                ),
                textAlign = TextAlign.Center
            )

            Text(
                text = draft.recipientUpi,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = MonoFontFamily,
                    color = GPaySecondaryText
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Live Fraud Risk Assessment Card from FastAPI Server
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = statusBgColor,
                border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (status) {
                            TransactionStatus.APPROVED -> Icons.Default.GppGood
                            TransactionStatus.FLAGGED -> Icons.Default.GppMaybe
                            TransactionStatus.BLOCKED -> Icons.Default.GppBad
                        },
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Server Status: ${status.apiValue} • Risk Score: ${
                                String.format("%.2f", tx?.riskScore ?: 0.0)
                            }",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = GPayPrimaryText
                            )
                        )
                        Text(
                            text = tx?.riskReason ?: result.message,
                            style = MaterialTheme.typography.bodySmall.copy(color = GPayPrimaryText)
                        )
                    }
                }
            }

            // Phase 3 Minimal Enhancement: Action & Scam Warning Card (if present)
            if (!result.recommendedAction.isNullOrBlank() || !result.scamWarning.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GPayPillFill,
                    border = androidx.compose.foundation.BorderStroke(1.dp, GPayBlue.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (!result.recommendedAction.isNullOrBlank()) {
                            Text(
                                text = "Action: ${result.recommendedAction}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = GPayBlue)
                            )
                        }
                        if (!result.scamWarning.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "⚠️ ${result.scamWarning}",
                                style = MaterialTheme.typography.bodySmall.copy(color = GPayPrimaryText)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Detailed UTR & Bank Reference Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = GPayOffWhite,
                border = androidx.compose.foundation.BorderStroke(0.8.dp, GPayBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ReceiptDetailRow("UPI Transaction ID", tx?.id ?: "LIVE-TX")
                    HorizontalDivider(color = GPayBorder, thickness = 0.5.dp)
                    ReceiptDetailRow("Bank Reference (UTR)", tx?.utrNumber ?: "402918392011")
                    HorizontalDivider(color = GPayBorder, thickness = 0.5.dp)
                    ReceiptDetailRow("Payment Rail", tx?.type?.label ?: draft.paymentType.label)
                    HorizontalDivider(color = GPayBorder, thickness = 0.5.dp)
                    ReceiptDetailRow("Debited From", "${currentUser.fullName} (${currentUser.upiId})")
                    if (result.updatedBalance != null) {
                        HorizontalDivider(color = GPayBorder, thickness = 0.5.dp)
                        ReceiptDetailRow(
                            "Updated Bank Balance",
                            PaymentValidators.formatInr(result.updatedBalance)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val shareText = buildString {
                        appendLine("NEXUS PAY Transaction Receipt")
                        appendLine("Status: ${status.apiValue}")
                        appendLine("Amount: ${PaymentValidators.formatInr(tx?.amount ?: paidAmount)}")
                        appendLine("To: ${draft.recipientName} (${draft.recipientUpi})")
                        appendLine("From: ${currentUser.fullName} (${currentUser.upiId})")
                        appendLine("UTR: ${tx?.utrNumber ?: "N/A"}")
                        appendLine("Fraud Risk Score: ${String.format("%.2f", tx?.riskScore ?: 0.0)}")
                    }
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share NEXUS PAY Receipt"))
                },
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("share_receipt_button")
            ) {
                Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share receipt")
            }

            Button(
                onClick = onDone,
                colors = ButtonDefaults.buttonColors(containerColor = GPayBlue),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("receipt_done_button")
            ) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun ReceiptDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(color = GPaySecondaryText)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = MonoFontFamily,
                color = GPayPrimaryText,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}
