package com.example.domain.model

data class VirtualCard(
    val cardNumber: String,
    val cardHolder: String,
    val expiry: String,
    val cvv: String,
    val network: CardNetwork,
    val bankName: String = "OK Axis Bank",
    val isTokenized: Boolean = true
)

enum class CardNetwork(val displayName: String) {
    VISA("VISA"),
    MASTERCARD("Mastercard"),
    RUPAY("RuPay"),
    UNKNOWN("Card")
}

data class UserAccount(
    val id: String,
    val fullName: String,
    val email: String,
    val upiId: String,
    val balance: Double,
    val virtualCard: VirtualCard?,
    val pin: String = "",
    val accountNumber: String = "",
    val ifscCode: String = "OKBK0000124",
    val isMerchant: Boolean = false,
    val category: String = "",
    val address: String? = null,
    val profession: String? = null,
    val avatarId: Int = 0,
    val profileImage: String? = null
)

data class AuthSession(
    val token: String,
    val user: UserAccount
)

enum class PaymentType(val apiValue: String, val label: String) {
    UPI("UPI", "UPI Transfer"),
    NFC("NFC", "Tap & Pay (NFC)"),
    QR("QR", "Scan & Pay (QR)")
}

enum class TransactionStatus(val apiValue: String) {
    APPROVED("APPROVED"),
    FLAGGED("FLAGGED"),
    BLOCKED("BLOCKED");

    companion object {
        fun fromString(value: String?): TransactionStatus {
            return when (value?.uppercase()?.trim()) {
                "FLAGGED", "WARNING", "REVIEW" -> FLAGGED
                "BLOCKED", "DECLINED", "FAILED", "REJECTED" -> BLOCKED
                else -> APPROVED
            }
        }
    }
}

data class TransactionRecord(
    val id: String,
    val senderId: String,
    val receiverId: String,
    val senderName: String,
    val receiverName: String,
    val senderUpi: String,
    val receiverUpi: String,
    val amount: Double,
    val type: PaymentType,
    val status: TransactionStatus,
    val riskScore: Double,
    val riskReason: String,
    val utrNumber: String,
    val timestamp: String,
    val note: String = ""
)

data class PaymentProcessResult(
    val success: Boolean,
    val status: TransactionStatus,
    val message: String,
    val updatedBalance: Double?,
    val transaction: TransactionRecord?
)

data class ParsedUpiPayload(
    val payeeUpiId: String,
    val payeeName: String,
    val amount: Double?,
    val note: String?,
    val merchantCode: String?,
    val rawQr: String
)

enum class ServerConnectionState {
    CHECKING,
    CONNECTED,
    OFFLINE
}
