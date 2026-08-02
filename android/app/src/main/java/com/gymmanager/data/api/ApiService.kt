package com.gymmanager.data.api

import com.gymmanager.data.model.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit interface mapping to every backend endpoint.
 * The Authorization header is injected by [AuthInterceptor] in [RetrofitClient].
 */
interface ApiService {

    // ── Auth ──────────────────────────────────────────────────────────────────
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("auth/staff-login")
    suspend fun staffLogin(@Body request: LoginRequest): Response<AuthResponse>

    @POST("auth/member-login")
    suspend fun memberLogin(@Body request: LoginRequest): Response<AuthResponse>

    // ── Member Portal (self-service) ───────────────────────────────────────
    @GET("member-portal/me")
    suspend fun getMemberProfile(): Response<MemberProfile>

    @GET("member-portal/me/attendance")
    suspend fun getMemberAttendance(): Response<List<Attendance>>

    @GET("member-portal/me/payments")
    suspend fun getMemberPayments(): Response<List<Payment>>

    @GET("member-portal/me/progress")
    suspend fun getMyProgress(): Response<List<ProgressEntry>>

    @POST("member-portal/me/progress")
    suspend fun addMyProgress(@Body request: ProgressEntryRequest): Response<ProgressEntry>

    @DELETE("member-portal/me/progress/{entryId}")
    suspend fun deleteMyProgress(@Path("entryId") entryId: Int): Response<Unit>

    @GET("member-portal/me/goals")
    suspend fun getMyGoals(): Response<List<Goal>>

    @POST("member-portal/me/goals")
    suspend fun addMyGoal(@Body request: GoalRequest): Response<Goal>

    @PUT("member-portal/me/goals/{goalId}")
    suspend fun updateMyGoal(@Path("goalId") goalId: Int, @Body request: GoalStatusRequest): Response<Goal>

    @DELETE("member-portal/me/goals/{goalId}")
    suspend fun deleteMyGoal(@Path("goalId") goalId: Int): Response<Unit>

    // ── Onboarding ────────────────────────────────────────────────────────────
    @POST("onboarding/create-gym")
    suspend fun createGym(@Body request: CreateGymRequest): Response<CreateGymResponse>

    // ── Settings ─────────────────────────────────────────────────────────────
    @GET("settings")
    suspend fun getSettings(): Response<TenantSettings>

    @PUT("settings")
    suspend fun updateSettings(@Body request: UpdateSettingsRequest): Response<TenantSettings>

    // ── Members ───────────────────────────────────────────────────────────────
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

    // ── Member Progress Tracking (staff/trainer recording on behalf of a member) ─
    @GET("members/{memberId}/progress")
    suspend fun getMemberProgress(@Path("memberId") memberId: Int): Response<List<ProgressEntry>>

    @POST("members/{memberId}/progress")
    suspend fun addMemberProgress(
        @Path("memberId") memberId: Int,
        @Body request: ProgressEntryRequest,
    ): Response<ProgressEntry>

    @DELETE("members/{memberId}/progress/{entryId}")
    suspend fun deleteMemberProgress(
        @Path("memberId") memberId: Int,
        @Path("entryId") entryId: Int,
    ): Response<Unit>

    @GET("members/{memberId}/goals")
    suspend fun getMemberGoals(@Path("memberId") memberId: Int): Response<List<Goal>>

    @POST("members/{memberId}/goals")
    suspend fun addMemberGoal(
        @Path("memberId") memberId: Int,
        @Body request: GoalRequest,
    ): Response<Goal>

    @PUT("members/{memberId}/goals/{goalId}")
    suspend fun updateMemberGoal(
        @Path("memberId") memberId: Int,
        @Path("goalId") goalId: Int,
        @Body request: GoalStatusRequest,
    ): Response<Goal>

    @DELETE("members/{memberId}/goals/{goalId}")
    suspend fun deleteMemberGoal(
        @Path("memberId") memberId: Int,
        @Path("goalId") goalId: Int,
    ): Response<Unit>

    // ── Plans ─────────────────────────────────────────────────────────────────
    @GET("plans")
    suspend fun getPlans(): Response<List<Plan>>

