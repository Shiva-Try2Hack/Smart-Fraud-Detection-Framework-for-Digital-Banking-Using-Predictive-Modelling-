package com.example.ui.screens

import com.example.R
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.CardNetwork
import com.example.domain.model.PaymentType
import com.example.domain.model.UserAccount
import com.example.domain.model.VirtualCard
import com.example.domain.util.PaymentValidators
import com.example.nfc.ActiveHceTokenStore
import com.example.nfc.HardwareNfcState
import com.example.nfc.NfcHardwareManager
import com.example.ui.theme.GPayBlue
import com.example.ui.theme.GPayBlueDark
import com.example.ui.theme.GPayBorder
import com.example.ui.theme.GPayGreen
import com.example.ui.theme.GPayGreenAccent
import com.example.ui.theme.GPayGreenLight
import com.example.ui.theme.GPayOffWhite
import com.example.ui.theme.GPayPillFill
import com.example.ui.theme.GPayPrimaryText
import com.example.ui.theme.GPayRed
import com.example.ui.theme.GPaySecondaryText
import com.example.ui.theme.GPayWhite
import com.example.ui.theme.GPayYellowAccent
import com.example.ui.theme.MonoFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TapAndPayScreen(
    currentUser: UserAccount,
    liveAccounts: List<UserAccount>,
    onSaveNewCard: (
        cardNumber: String,
        cardHolder: String,
        expiry: String,
        cvv: String,
        bankName: String,
        network: CardNetwork
    ) -> Unit,
    onInitiateNfcPayment: (merchant: UserAccount) -> Unit,
    onBack: () -> Unit,
    isDarkMode: Boolean = false
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val nfcState = remember { NfcHardwareManager.checkNfcState(context) }

    // Default derived card from user's live server profile if none explicitly set yet
    val activeCard = remember(currentUser) {
        currentUser.virtualCard ?: run {
            val hashDigits = (currentUser.id.hashCode().toLong() and 0x7FFFFFFFL)
                .toString()
                .padStart(12, '8')
                .take(11)
            val base15 = "4532$hashDigits"
            val checkDigit = (0..9).first { d ->
                PaymentValidators.isValidLuhn(base15 + d)
            }
            val fullPan = base15 + checkDigit
            VirtualCard(
                cardNumber = fullPan,
                cardHolder = currentUser.fullName.uppercase(),
                expiry = "08/29",
                cvv = ((currentUser.upiId.hashCode() and 0x7FFFFFFF) % 899 + 100).toString(),
                network = CardNetwork.VISA,
                bankName = "OK Axis Bank"
            )
        }
    }

    LaunchedEffect(activeCard, currentUser.upiId) {
        ActiveHceTokenStore.armToken(
            upiId = currentUser.upiId,
            cardNumber = activeCard.cardNumber,
            network = activeCard.network.displayName
        )
    }

    // 3D Tilt & Flip State
    var isFlippedToBack by remember { mutableStateOf(false) }
    var dragRotationX by remember { mutableFloatStateOf(0f) }
    var dragRotationY by remember { mutableFloatStateOf(0f) }
    var showAddCardSheet by remember { mutableStateOf(false) }

    val animatedFlipY by animateFloatAsState(
        targetValue = if (isFlippedToBack) 180f else 0f,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "card_flip_180"
    )
    val animatedTiltX by animateFloatAsState(
        targetValue = dragRotationX,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "card_tilt_x"
    )
    val animatedTiltY by animateFloatAsState(
        targetValue = dragRotationY,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "card_tilt_y"
    )

    // Pulsing Blue Radar Wave Animation for Contactless NFC
    val radarTransition = rememberInfiniteTransition(label = "nfc_radar")
    val radarWave1 by radarTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_wave_1"
    )
    val radarWave2 by radarTransition.animateFloat(
        initialValue = 0.62f,
        targetValue = 1.37f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_wave_2"
    )

    val density = LocalDensity.current.density

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDarkMode) com.example.ui.theme.GPayDarkBackground else GPayWhite)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .verticalScroll(rememberScrollState())
            .testTag("tap_and_pay_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("tap_and_pay_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = if (isDarkMode) com.example.ui.theme.GPayDarkPrimaryText else GPayPrimaryText
                )
            }
            Text(
                text = "Tap & Pay",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDarkMode) com.example.ui.theme.GPayDarkPrimaryText else GPayPrimaryText
                )
            )
            IconButton(
                onClick = { showAddCardSheet = true },
                modifier = Modifier.testTag("add_virtual_card_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AddCard,
                    contentDescription = "Add or manage card",
                    tint = GPayBlue
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 1. PULSING NFC RADAR + HARDWARE STATUS
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(140.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxR = size.minDimension / 2f

                val w1 = radarWave1.coerceIn(0f, 1f)
                drawCircle(
                    color = GPayBlue.copy(alpha = (1f - w1) * 0.45f),
                    radius = maxR * w1,
                    center = center,
                    style = Stroke(width = 2.5.dp.toPx())
                )

                val w2 = (radarWave2 % 1f).coerceIn(0f, 1f)
                drawCircle(
                    color = GPayBlue.copy(alpha = (1f - w2) * 0.45f),
                    radius = maxR * w2,
                    center = center,
                    style = Stroke(width = 2.5.dp.toPx())
                )

                val w3 = ((radarWave1 + 0.5f) % 1f).coerceIn(0f, 1f)
                drawCircle(
                    color = GPayBlue.copy(alpha = (1f - w3) * 0.35f),
                    radius = maxR * w3,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(GPayPillFill)
                        .border(1.5.dp, GPayBlue.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Contactless,
                        contentDescription = "Contactless Wave",
                        tint = GPayBlue,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when (nfcState) {
                        HardwareNfcState.ENABLED_READY -> "Hold near terminal • Ready to Tap & Pay"
                        HardwareNfcState.DISABLED_IN_SETTINGS -> "NFC is turned off in device settings"
                        HardwareNfcState.HARDWARE_NOT_PRESENT -> "HCE Virtual Card Ready • Contactless Mode"
                    },
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = GPayBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2. INTERACTIVE 3D VIRTUAL CARD (Drag to Tilt in 3D, Tap to Flip 180°)
        val totalRotationY = animatedFlipY + animatedTiltY
        val showingBackFace = (totalRotationY % 360f).let { norm ->
            val pos = if (norm < 0) norm + 360f else norm
            pos in 90f..270f
        }

        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .height(222.dp)
                .graphicsLayer {
                    rotationX = animatedTiltX
                    rotationY = totalRotationY
                    cameraDistance = 14f * density
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            dragRotationX = 0f
                            dragRotationY = 0f
                        },
                        onDragCancel = {
                            dragRotationX = 0f
                            dragRotationY = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragRotationY = (dragRotationY + dragAmount.x * 0.14f).coerceIn(-25f, 25f)
                            dragRotationX = (dragRotationX - dragAmount.y * 0.14f).coerceIn(-20f, 20f)
                        }
                    )
                }
                .shadow(
                    elevation = 14.dp,
                    shape = RoundedCornerShape(22.dp),
                    spotColor = GPayBlueDark.copy(alpha = 0.35f)
                )
                .clip(RoundedCornerShape(22.dp))
                .clickable { isFlippedToBack = !isFlippedToBack }
                .testTag("interactive_3d_virtual_card")
        ) {
            if (!showingBackFace) {
                VirtualCardFrontFace(card = activeCard)
            } else {
                // Mirror horizontally so text on the back face is right-side-up at 180° rotationY
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationY = 180f }
                ) {
                    VirtualCardBackFace(card = activeCard)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Hint Pill: Drag to rotate 3D • Tap to flip for CVV
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(GPayOffWhite)
                .clickable { isFlippedToBack = !isFlippedToBack }
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .testTag("flip_card_button")
        ) {
            Icon(
                imageVector = Icons.Default.FlipCameraAndroid,
                contentDescription = null,
                tint = GPayBlue,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isFlippedToBack) {
                    "Showing Back (CVV & Signature) • Tap to flip to Front"
                } else {
                    "Drag card for 3D tilt • Tap card to flip 180° for CVV"
                },
                style = MaterialTheme.typography.labelSmall.copy(
                    color = GPaySecondaryText,
                    fontWeight = FontWeight.Medium
                )
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 3. HARDWARE NFC STATUS & GOOGLE-STYLED FALLBACK CARD
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (nfcState) {
                HardwareNfcState.ENABLED_READY -> {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = GPayGreenLight,
                        border = androidx.compose.foundation.BorderStroke(1.dp, GPayGreen.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Nfc,
                                contentDescription = null,
                                tint = GPayGreen,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Hardware NFC & HCE Token Active",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = GPayPrimaryText
                                    )
                                )
                                Text(
                                    text = "EMV token armed for ${activeCard.network.displayName} •••• ${activeCard.cardNumber.takeLast(4)} (${currentUser.upiId})",
                                    style = MaterialTheme.typography.bodySmall.copy(color = GPaySecondaryText)
                                )
                            }
                        }
                    }
                }

                HardwareNfcState.DISABLED_IN_SETTINGS -> {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = GPayOffWhite,
                        border = androidx.compose.foundation.BorderStroke(1.dp, GPayBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = GPayBlue,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Turn on NFC for Contactless POS Tap",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = GPayPrimaryText
                                    )
                                )
                                Text(
                                    text = "Your device has NFC hardware, but it is currently disabled in Settings.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = GPaySecondaryText)
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                                    }
                                },
                                shape = RoundedCornerShape(50)
                            ) {
                                Text("Enable")
                            }
                        }
                    }
                }

                HardwareNfcState.HARDWARE_NOT_PRESENT -> {
                    // Clean Google-styled fallback card explaining contactless availability
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = GPayOffWhite,
                        border = androidx.compose.foundation.BorderStroke(1.dp, GPayBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("nfc_fallback_card")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(GPayPillFill),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Contactless,
                                        contentDescription = null,
                                        tint = GPayBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Contactless NFC Hardware Availability",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = GPayPrimaryText
                                        )
                                    )
                                    Text(
                                        text = "Physical NFC antenna not detected on this device/emulator. Your 3D virtual card HCE token is active and can process live NFC transactions via the Bank Server below.",
                                        style = MaterialTheme.typography.bodySmall.copy(color = GPaySecondaryText)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Action Buttons: Link New Card + Execute Live NFC Payment via Server
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showAddCardSheet = true },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("manage_card_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AddCard,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Link New Card")
                }

                var showMerchantPicker by remember { mutableStateOf(false) }
                Button(
                    onClick = {
                        val receiver = UserAccount(
                            id = "pos_terminal@okbank",
                            fullName = "NEXUS POS Terminal #8821",
                            email = "terminal@nexuspay.com",
                            upiId = "nexus_pos_terminal@okaxis",
                            balance = 0.0,
                            virtualCard = null
                        )
                        onInitiateNfcPayment(receiver)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GPayBlue),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("simulate_nfc_pos_tap_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Contactless,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pay Terminal")
                }
            }

            // Card Tokenization Security Info Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isDarkMode) com.example.ui.theme.GPayDarkSurface else GPayWhite,
                border = androidx.compose.foundation.BorderStroke(
                    0.8.dp,
                    if (isDarkMode) com.example.ui.theme.GPayDarkBorder else GPayBorder
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = GPayGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "RBI & NPCI Card Tokenization Details",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDarkMode) com.example.ui.theme.GPayDarkPrimaryText else GPayPrimaryText
                            )
                        )
                    }
                    HorizontalDivider(
                        color = if (isDarkMode) com.example.ui.theme.GPayDarkBorder else GPayBorder,
                        thickness = 0.5.dp
                    )
                    TokenDetailRow("Card Network", activeCard.network.displayName, isDarkMode)
                    TokenDetailRow("Issuing Bank", activeCard.bankName, isDarkMode)
                    TokenDetailRow("HCE Virtual Token", "DPAN •••• ${(activeCard.cardNumber.hashCode() and 0xFFFF)}", isDarkMode)
                    TokenDetailRow("Linked UPI Account", currentUser.upiId, isDarkMode)
                }
            }
        }

        Spacer(modifier = Modifier.height(90.dp))
    }

    // Add / Manage Virtual Card Modal with Luhn Validation & Issuer Detection
    if (showAddCardSheet) {
        AddVirtualCardBottomSheet(
            defaultHolderName = currentUser.fullName.uppercase(),
            onDismiss = { showAddCardSheet = false },
            onSaveCard = { pan, holder, exp, cvv, bank, net ->
                onSaveNewCard(pan, holder, exp, cvv, bank, net)
                showAddCardSheet = false
            }
        )
    }
}

