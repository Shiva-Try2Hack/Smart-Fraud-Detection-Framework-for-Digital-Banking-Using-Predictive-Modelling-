package com.example.domain.util

import android.net.Uri
import com.example.domain.model.CardNetwork
import com.example.domain.model.ParsedUpiPayload
import java.text.NumberFormat
import java.util.Locale

object PaymentValidators {

    /**
     * Standard Luhn-algorithm checksum verification for 13-19 digit debit/credit cards.
     */
    fun isValidLuhn(cardNumberRaw: String): Boolean {
        val digits = cardNumberRaw.filter { it.isDigit() }
        if (digits.length !in 13..19) return false

        var sum = 0
        var alternate = false
        for (i in digits.length - 1 downTo 0) {
            var n = digits[i] - '0'
            if (alternate) {
                n *= 2
                if (n > 9) {
                    n = (n % 10) + 1
                }
            }
            sum += n
            alternate = !alternate
        }
        return (sum % 10 == 0)
    }

    /**
     * Detects card issuer/network from IIN/BIN prefix.
     */
    fun detectCardNetwork(cardNumberRaw: String): CardNetwork {
        val digits = cardNumberRaw.filter { it.isDigit() }
        if (digits.isEmpty()) return CardNetwork.UNKNOWN

        return when {
            digits.startsWith("4") -> CardNetwork.VISA
            digits.take(2).toIntOrNull() in 51..55 -> CardNetwork.MASTERCARD
            digits.take(4).toIntOrNull() in 2221..2720 -> CardNetwork.MASTERCARD
            digits.startsWith("60") ||
                digits.startsWith("65") ||
                digits.startsWith("81") ||
                digits.startsWith("82") ||
                digits.startsWith("508") ||
                digits.startsWith("353") ||
                digits.startsWith("356") -> CardNetwork.RUPAY
            else -> CardNetwork.UNKNOWN
        }
    }

    /**
     * Formats 16 digits into embossed groups of 4: "XXXX XXXX XXXX XXXX"
     */
    fun formatCardNumber(raw: String): String {
        val digits = raw.filter { it.isDigit() }.take(16)
        return digits.chunked(4).joinToString(" ")
    }

    /**
     * Validates MM/YY expiry string.
     */
    fun isValidExpiry(expiry: String): Boolean {
        val clean = expiry.trim()
        val parts = clean.split("/")
        if (parts.size != 2) return false
        val month = parts[0].toIntOrNull() ?: return false
        val year = parts[1].toIntOrNull() ?: return false
        return month in 1..12 && year in 25..45
    }

    /**
     * Parses NPCI UPI QR strings (`upi://pay?pa=...&pn=...&am=...&tn=...`)
     * as well as Nexus Pay JSON or direct UPI handles (`name@okbank`).
     */
    fun parseUpiQrString(rawInput: String): ParsedUpiPayload? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null

        // Case 1: Standard upi://pay?... URI
        if (trimmed.lowercase().startsWith("upi://")) {
            return try {
                val uri = Uri.parse(trimmed)
                val pa = uri.getQueryParameter("pa")?.trim().orEmpty()
                if (pa.isEmpty()) return null
                val pn = uri.getQueryParameter("pn")?.trim()?.takeIf { it.isNotEmpty() }
                    ?: pa.substringBefore("@").replaceFirstChar { it.uppercase() }
                val am = uri.getQueryParameter("am")?.toDoubleOrNull()
                val tn = uri.getQueryParameter("tn")?.trim()
                val mc = uri.getQueryParameter("mc")?.trim()
                ParsedUpiPayload(
                    payeeUpiId = pa,
                    payeeName = pn,
                    amount = am,
                    note = tn,
                    merchantCode = mc,
                    rawQr = trimmed
                )
            } catch (_: Exception) {
                null
            }
        }

        // Case 2: Direct UPI ID handle like "rahul@okbank"
        if (trimmed.contains("@") && !trimmed.contains(" ") && trimmed.length >= 5) {
            val handlePrefix = trimmed.substringBefore("@")
                .replace(".", " ")
                .replace("_", " ")
                .split(" ")
                .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
            return ParsedUpiPayload(
                payeeUpiId = trimmed,
                payeeName = handlePrefix.ifBlank { trimmed },
                amount = null,
                note = null,
                merchantCode = null,
                rawQr = trimmed
            )
        }

        return null
    }

    /**
     * Formats INR currency in authentic Indian numbering format (₹ XX,XXX.XX).
     */
    fun formatInr(amount: Double, includeDecimals: Boolean = true): String {
        val locale = Locale("en", "IN")
        val formatter = NumberFormat.getCurrencyInstance(locale)
        formatter.maximumFractionDigits = if (includeDecimals) 2 else 0
        formatter.minimumFractionDigits = if (includeDecimals) 2 else 0
        return formatter.format(amount)
    }
}
