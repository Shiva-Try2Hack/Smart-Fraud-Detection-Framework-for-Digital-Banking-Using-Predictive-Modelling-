package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.repository.GPayRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * NexusPaymentSyncService
 *
 * Runs background heartbeat polling when user is logged in, keeping payment listeners alive
 * even when the screen is off or app is minimized, acquiring a temporary WakeLock to wake the
 * screen and sound an instant heads-up alert whenever money is received.
 */
class NexusPaymentSyncService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repository: GPayRepository
    private var lastKnownBalance: Double? = null
    private var currentUpiId: String? = null

    companion object {
        const val FOREGROUND_CHANNEL_ID = "nexus_gpay_sync_channel"
        const val PAYMENT_ALERT_CHANNEL_ID = "nexus_gpay_payment_channel"
        private const val SYNC_NOTIFICATION_ID = 9001

        const val ACTION_START_SYNC = "com.example.action.START_PAYMENT_SYNC"
        const val ACTION_STOP_SYNC = "com.example.action.STOP_PAYMENT_SYNC"
        const val EXTRA_UPI_ID = "extra_upi_id"
        const val EXTRA_INITIAL_BALANCE = "extra_initial_balance"

        fun start(context: Context, upiId: String, currentBalance: Double) {
            val intent = Intent(context, NexusPaymentSyncService::class.java).apply {
                action = ACTION_START_SYNC
                putExtra(EXTRA_UPI_ID, upiId)
                putExtra(EXTRA_INITIAL_BALANCE, currentBalance)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, NexusPaymentSyncService::class.java).apply {
                action = ACTION_STOP_SYNC
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = GPayRepository(applicationContext)
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SYNC -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_SYNC -> {
                val upi = intent.getStringExtra(EXTRA_UPI_ID)
                val initBal = intent.getDoubleExtra(EXTRA_INITIAL_BALANCE, -1.0)
                if (!upi.isNullOrBlank()) {
                    currentUpiId = upi
                    if (initBal >= 0 && lastKnownBalance == null) {
                        lastKnownBalance = initBal
                    }
                }
                startInForeground()
                startPeriodicSync()
            }
            else -> {
                startInForeground()
                startPeriodicSync()
            }
        }
        return START_STICKY
    }

    private fun startInForeground() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nexus_pay_notification)
            .setContentTitle("NEXUS PAY Active")
            .setContentText("Listening for real-time payments and transfers")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(SYNC_NOTIFICATION_ID, notification)
    }

    private fun startPeriodicSync() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val myUpi = currentUpiId
                    if (!myUpi.isNullOrBlank()) {
                        val accountsResult = repository.fetchAccounts()
                        accountsResult.onSuccess { accounts ->
                            val myAccount = accounts.firstOrNull {
                                it.upiId.equals(myUpi, ignoreCase = true)
                            }
                            if (myAccount != null) {
                                val prevBal = lastKnownBalance
                                if (prevBal != null && myAccount.balance > prevBal) {
                                    val diff = myAccount.balance - prevBal
                                    val txResult = repository.fetchTransactions()
                                    var senderName = "NEXUS PAY User"
                                    txResult.onSuccess { txs ->
                                        val latestReceived = txs.firstOrNull {
                                            it.receiverId.equals(myUpi, ignoreCase = true)
                                        }
                                        if (latestReceived != null) {
                                            senderName = accounts.firstOrNull {
                                                it.upiId.equals(latestReceived.senderId, ignoreCase = true)
                                            }?.fullName ?: latestReceived.senderId
                                        }
                                    }
                                    wakeDeviceAndAlert(diff, senderName)
                                }
                                lastKnownBalance = myAccount.balance
                            }
                        }
                    }
                } catch (_: Exception) {}
                delay(3500)
            }
        }
    }

    private fun wakeDeviceAndAlert(amountDiff: Double, senderDisplayName: String) {
        // 1. Temporarily acquire WakeLock so CPU and screen can wake up even if locked
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "NexusPay:PaymentAlertWakeLock"
        )
        wakeLock?.acquire(3000)

        // 2. Build high-priority heads up notification
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val formatted = "₹${String.format("%.2f", amountDiff)}"
        val builder = NotificationCompat.Builder(this, PAYMENT_ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nexus_pay_notification)
            .setContentTitle("Money Received: $formatted")
            .setContentText("Received from $senderDisplayName via NEXUS PAY")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(Notification.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 250, 150, 250))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, false)

        manager.notify((System.currentTimeMillis() % 10000).toInt(), builder.build())
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Background Sync Channel (Low priority, silent)
            val syncChannel = NotificationChannel(
                FOREGROUND_CHANNEL_ID,
                "Background Payment Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps connection alive to receive instant payments when screen is off"
                setShowBadge(false)
            }
            manager.createNotificationChannel(syncChannel)

            // Payment Alert Channel (High priority, pops on screen, sound & vibrate)
            val alertChannel = NotificationChannel(
                PAYMENT_ALERT_CHANNEL_ID,
                "Payment Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for incoming money transfers and transactions"
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(alertChannel)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