    @POST("plans")
    suspend fun createPlan(@Body request: CreatePlanRequest): Response<Plan>

    @PUT("plans/{id}")
    suspend fun updatePlan(
        @Path("id") id: Int,
        @Body request: CreatePlanRequest,
    ): Response<Plan>

    @DELETE("plans/{id}")
    suspend fun deletePlan(@Path("id") id: Int): Response<Unit>

    // ── Attendance ────────────────────────────────────────────────────────────
    @GET("attendance")
    suspend fun getTodayAttendance(): Response<List<Attendance>>

    @POST("attendance")
    suspend fun checkIn(@Body request: CheckInRequest): Response<Attendance>

    @PUT("attendance/{id}/checkout")
    suspend fun checkOut(@Path("id") attendanceId: Int): Response<Attendance>

    // ── Expenses ──────────────────────────────────────────────────────────────
    @GET("expenses/categories")
    suspend fun getExpenseCategories(): Response<List<ExpenseCategory>>

    @POST("expenses/categories")
    suspend fun createExpenseCategory(@Body request: CreateExpenseCategoryRequest): Response<ExpenseCategory>

    @GET("expenses")
    suspend fun getExpenses(
        @Query("categoryId") categoryId: Int?    = null,
        @Query("startDate")  startDate:  String? = null,
        @Query("endDate")    endDate:    String? = null,
    ): Response<List<Expense>>

    @POST("expenses")
    suspend fun createExpense(@Body request: CreateExpenseRequest): Response<Expense>

    @PUT("expenses/{id}")
    suspend fun updateExpense(
        @Path("id") id: Int,
        @Body request: CreateExpenseRequest,
    ): Response<Expense>

    @DELETE("expenses/{id}")
    suspend fun deleteExpense(@Path("id") id: Int): Response<Unit>

    // ── Staff ─────────────────────────────────────────────────────────────────
    @GET("staff")
    suspend fun getStaff(): Response<List<Staff>>

    @POST("staff")
    suspend fun createStaff(@Body request: CreateStaffRequest): Response<Staff>

    @PUT("staff/{id}")
    suspend fun updateStaff(
        @Path("id") id: Int,
        @Body request: UpdateStaffRequest,
    ): Response<Staff>

    @DELETE("staff/{id}")
    suspend fun deleteStaff(@Path("id") id: Int): Response<Unit>

    // ── Payments ─────────────────────────────────────────────────────────────
    @GET("payments/methods")
    suspend fun getPaymentMethods(): Response<List<PaymentMethod>>

    @POST("payments/methods")
    suspend fun createPaymentMethod(@Body request: CreatePaymentMethodRequest): Response<PaymentMethod>

    @GET("payments/expiring")
    suspend fun getExpiringMembers(@Query("days") days: Int = 30): Response<List<Member>>

    @GET("payments")
    suspend fun getPayments(
        @Query("memberId")  memberId:  Int?    = null,
        @Query("startDate") startDate: String? = null,
        @Query("endDate")   endDate:   String? = null,
    ): Response<List<Payment>>

    @POST("payments")
    suspend fun createPayment(@Body request: CreatePaymentRequest): Response<Payment>

    @PUT("payments/{id}")
    suspend fun updatePayment(
        @Path("id") id: Int,
        @Body request: UpdatePaymentRequest,
    ): Response<Payment>

    @DELETE("payments/{id}")
    suspend fun deletePayment(@Path("id") id: Int): Response<Unit>

    // ── Dashboard ─────────────────────────────────────────────────────────────
    @GET("dashboard/stats")
    suspend fun getDashboardStats(): Response<DashboardStats>

    // ── Staff Portal (TRAINER / RECEPTIONIST) ─────────────────────────────────
    /** Members assigned to the logged-in trainer */
    @GET("staff-portal/my-members")
    suspend fun getMyMembers(): Response<List<Member>>

    /** Today's attendance for the trainer's assigned members */
    @GET("staff-portal/my-attendance")
    suspend fun getMyAttendance(): Response<List<Attendance>>

    /** Mark check-in via staff portal (same as /attendance but no requireTenant guard) */
    @POST("staff-portal/attendance")
    suspend fun staffPortalCheckIn(@Body request: CheckInRequest): Response<Attendance>
}
