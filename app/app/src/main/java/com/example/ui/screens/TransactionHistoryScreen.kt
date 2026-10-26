package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.TransactionRecord
import com.example.domain.model.TransactionStatus
import com.example.domain.model.UserAccount
import com.example.domain.util.PaymentValidators
import com.example.ui.components.GPayContactAvatar
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(
    currentUser: UserAccount?,
    transactions: List<TransactionRecord>,
    selectedReceipt: TransactionRecord?,
    isLoading: Boolean,
    onSelectTransaction: (TransactionRecord?) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    isDarkMode: Boolean = false
) {
    BackHandler { onBack() }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") }

    val filteredTransactions = remember(transactions, searchQuery, selectedFilter) {
        transactions.filter { tx ->
            val matchesFilter = when (selectedFilter) {
                "APPROVED" -> tx.status == TransactionStatus.APPROVED
                "FLAGGED" -> tx.status == TransactionStatus.FLAGGED
                "BLOCKED" -> tx.status == TransactionStatus.BLOCKED
                "NFC" -> tx.type.apiValue == "NFC"
                "QR" -> tx.type.apiValue == "QR"
                else -> true
            }
            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                tx.senderName.contains(searchQuery, ignoreCase = true) ||
                    tx.receiverName.contains(searchQuery, ignoreCase = true) ||
                    tx.utrNumber.contains(searchQuery, ignoreCase = true) ||
                    tx.id.contains(searchQuery, ignoreCase = true)
            }
            matchesFilter && matchesSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDarkMode) com.example.ui.theme.GPayDarkBackground else GPayWhite)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag("transaction_history_screen")
    ) {
        // Top Header + Search Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("history_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = if (isDarkMode) com.example.ui.theme.GPayDarkPrimaryText else GPayPrimaryText
                )
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { 
                    Text(
                        "Search transactions or UTR",
                        color = if (isDarkMode) com.example.ui.theme.GPayDarkSecondaryText else GPaySecondaryText
                    ) 
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = if (isDarkMode) com.example.ui.theme.GPayDarkSecondaryText else GPaySecondaryText
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .weight(1f)
                    .testTag("history_search_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (isDarkMode) com.example.ui.theme.GPayDarkBlue else GPayBlue,
                    unfocusedBorderColor = if (isDarkMode) com.example.ui.theme.GPayDarkBorder else GPayBorder,
                    focusedContainerColor = if (isDarkMode) com.example.ui.theme.GPayDarkSurface else GPayOffWhite,
                    unfocusedContainerColor = if (isDarkMode) com.example.ui.theme.GPayDarkSurface else GPayOffWhite,
                    focusedTextColor = if (isDarkMode) com.example.ui.theme.GPayDarkPrimaryText else GPayPrimaryText,
                    unfocusedTextColor = if (isDarkMode) com.example.ui.theme.GPayDarkPrimaryText else GPayPrimaryText
                )
            )

            IconButton(
                onClick = onRefresh,
                modifier = Modifier.testTag("history_refresh_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = GPayBlue
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh transactions",
                        tint = GPayBlue
                    )
                }
            }
        }

        // Filter Chips Row (`ALL`, `APPROVED`, `FLAGGED`, `BLOCKED`, `NFC`, `QR`)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "ALL" to "All Activity (${transactions.size})",
                "APPROVED" to "Approved",
                "FLAGGED" to "Flagged Risk",
                "BLOCKED" to "Blocked",
                "NFC" to "Tap & Pay (NFC)",
                "QR" to "Scan QR"
            ).forEach { (key, label) ->
                val selected = selectedFilter == key
                FilterChip(
                    selected = selected,
                    onClick = { selectedFilter = key },
                    label = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        )
                    },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GPayPillFill,
                        selectedLabelColor = GPayBlue
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (filteredTransactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(GPayPillFill),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = GPayBlue,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "No matching transactions in live ledger",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = GPayPrimaryText
                        ),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Fetched directly from GET /api/admin/transactions?limit=100",
                        style = MaterialTheme.typography.bodySmall.copy(color = GPaySecondaryText),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredTransactions, key = { it.id }) { tx ->
                    val isIncoming = currentUser != null && (
                        tx.receiverId.equals(currentUser.id, ignoreCase = true) ||
                            tx.receiverId.equals(currentUser.upiId, ignoreCase = true) ||
                            tx.receiverUpi.equals(currentUser.upiId, ignoreCase = true)
                        )
                    val counterpartyName = if (isIncoming) {
                        tx.senderName.ifBlank { tx.senderId }
                    } else {
                        tx.receiverName.ifBlank { tx.receiverId }
                    }

                    TransactionListItem(
                        transaction = tx,
                        counterpartyName = counterpartyName,
                        isIncoming = isIncoming,
                        isDarkMode = isDarkMode,
                        onClick = { onSelectTransaction(tx) }
                    )
                }
            }
        }
    }

    // Detailed Receipt Modal with Transaction ID, UTR, Fraud Risk Score, and "Share Receipt"
    if (selectedReceipt != null) {
        ModalBottomSheet(
            onDismissRequest = { onSelectTransaction(null) },
            containerColor = if (isDarkMode) com.example.ui.theme.GPayDarkSurface else GPayWhite
        ) {
            TransactionReceiptDetailSheet(
                tx = selectedReceipt,
                currentUser = currentUser,
                onDismiss = { onSelectTransaction(null) }
            )
        }
    }
}

