package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.NotificationCompat
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.model.PaymentType
import com.example.security.BiometricAuthHelper
import com.example.ui.components.GPayPinDialog
import com.example.ui.components.MyUpiQrDialog
import com.example.ui.components.ServerConfigDialog
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BankTransferScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PayBillsScreen
import com.example.ui.screens.PayContactsScreen
import com.example.ui.screens.PaymentSheetScreen
import com.example.ui.screens.QrScannerScreen
import com.example.ui.screens.TapAndPayScreen
import com.example.ui.screens.TransactionHistoryScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.GPayViewModel
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private var hasLeftForeground = false
    var triggerAppReauth: (() -> Unit)? = null

    private var nfcAdapter: android.nfc.NfcAdapter? = null
    var onNfcDiscoveredCallback: ((String) -> Unit)? = null

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        createNotificationChannel()
        checkAndRequestNotificationPermission()
        nfcAdapter = android.nfc.NfcAdapter.getDefaultAdapter(this)

        lifecycle.addObserver(androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    hasLeftForeground = true
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    enableNfcReaderMode()
                    if (hasLeftForeground) {
                        hasLeftForeground = false
                        triggerAppReauth?.invoke()
                    }
                }
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    disableNfcReaderMode()
                }
                else -> {}
            }
        })

        setContent {
            var isDarkMode by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            MyApplicationTheme(darkTheme = isDarkMode) {
                GPayAppRoot(
                    activity = this,
                    isDarkMode = isDarkMode,
                    onToggleDarkMode = { isDarkMode = it }
                )
            }
        }
    }

    private fun enableNfcReaderMode() {
        val adapter = nfcAdapter ?: return
        if (!adapter.isEnabled) return
        val flags = android.nfc.NfcAdapter.FLAG_READER_NFC_A or
                android.nfc.NfcAdapter.FLAG_READER_NFC_B or
                android.nfc.NfcAdapter.FLAG_READER_NFC_F or
                android.nfc.NfcAdapter.FLAG_READER_NFC_V or
                android.nfc.NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS

        adapter.enableReaderMode(
            this,
            { tag ->
                val tagIdHex = tag.id.joinToString(":") { "%02X".format(it) }
                runOnUiThread {
                    onNfcDiscoveredCallback?.invoke(tagIdHex)
                }
            },
            flags,
            null
        )
    }

    private fun disableNfcReaderMode() {
        val adapter = nfcAdapter ?: return
        try {
            adapter.disableReaderMode(this)
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PAYMENT_CHANNEL_ID,
                "Payment Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for incoming money transfers and transactions"
                enableVibration(true)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun showPaymentReceivedNotification(amount: String, senderName: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(this, PAYMENT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nexus_pay_notification)
            .setContentTitle("Money Received: $amount")
            .setContentText("Received from $senderName via NEXUS PAY")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        manager.notify((System.currentTimeMillis() % 10000).toInt(), builder.build())
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(permission)
            }
        }
    }

    companion object {
        const val PAYMENT_CHANNEL_ID = "nexus_gpay_payment_channel"
    }
}

