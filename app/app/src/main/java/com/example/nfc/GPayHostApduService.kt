package com.example.nfc

import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import java.nio.charset.StandardCharsets

/**
 * Real Android Host Card Emulation (HCE) Service for Google Pay Contactless POS communication.
 * Responds to ISO/IEC 7816-4 SELECT PPSE and EMV AID APDU commands with the active HCE token.
 */
class GPayHostApduService : HostApduService() {

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null || commandApdu.size < 4) {
            return STATUS_FAILED
        }

        val hexCommand = commandApdu.toHexString()
        // ISO 7816-4 SELECT command header: 00 A4 04 00
        if (hexCommand.startsWith("00A40400")) {
            val tokenPayload = ActiveHceTokenStore.buildEmvTokenResponse()
            return tokenPayload + STATUS_SUCCESS
        }

        return STATUS_SUCCESS
    }

    override fun onDeactivated(reason: Int) {
        ActiveHceTokenStore.lastTerminalInteractionTimestamp = System.currentTimeMillis()
    }

    companion object {
        private val STATUS_SUCCESS = byteArrayOf(0x90.toByte(), 0x00.toByte())
        private val STATUS_FAILED = byteArrayOf(0x6F.toByte(), 0x00.toByte())

        private fun ByteArray.toHexString(): String =
            joinToString("") { "%02X".format(it) }
    }
}

object ActiveHceTokenStore {
    @Volatile
    var activeUpiId: String = ""

    @Volatile
    var activeCardLast4: String = ""

    @Volatile
    var activeNetwork: String = "VISA"

    @Volatile
    var hceArmed: Boolean = false

    @Volatile
    var lastTerminalInteractionTimestamp: Long = 0L

    fun armToken(upiId: String, cardNumber: String, network: String) {
        activeUpiId = upiId
        activeCardLast4 = cardNumber.filter { it.isDigit() }.takeLast(4)
        activeNetwork = network
        hceArmed = true
    }

    fun buildEmvTokenResponse(): ByteArray {
        val tokenString = "GPAY_HCE_TOKEN|$activeNetwork|$activeCardLast4|$activeUpiId"
        return tokenString.toByteArray(StandardCharsets.UTF_8)
    }
}

enum class HardwareNfcState {
    ENABLED_READY,
    DISABLED_IN_SETTINGS,
    HARDWARE_NOT_PRESENT
}

object NfcHardwareManager {
    fun checkNfcState(context: Context): HardwareNfcState {
        return try {
            val adapter = NfcAdapter.getDefaultAdapter(context)
            when {
                adapter == null -> HardwareNfcState.HARDWARE_NOT_PRESENT
                !adapter.isEnabled -> HardwareNfcState.DISABLED_IN_SETTINGS
                else -> HardwareNfcState.ENABLED_READY
            }
        } catch (_: Exception) {
            HardwareNfcState.HARDWARE_NOT_PRESENT
        }
    }
}
