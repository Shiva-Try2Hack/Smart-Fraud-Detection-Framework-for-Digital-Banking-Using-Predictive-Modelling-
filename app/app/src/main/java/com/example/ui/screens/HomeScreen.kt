package com.example.ui.screens

import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.PaymentType
import com.example.domain.model.ServerConnectionState
import com.example.domain.model.UserAccount
import com.example.domain.util.PaymentValidators
import com.example.ui.components.GPayContactAvatar
import com.example.ui.components.GPayHorizonBanner
import com.example.ui.components.GooglePayLogoHeader
import com.example.ui.components.copyToClipboard
import com.example.ui.theme.GPayBlue
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
import com.example.ui.theme.MonoFontFamily

data class BusinessItem(
    val name: String,
    val upiHandle: String,
    val category: String,
    val color: Color,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    currentUser: UserAccount,
    liveAccounts: List<UserAccount>,
    revealedBalance: Double?,
    upiLiteActive: Boolean,
    serverState: ServerConnectionState,
    serverUrl: String,
    onOpenQrScanner: () -> Unit,
    onOpenPayContacts: () -> Unit,
    onOpenBankTransfer: () -> Unit,
    onOpenPayBills: () -> Unit,
    onOpenTapAndPay: () -> Unit,
    onToggleUpiLite: () -> Unit,
    onOpenMyQr: () -> Unit,
    onCheckBalanceClick: () -> Unit,
    onHideBalanceClick: () -> Unit,
    onOpenTransactionHistory: () -> Unit,
    onSelectPersonToPay: (UserAccount) -> Unit,
    onSelectBusinessToPay: (name: String, upiHandle: String, note: String) -> Unit,
    onRefreshData: () -> Unit,
    onOpenServerSettings: () -> Unit,
    onSignOut: () -> Unit,
    onShowMessage: (String) -> Unit,
    onUpdateProfile: ((fullName: String, profession: String, address: String, avatarId: Int) -> Unit)? = null,
    isDarkMode: Boolean = false,
    onUploadProfilePhoto: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    var showProfileSheet by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                if (bytes != null && bytes.isNotEmpty()) {
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    onUploadProfilePhoto?.invoke(base64)
                    onShowMessage("Profile photo updated successfully")
                }
            } catch (e: Exception) {
                onShowMessage("Failed to process selected image: ${e.message}")
            }
        }
    }

    // Filter out current user from People list
    val peopleContacts = remember(liveAccounts, currentUser) {
        liveAccounts.filter { acc ->
            !acc.upiId.equals(currentUser.upiId, ignoreCase = true) &&
                acc.id != currentUser.id
        }
    }

    val businessesList = remember(liveAccounts) {
        val serverMerchants = liveAccounts.filter { it.isMerchant }.map { m ->
            BusinessItem(
                name = m.fullName,
                upiHandle = m.upiId,
                category = m.category.ifBlank { "Merchant" },
                color = GPayBlue,
                icon = Icons.Default.ReceiptLong
            )
        }
        val standardBusinesses = listOf(
            BusinessItem("Jio Prepaid", "jiopay@okbank", "Mobile Recharge", Color(0xFF0A2885), Icons.Default.PhoneAndroid),
            BusinessItem("Airtel", "airtel@okbank", "Postpaid & DTH", Color(0xFFE40000), Icons.Default.PhoneAndroid),
            BusinessItem("BESCOM", "bescom@okbank", "Electricity", Color(0xFF1E8E3E), Icons.Default.Bolt),
            BusinessItem("RedBus", "redbus@okbank", "Bus Tickets", Color(0xFFD84E55), Icons.Default.DirectionsBus),
            BusinessItem("Tata Play", "tataplay@okbank", "DTH Recharge", Color(0xFF6B1D9F), Icons.Default.Tv),
            BusinessItem("NHAI FASTag", "fastag@okbank", "Toll Recharge", Color(0xFF1A73E8), Icons.Default.DirectionsBus)
        )
        serverMerchants + standardBusinesses
    }

    // Smooth animated counter for revealed INR balance
    val animatedBalance by animateFloatAsState(
        targetValue = (revealedBalance ?: 0.0).toFloat(),
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        label = "inr_balance_counter"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDarkMode) com.example.ui.theme.GPayDarkBackground else GPayWhite)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            // 1. TOP SEARCH BAR + HORIZON HEADER ILLUSTRATION
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                GPayHorizonBanner(
                    isDarkMode = isDarkMode,
                    modifier = Modifier.fillMaxWidth()
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    // Floating Search Bar + User Profile Circular Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .shadow(
                                    elevation = 4.dp,
                                    shape = RoundedCornerShape(50),
                                    spotColor = Color.Black.copy(alpha = 0.14f)
                                )
                                .clip(RoundedCornerShape(50))
                                .clickable { onOpenPayContacts() }
                                .testTag("home_search_bar"),
                            color = if (isDarkMode) Color(0xFF28292A) else GPayWhite,
                            shape = RoundedCornerShape(50),
                            border = androidx.compose.foundation.BorderStroke(
                                0.5.dp,
                                if (isDarkMode) Color(0xFF3C4043) else GPayBorder
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search contacts",
                                    tint = if (isDarkMode) com.example.ui.theme.GPayDarkPrimaryText else GPayPrimaryText,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Search by name or number",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = if (isDarkMode) com.example.ui.theme.GPayDarkSecondaryText else GPaySecondaryText
                                    ),
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                IconButton(
                                    onClick = onOpenQrScanner,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = "Quick Scan QR",
                                        tint = GPayBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Profile Circular Badge on the right
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable { showProfileSheet = true }
                                .testTag("home_profile_avatar"),
                            contentAlignment = Alignment.Center
                        ) {
                            GPayContactAvatar(
                                name = currentUser.fullName,
                                size = 44.dp,
                                fontSizeSp = 18,
                                profileImage = currentUser.profileImage,
                                showVerifiedBadge = false
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Center Google Pay Emblem over the pastel horizon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GooglePayLogoHeader(logoSize = 24.dp, isDarkBackground = isDarkMode)

                        // Live Server Indicator Chip
                        Surface(
                            onClick = onOpenServerSettings,
                            shape = RoundedCornerShape(50),
                            color = if (isDarkMode) com.example.ui.theme.GPayDarkSurface.copy(alpha = 0.9f) else GPayWhite.copy(alpha = 0.9f),
                            border = androidx.compose.foundation.BorderStroke(
                                0.5.dp,
                                if (isDarkMode) com.example.ui.theme.GPayDarkBorder else GPayBorder
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (serverState == ServerConnectionState.CONNECTED) GPayGreenAccent else GPayRed
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (serverState == ServerConnectionState.CONNECTED) "Live Bank" else "Offline",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isDarkMode) com.example.ui.theme.GPayDarkPrimaryText else GPayPrimaryText,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. 4-GRID PRIMARY ACTIONS (Scan any QR, Pay contacts, Bank transfer, Pay bills)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PrimaryActionTile(
                    icon = Icons.Default.QrCodeScanner,
                    title = "Scan any\nQR code",
                    testTag = "action_scan_qr",
                    onClick = onOpenQrScanner,
                    modifier = Modifier.weight(1f),
                    isDarkMode = isDarkMode
                )
                PrimaryActionTile(
                    icon = Icons.Default.ContactPage,
                    title = "Pay\ncontacts",
                    testTag = "action_pay_contacts",
                    onClick = onOpenPayContacts,
                    modifier = Modifier.weight(1f),
                    isDarkMode = isDarkMode
                )
                PrimaryActionTile(
                    icon = Icons.Default.AccountBalance,
                    title = "Bank\ntransfer",
                    testTag = "action_bank_transfer",
                    onClick = onOpenBankTransfer,
                    modifier = Modifier.weight(1f),
                    isDarkMode = isDarkMode
                )
                PrimaryActionTile(
                    icon = Icons.Default.ReceiptLong,
                    title = "Pay bills &\nrecharges",
                    testTag = "action_pay_bills",
                    onClick = onOpenPayBills,
                    modifier = Modifier.weight(1f),
                    isDarkMode = isDarkMode
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. SECONDARY PILL ROW (Tap & Pay, Activate UPI Lite, UPI ID)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pill 1: Tap & Pay (NFC wave icon -> 3D card terminal)
                Surface(
                    onClick = onOpenTapAndPay,
                    shape = RoundedCornerShape(50),
                    color = if (isDarkMode) com.example.ui.theme.GPayDarkSurface else GPayPillFill,
                    border = androidx.compose.foundation.BorderStroke(
                        0.5.dp,
                        if (isDarkMode) com.example.ui.theme.GPayDarkBorder else GPayBlue.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.testTag("pill_tap_and_pay")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Contactless,
                            contentDescription = "Tap & Pay NFC",
                            tint = if (isDarkMode) com.example.ui.theme.GPayDarkBlue else GPayBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Tap & Pay",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = if (isDarkMode) com.example.ui.theme.GPayDarkBlue else GPayBlue,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                // Pill 2: Activate UPI Lite (+)
                Surface(
                    onClick = onToggleUpiLite,
                    shape = RoundedCornerShape(50),
                    color = when {
                        upiLiteActive && isDarkMode -> Color(0xFF0F381E)
                        upiLiteActive -> GPayGreenLight
                        isDarkMode -> com.example.ui.theme.GPayDarkSurface
                        else -> GPayOffWhite
                    },
                    border = androidx.compose.foundation.BorderStroke(
                        width = 0.8.dp,
                        color = when {
                            upiLiteActive -> GPayGreen
                            isDarkMode -> com.example.ui.theme.GPayDarkBorder
                            else -> GPayBorder
                        }
                    ),
                    modifier = Modifier.testTag("pill_upi_lite")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (upiLiteActive) Icons.Default.CheckCircle else Icons.Default.AddCircleOutline,
                            contentDescription = null,
                            tint = if (upiLiteActive) GPayGreen else if (isDarkMode) com.example.ui.theme.GPayDarkPrimaryText else GPayPrimaryText,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (upiLiteActive) "UPI Lite: Active" else "Activate UPI Lite",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = if (upiLiteActive) GPayGreen else if (isDarkMode) com.example.ui.theme.GPayDarkPrimaryText else GPayPrimaryText,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }

                // Pill 3: UPI ID: [user]@okbank (tap to copy or view QR)
                Surface(
                    onClick = {
                        copyToClipboard(context, "UPI ID", currentUser.upiId)
                        onShowMessage("UPI ID copied: ${currentUser.upiId}")
                        onOpenMyQr()
                    },
                    shape = RoundedCornerShape(50),
                    color = if (isDarkMode) com.example.ui.theme.GPayDarkSurface else GPayOffWhite,
                    border = androidx.compose.foundation.BorderStroke(
                        0.8.dp,
                        if (isDarkMode) com.example.ui.theme.GPayDarkBorder else GPayBorder
                    ),
                    modifier = Modifier.testTag("pill_upi_id")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "UPI ID: ${currentUser.upiId}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = MonoFontFamily,
                                color = if (isDarkMode) com.example.ui.theme.GPayDarkSecondaryText else GPaySecondaryText,
                                fontSize = 11.sp
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy UPI ID",
                            tint = if (isDarkMode) com.example.ui.theme.GPayDarkSecondaryText else GPaySecondaryText,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // 4. PEOPLE SECTION (Live Accounts from `/api/accounts`)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "People",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDarkMode) com.example.ui.theme.GPayDarkPrimaryText else GPayPrimaryText
                    )
                )
                IconButton(
                    onClick = onRefreshData,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh live contacts",
                        tint = if (isDarkMode) com.example.ui.theme.GPayDarkBlue else GPayBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (peopleContacts.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .border(1.dp, if (isDarkMode) com.example.ui.theme.GPayDarkBorder else GPayBorder, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    color = if (isDarkMode) com.example.ui.theme.GPayDarkSurface else GPayOffWhite
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GPayContactAvatar(name = currentUser.fullName, size = 44.dp, fontSizeSp = 18)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "No other accounts found on server yet",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = if (isDarkMode) com.example.ui.theme.GPayDarkPrimaryText else GPayPrimaryText
                                )
                            )
                            Text(
                                text = "Fetched live from GET /api/accounts. Tap Refresh or register another user.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isDarkMode) com.example.ui.theme.GPayDarkSecondaryText else GPaySecondaryText
                                )
                            )
                        }
                    }
                }
            } else {
                // 4-column grid of circular contact bubbles
                val chunkedContacts = remember(peopleContacts) {
                    peopleContacts.take(12).chunked(4)
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    chunkedContacts.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            rowItems.forEach { person ->
                                PersonBubbleItem(
                                    account = person,
                                    onClick = { onSelectPersonToPay(person) },
                                    modifier = Modifier.weight(1f),
                                    isDarkMode = isDarkMode
                                )
                            }
                            repeat(4 - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 5. BUSINESSES SECTION
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Businesses",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDarkMode) com.example.ui.theme.GPayDarkPrimaryText else GPayPrimaryText
                    )
                )
                Surface(
                    onClick = onOpenPayBills,
                    shape = RoundedCornerShape(50),
                    color = GPayPillFill
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Explore",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = GPayBlue,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = GPayBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(businessesList) { biz ->
                    Column(
                        modifier = Modifier
                            .width(74.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                onSelectBusinessToPay(
                                    biz.name,
                                    biz.upiHandle,
                                    "${biz.category} - ${biz.name}"
                                )
                            }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(biz.color.copy(alpha = 0.12f))
                                .border(1.dp, biz.color.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = biz.icon,
                                contentDescription = biz.name,
                                tint = biz.color,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = biz.name.split(" ").first(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (isDarkMode) com.example.ui.theme.GPayDarkPrimaryText else GPayPrimaryText,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // 6. BOTTOM GOOGLE PAY ROWS: "See transaction history" & "Check bank balance"
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Transaction History Card Row
                Surface(
                    onClick = onOpenTransactionHistory,
                    shape = RoundedCornerShape(16.dp),
                    color = if (isDarkMode) com.example.ui.theme.GPayDarkSurface else GPayWhite,
                    border = androidx.compose.foundation.BorderStroke(
                        0.8.dp,
                        if (isDarkMode) com.example.ui.theme.GPayDarkBorder else GPayBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("see_transaction_history_row")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isDarkMode) com.example.ui.theme.GPayDarkPillFill else GPayPillFill),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = if (isDarkMode) com.example.ui.theme.GPayDarkBlue else GPayBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "See transaction history",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = if (isDarkMode) com.example.ui.theme.GPayDarkPrimaryText else GPayPrimaryText,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Text(
                                text = "Live ledger & Fraud Risk Assessment scores",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isDarkMode) com.example.ui.theme.GPayDarkSecondaryText else GPaySecondaryText
                                )
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = if (isDarkMode) com.example.ui.theme.GPayDarkSecondaryText else GPaySecondaryText
                        )
                    }
                }

                // Check Bank Balance Card (Hidden by default -> launches 4-Dot UPI PIN Dialog -> reveals INR counter)
                Surface(
                    onClick = {
                        if (revealedBalance == null) {
                            onCheckBalanceClick()
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = when {
                        revealedBalance != null && isDarkMode -> Color(0xFF132A1C)
                        revealedBalance != null -> GPayGreenLight
                        isDarkMode -> com.example.ui.theme.GPayDarkSurface
                        else -> GPayWhite
                    },
                    border = androidx.compose.foundation.BorderStroke(
                        width = 0.8.dp,
                        color = when {
                            revealedBalance != null -> GPayGreen.copy(alpha = 0.5f)
                            isDarkMode -> com.example.ui.theme.GPayDarkBorder
                            else -> GPayBorder
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("check_bank_balance_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (revealedBalance != null) GPayWhite else GPayPillFill),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = if (revealedBalance != null) GPayGreen else GPayBlue,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (revealedBalance != null) {
                                        "Available Bank Balance"
                                    } else {
                                        "Check bank balance"
                                    },
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = if (isDarkMode) com.example.ui.theme.GPayDarkPrimaryText else GPayPrimaryText,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                                Text(
                                    text = if (revealedBalance != null) {
                                        "OK Axis Bank •••• ${currentUser.accountNumber.takeLast(4)} • Verified via UPI PIN"
                                    } else {
                                        "Requires 4-digit UPI PIN verification"
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = if (isDarkMode) com.example.ui.theme.GPayDarkSecondaryText else GPaySecondaryText
                                    )
                                )
                            }
                            if (revealedBalance != null) {
                                IconButton(
                                    onClick = onHideBalanceClick,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VisibilityOff,
                                        contentDescription = "Hide balance",
                                        tint = GPaySecondaryText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = GPaySecondaryText
                                )
                            }
                        }

                        AnimatedVisibility(visible = revealedBalance != null) {
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                HorizontalDivider(
                                    color = GPayGreen.copy(alpha = 0.2f),
                                    thickness = 0.5.dp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Text(
                                        text = PaymentValidators.formatInr(animatedBalance.toDouble()),
                                        style = MaterialTheme.typography.displaySmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = GPayGreen
                                        ),
                                        modifier = Modifier.testTag("revealed_balance_text")
                                    )
                                    TextButton(onClick = onCheckBalanceClick) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            tint = GPayGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Re-verify",
                                            style = MaterialTheme.typography.labelMedium.copy(color = GPayGreen)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(90.dp))
        }
    }

    // User Profile & Account Sheet
    if (showProfileSheet) {
        ModalBottomSheet(
            onDismissRequest = { showProfileSheet = false },
            containerColor = if (isDarkMode) com.example.ui.theme.GPayDarkSurface else GPayWhite
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    GPayContactAvatar(
                        name = currentUser.fullName,
                        size = 76.dp,
                        fontSizeSp = 28,
                        profileImage = currentUser.profileImage,
                        showVerifiedBadge = true
                    )
                    IconButton(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        modifier = Modifier
                            .size(28.dp)
                            .background(GPayBlue, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Upload Profile Picture",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = currentUser.fullName,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = GPayPrimaryText
                    )
                )
                Text(
                    text = currentUser.upiId,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = MonoFontFamily,
                        color = GPayBlue
                    )
                )
                if (!currentUser.profession.isNullOrBlank()) {
                    Text(
                        text = currentUser.profession!!,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = GPayBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
                if (!currentUser.address.isNullOrBlank()) {
                    Text(
                        text = currentUser.address!!,
                        style = MaterialTheme.typography.bodySmall.copy(color = GPaySecondaryText)
                    )
                }
                if (currentUser.email.isNotBlank()) {
                    Text(
                        text = currentUser.email,
                        style = MaterialTheme.typography.bodySmall.copy(color = GPaySecondaryText)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        showProfileSheet = false
                        showEditProfileDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GPayBlue),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Customize Profile & Account")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isDarkMode) Color(0xFF28292A) else GPayOffWhite,
                    border = androidx.compose.foundation.BorderStroke(
                        0.8.dp,
                        if (isDarkMode) Color(0xFF3C4043) else GPayBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        ProfileMenuItem(
                            icon = Icons.Default.QrCode2,
                            title = "My UPI QR Code",
                            subtitle = "Show QR code to receive money",
                            onClick = {
                                showProfileSheet = false
                                onOpenMyQr()
                            }
                        )
                        HorizontalDivider(
                            color = GPayBorder,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        ProfileMenuItem(
                            icon = Icons.Default.Contactless,
                            title = "Tap & Pay",
                            subtitle = currentUser.virtualCard?.let {
                                "${it.network.displayName} •••• ${it.cardNumber.takeLast(4)}"
                            } ?: "Contactless NFC payments",
                            onClick = {
                                showProfileSheet = false
                                onOpenTapAndPay()
                            }
                        )
                        HorizontalDivider(
                            color = GPayBorder,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        ProfileMenuItem(
                            icon = Icons.Default.Dns,
                            title = "Bank Server Configuration",
                            subtitle = serverUrl,
                            onClick = {
                                showProfileSheet = false
                                onOpenServerSettings()
                            }
                        )
                        HorizontalDivider(
                            color = GPayBorder,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        ProfileMenuItem(
                            icon = Icons.Default.Logout,
                            title = "Sign Out",
                            subtitle = "Switch NEXUS PAY bank account",
                            iconTint = GPayRed,
                            onClick = {
                                showProfileSheet = false
                                onSignOut()
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showEditProfileDialog) {
        com.example.ui.components.EditProfileDialog(
            user = currentUser,
            onDismiss = { showEditProfileDialog = false },
            onSave = { fullName, profession, address, avatarId ->
                showEditProfileDialog = false
                onUpdateProfile?.invoke(fullName, profession, address, avatarId)
            }
        )
    }
}

@Composable
private fun PrimaryActionTile(
    icon: ImageVector,
    title: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDarkMode: Boolean = false
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (isDarkMode) Color(0xFF28292A) else GPayPillFill),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title.replace("\n", " "),
                tint = if (isDarkMode) com.example.ui.theme.GPayDarkBlue else GPayBlue,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                color = if (isDarkMode) com.example.ui.theme.GPayDarkPrimaryText else GPayPrimaryText,
                fontWeight = FontWeight.Medium,
                lineHeight = 16.sp
            ),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PersonBubbleItem(
    account: UserAccount,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDarkMode: Boolean = false
) {
    val firstName = remember(account.fullName) {
        account.fullName.trim().split(" ").firstOrNull() ?: account.fullName
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp)
            .testTag("person_bubble_${account.upiId}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GPayContactAvatar(
            name = account.fullName,
            size = 56.dp,
            fontSizeSp = 21,
            profileImage = account.profileImage
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = firstName,
            style = MaterialTheme.typography.bodySmall.copy(
                color = if (isDarkMode) com.example.ui.theme.GPayDarkPrimaryText else GPayPrimaryText,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color = GPayBlue,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    color = if (iconTint == GPayRed) GPayRed else GPayPrimaryText,
                    fontWeight = FontWeight.SemiBold
                )
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(color = GPaySecondaryText)
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = GPaySecondaryText
        )
    }
}