@Composable
private fun TransactionListItem(
    transaction: TransactionRecord,
    counterpartyName: String,
    isIncoming: Boolean,
    isDarkMode: Boolean = false,
    onClick: () -> Unit
) {
    val statusBadgeColor = when (transaction.status) {
        TransactionStatus.APPROVED -> GPayGreen
        TransactionStatus.FLAGGED -> GPayYellow
        TransactionStatus.BLOCKED -> GPayRed
    }
    val statusBgColor = when (transaction.status) {
        TransactionStatus.APPROVED -> if (isDarkMode) Color(0xFF0F381E) else GPayGreenLight
        TransactionStatus.FLAGGED -> if (isDarkMode) Color(0xFF382E05) else GPayYellowLight
        TransactionStatus.BLOCKED -> if (isDarkMode) Color(0xFF3B1210) else GPayRedLight
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isDarkMode) com.example.ui.theme.GPayDarkSurface else GPayWhite,
        border = androidx.compose.foundation.BorderStroke(
            0.6.dp,
            if (isDarkMode) com.example.ui.theme.GPayDarkBorder else GPayBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("transaction_item_${transaction.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GPayContactAvatar(
                name = counterpartyName,
                size = 46.dp,
                fontSizeSp = 18
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = counterpartyName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDarkMode) com.example.ui.theme.GPayDarkPrimaryText else GPayPrimaryText
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${transaction.timestamp} • ${transaction.type.apiValue}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (isDarkMode) com.example.ui.theme.GPayDarkSecondaryText else GPaySecondaryText
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = statusBgColor
                    ) {
                        Text(
                            text = transaction.status.apiValue,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = statusBadgeColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "Risk: ${String.format("%.2f", transaction.riskScore)}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = MonoFontFamily,
                            color = if (isDarkMode) com.example.ui.theme.GPayDarkSecondaryText else GPaySecondaryText,
                            fontSize = 10.sp
                        )
                    )
                }
            }

            // Authentic GPay green `+₹` for incoming or black/white `-₹` for outgoing payments
            val formattedAmt = PaymentValidators.formatInr(transaction.amount)
            Text(
                text = when {
                    transaction.status == TransactionStatus.BLOCKED -> formattedAmt
                    isIncoming -> "+$formattedAmt"
                    else -> "-$formattedAmt"
                },
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = when {
                        transaction.status == TransactionStatus.BLOCKED -> GPayRed
                        isIncoming -> GPayGreen
                        else -> if (isDarkMode) com.example.ui.theme.GPayDarkPrimaryText else GPayPrimaryText
                    }
                )
            )
        }
    }
}

