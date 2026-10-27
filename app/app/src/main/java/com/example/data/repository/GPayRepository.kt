package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.remote.GPayApiService
import com.example.domain.model.AuthSession
import com.example.domain.model.CardNetwork
import com.example.domain.model.PaymentProcessResult
import com.example.domain.model.PaymentType
import com.example.domain.model.TransactionRecord
import com.example.domain.model.TransactionStatus
import com.example.domain.model.UserAccount
import com.example.domain.model.VirtualCard
import com.example.domain.util.PaymentValidators
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

private val Context.dataStore by preferencesDataStore(name = "gpay_preferences")

data class ServerHealthInfo(
    val verifiedUrl: String,
    val serviceName: String,
    val status: String,
    val version: String
)

class GPayRepository(private val context: Context) {

    companion object {
        const val DEFAULT_SERVER_URL = "https://nexus-gateway.trycloudflare.com"
        const val EMULATOR_SERVER_URL = "http://10.0.2.2:8000"
        const val LOCALHOST_SERVER_URL = "http://127.0.0.1:8000"
        const val DEFAULT_PORT = 8000

        private const val SHARED_PREFS_NAME = "gpay_server_prefs"
        private const val PREF_VERIFIED_SERVER_URL = "verified_server_url"
        private const val PREF_JWT_TOKEN = "jwt_token"
        private const val PREF_SAVED_EMAIL = "saved_email"

        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_JWT_TOKEN = stringPreferencesKey("jwt_token")
        private val KEY_SAVED_EMAIL = stringPreferencesKey("saved_email")
        private val KEY_SAVED_UPI = stringPreferencesKey("saved_upi")
    }