@Composable
private fun VirtualCardFrontFace(card: VirtualCard) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF1A73E8),
                        Color(0xFF174EA6),
                        Color(0xFF0D306B)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(900f, 600f)
                )
            )
            .padding(22.dp)
    ) {
        // Decorative subtle geometric light circles on card surface
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.07f),
                radius = size.height * 0.75f,
                center = Offset(size.width * 0.88f, size.height * 0.2f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.05f),
                radius = size.height * 0.55f,
                center = Offset(size.width * 0.15f, size.height * 0.95f)
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row: Bank Name + Contactless Wave Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.nexus_pay_logo_white),
                        contentDescription = "Nexus Pay",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NEXUS PAY",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "• ${card.bankName}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    )
                }

                Icon(
                    imageVector = Icons.Default.Contactless,
                    contentDescription = "Contactless enabled",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Middle Row: Golden EMV Chip + Embossed 16-Digit Card Number
            Column {
                // Realistic Golden EMV Chip Canvas
                Canvas(
                    modifier = Modifier
                        .size(width = 46.dp, height = 34.dp)
                        .clip(RoundedCornerShape(6.dp))
                ) {
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFFFE082),
                                Color(0xFFFBC02D),
                                Color(0xFFF57F17)
                            )
                        ),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                    val lineColor = Color(0xFF8D6E63).copy(alpha = 0.55f)
                    drawLine(
                        color = lineColor,
                        start = Offset(0f, size.height * 0.33f),
                        end = Offset(size.width, size.height * 0.33f),
                        strokeWidth = 1.2.dp.toPx()
                    )
                    drawLine(
                        color = lineColor,
                        start = Offset(0f, size.height * 0.66f),
                        end = Offset(size.width, size.height * 0.66f),
                        strokeWidth = 1.2.dp.toPx()
                    )
                    drawLine(
                        color = lineColor,
                        start = Offset(size.width * 0.35f, 0f),
                        end = Offset(size.width * 0.35f, size.height),
                        strokeWidth = 1.2.dp.toPx()
                    )
                    drawLine(
                        color = lineColor,
                        start = Offset(size.width * 0.65f, 0f),
                        end = Offset(size.width * 0.65f, size.height),
                        strokeWidth = 1.2.dp.toPx()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = PaymentValidators.formatCardNumber(card.cardNumber),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = MonoFontFamily,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.2.sp,
                        fontSize = 20.sp
                    )
                )
            }

            // Bottom Row: Cardholder Name, Expiry (MM/YY), and Network Logo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "CARDHOLDER NAME",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(alpha = 0.68f),
                            fontSize = 9.sp,
                            letterSpacing = 1.sp
                        )
                    )
                    Text(
                        text = card.cardHolder,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = MonoFontFamily,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "VALID THRU",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(alpha = 0.68f),
                            fontSize = 9.sp
                        )
                    )
                    Text(
                        text = card.expiry,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = MonoFontFamily,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                Spacer(modifier = Modifier.width(18.dp))

                // Network Emblem
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = card.network.displayName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VirtualCardBackFace(card: VirtualCard) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF0D306B),
                        Color(0xFF174EA6)
                    )
                )
            )
            .padding(vertical = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Magnetic Stripe
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(Color(0xFF141619))
        )

        // Authorized Signature Strip & 3-Digit CVV
        Column(modifier = Modifier.padding(horizontal = 22.dp)) {
            Text(
                text = "AUTHORIZED SIGNATURE • NOT VALID UNLESS SIGNED",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 9.sp
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                        .background(Color(0xFFE8EAED))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = card.cardHolder,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = GPaySecondaryText,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
                Box(
                    modifier = Modifier
                        .width(68.dp)
                        .height(38.dp)
                        .clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                        .background(GPayYellowAccent)
                        .testTag("card_back_cvv_box"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "CVV ${card.cvv}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = MonoFontFamily,
                            color = GPayPrimaryText,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                }
            }
        }

        // Bottom Security Note
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "NEXUS PAY HCE Contactless Token • NPCI Certified",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 10.sp
                )
            )
            Text(
                text = card.network.displayName,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddVirtualCardBottomSheet(
    defaultHolderName: String,
    onDismiss: () -> Unit,
    onSaveCard: (
        cardNumber: String,
        cardHolder: String,
        expiry: String,
        cvv: String,
        bankName: String,
        network: CardNetwork
    ) -> Unit
) {
    var cardNumber by remember { mutableStateOf("") }
    var cardHolder by remember { mutableStateOf(defaultHolderName) }
    var expiry by remember { mutableStateOf("08/29") }
    var cvv by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("OK Axis Bank") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val detectedNetwork = remember(cardNumber) {
        PaymentValidators.detectCardNetwork(cardNumber)
    }
    val luhnValid = remember(cardNumber) {
        PaymentValidators.isValidLuhn(cardNumber)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = GPayWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 10.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Link Debit / Credit Card",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = GPayPrimaryText
                    )
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = GPayPillFill
                ) {
                    Text(
                        text = detectedNetwork.displayName,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = GPayBlue,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "Validated in real-time with the Luhn checksum algorithm and tokenized for HCE Tap & Pay.",
                style = MaterialTheme.typography.bodySmall.copy(color = GPaySecondaryText)
            )

            OutlinedTextField(
                value = cardNumber,
                onValueChange = {
                    cardNumber = it.filter { ch -> ch.isDigit() }.take(16)
                    errorMsg = null
                },
                label = { Text("16-Digit Card Number") },
                trailingIcon = {
                    if (cardNumber.length >= 13) {
                        Icon(
                            imageVector = if (luhnValid) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = if (luhnValid) GPayGreen else GPayRed
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("luhn_card_number_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GPayBlue,
                    unfocusedBorderColor = GPayBorder
                )
            )

            // Helper chip to generate a valid Luhn test Visa/Mastercard/RuPay number if needed
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "Visa" to "4532015112830366",
                    "Mastercard" to "5425233430109903",
                    "RuPay" to "6070940123456785"
                ).forEach { (label, sampleNumber) ->
                    // Ensure Luhn check digit on sample
                    val base15 = sampleNumber.take(15)
                    val valid16 = base15 + (0..9).first { d -> PaymentValidators.isValidLuhn(base15 + d) }
                    Surface(
                        onClick = {
                            cardNumber = valid16
                            errorMsg = null
                        },
                        shape = RoundedCornerShape(50),
                        color = GPayOffWhite,
                        border = androidx.compose.foundation.BorderStroke(0.6.dp, GPayBorder)
                    ) {
                        Text(
                            text = "Fill $label",
                            style = MaterialTheme.typography.labelSmall.copy(color = GPayBlue),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = cardHolder,
                onValueChange = { cardHolder = it.uppercase() },
                label = { Text("Cardholder Name") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = expiry,
                    onValueChange = { expiry = it.take(5) },
                    label = { Text("Expiry (MM/YY)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = cvv,
                    onValueChange = { cvv = it.filter { ch -> ch.isDigit() }.take(3) },
                    label = { Text("3-Digit CVV") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            if (!errorMsg.isNullOrBlank()) {
                Text(
                    text = errorMsg!!,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = GPayRed,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            Button(
                onClick = {
                    when {
                        !PaymentValidators.isValidLuhn(cardNumber) -> {
                            errorMsg = "Invalid card number: failed Luhn-algorithm checksum verification."
                        }
                        !PaymentValidators.isValidExpiry(expiry) -> {
                            errorMsg = "Enter a valid expiry date in MM/YY format (e.g., 08/29)."
                        }
                        cvv.length != 3 -> {
                            errorMsg = "Enter a valid 3-digit CVV."
                        }
                        cardHolder.isBlank() -> {
                            errorMsg = "Enter the cardholder's name."
                        }
                        else -> {
                            onSaveCard(
                                cardNumber,
                                cardHolder,
                                expiry,
                                cvv,
                                bankName,
                                detectedNetwork
                            )
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GPayBlue),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_virtual_card_button")
            ) {
                Text("Tokenize & Save to Bank Profile")
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun TokenDetailRow(label: String, value: String, isDarkMode: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                color = if (isDarkMode) com.example.ui.theme.GPayDarkSecondaryText else GPaySecondaryText
            )
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = MonoFontFamily,
                color = if (isDarkMode) com.example.ui.theme.GPayDarkPrimaryText else GPayPrimaryText,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}
