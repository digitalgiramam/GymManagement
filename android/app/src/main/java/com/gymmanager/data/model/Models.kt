package com.gymmanager.data.model

// ─────────────────────────────────────────────────────────────────────────────
//  Auth — email / password
// ─────────────────────────────────────────────────────────────────────────────

data class LoginRequest(
    val email: String,
    val password: String,
)

data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String,
)

data class AuthResponse(
    val token: String,
    val user: AuthUser,
)

data class AuthUser(
    val id: Int,
    val email: String?,
    val name: String,
    val tenantId: Int?,   // null until onboarding complete
    val role: String = "OWNER",   // "OWNER" | "STAFF" | "MEMBER"
)

// ─────────────────────────────────────────────────────────────────────────────
//  Onboarding
// ─────────────────────────────────────────────────────────────────────────────

data class CreateGymRequest(
    val gymName: String,
    val address: String? = null,
    val phone: String? = null,
    val currencySymbol: String = "$",
)

data class CreateGymResponse(
    val token: String,
    val tenant: TenantSummary,
)

data class TenantSummary(
    val id: Int,
    val name: String,
    val address: String?,
    val phone: String?,
    val currencySymbol: String,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Settings (Tenant)
// ─────────────────────────────────────────────────────────────────────────────

data class TenantSettings(
    val id: Int,
    val name: String,
    val address: String?,
    val phone: String?,
    val contactPerson: String?,
    val currencySymbol: String,
    val checkInWindowMinutes: Int,
    val taxRate: Double,
    val logoBase64: String?,
    val createdAt: String,
)

data class UpdateSettingsRequest(
    val name: String? = null,
    val address: String? = null,
    val phone: String? = null,
    val contactPerson: String? = null,
    val currencySymbol: String? = null,
    val checkInWindowMinutes: Int? = null,
    val taxRate: Double? = null,
    val logoBase64: String? = null,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Plan
// ─────────────────────────────────────────────────────────────────────────────

data class Plan(
    val id: Int,
    val tenantId: Int,
    val name: String,
    val durationDays: Int,
    val fee: Double,
    val isActive: Boolean,
    val createdAt: String,
)

data class CreatePlanRequest(
    val name: String,
    val durationDays: Int,
    val fee: Double,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Member
// ─────────────────────────────────────────────────────────────────────────────

data class Member(
    val id: Int,
    val tenantId: Int,
    val fullName: String,
    val phone: String,
    val email: String?,
    val location: String?,
    val joinDate: String,
    val planId: Int,
    val plan: Plan?,
    val status: String,         // "Active" | "Inactive"
    val createdAt: String,
    val lastPaymentDate: String?,
    val membershipExpiry: String?,
    val daysUntilExpiry: Int?,
    val walletBalance: Double = 0.0,   // advance payment credit
)

data class MemberDetail(
    val id: Int,
    val tenantId: Int,
    val fullName: String,
    val phone: String,
    val email: String?,
    val location: String?,
    val joinDate: String,
    val planId: Int,
    val plan: Plan?,
    val status: String,
    val createdAt: String,
    val lastPaymentDate: String?,
    val membershipExpiry: String?,
    val daysUntilExpiry: Int?,
    val attendance: List<Attendance>,
    val payments: List<Payment>,
)

data class CreateMemberRequest(
    val fullName: String,
    val phone: String,
    val email: String? = null,
    val location: String? = null,
    val planId: Int,
    val status: String = "Active",
    val joinDate: String? = null,
)

data class UpdateMemberRequest(
    val fullName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val location: String? = null,
    val planId: Int? = null,
    val status: String? = null,
    val joinDate: String? = null,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Attendance
// ─────────────────────────────────────────────────────────────────────────────

data class Attendance(
    val id: Int,
    val tenantId: Int,
    val memberId: Int,
    val member: MemberSummary?,
    val checkedInAt: String,
    val checkedOutAt: String? = null,   // null = still checked in
)

data class CheckInRequest(
    val memberId: Int,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Payment
// ─────────────────────────────────────────────────────────────────────────────

data class PaymentMethod(
    val id: Int,
    val tenantId: Int,
    val name: String,
    val isActive: Boolean,
)

data class PaymentMethodSummary(
    val id: Int,
    val name: String,
)

data class Payment(
    val id: Int,
    val tenantId: Int,
    val memberId: Int,
    val member: MemberSummary?,
    val amount: Double,
    val methodId: Int,
    val method: PaymentMethodSummary?,
    val notes: String?,
    val paymentDate: String,
    /**
     * Signed wallet delta applied when this payment was recorded.
     *   > 0  member overpaid  → credit
     *   < 0  member underpaid → debt
     *   = 0  exact payment
     */
    val walletAdjustment: Double = 0.0,
)

data class CreatePaymentRequest(
    val memberId: Int,
    val amount: Double,
    val methodId: Int,
    val notes: String? = null,
    val paymentDate: String? = null,
)

data class UpdatePaymentRequest(
    val amount: Double? = null,
    val methodId: Int? = null,
    val notes: String? = null,
)

data class WalletBalance(
    val memberId: Int,
    val fullName: String,
    val walletBalance: Double,
)

data class CreatePaymentMethodRequest(
    val name: String,
    val isActive: Boolean = true,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Expense
// ─────────────────────────────────────────────────────────────────────────────

data class ExpenseCategory(
    val id: Int,
    val tenantId: Int,
    val name: String,
)

data class ExpenseCategorySummary(
    val id: Int,
    val name: String,
)

data class Expense(
    val id: Int,
    val tenantId: Int,
    val title: String,
    val categoryId: Int,
    val category: ExpenseCategorySummary?,
    val amount: Double,
    val expenseDate: String,
    val notes: String?,
)

data class CreateExpenseRequest(
    val title: String,
    val categoryId: Int,
    val amount: Double,
    val expenseDate: String? = null,
    val notes: String? = null,
)

data class CreateExpenseCategoryRequest(
    val name: String,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Staff
// ─────────────────────────────────────────────────────────────────────────────

data class Staff(
    val id: Int,
    val tenantId: Int,
    val fullName: String,
    val email: String,
    val phone: String?,
    val role: String,   // "OWNER" | "RECEPTIONIST" | "TRAINER"
    val notes: String?,
    val createdAt: String,
)

data class CreateStaffRequest(
    val fullName: String,
    val email: String,
    val phone: String? = null,
    val role: String = "RECEPTIONIST",
    val notes: String? = null,
    val password: String? = null,  // optional login password
)

data class UpdateStaffRequest(
    val fullName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val role: String? = null,
    val notes: String? = null,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Dashboard
// ─────────────────────────────────────────────────────────────────────────────

data class DashboardStats(
    val totalActiveMembers: Int,
    val totalInactiveMembers: Int,
    val todayCheckIns: Int,
    val currentMonthRevenue: Double,
    val currentMonthExpenses: Double,
    val netProfit: Double,
    val last5CheckIns: List<Attendance>,
    val last5Payments: List<Payment>,
    val last5Expenses: List<Expense>,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Shared / embedded models
// ─────────────────────────────────────────────────────────────────────────────

data class MemberSummary(
    val id: Int,
    val fullName: String,
    val phone: String,
)

data class ApiError(
    val error: String,
    val code: String? = null,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Member Portal — self-service models
// ─────────────────────────────────────────────────────────────────────────────

data class MemberProfile(
    val id: Int,
    val fullName: String,
    val phone: String,
    val email: String?,
    val location: String?,
    val joinDate: String,
    val status: String,
    val plan: Plan?,
    val membershipExpiry: String?,
    val daysUntilExpiry: Int?,
)
