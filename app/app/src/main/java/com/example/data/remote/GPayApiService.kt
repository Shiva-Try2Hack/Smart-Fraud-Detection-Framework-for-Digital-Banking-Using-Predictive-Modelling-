package com.example.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Retrofit2 Contract matching the FastAPI / SQLite Bank Server specification:
 * - GET / (Health-check returning { "service": "Nexus Pay FDS Server API", "status": "online", "version": "2.4.0" })
 * - POST /api/auth/login
 * - POST /api/auth/register
 * - POST /api/auth/verify-pin
 * - GET /api/accounts
 * - POST /api/payment/process
 * - GET /api/admin/transactions?limit=100
 */
interface GPayApiService {

    @GET("/")
    suspend fun healthCheck(): Response<ResponseBody>

    @GET
    suspend fun pingUrl(@Url fullUrl: String): Response<ResponseBody>

    @POST("/api/auth/login")
    suspend fun login(
        @Body request: Map<String, @JvmSuppressWildcards Any>
    ): Response<ResponseBody>

    @POST("/api/auth/register")
    suspend fun register(
        @Body request: Map<String, @JvmSuppressWildcards Any>
    ): Response<ResponseBody>

    @POST("/api/auth/verify-pin")
    suspend fun verifyPin(
        @Body request: Map<String, @JvmSuppressWildcards Any>,
        @Header("Authorization") authHeader: String? = null
    ): Response<ResponseBody>

    @GET("/api/accounts")
    suspend fun getAccounts(
        @Header("Authorization") authHeader: String? = null
    ): Response<ResponseBody>

    @POST("/api/payment/process")
    suspend fun processPayment(
        @Body request: Map<String, @JvmSuppressWildcards Any>,
        @Header("Authorization") authHeader: String? = null
    ): Response<ResponseBody>

    @GET("/api/admin/transactions")
    suspend fun getTransactions(
        @Query("limit") limit: Int = 100,
        @Header("Authorization") authHeader: String? = null
    ): Response<ResponseBody>

    @GET("/api/accounts/{identifier}/transactions")
    suspend fun getUserTransactions(
        @Path("identifier") identifier: String,
        @Query("limit") limit: Int = 100,
        @Header("Authorization") authHeader: String? = null
    ): Response<ResponseBody>

    @POST("/api/accounts/{identifier}/profile-image")
    suspend fun uploadProfileImage(
        @Path("identifier") identifier: String,
        @Body request: Map<String, @JvmSuppressWildcards Any>,
        @Header("Authorization") authHeader: String? = null
    ): Response<ResponseBody>

    @PUT("/api/accounts/{accountId}/card")
    suspend fun updateVirtualCard(
        @Path("accountId") accountId: String,
        @Body request: Map<String, @JvmSuppressWildcards Any>,
        @Header("Authorization") authHeader: String? = null
    ): Response<ResponseBody>
}
