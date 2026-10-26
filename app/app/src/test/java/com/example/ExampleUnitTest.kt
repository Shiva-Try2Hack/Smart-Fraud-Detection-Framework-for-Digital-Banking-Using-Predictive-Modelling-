package com.example

import com.example.domain.model.CardNetwork
import com.example.domain.util.PaymentValidators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun luhnValidator_validatesRealCardNumbers() {
        // Valid Visa 16-digit Luhn number
        assertTrue(PaymentValidators.isValidLuhn("4532015112830366"))
        // Invalid Luhn number
        assertFalse(PaymentValidators.isValidLuhn("4532015112830367"))
    }

    @Test
    fun detectCardNetwork_identifiesVisaMastercardRuPay() {
        assertEquals(CardNetwork.VISA, PaymentValidators.detectCardNetwork("4532015112830366"))
        assertEquals(CardNetwork.MASTERCARD, PaymentValidators.detectCardNetwork("5425233430109903"))
        assertEquals(CardNetwork.RUPAY, PaymentValidators.detectCardNetwork("6070940123456789"))
    }

    @Test
    fun parseDirectUpiHandle_extractsPayeeUpiAndName() {
        val parsed = PaymentValidators.parseUpiQrString("rahul.sharma@okbank")
        assertNotNull(parsed)
        assertEquals("rahul.sharma@okbank", parsed?.payeeUpiId)
        assertEquals("Rahul Sharma", parsed?.payeeName)
    }

    @Test
    fun formatInr_formatsIndianRupeeCorrectly() {
        val formatted = PaymentValidators.formatInr(12500.50)
        assertTrue(formatted.contains("12,500.50"))
    }
}
