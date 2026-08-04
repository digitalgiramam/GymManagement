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
    /** Gym's configured currency symbol — same value shown in Gym Setup. */
    val currencySymbol: String? = null,
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
    val status: String,              // "Active" | "Inactive"
    val createdAt: String,
    val lastPaymentDate: String?,
    val membershipExpiry: String?,
    val daysUntilExpiry: Int?,
    /** "Full Paid" | "Partial Paid" | "Not Paid" */
    val paymentStatus: String?       = null,
    val lastPaymentAmount: Double?   = null,
    val lastPlanFee: Double?         = null,
    val overdueAmount: Double?       = null,
    /** Trainer assigned to this member (null = unassigned) */
    val trainerId: Int?              = null,
    val trainerName: String?         = null,
    /** Height in cm — used to compute BMI in progress tracking. */
    val heightCm: Double?            = null,
    val dateOfBirth: String?          = null,
    val gender: String?               = null,
    val bloodGroup: String?           = null,
    val emergencyContactName: String? = null,
    val emergencyContactPhone: String? = null,
    val referralSource: String?       = null,
    /** Medical conditions, injuries, allergies, regular medication — free text. */
    val healthNotes: String?          = null,
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
    /** "Full Paid" | "Partial Paid" | "Not Paid" */
    val paymentStatus: String?       = null,
    val lastPaymentAmount: Double?   = null,
    val lastPlanFee: Double?         = null,
    val overdueAmount: Double?       = null,
    val trainerId: Int?              = null,
    val trainerName: String?         = null,
    val heightCm: Double?            = null,
    val dateOfBirth: String?          = null,
    val gender: String?               = null,
    val bloodGroup: String?           = null,
    val emergencyContactName: String? = null,
    val emergencyContactPhone: String? = null,
    val referralSource: String?       = null,
    val healthNotes: String?          = null,
)

data class CreateMemberRequest(
    val fullName: String,
    val phone: String,
    val email: String? = null,
    val location: String? = null,
    val planId: Int,
    val status: String = "Active",
    val joinDate: String? = null,
    /** Trainer to assign this member to (null = unassigned) */
    val trainerId: Int? = null,
    /** Optional login password for member portal access */
    val password: String? = null,
    val heightCm: Double? = null,
    val dateOfBirth: String? = null,
    val gender: String? = null,
    val bloodGroup: String? = null,
    val emergencyContactName: String? = null,
    val emergencyContactPhone: String? = null,
    val referralSource: String? = null,
    val healthNotes: String? = null,
)

data class UpdateMemberRequest(
    val fullName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val location: String? = null,
    val planId: Int? = null,
    val status: String? = null,
    val joinDate: String? = null,
    val trainerId: Int? = null,
    val password: String? = null,
    val heightCm: Double? = null,
    val dateOfBirth: String? = null,
    val gender: String? = null,
    val bloodGroup: String? = null,
    val emergencyContactName: String? = null,
    val emergencyContactPhone: String? = null,
    val referralSource: String? = null,
    val healthNotes: String? = null,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Progress Tracking — weight/BMI/measurements + goals
// ─────────────────────────────────────────────────────────────────────────────

data class ProgressEntry(
    val id: Int,
    val tenantId: Int,
    val memberId: Int,
    val recordedByStaffId: Int?,
    /** Name of the trainer/staff who logged this, or null if the member self-logged it. */
    val recordedByName: String?,
    val entryDate: String,
    val weightKg: Double?,
    /** Computed server-side from weight + the member's stored height. Null if height isn't set. */
    val bmi: Double?,
    val chestCm: Double?,
    val waistCm: Double?,
    val hipsCm: Double?,
    val armsCm: Double?,
    val thighsCm: Double?,
    val notes: String?,
    val createdAt: String,
)

data class ProgressEntryRequest(
    val entryDate: String? = null,
    val weightKg: Double? = null,
    val chestCm: Double? = null,
    val waistCm: Double? = null,
    val hipsCm: Double? = null,
    val armsCm: Double? = null,
    val thighsCm: Double? = null,
    val notes: String? = null,
)

data class Goal(
    val id: Int,
    val tenantId: Int,
    val memberId: Int,
    val goalType: String,   // "WEIGHT" | "MEASUREMENT" | "CUSTOM"
    val description: String,
    val targetWeightKg: Double?,
    val targetDate: String?,
    val status: String,     // "ACTIVE" | "ACHIEVED" | "ABANDONED"
    val createdAt: String,
    val achievedAt: String?,
)

data class GoalRequest(
    val goalType: String = "CUSTOM",
    val description: String,
    val targetWeightKg: Double? = null,
    val targetDate: String? = null,
)

data class GoalStatusRequest(
    val status: String,   // "ACTIVE" | "ACHIEVED" | "ABANDONED"
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
    val planId: Int?,
    /** Name of the subscription plan covered by this payment (e.g. "Monthly"). */
    val planName: String?,
    val planDurationDays: Int,
    /** Plan fee snapshotted at payment time — used for partial-payment detection. */
    val planFee: Double?,
    /** Amount actually paid in this transaction. */
    val amount: Double,
    /** Outstanding balance = planFee - amount (0 if fully paid or expired). */
    val overdueAmount: Double?,
    val methodId: Int,
    val method: PaymentMethodSummary?,
    val notes: String?,
    val paymentDate: String,
    /** New membership expiry date set by this payment. */
    val membershipExtendedTo: String?,
    /** "Active" | "Partial" | "Overdue" */
    val membershipStatus: String?,
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
    /** Set a new login password — null = leave unchanged */
    val password: String? = null,
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
    /** "Full Paid" | "Partial Paid" | "Not Paid" */
    val paymentStatus: String?,
    /** Outstanding balance (0 if fully paid or no active subscription). */
    val overdueAmount: Double?,
    /** Amount paid in the latest payment. */
    val lastPaymentAmount: Double?,
    /** Plan fee at the time of the last payment. */
    val lastPlanFee: Double?,
)