    private val sharedPrefs: SharedPreferences by lazy {
        context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Volatile
    private var activeBaseUrl: String = loadInitialServerUrl()

    @Volatile
    private var activeJwtToken: String? = sharedPrefs.getString(PREF_JWT_TOKEN, null)

    private fun loadInitialServerUrl(): String {
        val savedInSharedPrefs = sharedPrefs.getString(PREF_VERIFIED_SERVER_URL, null)
        if (!savedInSharedPrefs.isNullOrBlank()) {
            return normalizeUrl(savedInSharedPrefs)
        }
        return DEFAULT_SERVER_URL
    }

    private fun isRunningOnEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.lowercase().contains("vbox") ||
            Build.FINGERPRINT.lowercase().contains("emulator") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.lowercase().contains("droid4x") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu") ||
            Build.PRODUCT.contains("sdk") ||
            Build.PRODUCT.contains("emulator"))
    }

    private val dynamicHostInterceptor = Interceptor { chain ->
        val original = chain.request()
        val configuredUrl = activeBaseUrl.trim().removeSuffix("/").toHttpUrlOrNull()
        val requestBuilder = original.newBuilder()

        if (configuredUrl != null) {
            val newUrl = original.url.newBuilder()
                .scheme(configuredUrl.scheme)
                .host(configuredUrl.host)
                .port(configuredUrl.port)
                .build()
            requestBuilder.url(newUrl)
        }

        val token = activeJwtToken
        if (!token.isNullOrBlank() && original.header("Authorization") == null) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        chain.proceed(requestBuilder.build())
    }

    // OkHttpClient configured with 3s connectTimeout and 5s readTimeout per requirement
    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .addInterceptor(dynamicHostInterceptor)
            .addInterceptor(logging)
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    // Dedicated direct ping client (no host rewrite interceptor) so candidate fallback URLs can be tested directly
    private val pingHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val apiService: GPayApiService by lazy {
        Retrofit.Builder()
            .baseUrl("$DEFAULT_SERVER_URL/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GPayApiService::class.java)
    }

    val serverUrlFlow: Flow<String> = context.dataStore.data.map { prefs ->
        val saved = sharedPrefs.getString(PREF_VERIFIED_SERVER_URL, null)
            ?: prefs[KEY_SERVER_URL]
            ?: DEFAULT_SERVER_URL
        activeBaseUrl = normalizeUrl(saved)
        activeBaseUrl
    }

    val savedEmailFlow: Flow<String> = context.dataStore.data.map { prefs ->
        sharedPrefs.getString(PREF_SAVED_EMAIL, null)
            ?: prefs[KEY_SAVED_EMAIL].orEmpty()
    }

    suspend fun initializeFromStore(): String {
        val prefs = context.dataStore.data.first()
        val url = sharedPrefs.getString(PREF_VERIFIED_SERVER_URL, null)
            ?: prefs[KEY_SERVER_URL]
            ?: DEFAULT_SERVER_URL
        val normalized = normalizeUrl(url)
        activeBaseUrl = normalized
        activeJwtToken = sharedPrefs.getString(PREF_JWT_TOKEN, null) ?: prefs[KEY_JWT_TOKEN]
        persistServerUrlLocally(normalized)
        return normalized
    }

    suspend fun setServerUrl(newInput: String): String {
        val normalized = normalizeUrl(newInput)
        persistServerUrlLocally(normalized)
        return normalized
    }

    private suspend fun persistServerUrlLocally(normalizedUrl: String) {
        activeBaseUrl = normalizedUrl
        sharedPrefs.edit().putString(PREF_VERIFIED_SERVER_URL, normalizedUrl).apply()
        runCatching {
            context.dataStore.edit { prefs ->
                prefs[KEY_SERVER_URL] = normalizedUrl
            }
        }
    }

    fun getCurrentServerUrl(): String = activeBaseUrl

    /**
     * Converts raw IP or URL input into a valid `http://<host>:<port>` base URL.
     * Examples:
     * - `192.168.29.40` -> `http://192.168.29.40:8000`
     * - `10.0.2.2:8000` -> `http://10.0.2.2:8000`
     * - `http://192.168.1.10:8000/` -> `http://192.168.1.10:8000`
     */
    fun normalizeUrl(raw: String): String {
        var trimmed = raw.trim().removeSuffix("/")
        if (trimmed.isEmpty()) return DEFAULT_SERVER_URL

        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "http://$trimmed"
        }

        val parsed = trimmed.toHttpUrlOrNull()
        if (parsed != null) {
            val hostPart = trimmed.substringAfter("://").substringBefore("/")
            val hasExplicitPort = hostPart.contains(":")
            val port = if (hasExplicitPort) parsed.port else DEFAULT_PORT
            return "${parsed.scheme}://${parsed.host}:$port"
        }
        return trimmed
    }

    /**
     * Smart Ping / Health-Check Routine with Automatic Dual Network Fallback:
     * 1. Hits `GET /` on the active/requested server URL (`{ "service": "Nexus Pay FDS Server API", "status": "online", "version": "2.4.0" }`).
     * 2. If unreachable and `allowAutoFallback == true`, automatically tests:
     *    - `http://10.0.2.2:8000` (Android Emulator host loopback)
     *    - `http://192.168.29.40:8000` (Physical device Wi-Fi LAN default)
     *    - `http://127.0.0.1:8000` (ADB reverse port-forwarding fallback)
     * 3. Immediately persists the working URL to SharedPreferences so all subsequent API calls use the verified IP.
     */
    suspend fun checkServerConnectivity(
        overrideTargetInput: String? = null,
        allowAutoFallback: Boolean = true
    ): Result<ServerHealthInfo> = withContext(Dispatchers.IO) {
        val primaryUrl = if (!overrideTargetInput.isNullOrBlank()) {
            normalizeUrl(overrideTargetInput)
        } else {
            activeBaseUrl
        }

        val candidates = LinkedHashSet<String>().apply {
            add(primaryUrl)
            if (allowAutoFallback) {
                if (isRunningOnEmulator()) {
                    add(EMULATOR_SERVER_URL)
                    add(DEFAULT_SERVER_URL)
                    add(LOCALHOST_SERVER_URL)
                } else {
                    add(DEFAULT_SERVER_URL)
                    add(EMULATOR_SERVER_URL)
                    add(LOCALHOST_SERVER_URL)
                }
            }
        }

        var lastError: Exception? = null
        for (candidateUrl in candidates) {
            val pingResult = pingSingleServer(candidateUrl)
            if (pingResult.isSuccess) {
                val healthInfo = pingResult.getOrThrow()
                persistServerUrlLocally(healthInfo.verifiedUrl)
                return@withContext Result.success(healthInfo)
            } else {
                if (lastError == null) {
                    lastError = pingResult.exceptionOrNull() as? Exception
                        ?: Exception("Server unreachable at $candidateUrl")
                }
            }
        }

        Result.failure(lastError ?: Exception("Bank server unreachable"))
    }

    private fun pingSingleServer(baseUrl: String): Result<ServerHealthInfo> {
        val cleanBase = normalizeUrl(baseUrl)
        return try {
            val request = Request.Builder()
                .url("$cleanBase/")
                .get()
                .build()

            pingHttpClient.newCall(request).execute().use { response ->
                if (response.code == 200) {
                    val bodyStr = response.body?.string().orEmpty()
                    val json = runCatching { JSONObject(bodyStr) }.getOrNull()
                    val service = json?.optString("service")?.ifBlank { "Nexus Pay FDS Server API" }
                        ?: "Nexus Pay FDS Server API"
                    val status = json?.optString("status")?.ifBlank { "online" } ?: "online"
                    val version = json?.optString("version")?.ifBlank { "2.4.0" } ?: "2.4.0"

                    return Result.success(
                        ServerHealthInfo(
                            verifiedUrl = cleanBase,
                            serviceName = service,
                            status = status,
                            version = version
                        )
                    )
                }
            }

            // Secondary check against /api/accounts in case root "/" is behind a proxy
            val fallbackReq = Request.Builder()
                .url("$cleanBase/api/accounts")
                .get()
                .build()
            pingHttpClient.newCall(fallbackReq).execute().use { response ->
                if (response.isSuccessful) {
                    return Result.success(
                        ServerHealthInfo(
                            verifiedUrl = cleanBase,
                            serviceName = "Nexus Pay FDS Server API",
                            status = "online",
                            version = "2.4.0"
                        )
                    )
                }
                Result.failure(Exception("HTTP ${response.code} from $cleanBase"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Authenticates user via `POST /api/auth/login`
     * Request: `{ "email": string, "password": string }`
     */
    suspend fun login(email: String, password: String): Result<AuthSession> = withContext(Dispatchers.IO) {
        try {
            val payload = mapOf(
                "login" to email.trim(),
                "email" to email.trim(),
                "password" to password
            )
            val response = apiService.login(payload)
            val rawBody = response.body()?.string() ?: response.errorBody()?.string().orEmpty()

            if (!response.isSuccessful) {
                val errMsg = extractErrorMessage(rawBody, "Login failed (HTTP ${response.code()})")
                return@withContext Result.failure(Exception(errMsg))
            }

            val json = JSONObject(rawBody)
            val token = json.optString("access_token")
                .ifBlank { json.optString("token") }
                .ifBlank { "jwt-live-session" }

            val userObj = json.optJSONObject("user")
                ?: json.optJSONObject("account")
                ?: json.optJSONObject("data")
                ?: json

            val userAccount = parseUserAccount(userObj, fallbackEmail = email.trim())
            activeJwtToken = token

            sharedPrefs.edit()
                .putString(PREF_JWT_TOKEN, token)
                .putString(PREF_SAVED_EMAIL, userAccount.email)
                .apply()

            context.dataStore.edit { prefs ->
                prefs[KEY_JWT_TOKEN] = token
                prefs[KEY_SAVED_EMAIL] = userAccount.email
                prefs[KEY_SAVED_UPI] = userAccount.upiId
            }

            Result.success(AuthSession(token = token, user = userAccount))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Registers a new bank user via `POST /api/auth/register`
     * Request: `{ "full_name": string, "email": string, "password": string, "pin": string }`
     */
    suspend fun register(
        fullName: String,
        email: String,
        password: String,
        pin: String
    ): Result<AuthSession> = withContext(Dispatchers.IO) {
        try {
            val payload = mapOf(
                "full_name" to fullName.trim(),
                "email" to email.trim(),
                "password" to password,
                "pin" to pin.trim()
            )
            val response = apiService.register(payload)
            val rawBody = response.body()?.string() ?: response.errorBody()?.string().orEmpty()

            if (!response.isSuccessful) {
                val errMsg = extractErrorMessage(rawBody, "Registration failed (HTTP ${response.code()})")
                return@withContext Result.failure(Exception(errMsg))
            }

            val json = JSONObject(rawBody)
            val token = json.optString("access_token")
                .ifBlank { json.optString("token") }

            val userObj = json.optJSONObject("user")
                ?: json.optJSONObject("account")
                ?: json.optJSONObject("data")
                ?: json

            if (userObj.optString("upi_id").isBlank() && userObj.optString("id").isBlank()) {
                return@withContext login(email = email, password = password)
            }

            val userAccount = parseUserAccount(userObj, fallbackEmail = email.trim(), fallbackName = fullName.trim())
            val effectiveToken = token.ifBlank { "jwt-live-session" }
            activeJwtToken = effectiveToken

            sharedPrefs.edit()
                .putString(PREF_JWT_TOKEN, effectiveToken)
                .putString(PREF_SAVED_EMAIL, userAccount.email)
                .apply()

            context.dataStore.edit { prefs ->
                prefs[KEY_JWT_TOKEN] = effectiveToken
                prefs[KEY_SAVED_EMAIL] = userAccount.email
                prefs[KEY_SAVED_UPI] = userAccount.upiId
            }

            Result.success(AuthSession(token = effectiveToken, user = userAccount))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Verifies 4-digit bank PIN against server hash via `POST /api/auth/verify-pin`
     * Request: `{ "identifier": string, "upi_id": string, "pin": string }`
     */
    suspend fun verifyPin(upiId: String, pin: String): Result<Double?> = withContext(Dispatchers.IO) {
        try {
            val payload = mapOf(
                "identifier" to upiId.trim(),
                "upi_id" to upiId.trim(),
                "pin" to pin.trim()
            )
            val response = apiService.verifyPin(payload)
            val rawBody = response.body()?.string() ?: response.errorBody()?.string().orEmpty()

            if (!response.isSuccessful) {
                val errMsg = extractErrorMessage(rawBody, "Incorrect UPI PIN")
                return@withContext Result.failure(Exception(errMsg))
            }

            if (rawBody.isNotBlank()) {
                val json = JSONObject(rawBody)
                if (json.has("verified") && !json.optBoolean("verified", true)) {
                    val msg = json.optString("message").ifBlank { "Incorrect 4-digit UPI PIN" }
                    return@withContext Result.failure(Exception(msg))
                }
                if (json.has("valid") && !json.optBoolean("valid", true)) {
                    val msg = json.optString("message").ifBlank { "Incorrect 4-digit UPI PIN" }
                    return@withContext Result.failure(Exception(msg))
                }
                val statusStr = json.optString("status", "").uppercase()
                if (statusStr == "FAILED" || statusStr == "INVALID" || statusStr == "ERROR") {
                    val msg = json.optString("message").ifBlank { "Incorrect 4-digit UPI PIN" }
                    return@withContext Result.failure(Exception(msg))
                }
                val balanceFromVerify = if (json.has("balance") && !json.isNull("balance")) {
                    json.optDouble("balance")
                } else {
                    json.optJSONObject("user")?.optDouble("balance")
                }
                return@withContext Result.success(balanceFromVerify)
            }

            Result.success(null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches all registered live bank accounts / users with active UPI handles (`GET /api/accounts`).
     */
    suspend fun fetchAccounts(): Result<List<UserAccount>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getAccounts()
            val rawBody = response.body()?.string() ?: response.errorBody()?.string().orEmpty()

            if (!response.isSuccessful) {
                val errMsg = extractErrorMessage(rawBody, "Failed to load accounts (HTTP ${response.code()})")
                return@withContext Result.failure(Exception(errMsg))
            }

            val accounts = parseAccountsList(rawBody)
            Result.success(accounts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Processes a live payment (`POST /api/payment/process`)
     * Request: `{ "sender_id": string, "receiver_id": string, "amount": float, "pin": string, "type": "UPI" | "NFC" | "QR" }`
     */
    suspend fun processPayment(
        senderId: String,
        receiverId: String,
        amount: Double,
        pin: String,
        type: PaymentType,
        note: String = ""
    ): Result<PaymentProcessResult> = withContext(Dispatchers.IO) {
        try {
            val payload = mutableMapOf<String, Any>(
                "sender_id" to senderId,
                "receiver_id" to receiverId,
                "amount" to amount,
                "pin" to pin,
                "type" to type.apiValue
            )
            if (note.isNotBlank()) {
                payload["note"] = note.trim()
            }

            val response = apiService.processPayment(payload)
            val rawBody = response.body()?.string() ?: response.errorBody()?.string().orEmpty()

            if (!response.isSuccessful) {
                val errMsg = extractErrorMessage(rawBody, "Payment declined by server (HTTP ${response.code()})")
                val blockedTx = runCatching {
                    val errJson = JSONObject(rawBody)
                    val txObj = errJson.optJSONObject("transaction") ?: errJson
                    if (txObj.has("status") || txObj.has("risk_score")) {
                        parseTransaction(txObj)
                    } else null
                }.getOrNull()

                if (blockedTx != null) {
                    return@withContext Result.success(
                        PaymentProcessResult(
                            success = false,
                            status = blockedTx.status,
                            message = errMsg,
                            updatedBalance = null,
                            transaction = blockedTx
                        )
                    )
                }
                return@withContext Result.failure(Exception(errMsg))
            }

            val json = JSONObject(rawBody)
            val txObj = json.optJSONObject("transaction")
                ?: json.optJSONObject("data")
                ?: json

            val statusStr = json.optString("status")
                .ifBlank { txObj.optString("status", "APPROVED") }
            val txStatus = TransactionStatus.fromString(statusStr)
            val isSuccess = txStatus != TransactionStatus.BLOCKED &&
                json.optBoolean("success", true)

            val message = json.optString("message")
                .ifBlank { json.optString("detail") }
                .ifBlank {
                    when (txStatus) {
                        TransactionStatus.APPROVED -> "Payment processed via NPCI UPI"
                        TransactionStatus.FLAGGED -> "Payment processed with Fraud Risk Flag"
                        TransactionStatus.BLOCKED -> "Transaction blocked by Fraud Risk Engine"
                    }
                }

            val newBalance = when {
                json.has("sender_new_balance") && !json.isNull("sender_new_balance") -> json.optDouble("sender_new_balance")
                json.has("new_balance") && !json.isNull("new_balance") -> json.optDouble("new_balance")
                json.has("balance") && !json.isNull("balance") -> json.optDouble("balance")
                json.has("sender_balance") && !json.isNull("sender_balance") -> json.optDouble("sender_balance")
                else -> null
            }

            val record = parseTransaction(
                obj = txObj,
                fallbackSenderId = senderId,
                fallbackReceiverId = receiverId,
                fallbackAmount = amount,
                fallbackType = type,
                fallbackNote = note
            )

            val recAction = json.optString("recommended_action").ifBlank { null }
            val scamWarnObj = json.optJSONObject("scam_warning")
            val scamWarnMsg = scamWarnObj?.optString("message") ?: json.optString("scam_warning").ifBlank { null }

            Result.success(
                PaymentProcessResult(
                    success = isSuccess,
                    status = txStatus,
                    message = message,
                    updatedBalance = newBalance,
                    transaction = record,
                    recommendedAction = recAction,
                    scamWarning = scamWarnMsg
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches user-specific or live ledger of transactions (`GET /api/accounts/{identifier}/transactions` or fallback `/api/admin/transactions`).
     */
    suspend fun fetchTransactions(identifier: String? = null, limit: Int = 100): Result<List<TransactionRecord>> = withContext(Dispatchers.IO) {
        try {
            val response = if (!identifier.isNullOrBlank()) {
                val userRes = apiService.getUserTransactions(identifier = identifier, limit = limit)
                if (userRes.isSuccessful) userRes else apiService.getTransactions(limit = limit)
            } else {
                apiService.getTransactions(limit = limit)
            }
            val rawBody = response.body()?.string() ?: response.errorBody()?.string().orEmpty()

            if (!response.isSuccessful) {
                val errMsg = extractErrorMessage(rawBody, "Failed to load ledger (HTTP ${response.code()})")
                return@withContext Result.failure(Exception(errMsg))
            }

            val list = parseTransactionsList(rawBody)
            val filtered = if (!identifier.isNullOrBlank()) {
                val cleanId = identifier.trim().lowercase()
                list.filter { tx ->
                    tx.senderId.trim().lowercase() == cleanId ||
                    tx.receiverId.trim().lowercase() == cleanId ||
                    tx.senderUpi.trim().lowercase() == cleanId ||
                    tx.receiverUpi.trim().lowercase() == cleanId
                }
            } else {
                list
            }
            Result.success(filtered)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Uploads Base64 profile picture to server (`POST /api/accounts/{identifier}/profile-image`).
     */
    suspend fun uploadProfileImage(identifier: String, imageBase64: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.uploadProfileImage(
                identifier = identifier,
                request = mapOf(
                    "image" to imageBase64,
                    "image_data" to imageBase64
                )
            )
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(Exception("Upload failed HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Saves linked virtual debit/credit card to server profile (`PUT /api/accounts/{id}/card`).
     */
    suspend fun saveVirtualCardToServer(
        accountId: String,
        card: VirtualCard
    ): Result<VirtualCard> = withContext(Dispatchers.IO) {
        val cardPayload = mapOf(
            "card_number" to card.cardNumber.filter { it.isDigit() },
            "card_holder" to card.cardHolder,
            "expiry" to card.expiry,
            "cvv" to card.cvv,
            "network" to card.network.displayName,
            "bank_name" to card.bankName
        )
        try {
            val response = apiService.updateVirtualCard(
                accountId = accountId,
                request = mapOf("virtual_card" to cardPayload) + cardPayload
            )
            if (response.isSuccessful) {
                return@withContext Result.success(card)
            }
            val rawErr = response.errorBody()?.string().orEmpty()
            val errMsg = extractErrorMessage(rawErr, "Server responded HTTP ${response.code()}")
            Result.failure(Exception(errMsg))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearSession() {
        activeJwtToken = null
        sharedPrefs.edit().remove(PREF_JWT_TOKEN).apply()
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_JWT_TOKEN)
        }
    }

    private fun parseAccountsList(rawBody: String): List<UserAccount> {
        if (rawBody.isBlank()) return emptyList()
        val trimmed = rawBody.trim()
        val jsonArray = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> {
                val obj = JSONObject(trimmed)
                obj.optJSONArray("accounts")
                    ?: obj.optJSONArray("users")
                    ?: obj.optJSONArray("data")
                    ?: obj.optJSONArray("items")
                    ?: JSONArray()
            }
            else -> JSONArray()
        }

        val result = mutableListOf<UserAccount>()
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.optJSONObject(i) ?: continue
            result.add(parseUserAccount(item))
        }
        return result
    }

    private fun parseTransactionsList(rawBody: String): List<TransactionRecord> {
        if (rawBody.isBlank()) return emptyList()
        val trimmed = rawBody.trim()
        val jsonArray = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> {
                val obj = JSONObject(trimmed)
                obj.optJSONArray("transactions")
                    ?: obj.optJSONArray("ledger")
                    ?: obj.optJSONArray("data")
                    ?: obj.optJSONArray("items")
                    ?: JSONArray()
            }
            else -> JSONArray()
        }

        val result = mutableListOf<TransactionRecord>()
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.optJSONObject(i) ?: continue
            result.add(parseTransaction(item))
        }
        return result
    }

    private fun parseUserAccount(
        obj: JSONObject,
        fallbackEmail: String = "",
        fallbackName: String = ""
    ): UserAccount {
        val id = obj.opt("id")?.toString()?.takeIf { it != "null" }
            ?: obj.optString("user_id")
            .ifBlank { obj.optString("upi_id") }

        val email = obj.optString("email").ifBlank { fallbackEmail }
        val fullName = obj.optString("full_name")
            .ifBlank { obj.optString("name") }
            .ifBlank { obj.optString("username") }
            .ifBlank { fallbackName }
            .ifBlank { email.substringBefore("@").replaceFirstChar { it.uppercase() } }

        val upiId = obj.optString("upi_id")
            .ifBlank { obj.optString("vpa") }
            .ifBlank {
                val prefix = email.substringBefore("@").ifBlank {
                    fullName.lowercase().replace(" ", "")
                }
                "$prefix@okbank"
            }

        val balance = obj.optDouble("balance", 0.0).let { if (it.isNaN()) 0.0 else it }
        val pin = obj.optString("pin", "")
        val accountNumber = obj.optString("account_number")
            .ifBlank { obj.optString("acc_no") }
            .ifBlank {
                val hashDigits = (id.hashCode().toLong() and 0x7FFFFFFFL).toString().padStart(10, '4')
                "5010$hashDigits".take(14)
            }
        val ifscCode = obj.optString("ifsc_code")
            .ifBlank { obj.optString("ifsc") }
            .ifBlank { "OKBK0000891" }

        val isMerchant = obj.optBoolean("is_merchant", false) ||
            obj.optString("role").equals("merchant", ignoreCase = true) ||
            obj.optString("type").equals("merchant", ignoreCase = true)

        val category = obj.optString("category", "")
        val address = obj.optString("address", "").takeIf { it.isNotBlank() }
        val profession = obj.optString("profession", "").takeIf { it.isNotBlank() }
        val avatarId = obj.optInt("avatar_id", 0)
        val profileImage = obj.optString("profile_image", "").takeIf { it.isNotBlank() }
        val virtualCard = parseVirtualCard(obj.opt("virtual_card"), fullName, id)

        return UserAccount(
            id = id,
            fullName = fullName,
            email = email,
            upiId = upiId,
            balance = balance,
            virtualCard = virtualCard,
            pin = pin,
            accountNumber = accountNumber,
            ifscCode = ifscCode,
            isMerchant = isMerchant,
            category = category,
            address = address,
            profession = profession,
            avatarId = avatarId,
            profileImage = profileImage
        )
    }

    private fun parseVirtualCard(rawCard: Any?, holderName: String, accountId: String): VirtualCard? {
        if (rawCard == null || rawCard == JSONObject.NULL) return null

        if (rawCard is JSONObject) {
            val number = rawCard.optString("card_number")
                .ifBlank { rawCard.optString("number") }
                .ifBlank { rawCard.optString("pan") }
            if (number.isBlank()) return null

            val holder = rawCard.optString("card_holder")
                .ifBlank { rawCard.optString("holder_name") }
                .ifBlank { holderName.uppercase() }
            val expiry = rawCard.optString("expiry")
                .ifBlank { rawCard.optString("exp") }
                .ifBlank { "08/29" }
            val cvv = rawCard.optString("cvv").ifBlank { "842" }
            val network = PaymentValidators.detectCardNetwork(number)
            val bankName = rawCard.optString("bank_name").ifBlank { "OK Axis Bank" }

            return VirtualCard(
                cardNumber = number.filter { it.isDigit() },
                cardHolder = holder.uppercase(),
                expiry = expiry,
                cvv = cvv,
                network = network,
                bankName = bankName
            )
        }

        val cardStr = rawCard.toString().trim()
        if (cardStr.isBlank() || cardStr == "null" || cardStr == "None") return null

        if (cardStr.startsWith("{")) {
            return runCatching {
                parseVirtualCard(JSONObject(cardStr), holderName, accountId)
            }.getOrNull()
        }

        val digitsOnly = cardStr.filter { it.isDigit() }
        if (digitsOnly.length >= 4) {
            val fullNumber = if (digitsOnly.length >= 12) {
                digitsOnly.take(16)
            } else {
                ("453271892043" + digitsOnly).take(16)
            }
            val cvvSeed = ((accountId.hashCode() and 0x7FFFFFFF) % 899 + 100).toString()
            return VirtualCard(
                cardNumber = fullNumber,
                cardHolder = holderName.uppercase(),
                expiry = "09/29",
                cvv = cvvSeed,
                network = PaymentValidators.detectCardNetwork(fullNumber),
                bankName = "OK Axis Bank"
            )
        }

        return null
    }

    private fun parseTransaction(
        obj: JSONObject,
        fallbackSenderId: String = "",
        fallbackReceiverId: String = "",
        fallbackAmount: Double = 0.0,
        fallbackType: PaymentType = PaymentType.UPI,
        fallbackNote: String = ""
    ): TransactionRecord {
        val id = obj.opt("id")?.toString()?.takeIf { it != "null" }
            ?: obj.optString("transaction_id")
            .ifBlank { obj.optString("tx_id") }
            .ifBlank { "TX-${System.currentTimeMillis()}" }

        val senderId = obj.opt("sender_id")?.toString()?.takeIf { it != "null" }
            ?: obj.optString("from_id")
            .ifBlank { fallbackSenderId }

        val receiverId = obj.opt("receiver_id")?.toString()?.takeIf { it != "null" }
            ?: obj.optString("to_id")
            .ifBlank { fallbackReceiverId }

        val senderName = obj.optString("sender_name")
            .ifBlank { obj.optString("sender_upi") }
            .ifBlank { senderId }

        val receiverName = obj.optString("receiver_name")
            .ifBlank { obj.optString("receiver_upi") }
            .ifBlank { receiverId }

        val senderUpi = obj.optString("sender_upi").ifBlank { senderId }
        val receiverUpi = obj.optString("receiver_upi").ifBlank { receiverId }

        val amount = obj.optDouble("amount", fallbackAmount).let {
            if (it.isNaN()) fallbackAmount else it
        }

        val rawType = obj.optString("type")
            .ifBlank { obj.optString("payment_type") }
            .uppercase()
        val paymentType = when (rawType) {
            "NFC", "TAP", "CONTACTLESS" -> PaymentType.NFC
            "QR", "SCAN" -> PaymentType.QR
            "UPI" -> PaymentType.UPI
            else -> fallbackType
        }

        val status = TransactionStatus.fromString(
            obj.optString("status").ifBlank { obj.optString("fraud_status", "APPROVED") }
        )

        val riskScore = when {
            obj.has("risk_score") && !obj.isNull("risk_score") -> obj.optDouble("risk_score", 0.0)
            obj.has("fraud_score") && !obj.isNull("fraud_score") -> obj.optDouble("fraud_score", 0.0)
            else -> 0.0
        }.let { if (it.isNaN()) 0.0 else it }

        val riskReason = obj.optString("risk_reason")
            .ifBlank { obj.optString("fraud_reason") }
            .ifBlank { obj.optString("reason") }
            .ifBlank {
                when (status) {
                    TransactionStatus.APPROVED -> "Verified low-risk transaction by Bank Fraud Engine"
                    TransactionStatus.FLAGGED -> "Velocity / amount anomaly flagged for review"
                    TransactionStatus.BLOCKED -> "Blocked by real-time Fraud Risk Engine"
                }
            }

        val utr = obj.optString("utr")
            .ifBlank { obj.optString("reference_id") }
            .ifBlank { obj.optString("rrn") }
            .ifBlank {
                val hashNum = ((id.hashCode().toLong() and 0x7FFFFFFFL) + 402910000000L).toString()
                hashNum.take(12)
            }

        val timestamp = obj.optString("timestamp")
            .ifBlank { obj.optString("created_at") }
            .ifBlank { obj.optString("date") }
            .ifBlank { "Just now" }

        val note = obj.optString("note")
            .ifBlank { obj.optString("description") }
            .ifBlank { fallbackNote }

        return TransactionRecord(
            id = id,
            senderId = senderId,
            receiverId = receiverId,
            senderName = senderName,
            receiverName = receiverName,
            senderUpi = senderUpi,
            receiverUpi = receiverUpi,
            amount = amount,
            type = paymentType,
            status = status,
            riskScore = riskScore,
            riskReason = riskReason,
            utrNumber = utr,
            timestamp = timestamp,
            note = note
        )
    }

    private fun extractErrorMessage(rawBody: String, fallback: String): String {
        if (rawBody.isBlank()) return fallback
        return try {
            val json = JSONObject(rawBody)
            when {
                json.has("detail") -> {
                    val detail = json.opt("detail")
                    if (detail is JSONArray && detail.length() > 0) {
                        detail.optJSONObject(0)?.optString("msg") ?: detail.toString()
                    } else {
                        detail?.toString() ?: fallback
                    }
                }
                json.has("message") -> json.optString("message", fallback)
                json.has("error") -> json.optString("error", fallback)
                else -> fallback
            }
        } catch (_: Exception) {
            rawBody.take(140).ifBlank { fallback }
        }
    }
}