@Composable
private fun TransactionReceiptDetailSheet(
    tx: TransactionRecord,
    currentUser: UserAccount?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val statusColor = when (tx.status) {
        TransactionStatus.APPROVED -> GPayGreenAccent
        TransactionStatus.FLAGGED -> GPayYellow
        TransactionStatus.BLOCKED -> GPayRed
    }
    val statusBgColor = when (tx.status) {
        TransactionStatus.APPROVED -> GPayGreenLight
        TransactionStatus.FLAGGED -> GPayYellowLight
        TransactionStatus.BLOCKED -> GPayRedLight
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag("transaction_detail_receipt_modal"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GooglePayLogoHeader(logoSize = 22.dp)
        Spacer(modifier = Modifier.height(14.dp))

        GPayContactAvatar(
            name = tx.receiverName.ifBlank { tx.receiverId },
            size = 56.dp,
            fontSizeSp = 22,
            showVerifiedBadge = tx.status == TransactionStatus.APPROVED
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = PaymentValidators.formatInr(tx.amount),
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.Bold,
                color = GPayPrimaryText
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "${tx.senderName} → ${tx.receiverName}",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = GPaySecondaryText,
                fontWeight = FontWeight.Medium
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Fraud Risk Assessment Score Banner
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = statusBgColor,
            border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (tx.status) {
                        TransactionStatus.APPROVED -> Icons.Default.GppGood
                        TransactionStatus.FLAGGED -> Icons.Default.GppMaybe
                        TransactionStatus.BLOCKED -> Icons.Default.GppBad
                    },
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Fraud Risk Assessment Score: ${String.format("%.2f", tx.riskScore)} (${tx.status.apiValue})",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = GPayPrimaryText
                        )
                    )
                    Text(
                        text = tx.riskReason,
                        style = MaterialTheme.typography.bodySmall.copy(color = GPayPrimaryText)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Ledger Details Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = GPayOffWhite,
            border = androidx.compose.foundation.BorderStroke(0.8.dp, GPayBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LedgerRow("Transaction ID", tx.id)
                HorizontalDivider(color = GPayBorder, thickness = 0.5.dp)
                LedgerRow("Bank Reference (UTR)", tx.utrNumber)
                HorizontalDivider(color = GPayBorder, thickness = 0.5.dp)
                LedgerRow("Payment Channel", tx.type.label)
                HorizontalDivider(color = GPayBorder, thickness = 0.5.dp)
                LedgerRow("Sender ID / UPI", tx.senderUpi)
                HorizontalDivider(color = GPayBorder, thickness = 0.5.dp)
                LedgerRow("Receiver ID / UPI", tx.receiverUpi)
                HorizontalDivider(color = GPayBorder, thickness = 0.5.dp)
                LedgerRow("Timestamp", tx.timestamp)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = {
                val receiptText = buildString {
                    appendLine("NEXUS PAY Official Receipt")
                    appendLine("----------------------------")
                    appendLine("Transaction ID: ${tx.id}")
                    appendLine("UTR Reference: ${tx.utrNumber}")
                    appendLine("Status: ${tx.status.apiValue}")
                    appendLine("Amount: ${PaymentValidators.formatInr(tx.amount)}")
                    appendLine("From: ${tx.senderName} (${tx.senderUpi})")
                    appendLine("To: ${tx.receiverName} (${tx.receiverUpi})")
                    appendLine("Payment Mode: ${tx.type.label}")
                    appendLine("Fraud Risk Score: ${String.format("%.2f", tx.riskScore)}")
                    appendLine("Timestamp: ${tx.timestamp}")
                }
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, receiptText)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Transaction Receipt"))
            },
            colors = ButtonDefaults.buttonColors(containerColor = GPayBlue),
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("modal_share_receipt_button")
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Share Receipt")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun LedgerRow(label: String, value: String) {
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
