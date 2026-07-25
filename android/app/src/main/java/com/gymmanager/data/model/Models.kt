package com.gymmanager.data.model

import com.google.gson.annotations.SerializedName

// ─────────────────────────────────────────────────────────────────────────────
//  Auth
// ─────────────────────────────────────────────────────────────────────────────

data class LoginRequest(
    val username: String,
    val password: String,
)

data class LoginResponse(
    val token: String,
    val username: String,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Plan
// ─────────────────────────────────────────────────────────────────────────────

data class Plan(
    val id: Int,
    val name: String,
    val durationDays: Int,
    val fee: Double,
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
    val fullName: String,
    val phone: String,
    val email: String?,
    val joinDate: String,
    val planId: Int,
    val plan: Plan?,
    val status: String,   // "Active" | "Inactive"
    val createdAt: String,
)

data class MemberDetail(
    val id: Int,
    val fullName: String,
    val phone: String,
    val email: String?,
    val joinDate: String,
    val planId: Int,
    val plan: Plan?,
    val status: String,
    val createdAt: String,
    val attendance: List<Attendance>,
    val payments: List<Payment>,
)

data class CreateMemberRequest(
    val fullName: String,
    val phone: String,
    val email: String?,
    val planId: Int,
    val status: String = "Active",
)

data class UpdateMemberRequest(
    val fullName: String?,
    val phone: String?,
    val email: String?,
    val planId: Int?,
    val status: String?,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Attendance
// ─────────────────────────────────────────────────────────────────────────────

data class Attendance(
    val id: Int,
    val memberId: Int,
    val member: MemberSummary?,
    val checkedInAt: String,
)

data class CheckInRequest(
    val memberId: Int,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Payment
// ─────────────────────────────────────────────────────────────────────────────

data class Payment(
    val id: Int,
    val memberId: Int,
    val member: MemberSummary?,
    val amount: Double,
    val method: String,   // "Cash" | "Card" | "Transfer"
    val notes: String?,
    val paymentDate: String,
)

data class CreatePaymentRequest(
    val memberId: Int,
    val amount: Double,
    val method: String,
    val notes: String?,
    val paymentDate: String?,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Dashboard
// ─────────────────────────────────────────────────────────────────────────────

data class DashboardStats(
    val totalActiveMembers: Int,
    val totalInactiveMembers: Int,
    val todayCheckIns: Int,
    val currentMonthRevenue: Double,
    val last5CheckIns: List<Attendance>,
    val last5Payments: List<Payment>,
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
)