@Composable
fun GPayAppRoot(
    activity: MainActivity,
    isDarkMode: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    viewModel: GPayViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var balancePinShakeCount by remember { mutableIntStateOf(0) }
    var isAppLocked by remember { mutableStateOf(false) }
    var isSettingsDialogOpen by remember { mutableStateOf(false) }

    LaunchedEffect(activity, state.currentUser) {
        activity.triggerAppReauth = {
            if (state.currentUser != null) {
                isAppLocked = true
            }
        }
        activity.onNfcDiscoveredCallback = { tagId ->
            if (state.currentUser != null) {
                viewModel.startPaymentFromQr(
                    payeeUpi = "nfc_pos_$tagId@okbank",
                    payeeName = "NFC Terminal ($tagId)",
                    amount = null,
                    note = "Contactless NFC Hardware Tap"
                )
            }
        }
    }

    // Trigger Android Notification when money is received
    var previousBalance by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(state.currentUser?.balance, state.transactions) {
        val newBalance = state.currentUser?.balance
        if (newBalance != null) {
            val old = previousBalance
            if (old != null && newBalance > old) {
                val diff = newBalance - old
                val formattedDiff = "₹${String.format("%.2f", diff)}"
                val currentUpi = state.currentUser?.upiId?.lowercase()
                val recentReceivedTx = state.transactions.firstOrNull { tx ->
                    tx.receiverId.lowercase() == currentUpi && tx.status == com.example.domain.model.TransactionStatus.APPROVED
                }
                val senderAccount = recentReceivedTx?.let { rx ->
                    state.accounts.firstOrNull { it.upiId.equals(rx.senderId, ignoreCase = true) }
                }
                val senderDisplayName = senderAccount?.fullName ?: recentReceivedTx?.senderId ?: "NEXUS PAY User"
                activity.showPaymentReceivedNotification(
                    amount = formattedDiff,
                    senderName = senderDisplayName
                )
            }
            previousBalance = newBalance
        }
    }

    LaunchedEffect(state.generalMessage) {
        val msg = state.generalMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearGeneralMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding() * 0f)
        ) {
            androidx.compose.animation.AnimatedContent(
                targetState = state.currentScreen,
                transitionSpec = {
                    val initial = initialState
                    val target = targetState

                    // If switching between bottom taskbar tabs (HOME, TAP_AND_PAY, TRANSACTION_HISTORY)
                    val isInitialTaskbar = initial in listOf(AppScreen.HOME, AppScreen.TAP_AND_PAY, AppScreen.TRANSACTION_HISTORY)
                    val isTargetTaskbar = target in listOf(AppScreen.HOME, AppScreen.TAP_AND_PAY, AppScreen.TRANSACTION_HISTORY)

                    if (isInitialTaskbar && isTargetTaskbar) {
                        // Horizontal directional slide with subtle fade and spring curve
                        val orderMap = mapOf(
                            AppScreen.HOME to 0,
                            AppScreen.TAP_AND_PAY to 1,
                            AppScreen.TRANSACTION_HISTORY to 2
                        )
                        val fromIndex = orderMap[initial] ?: 0
                        val toIndex = orderMap[target] ?: 0

                        if (toIndex > fromIndex) {
                            (androidx.compose.animation.slideInHorizontally(
                                animationSpec = androidx.compose.animation.core.spring(
                                    dampingRatio = 0.85f,
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                                ),
                                initialOffsetX = { width -> (width * 0.35f).toInt() }
                            ) + androidx.compose.animation.fadeIn(
                                animationSpec = androidx.compose.animation.core.tween(320)
                            )).togetherWith(
                                androidx.compose.animation.slideOutHorizontally(
                                    animationSpec = androidx.compose.animation.core.spring(
                                        dampingRatio = 0.85f,
                                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                                    ),
                                    targetOffsetX = { width -> (-width * 0.35f).toInt() }
                                ) + androidx.compose.animation.fadeOut(
                                    animationSpec = androidx.compose.animation.core.tween(240)
                                )
                            )
                        } else {
                            (androidx.compose.animation.slideInHorizontally(
                                animationSpec = androidx.compose.animation.core.spring(
                                    dampingRatio = 0.85f,
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                                ),
                                initialOffsetX = { width -> (-width * 0.35f).toInt() }
                            ) + androidx.compose.animation.fadeIn(
                                animationSpec = androidx.compose.animation.core.tween(320)
                            )).togetherWith(
                                androidx.compose.animation.slideOutHorizontally(
                                    animationSpec = androidx.compose.animation.core.spring(
                                        dampingRatio = 0.85f,
                                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                                    ),
                                    targetOffsetX = { width -> (width * 0.35f).toInt() }
                                ) + androidx.compose.animation.fadeOut(
                                    animationSpec = androidx.compose.animation.core.tween(240)
                                )
                            )
                        }
                    } else if (target == AppScreen.PAYMENT_SHEET || target == AppScreen.QR_SCANNER) {
                        // Modal / Bottom sheet entrance: vertical slide-up + scale
                        (androidx.compose.animation.slideInVertically(
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = 0.82f,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                            ),
                            initialOffsetY = { height -> (height * 0.45f).toInt() }
                        ) + androidx.compose.animation.fadeIn(
                            animationSpec = androidx.compose.animation.core.tween(280)
                        )).togetherWith(
                            androidx.compose.animation.fadeOut(
                                animationSpec = androidx.compose.animation.core.tween(200)
                            ) + androidx.compose.animation.scaleOut(
                                targetScale = 0.94f,
                                animationSpec = androidx.compose.animation.core.tween(250)
                            )
                        )
                    } else if (initial == AppScreen.PAYMENT_SHEET || initial == AppScreen.QR_SCANNER) {
                        // Pop exit from modal / sheet
                        (androidx.compose.animation.fadeIn(
                            animationSpec = androidx.compose.animation.core.tween(250)
                        ) + androidx.compose.animation.scaleIn(
                            initialScale = 0.96f,
                            animationSpec = androidx.compose.animation.core.tween(250)
                        )).togetherWith(
                            androidx.compose.animation.slideOutVertically(
                                animationSpec = androidx.compose.animation.core.spring(
                                    dampingRatio = 0.9f,
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                                ),
                                targetOffsetY = { height -> (height * 0.45f).toInt() }
                            ) + androidx.compose.animation.fadeOut(
                                animationSpec = androidx.compose.animation.core.tween(200)
                            )
                        )
                    } else {
                        // Standard detail navigation (e.g. Contacts, Pay Bills, Bank Transfer)
                        (androidx.compose.animation.slideInHorizontally(
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = 0.88f,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                            ),
                            initialOffsetX = { width -> (width * 0.4f).toInt() }
                        ) + androidx.compose.animation.fadeIn(
                            animationSpec = androidx.compose.animation.core.tween(300)
                        )).togetherWith(
                            androidx.compose.animation.slideOutHorizontally(
                                animationSpec = androidx.compose.animation.core.spring(
                                    dampingRatio = 0.88f,
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                                ),
                                targetOffsetX = { width -> (-width * 0.4f).toInt() }
                            ) + androidx.compose.animation.fadeOut(
                                animationSpec = androidx.compose.animation.core.tween(220)
                            )
                        )
                    }
                },
                label = "ScreenAnimatedTransition"
            ) { targetScreen ->
                when (targetScreen) {
                    AppScreen.AUTH -> {
                        AuthScreen(
                            serverUrl = state.serverUrl,
                            serverState = state.serverState,
                            serverStatusDetail = state.serverStatusDetail,
                            savedEmail = state.savedEmail,
                            liveAccounts = state.accounts,
                            isLoading = state.isLoading,
                            authError = state.authError,
                            onLoginSubmit = { email, password ->
                                viewModel.login(email = email, password = password)
                            },
                            onRegisterSubmit = { fullName, email, password, pin ->
                                viewModel.register(
                                    fullName = fullName,
                                    email = email,
                                    password = password,
                                    pin = pin
                                )
                            },
                            onBiometricPromptRequest = { onVerified ->
                                BiometricAuthHelper.authenticate(
                                    activity = activity,
                                    title = "NEXUS PAY Biometric Sign-In",
                                    subtitle = "Verify your identity to access your bank account",
                                    onSuccess = onVerified,
                                    onErrorOrFallback = { msg ->
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                )
                            },
                            onOpenServerConfig = { viewModel.openServerConfigDialog(true) },
                            onRetryPing = { viewModel.testServerConnection() }
                        )
                    }

                    AppScreen.HOME -> {
                        val user = state.currentUser
                        if (user != null) {
                            HomeScreen(
                                currentUser = user,
                                liveAccounts = state.accounts,
                                revealedBalance = state.revealedBalance,
                                upiLiteActive = state.upiLiteActive,
                                serverState = state.serverState,
                                serverUrl = state.serverUrl,
                                onOpenQrScanner = { viewModel.navigateTo(AppScreen.QR_SCANNER) },
                                onOpenPayContacts = { viewModel.navigateTo(AppScreen.PAY_CONTACTS) },
                                onOpenBankTransfer = { viewModel.navigateTo(AppScreen.BANK_TRANSFER) },
                                onOpenPayBills = { viewModel.navigateTo(AppScreen.PAY_BILLS) },
                                onOpenTapAndPay = { viewModel.navigateTo(AppScreen.TAP_AND_PAY) },
                                onToggleUpiLite = { viewModel.toggleUpiLite() },
                                onOpenMyQr = { viewModel.openMyQrDialog(true) },
                                onCheckBalanceClick = { viewModel.openBalancePinDialog() },
                                onHideBalanceClick = { viewModel.hideRevealedBalance() },
                                onOpenTransactionHistory = { viewModel.navigateTo(AppScreen.TRANSACTION_HISTORY) },
                                onSelectPersonToPay = { person ->
                                    viewModel.startPaymentToAccount(
                                        account = person,
                                        paymentType = PaymentType.UPI
                                    )
                                },
                                onSelectBusinessToPay = { bizName, upiHandle, note ->
                                    viewModel.startPaymentFromQr(
                                        payeeUpi = upiHandle,
                                        payeeName = bizName,
                                        amount = null,
                                        note = note
                                    )
                                },
                                onRefreshData = { viewModel.refreshLiveAccountsAndLedger() },
                                onOpenServerSettings = { viewModel.openServerConfigDialog(true) },
                                onSignOut = { viewModel.signOut() },
                                onShowMessage = { msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                },
                                onUpdateProfile = { fullName, profession, address, avatarId ->
                                    viewModel.updateUserProfile(fullName, profession, address, avatarId)
                                },
                                isDarkMode = isDarkMode,
                                onUploadProfilePhoto = { b64 ->
                                    viewModel.uploadProfilePhoto(b64)
                                }
                            )
                        }
                    }

                    AppScreen.QR_SCANNER -> {
                        QrScannerScreen(
                            currentUser = state.currentUser,
                            liveAccounts = state.accounts,
                            onQrDetected = { parsedQr ->
                                viewModel.startPaymentFromQr(
                                    payeeUpi = parsedQr.payeeUpiId,
                                    payeeName = parsedQr.payeeName,
                                    amount = parsedQr.amount,
                                    note = parsedQr.note
                                )
                            },
                            onClose = { viewModel.navigateBack() }
                        )
                    }

                    AppScreen.PAY_CONTACTS -> {
                        val user = state.currentUser
                        if (user != null) {
                            PayContactsScreen(
                                currentUser = user,
                                liveAccounts = state.accounts,
                                onSelectContact = { contact ->
                                    viewModel.startPaymentToAccount(
                                        account = contact,
                                        paymentType = PaymentType.UPI
                                    )
                                },
                                onPayDirectUpi = { upiId, name ->
                                    viewModel.startPaymentFromQr(
                                        payeeUpi = upiId,
                                        payeeName = name,
                                        amount = null,
                                        note = null
                                    )
                                },
                                onRefreshContacts = { viewModel.refreshLiveAccountsAndLedger() },
                                onBack = { viewModel.navigateBack() }
                            )
                        }
                    }

                    AppScreen.BANK_TRANSFER -> {
                        BankTransferScreen(
                            liveAccounts = state.accounts,
                            onConfirmBankDetails = { recipientName, accNo, ifsc ->
                                viewModel.startBankTransferPayment(
                                    recipientName = recipientName,
                                    accountNumber = accNo,
                                    ifscCode = ifsc
                                )
                            },
                            onBack = { viewModel.navigateBack() }
                        )
                    }

                    AppScreen.PAY_BILLS -> {
                        PayBillsScreen(
                            liveAccounts = state.accounts,
                            onSelectBilliller = { name, upiHandle, defaultAmount, note ->
                                viewModel.startPaymentFromQr(
                                    payeeUpi = upiHandle,
                                    payeeName = name,
                                    amount = defaultAmount.toDoubleOrNull(),
                                    note = note
                                )
                            },
                            onBack = { viewModel.navigateBack() }
                        )
                    }

                    AppScreen.TAP_AND_PAY -> {
                        val user = state.currentUser
                        if (user != null) {
                            TapAndPayScreen(
                                currentUser = user,
                                liveAccounts = state.accounts,
                                onSaveNewCard = { pan, holder, exp, cvv, bank, network ->
                                    viewModel.linkNewVirtualCard(
                                        cardNumber = pan,
                                        cardHolder = holder,
                                        expiry = exp,
                                        cvv = cvv,
                                        bankName = bank,
                                        network = network
                                    )
                                },
                                onInitiateNfcPayment = { merchant ->
                                    viewModel.startPaymentToAccount(
                                        account = merchant,
                                        paymentType = PaymentType.NFC,
                                        presetAmount = "450",
                                        presetNote = "Contactless NFC Tap & Pay"
                                    )
                                },
                                onBack = { viewModel.navigateBack() },
                                isDarkMode = isDarkMode
                            )
                        }
                    }

                    AppScreen.PAYMENT_SHEET -> {
                        val user = state.currentUser
                        val draft = state.paymentDraft
                        if (user != null && draft != null) {
                            PaymentSheetScreen(
                                currentUser = user,
                                draft = draft,
                                liveAccounts = state.accounts,
                                isVerifyingPin = state.isVerifyingPin,
                                paymentResult = state.lastPaymentResult,
                                onBiometricRequest = { onVerified ->
                                    BiometricAuthHelper.authenticate(
                                        activity = activity,
                                        title = "Verify NEXUS PAY Payment",
                                        subtitle = "Confirm high-value transfer to ${draft.recipientName}",
                                        onSuccess = onVerified,
                                        onErrorOrFallback = { msg ->
                                            scope.launch { snackbarHostState.showSnackbar(msg) }
                                        }
                                    )
                                },
                                onExecutePayment = { amount, pin, note, type, onErr ->
                                    viewModel.executePayment(
                                        amount = amount,
                                        pin = pin,
                                        note = note,
                                        paymentType = type,
                                        onPinOrServerError = onErr
                                    )
                                },
                                onBackToHome = { viewModel.navigateBack() }
                            )
                        }
                    }

                    AppScreen.TRANSACTION_HISTORY -> {
                        TransactionHistoryScreen(
                            currentUser = state.currentUser,
                            transactions = state.transactions,
                            selectedReceipt = state.selectedReceiptTransaction,
                            isLoading = state.isLoading,
                            onSelectTransaction = { tx -> viewModel.selectReceiptTransaction(tx) },
                            onRefresh = { viewModel.refreshTransactionsOnly() },
                            onBack = { viewModel.navigateBack() },
                            isDarkMode = isDarkMode
                        )
                    }
                }
            }

            // 4-Dot UPI PIN Dialog for "Check Bank Balance"
            val currentUser = state.currentUser
            if (state.isBalancePinDialogOpen && currentUser != null) {
                GPayPinDialog(
                    bankName = "OK Axis Bank",
                    upiId = currentUser.upiId,
                    accountMask = currentUser.accountNumber,
                    subtitleLabel = "Check Bank Balance",
                    amountFormatted = null,
                    isVerifying = state.isVerifyingPin,
                    errorMessage = state.balancePinError,
                    shakeTriggerCount = balancePinShakeCount,
                    onBiometricRequest = {
                        BiometricAuthHelper.authenticate(
                            activity = activity,
                            title = "Verify Bank Balance Check",
                            subtitle = "Authenticate to view your live INR balance",
                            onSuccess = {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Biometric verified — enter 4-digit Bank PIN to decrypt server hash")
                                }
                            },
                            onErrorOrFallback = { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        )
                    },
                    onDismiss = { viewModel.dismissBalancePinDialog() },
                    onSubmitPin = { enteredPin ->
                        viewModel.verifyBalanceWithPin(
                            pin = enteredPin,
                            onWrongPinShake = { balancePinShakeCount++ }
                        )
                    }
                )
            }

            // Personal UPI QR Modal
            if (state.isMyQrDialogOpen && currentUser != null) {
                MyUpiQrDialog(
                    user = currentUser,
                    onDismiss = { viewModel.openMyQrDialog(false) },
                    onCopiedMessage = { msg ->
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    }
                )
            }

            // Live FastAPI Bank Server Configuration Dialog
            if (state.isServerConfigDialogOpen) {
                ServerConfigDialog(
                    currentUrl = state.serverUrl,
                    serverState = state.serverState,
                    statusDetail = state.serverStatusDetail,
                    isPinging = state.isPingingDialog,
                    onDismiss = { viewModel.openServerConfigDialog(false) },
                    onLivePingTest = { testUrl ->
                        viewModel.pingCustomIpInDialog(testUrl)
                    },
                    onSaveAndTest = { newUrl ->
                        viewModel.updateServerUrlAndTest(newUrl)
                    }
                )
            }

            // Background / Reopen Security Lock Overlay
            if (isAppLocked && currentUser != null) {
                com.example.ui.components.AppLockOverlay(
                    activity = activity,
                    user = currentUser,
                    onUnlocked = { isAppLocked = false }
                )
            }

            // Essential App Settings Dialog
            if (isSettingsDialogOpen) {
                com.example.ui.components.NexusSettingsDialog(
                    isDarkMode = isDarkMode,
                    onToggleDarkMode = onToggleDarkMode,
                    serverUrl = state.serverUrl,
                    serverState = state.serverState,
                    onOpenServerConfig = {
                        isSettingsDialogOpen = false
                        viewModel.openServerConfigDialog(true)
                    },
                    onDismiss = { isSettingsDialogOpen = false }
                )
            }

            // Floating Glass/Acrylic Taskbar
            if (currentUser != null && !isAppLocked) {
                com.example.ui.components.FloatingGlassTaskbar(
                    currentScreen = state.currentScreen,
                    isDarkMode = isDarkMode,
                    onNavigate = { targetScreen ->
                        viewModel.navigateTo(targetScreen)
                    },
                    onOpenSettings = {
                        isSettingsDialogOpen = true
                    },
                    modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                )
            }
        }
    }
}
