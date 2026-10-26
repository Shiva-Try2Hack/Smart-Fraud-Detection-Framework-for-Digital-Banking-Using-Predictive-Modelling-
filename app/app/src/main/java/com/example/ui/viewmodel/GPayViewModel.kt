package com.example.ui.viewmodel

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.GPayRepository
import com.example.domain.model.CardNetwork
import com.example.domain.model.PaymentProcessResult
import com.example.domain.model.PaymentType
import com.example.domain.model.ServerConnectionState
import com.example.domain.model.TransactionRecord
import com.example.domain.model.UserAccount
import com.example.domain.model.VirtualCard
import com.example.nfc.ActiveHceTokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppScreen {
    AUTH,
    HOME,
    QR_SCANNER,
    PAY_CONTACTS,
    BANK_TRANSFER,
    PAY_BILLS,
    TAP_AND_PAY,
    PAYMENT_SHEET,
    TRANSACTION_HISTORY
}

data class PaymentDraft(
    val recipientAccount: UserAccount?,
    val recipientName: String,
    val recipientUpi: String,
    val recipientAccountId: String,
    val presetAmount: String = "",
    val presetNote: String = "",
    val paymentType: PaymentType = PaymentType.UPI,
    val isBankTransfer: Boolean = false,
    val bankIfsc: String = "",
    val bankAccountNo: String = ""
)

data class GPayUiState(
    val currentScreen: AppScreen = AppScreen.AUTH,
    val serverUrl: String = GPayRepository.DEFAULT_SERVER_URL,
    val serverState: ServerConnectionState = ServerConnectionState.CHECKING,
    val serverStatusDetail: String = "Checking connection...",
    val serverServiceInfo: String = "Nexus Pay FDS Server API",
    val isPingingDialog: Boolean = false,
    val isLoading: Boolean = false,
    val authError: String? = null,
    val generalMessage: String? = null,
    val savedEmail: String = "",
    val currentUser: UserAccount? = null,
    val accounts: List<UserAccount> = emptyList(),
    val transactions: List<TransactionRecord> = emptyList(),
    val revealedBalance: Double? = null,
    val isBalancePinDialogOpen: Boolean = false,
    val balancePinError: String? = null,
    val isVerifyingPin: Boolean = false,
    val upiLiteActive: Boolean = false,
    val paymentDraft: PaymentDraft? = null,
    val lastPaymentResult: PaymentProcessResult? = null,
    val selectedReceiptTransaction: TransactionRecord? = null,
    val isMyQrDialogOpen: Boolean = false,
    val isServerConfigDialogOpen: Boolean = false
)

class GPayViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GPayRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(GPayUiState())
    val uiState: StateFlow<GPayUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val savedUrl = repository.initializeFromStore()
            _uiState.update { it.copy(serverUrl = savedUrl) }
            testServerConnection(allowAutoFallback = true)
        }
        viewModelScope.launch {
            repository.savedEmailFlow.collect { savedEmail ->
                _uiState.update { it.copy(savedEmail = savedEmail) }
            }
        }
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(4000)
                if (_uiState.value.currentUser != null && _uiState.value.serverState == ServerConnectionState.CONNECTED) {
                    refreshLiveAccountsAndLedger()
                }
            }
        }
    }

    fun navigateTo(screen: AppScreen) {
        _uiState.update {
            it.copy(
                currentScreen = screen,
                authError = null,
                generalMessage = null
            )
        }
        if (screen == AppScreen.HOME || screen == AppScreen.PAY_CONTACTS) {
            refreshLiveAccountsAndLedger()
        } else if (screen == AppScreen.TRANSACTION_HISTORY) {
            refreshTransactionsOnly()
        }
    }

    fun navigateBack(): Boolean {
        val current = _uiState.value.currentScreen
        return when (current) {
            AppScreen.AUTH, AppScreen.HOME -> false
            else -> {
                _uiState.update {
                    it.copy(
                        currentScreen = AppScreen.HOME,
                        lastPaymentResult = null,
                        authError = null
                    )
                }
                refreshLiveAccountsAndLedger()
                true
            }
        }
    }

    fun openServerConfigDialog(open: Boolean) {
        _uiState.update { it.copy(isServerConfigDialogOpen = open) }
    }

    fun openMyQrDialog(open: Boolean) {
        _uiState.update { it.copy(isMyQrDialogOpen = open) }
    }

    fun selectReceiptTransaction(tx: TransactionRecord?) {
        _uiState.update { it.copy(selectedReceiptTransaction = tx) }
    }

    fun clearGeneralMessage() {
        _uiState.update { it.copy(generalMessage = null, authError = null) }
    }

    /**
     * Live Ping button inside the IP configuration dialog (without closing the dialog yet)
     */
    fun pingCustomIpInDialog(rawIpOrUrl: String) {
        viewModelScope.launch {
            val normalized = repository.setServerUrl(rawIpOrUrl)
            _uiState.update {
                it.copy(
                    serverUrl = normalized,
                    isPingingDialog = true,
                    serverState = ServerConnectionState.CHECKING,
                    serverStatusDetail = "Pinging $normalized/ ..."
                )
            }
            val result = repository.checkServerConnectivity(
                overrideTargetInput = normalized,
                allowAutoFallback = false
            )
            result.fold(
                onSuccess = { info ->
                    _uiState.update {
                        it.copy(
                            isPingingDialog = false,
                            serverUrl = info.verifiedUrl,
                            serverState = ServerConnectionState.CONNECTED,
                            serverServiceInfo = "${info.serviceName} v${info.version}",
                            serverStatusDetail = "Connected to Bank Server • Online (${info.serviceName} v${info.version})"
                        )
                    }
                    repository.fetchAccounts().onSuccess { liveAccounts ->
                        _uiState.update { state -> state.copy(accounts = liveAccounts) }
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isPingingDialog = false,
                            serverState = ServerConnectionState.OFFLINE,
                            serverStatusDetail = "Offline — Could not reach $normalized (${error.localizedMessage ?: "Timeout"})"
                        )
                    }
                }
            )
        }
    }

    /**
     * Saves the IP to SharedPreferences, runs health-check (with emulator/LAN fallback if needed),
     * and closes the dialog when verified.
     */
    fun updateServerUrlAndTest(newUrl: String) {
        viewModelScope.launch {
            val normalized = repository.setServerUrl(newUrl)
            _uiState.update {
                it.copy(
                    serverUrl = normalized,
                    isPingingDialog = true,
                    serverState = ServerConnectionState.CHECKING,
                    serverStatusDetail = "Pinging $normalized/ ..."
                )
            }
            val result = repository.checkServerConnectivity(
                overrideTargetInput = normalized,
                allowAutoFallback = true
            )
            result.fold(
                onSuccess = { info ->
                    _uiState.update {
                        it.copy(
                            isPingingDialog = false,
                            isServerConfigDialogOpen = false,
                            serverUrl = info.verifiedUrl,
                            serverState = ServerConnectionState.CONNECTED,
                            serverServiceInfo = "${info.serviceName} v${info.version}",
                            serverStatusDetail = "Connected to Bank Server • Online (${info.serviceName} v${info.version})",
                            generalMessage = "Connected to ${info.serviceName} (${info.verifiedUrl})"
                        )
                    }
                    repository.fetchAccounts().onSuccess { liveAccounts ->
                        _uiState.update { state -> state.copy(accounts = liveAccounts) }
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isPingingDialog = false,
                            isServerConfigDialogOpen = false,
                            serverState = ServerConnectionState.OFFLINE,
                            serverStatusDetail = "Offline — Tap to change IP (${error.localizedMessage ?: "Unreachable"})"
                        )
                    }
                }
            )
        }
    }

    fun testServerConnection(allowAutoFallback: Boolean = true) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    serverState = ServerConnectionState.CHECKING,
                    serverStatusDetail = "Connecting to ${repository.getCurrentServerUrl()}..."
                )
            }
            val result = repository.checkServerConnectivity(
                overrideTargetInput = null,
                allowAutoFallback = allowAutoFallback
            )
            result.fold(
                onSuccess = { info ->
                    _uiState.update {
                        it.copy(
                            serverUrl = info.verifiedUrl,
                            serverState = ServerConnectionState.CONNECTED,
                            serverServiceInfo = "${info.serviceName} v${info.version}",
                            serverStatusDetail = "Connected to Bank Server • Online (${info.serviceName} v${info.version})"
                        )
                    }
                    repository.fetchAccounts().onSuccess { liveAccounts ->
                        _uiState.update { state -> state.copy(accounts = liveAccounts) }
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            serverUrl = repository.getCurrentServerUrl(),
                            serverState = ServerConnectionState.OFFLINE,
                            serverStatusDetail = "Offline — Tap to change IP (${error.localizedMessage ?: "Unreachable"})"
                        )
                    }
                }
            )
        }
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(authError = "Please enter your email and password.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, authError = null) }
            val result = repository.login(email = email, password = password)
            result.fold(
                onSuccess = { session ->
                    armNfcHceIfCardExists(session.user)
                    com.example.service.NexusPaymentSyncService.start(
                        context = getApplication<Application>(),
                        upiId = session.user.upiId,
                        currentBalance = session.user.balance
                    )
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            currentUser = session.user,
                            currentScreen = AppScreen.HOME,
                            serverState = ServerConnectionState.CONNECTED,
                            revealedBalance = null,
                            authError = null
                        )
                    }
                    refreshLiveAccountsAndLedger()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            authError = err.message ?: "Authentication failed"
                        )
                    }
                }
            )
        }
    }

    fun register(fullName: String, email: String, password: String, pin: String) {
        if (fullName.isBlank() || email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(authError = "All fields are required to open a bank account.") }
            return
        }
        val cleanPin = pin.filter { it.isDigit() }
        if (cleanPin.length != 4) {
            _uiState.update { it.copy(authError = "UPI PIN must be exactly 4 digits.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, authError = null) }
            val result = repository.register(
                fullName = fullName,
                email = email,
                password = password,
                pin = cleanPin
            )
            result.fold(
                onSuccess = { session ->
                    armNfcHceIfCardExists(session.user)
                    com.example.service.NexusPaymentSyncService.start(
                        context = getApplication<Application>(),
                        upiId = session.user.upiId,
                        currentBalance = session.user.balance
                    )
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            currentUser = session.user,
                            currentScreen = AppScreen.HOME,
                            serverState = ServerConnectionState.CONNECTED,
                            revealedBalance = null,
                            authError = null
                        )
                    }
                    refreshLiveAccountsAndLedger()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            authError = err.message ?: "Registration failed"
                        )
                    }
                }
            )
        }
    }

    private var lastObservedBalance: Double? = null

    private fun dispatchPaymentNotification(amountDiff: Double, senderDisplayName: String) {
        val app = getApplication<Application>()

        // 1. Acquire WakeLock to turn on screen if screen is off/device locked
        try {
            val powerManager = app.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            val wakeLock = powerManager?.newWakeLock(
                android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "NexusPay:ForegroundPaymentAlert"
            )
            wakeLock?.acquire(3000)
        } catch (_: Exception) {}

        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "nexus_gpay_payment_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Payment Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for incoming money transfers and transactions"
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }

        val launchIntent = app.packageManager.getLaunchIntentForPackage(app.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                app,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val formatted = "₹${String.format("%.2f", amountDiff)}"
        val builder = NotificationCompat.Builder(app, channelId)
            .setSmallIcon(com.example.R.drawable.ic_nexus_pay_notification)
            .setContentTitle("Money Received: $formatted")
            .setContentText("Received from $senderDisplayName via NEXUS PAY")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(android.app.Notification.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 250, 150, 250))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, false)

        manager.notify((System.currentTimeMillis() % 10000).toInt(), builder.build())
    }

    fun refreshLiveAccountsAndLedger() {
        viewModelScope.launch {
            val accountsResult = repository.fetchAccounts()
            accountsResult.onSuccess { liveAccounts ->
                val current = _uiState.value.currentUser
                val syncedUser = if (current != null) {
                    liveAccounts.find {
                        (it.id.isNotBlank() && it.id == current.id) ||
                            (it.upiId.equals(current.upiId, ignoreCase = true)) ||
                            (it.email.isNotBlank() && it.email.equals(current.email, ignoreCase = true))
                    }?.let { fresh ->
                        fresh.copy(virtualCard = fresh.virtualCard ?: current.virtualCard)
                    } ?: current
                } else null

                if (syncedUser != null) {
                    val prev = lastObservedBalance
                    if (prev != null && syncedUser.balance > prev) {
                        val diff = syncedUser.balance - prev
                        val recentTx = _uiState.value.transactions.firstOrNull { tx ->
                            tx.receiverId.equals(syncedUser.upiId, ignoreCase = true)
                        }
                        val senderName = recentTx?.let { rx ->
                            liveAccounts.firstOrNull { it.upiId.equals(rx.senderId, ignoreCase = true) }?.fullName
                                ?: rx.senderId
                        } ?: "NEXUS PAY User"
                        dispatchPaymentNotification(diff, senderName)
                    }
                    lastObservedBalance = syncedUser.balance
                }

                _uiState.update {
                    it.copy(
                        accounts = liveAccounts,
                        currentUser = syncedUser ?: it.currentUser,
                        serverState = ServerConnectionState.CONNECTED,
                        revealedBalance = if (it.revealedBalance != null && syncedUser != null) {
                            syncedUser.balance
                        } else {
                            it.revealedBalance
                        }
                    )
                }
            }

            val currentUser = _uiState.value.currentUser
            val txResult = repository.fetchTransactions(identifier = currentUser?.upiId, limit = 100)
            txResult.onSuccess { liveTx ->
                _uiState.update { it.copy(transactions = liveTx) }
            }
        }
    }

    fun refreshTransactionsOnly() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val currentUser = _uiState.value.currentUser
            val txResult = repository.fetchTransactions(identifier = currentUser?.upiId, limit = 100)
            txResult.fold(
                onSuccess = { liveTx ->
                    _uiState.update { it.copy(isLoading = false, transactions = liveTx) }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false) }
                }
            )
        }
    }

    fun uploadProfilePhoto(base64: String) {
        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = repository.uploadProfileImage(user.upiId, base64)
            result.fold(
                onSuccess = {
                    val updated = user.copy(profileImage = base64)
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            currentUser = updated,
                            accounts = state.accounts.map { if (it.upiId == user.upiId || it.id == user.id) updated else it },
                            generalMessage = "Profile picture updated successfully!"
                        )
                    }
                    refreshLiveAccountsAndLedger()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            generalMessage = "Failed to upload photo: ${err.message}"
                        )
                    }
                }
            )
        }
    }

    fun openBalancePinDialog() {
        _uiState.update {
            it.copy(
                isBalancePinDialogOpen = true,
                balancePinError = null
            )
        }
    }

    fun dismissBalancePinDialog() {
        _uiState.update {
            it.copy(
                isBalancePinDialogOpen = false,
                balancePinError = null,
                isVerifyingPin = false
            )
        }
    }

    fun verifyBalanceWithPin(pin: String, onWrongPinShake: () -> Unit) {
        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isVerifyingPin = true, balancePinError = null) }
            val verifyRes = repository.verifyPin(upiId = user.upiId, pin = pin)
            verifyRes.fold(
                onSuccess = { verifiedBalance ->
                    val freshAccounts = repository.fetchAccounts().getOrNull()
                    val latestUser = freshAccounts?.find {
                        it.upiId.equals(user.upiId, ignoreCase = true) || it.id == user.id
                    }
                    val liveBalance = verifiedBalance ?: latestUser?.balance ?: user.balance
                    val updatedUser = (latestUser ?: user).copy(
                        balance = liveBalance,
                        virtualCard = latestUser?.virtualCard ?: user.virtualCard
                    )
                    _uiState.update {
                        it.copy(
                            isVerifyingPin = false,
                            isBalancePinDialogOpen = false,
                            balancePinError = null,
                            currentUser = updatedUser,
                            accounts = freshAccounts ?: it.accounts,
                            revealedBalance = liveBalance
                        )
                    }
                },
                onFailure = { err ->
                    onWrongPinShake()
                    _uiState.update {
                        it.copy(
                            isVerifyingPin = false,
                            balancePinError = err.message ?: "Incorrect 4-digit UPI PIN"
                        )
                    }
                }
            )
        }
    }

    fun hideRevealedBalance() {
        _uiState.update { it.copy(revealedBalance = null) }
    }

    fun toggleUpiLite() {
        _uiState.update { state ->
            val next = !state.upiLiteActive
            state.copy(
                upiLiteActive = next,
                generalMessage = if (next) {
                    "UPI Lite activated for instant PIN-free small value payments"
                } else {
                    "UPI Lite paused"
                }
            )
        }
    }

    fun startPaymentToAccount(
        account: UserAccount,
        paymentType: PaymentType = PaymentType.UPI,
        presetAmount: String = "",
        presetNote: String = ""
    ) {
        val receiverIdentifier = account.id.ifBlank { account.upiId }
        _uiState.update {
            it.copy(
                paymentDraft = PaymentDraft(
                    recipientAccount = account,
                    recipientName = account.fullName,
                    recipientUpi = account.upiId,
                    recipientAccountId = receiverIdentifier,
                    presetAmount = presetAmount,
                    presetNote = presetNote,
                    paymentType = paymentType
                ),
                lastPaymentResult = null,
                currentScreen = AppScreen.PAYMENT_SHEET
            )
        }
    }

    fun startPaymentFromQr(
        payeeUpi: String,
        payeeName: String,
        amount: Double?,
        note: String?
    ) {
        val matchedAccount = _uiState.value.accounts.find {
            it.upiId.equals(payeeUpi, ignoreCase = true) ||
                it.id.equals(payeeUpi, ignoreCase = true) ||
                (payeeName.isNotBlank() && it.fullName.equals(payeeName, ignoreCase = true)) ||
                it.email.equals(payeeUpi, ignoreCase = true)
        }
        val effectiveReceiverId = matchedAccount?.upiId?.ifBlank { matchedAccount.id } ?: payeeUpi
        val effectiveName = matchedAccount?.fullName ?: payeeName

        _uiState.update {
            it.copy(
                paymentDraft = PaymentDraft(
                    recipientAccount = matchedAccount,
                    recipientName = effectiveName,
                    recipientUpi = matchedAccount?.upiId ?: payeeUpi,
                    recipientAccountId = effectiveReceiverId,
                    presetAmount = amount?.let { amt ->
                        if (amt % 1.0 == 0.0) amt.toInt().toString() else amt.toString()
                    }.orEmpty(),
                    presetNote = note.orEmpty(),
                    paymentType = PaymentType.QR
                ),
                lastPaymentResult = null,
                currentScreen = AppScreen.PAYMENT_SHEET
            )
        }
    }

    fun startBankTransferPayment(
        recipientName: String,
        accountNumber: String,
        ifscCode: String
    ) {
        val matched = _uiState.value.accounts.find {
            it.accountNumber.equals(accountNumber, ignoreCase = true) ||
                it.fullName.equals(recipientName, ignoreCase = true) ||
                it.upiId.equals(accountNumber, ignoreCase = true)
        }
        val receiverId = matched?.id?.ifBlank { matched.upiId }
            ?: matched?.upiId
            ?: "${recipientName.lowercase().replace(" ", "")}@${ifscCode.take(4).lowercase()}bank"

        _uiState.update {
            it.copy(
                paymentDraft = PaymentDraft(
                    recipientAccount = matched,
                    recipientName = matched?.fullName ?: recipientName,
                    recipientUpi = matched?.upiId ?: "A/C ••••${accountNumber.takeLast(4)} ($ifscCode)",
                    recipientAccountId = receiverId,
                    presetNote = "Bank Transfer ($ifscCode)",
                    paymentType = PaymentType.UPI,
                    isBankTransfer = true,
                    bankIfsc = ifscCode,
                    bankAccountNo = accountNumber
                ),
                lastPaymentResult = null,
                currentScreen = AppScreen.PAYMENT_SHEET
            )
        }
    }

    fun executePayment(
        amount: Double,
        pin: String,
        note: String,
        paymentType: PaymentType,
        onPinOrServerError: (String) -> Unit
    ) {
        val sender = _uiState.value.currentUser ?: return
        val draft = _uiState.value.paymentDraft ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isVerifyingPin = true) }

            val pinCheck = repository.verifyPin(upiId = sender.upiId, pin = pin)
            if (pinCheck.isFailure) {
                val msg = pinCheck.exceptionOrNull()?.message ?: "Incorrect 4-digit UPI PIN"
                _uiState.update { it.copy(isVerifyingPin = false) }
                onPinOrServerError(msg)
                return@launch
            }

            val senderIdentifier = sender.upiId.ifBlank { sender.id }
            val primaryReceiver = draft.recipientUpi.ifBlank { draft.recipientAccountId }

            val processRes = repository.processPayment(
                senderId = senderIdentifier,
                receiverId = primaryReceiver,
                amount = amount,
                pin = pin,
                type = paymentType,
                note = note
            )

            processRes.fold(
                onSuccess = { paymentResult ->
                    // Small deliberate delay (1200ms) before completing payment for realistic gateway simulation
                    kotlinx.coroutines.delay(1200)
                    _uiState.update { state ->
                        val updatedBal = paymentResult.updatedBalance ?: state.currentUser?.balance
                        state.copy(
                            isVerifyingPin = false,
                            lastPaymentResult = paymentResult,
                            currentUser = state.currentUser?.let { u ->
                                if (updatedBal != null) u.copy(balance = updatedBal) else u
                            },
                            revealedBalance = if (state.revealedBalance != null && updatedBal != null) {
                                updatedBal
                            } else {
                                state.revealedBalance
                            }
                        )
                    }
                    refreshLiveAccountsAndLedger()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isVerifyingPin = false) }
                    onPinOrServerError(err.message ?: "Payment failed on server")
                }
            )
        }
    }

    fun linkNewVirtualCard(
        cardNumber: String,
        cardHolder: String,
        expiry: String,
        cvv: String,
        bankName: String,
        network: CardNetwork
    ) {
        val currentUser = _uiState.value.currentUser ?: return
        val newCard = VirtualCard(
            cardNumber = cardNumber.filter { it.isDigit() },
            cardHolder = cardHolder.trim().uppercase(),
            expiry = expiry.trim(),
            cvv = cvv.trim(),
            network = network,
            bankName = bankName.trim().ifBlank { "OK Axis Bank" },
            isTokenized = true
        )

        armNfcHce(currentUser.upiId, newCard)

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = repository.saveVirtualCardToServer(
                accountId = currentUser.id.ifBlank { currentUser.upiId },
                card = newCard
            )
            val updatedUser = currentUser.copy(virtualCard = newCard)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            currentUser = updatedUser,
                            generalMessage = "${network.displayName} card •••• ${newCard.cardNumber.takeLast(4)} tokenized & synced to bank profile"
                        )
                    }
                },
                onFailure = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            currentUser = updatedUser,
                            generalMessage = "${network.displayName} card •••• ${newCard.cardNumber.takeLast(4)} armed for HCE Tap & Pay"
                        )
                    }
                }
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            com.example.service.NexusPaymentSyncService.stop(getApplication<Application>())
            repository.clearSession()
            _uiState.update {
                it.copy(
                    currentScreen = AppScreen.AUTH,
                    currentUser = null,
                    revealedBalance = null,
                    paymentDraft = null,
                    lastPaymentResult = null
                )
            }
            testServerConnection(allowAutoFallback = true)
        }
    }

    fun updateUserProfile(
        fullName: String,
        profession: String,
        address: String,
        avatarId: Int
    ) {
        val user = _uiState.value.currentUser ?: return
        val updated = user.copy(
            fullName = fullName.ifBlank { user.fullName },
            profession = profession.takeIf { it.isNotBlank() },
            address = address.takeIf { it.isNotBlank() },
            avatarId = avatarId
        )
        _uiState.update { state ->
            state.copy(
                currentUser = updated,
                accounts = state.accounts.map { if (it.upiId == user.upiId || it.id == user.id) updated else it },
                generalMessage = "Profile updated successfully"
            )
        }
    }

    private fun armNfcHceIfCardExists(user: UserAccount) {
        val card = user.virtualCard ?: return
        armNfcHce(user.upiId, card)
    }

    private fun armNfcHce(upiId: String, card: VirtualCard) {
        ActiveHceTokenStore.armToken(
            upiId = upiId,
            cardNumber = card.cardNumber,
            network = card.network.displayName
        )
    }
}
