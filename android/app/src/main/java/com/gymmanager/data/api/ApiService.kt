package com.gymmanager.data.api

import com.gymmanager.data.model.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit interface mapping to every backend endpoint.
 * The Authorization header is injected by [AuthInterceptor] — no need to
 * add it manually to each call.
 */
interface ApiService {

    // ── Auth ─────────────────────────────────────────────────────────────────
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    // ── Members ──────────────────────────────────────────────────────────────
    @GET("members")
    suspend fun getMembers(
        @Query("search") search: String? = null,
    ): Response<List<Member>>

    @GET("members/{id}")
    suspend fun getMemberDetail(@Path("id") id: Int): Response<MemberDetail>

    @POST("members")
    suspend fun createMember(@Body request: CreateMemberRequest): Response<Member>

    @PUT("members/{id}")
    suspend fun updateMember(
        @Path("id") id: Int,
        @Body request: UpdateMemberRequest,
    ): Response<Member>

    @DELETE("members/{id}")
    suspend fun deleteMember(@Path("id") id: Int): Response<Unit>

    // ── Plans ─────────────────────────────────────────────────────────────────
    @GET("plans")
    suspend fun getPlans(): Response<List<Plan>>

    @POST("plans")
    suspend fun createPlan(@Body request: CreatePlanRequest): Response<Plan>

    // ── Attendance ────────────────────────────────────────────────────────────
    @GET("attendance")
    suspend fun getTodayAttendance(): Response<List<Attendance>>

    @POST("attendance")
    suspend fun checkIn(@Body request: CheckInRequest): Response<Attendance>

    // ── Payments ──────────────────────────────────────────────────────────────
    @GET("payments")
    suspend fun getPayments(
        @Query("memberId")  memberId:  Int?    = null,
        @Query("startDate") startDate: String? = null,
        @Query("endDate")   endDate:   String? = null,
    ): Response<List<Payment>>

    @POST("payments")
    suspend fun createPayment(@Body request: CreatePaymentRequest): Response<Payment>

    // ── Dashboard ─────────────────────────────────────────────────────────────
    @GET("dashboard/stats")
    suspend fun getDashboardStats(): Response<DashboardStats>
}
