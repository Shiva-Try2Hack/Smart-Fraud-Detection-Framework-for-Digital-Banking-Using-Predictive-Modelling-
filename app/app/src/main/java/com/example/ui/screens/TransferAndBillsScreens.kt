package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.model.UserAccount
import com.example.domain.util.PaymentValidators
import com.example.ui.components.GPayContactAvatar
import com.example.ui.theme.GPayBlue
import com.example.ui.theme.GPayBorder
import com.example.ui.theme.GPayGreen
import com.example.ui.theme.GPayOffWhite
import com.example.ui.theme.GPayPillFill
import com.example.ui.theme.GPayPrimaryText
import com.example.ui.theme.GPayRed
import com.example.ui.theme.GPaySecondaryText
import com.example.ui.theme.GPayWhite
import com.example.ui.theme.MonoFontFamily

/**
 * Interactive selector showing live contacts fetched from `GET /api/accounts`
 * plus direct UPI ID entry.
 */
@Composable
fun PayContactsScreen(
    currentUser: UserAccount,
    liveAccounts: List<UserAccount>,
    onSelectContact: (UserAccount) -> Unit,
    onPayDirectUpi: (upiId: String, name: String) -> Unit,
    onRefreshContacts: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    var searchQuery by remember { mutableStateOf("") }

    val filteredContacts = remember(liveAccounts, currentUser, searchQuery) {
        liveAccounts.filter { acc ->
            acc.upiId != currentUser.upiId && acc.id != currentUser.id
        }.filter { acc ->
            if (searchQuery.isBlank()) true
            else {
                acc.fullName.contains(searchQuery, ignoreCase = true) ||
                    acc.upiId.contains(searchQuery, ignoreCase = true) ||
                    acc.email.contains(searchQuery, ignoreCase = true) ||
                    acc.accountNumber.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GPayWhite)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag("pay_contacts_screen")
    ) {
        // Top Search Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = GPayPrimaryText
                )
            }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Enter name, UPI ID, or email") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = GPaySecondaryText
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .weight(1f)
                    .testTag("contacts_search_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GPayBlue,
                    unfocusedBorderColor = GPayBorder,
                    focusedContainerColor = GPayOffWhite,
                    unfocusedContainerColor = GPayOffWhite
                )
            )
            IconButton(onClick = onRefreshContacts) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh live accounts",
                    tint = GPayBlue
                )
            }
        }

        // If user typed a valid custom UPI handle like "merchant@okbank", offer direct pay row
        val parsedDirectUpi = remember(searchQuery) {
            PaymentValidators.parseUpiQrString(searchQuery)
        }
        if (parsedDirectUpi != null) {
            Surface(
                onClick = {
                    onPayDirectUpi(parsedDirectUpi.payeeUpiId, parsedDirectUpi.payeeName)
                },
                color = GPayPillFill,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GPayContactAvatar(name = parsedDirectUpi.payeeName, size = 42.dp, fontSizeSp = 17)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pay UPI ID: ${parsedDirectUpi.payeeUpiId}",
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = GPayBlue,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Text(
                            text = "Verified NPCI UPI Handle • Tap to send money",
                            style = MaterialTheme.typography.bodySmall.copy(color = GPaySecondaryText)
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = GPayBlue
                    )
                }
            }
        }

        Text(
            text = "LIVE BANK ACCOUNTS (${filteredContacts.size})",
            style = MaterialTheme.typography.labelSmall.copy(
                color = GPaySecondaryText,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredContacts, key = { it.id.ifBlank { it.upiId } }) { contact ->
                Surface(
                    onClick = { onSelectContact(contact) },
                    shape = RoundedCornerShape(16.dp),
                    color = GPayWhite,
                    border = androidx.compose.foundation.BorderStroke(0.6.dp, GPayBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("contact_row_${contact.upiId}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GPayContactAvatar(
                            name = contact.fullName,
                            size = 48.dp,
                            fontSizeSp = 19,
                            profileImage = contact.profileImage,
                            showVerifiedBadge = true
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = contact.fullName,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = GPayPrimaryText,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            Text(
                                text = contact.upiId,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = MonoFontFamily,
                                    color = GPayBlue
                                )
                            )
                            if (contact.email.isNotBlank()) {
                                Text(
                                    text = contact.email,
                                    style = MaterialTheme.typography.bodySmall.copy(color = GPaySecondaryText)
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = GPaySecondaryText
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bank Transfer Screen: Full IFSC, Account Number, Confirm Account Number, and Recipient Name form.
 */
@Composable
fun BankTransferScreen(
    liveAccounts: List<UserAccount>,
    onConfirmBankDetails: (recipientName: String, accountNumber: String, ifsc: String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    var accountNo by remember { mutableStateOf("") }
    var confirmAccountNo by remember { mutableStateOf("") }
    var ifscCode by remember { mutableStateOf("OKBK0000891") }
    var recipientName by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GPayWhite)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .verticalScroll(rememberScrollState())
            .testTag("bank_transfer_screen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = GPayPrimaryText
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Enter recipient bank details",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = GPayPrimaryText
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = accountNo,
                onValueChange = {
                    accountNo = it.filter { ch -> ch.isDigit() }.take(18)
                    formError = null
                },
                label = { Text("Bank account number") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bank_account_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GPayBlue,
                    unfocusedBorderColor = GPayBorder
                )
            )

            OutlinedTextField(
                value = confirmAccountNo,
                onValueChange = {
                    confirmAccountNo = it.filter { ch -> ch.isDigit() }.take(18)
                    formError = null
                },
                label = { Text("Re-enter bank account number") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bank_confirm_account_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GPayBlue,
                    unfocusedBorderColor = GPayBorder
                )
            )

            OutlinedTextField(
                value = ifscCode,
                onValueChange = {
                    ifscCode = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(11)
                    formError = null
                },
                label = { Text("IFSC code (11 characters)") },
                trailingIcon = {
                    if (ifscCode.length == 11) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Valid IFSC",
                            tint = GPayGreen
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bank_ifsc_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GPayBlue,
                    unfocusedBorderColor = GPayBorder
                )
            )

            OutlinedTextField(
                value = recipientName,
                onValueChange = {
                    recipientName = it
                    formError = null
                },
                label = { Text("Recipient name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bank_recipient_name_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GPayBlue,
                    unfocusedBorderColor = GPayBorder
                )
            )

            if (!formError.isNullOrBlank()) {
                Text(
                    text = formError!!,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = GPayRed,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            Button(
                onClick = {
                    when {
                        accountNo.length < 6 -> {
                            formError = "Please enter a valid bank account number (at least 6 digits)"
                        }
                        accountNo != confirmAccountNo -> {
                            formError = "Bank account numbers do not match"
                        }
                        ifscCode.length < 4 -> {
                            formError = "Please enter a valid bank IFSC code"
                        }
                        recipientName.isBlank() -> {
                            formError = "Please enter the recipient's full name"
                        }
                        else -> {
                            onConfirmBankDetails(recipientName.trim(), accountNo.trim(), ifscCode.trim())
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GPayBlue),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("bank_transfer_confirm_button")
            ) {
                Text(
                    text = "Confirm & Continue",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = GPayWhite,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            // Quick Auto-Fill from Live Registered Server Accounts
            if (liveAccounts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "SELECT FROM REGISTERED BANK ACCOUNTS ON SERVER",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = GPaySecondaryText,
                        fontWeight = FontWeight.Bold
                    )
                )
                liveAccounts.take(6).forEach { acc ->
                    Surface(
                        onClick = {
                            accountNo = acc.accountNumber
                            confirmAccountNo = acc.accountNumber
                            ifscCode = acc.ifscCode
                            recipientName = acc.fullName
                            formError = null
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = GPayOffWhite,
                        border = androidx.compose.foundation.BorderStroke(0.6.dp, GPayBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalance,
                                contentDescription = null,
                                tint = GPayBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = acc.fullName,
                                    style = MaterialTheme.typography.titleSmall.copy(color = GPayPrimaryText)
                                )
                                Text(
                                    text = "A/C: ${acc.accountNumber} • IFSC: ${acc.ifscCode}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = MonoFontFamily,
                                        color = GPaySecondaryText
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class BillServiceCategory(
    val title: String,
    val subtitle: String,
    val upiHandle: String,
    val defaultAmount: String,
    val icon: ImageVector,
    val accent: Color
)

/**
 * Pay Bills & Recharges Screen: Categorized service cards (Mobile, Electricity, DTH, FASTag, Broadband, Water).
 */
@Composable
fun PayBillsScreen(
    liveAccounts: List<UserAccount>,
    onSelectBilliller: (name: String, upiHandle: String, defaultAmount: String, note: String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val categories = remember {
        listOf(
            BillServiceCategory(
                title = "Mobile Recharge",
                subtitle = "Jio, Airtel, Vi, BSNL Prepaid",
                upiHandle = "jio.recharge@okbank",
                defaultAmount = "299",
                icon = Icons.Default.PhoneAndroid,
                accent = GPayBlue
            ),
            BillServiceCategory(
                title = "Electricity Bill",
                subtitle = "BESCOM, Tata Power, Adani, MSEB",
                upiHandle = "bescom.power@okbank",
                defaultAmount = "1450",
                icon = Icons.Default.Bolt,
                accent = GPayGreen
            ),
            BillServiceCategory(
                title = "DTH / Cable TV",
                subtitle = "Tata Play, Airtel Digital TV, Dish TV",
                upiHandle = "tataplay.dth@okbank",
                defaultAmount = "450",
                icon = Icons.Default.Tv,
                accent = Color(0xFF8430CE)
            ),
            BillServiceCategory(
                title = "FASTag Recharge",
                subtitle = "NHAI, ICICI, Axis, HDFC FASTag",
                upiHandle = "nhai.fastag@okbank",
                defaultAmount = "500",
                icon = Icons.Default.DirectionsCar,
                accent = Color(0xFFE8710A)
            ),
            BillServiceCategory(
                title = "Broadband & Fiber",
                subtitle = "JioFiber, Airtel Xstream, ACT Fibernet",
                upiHandle = "jiofiber@okbank",
                defaultAmount = "999",
                icon = Icons.Default.Router,
                accent = Color(0xFF007B83)
            ),
            BillServiceCategory(
                title = "Water & Municipal",
                subtitle = "BWSSB, Delhi Jal Board, MCGM",
                upiHandle = "bwssb.water@okbank",
                defaultAmount = "380",
                icon = Icons.Default.WaterDrop,
                accent = GPayBlue
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GPayWhite)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .verticalScroll(rememberScrollState())
            .testTag("pay_bills_screen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = GPayPrimaryText
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Pay bills & recharges",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = GPayPrimaryText
                )
            )
        }

        Text(
            text = "PAYMENT CATEGORIES",
            style = MaterialTheme.typography.labelSmall.copy(
                color = GPaySecondaryText,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            categories.forEach { cat ->
                val targetHandle = cat.upiHandle
                Surface(
                    onClick = {
                        onSelectBilliller(
                            cat.title,
                        targetHandle,
                        cat.defaultAmount,
                        "${cat.title} (${cat.subtitle.split(",").first()})"
                    )
                },
                    shape = RoundedCornerShape(18.dp),
                    color = GPayWhite,
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, GPayBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(cat.accent.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = cat.icon,
                                contentDescription = cat.title,
                                tint = cat.accent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cat.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = GPayPrimaryText
                                )
                            )
                            Text(
                                text = cat.subtitle,
                                style = MaterialTheme.typography.bodySmall.copy(color = GPaySecondaryText)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = GPayPillFill
                        ) {
                            Text(
                                text = "₹${cat.defaultAmount}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = GPayBlue,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
