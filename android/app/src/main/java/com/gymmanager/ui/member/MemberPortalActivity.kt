package com.gymmanager.ui.member

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.gymmanager.R
import com.gymmanager.databinding.ActivityMemberPortalBinding
import com.gymmanager.gymApp
import com.gymmanager.ui.auth.LoginActivity
import com.gymmanager.utils.NetworkResult
import com.gymmanager.utils.hide
import com.gymmanager.utils.show
import com.gymmanager.utils.showSnackbarError
import com.gymmanager.utils.toCurrencyString
import java.text.SimpleDateFormat
import java.util.*

/**
 * Self-service portal for gym members.
 * Shows: profile, membership expiry, payment status, payment history,
 * and attendance history.
 */
class MemberPortalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMemberPortalBinding

    private val viewModel: MemberPortalViewModel by lazy {
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return MemberPortalViewModel(gymApp.memberPortalRepository) as T
            }
        })[MemberPortalViewModel::class.java]
    }

    private val attendanceAdapter = MemberAttendanceAdapter()
    private val paymentAdapter    = MemberPaymentAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMemberPortalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvAttendance.adapter = attendanceAdapter
        binding.rvPayments.adapter   = paymentAdapter

        binding.btnLogout.setOnClickListener { logout() }
        binding.swipeRefresh.setOnRefreshListener { viewModel.loadAll() }

        observeViewModel()
        viewModel.loadAll()
    }

    private fun observeViewModel() {
        viewModel.profile.observe(this) { result ->
            binding.swipeRefresh.isRefreshing = false
            when (result) {
                is NetworkResult.Loading -> binding.progressBar.show()
                is NetworkResult.Success -> {
                    binding.progressBar.hide()
                    val p      = result.data
                    val symbol = gymApp.tokenManager.getCurrencySymbol()

                    // ── Profile card ─────────────────────────────────────
                    binding.tvName.text   = p.fullName
                    binding.tvPhone.text  = p.phone
                    binding.tvEmail.text  = p.email ?: "—"
                    binding.tvPlan.text   = p.plan?.name ?: "—"
                    binding.tvStatus.text = p.status

                    val expiry = p.daysUntilExpiry
                    binding.tvExpiry.text = when {
                        expiry == null -> "—"
                        expiry < 0    -> "Expired ${-expiry} days ago"
                        expiry == 0   -> "Expires today"
                        else          -> "Expires in $expiry days"
                    }
                    binding.tvExpiryDate.text = p.membershipExpiry?.let { formatDate(it) } ?: ""

                    // ── Payment Status card ───────────────────────────────
                    val payStatus = p.paymentStatus ?: "Not Paid"

                    binding.tvPaymentStatusBadge.text = payStatus
                    binding.tvPaymentStatusBadge.setBackgroundResource(
                        when (payStatus) {
                            "Full Paid"    -> R.drawable.bg_status_active
                            "Partial Paid" -> R.drawable.bg_status_partial
                            else           -> R.drawable.bg_status_overdue
                        }
                    )

                    // Amount paid
                    val lastAmount = p.lastPaymentAmount
                    binding.tvLastPaymentAmount.text =
                        lastAmount?.toCurrencyString(symbol) ?: "—"

                    // Plan fee
                    val lastFee = p.lastPlanFee
                    binding.tvPlanFee.text = lastFee?.toCurrencyString(symbol) ?: "—"

                    // Balance due — only visible for Partial Paid
                    val balance = p.overdueAmount ?: 0.0
                    if (payStatus == "Partial Paid" && balance > 0) {
                        binding.rowPaymentBalance.visibility = View.VISIBLE
                        binding.tvPaymentBalance.text = balance.toCurrencyString(symbol)
                    } else {
                        binding.rowPaymentBalance.visibility = View.GONE
                    }

                    // Subscription valid-until (same as membership expiry)
                    val expiryDate = p.membershipExpiry
                    if (expiryDate != null) {
                        binding.tvPaymentExpiry.text = formatDate(expiryDate)
                        // Red if expired, green if active
                        val isExpired = try {
                            val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                            df.timeZone = TimeZone.getTimeZone("UTC")
                            val d = df.parse(expiryDate) ?: Date(0)
                            !d.after(Date())
                        } catch (_: Exception) { true }
                        binding.tvPaymentExpiry.setTextColor(
                            if (isExpired) Color.parseColor("#C62828")
                            else           Color.parseColor("#2E7D32")
                        )
                    } else {
                        binding.tvPaymentExpiry.text = "—"
                    }
                }
                is NetworkResult.Error -> {
                    binding.progressBar.hide()
                    binding.root.showSnackbarError(result.message)
                }
            }
        }

        viewModel.attendance.observe(this) { result ->
            if (result is NetworkResult.Success) attendanceAdapter.submitList(result.data)
        }

        viewModel.payments.observe(this) { result ->
            if (result is NetworkResult.Success) {
                val list = result.data
                paymentAdapter.submitList(list)
                binding.tvNoPayments.visibility =
                    if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    /** Format ISO date string → "dd MMM yyyy". Falls back to first 10 chars. */
    private fun formatDate(iso: String): String = try {
        val parserZ  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            .also { it.timeZone = TimeZone.getTimeZone("UTC") }
        val parserMs = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val display  = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val date = runCatching { parserZ.parse(iso)!! }.getOrNull() ?: parserMs.parse(iso)!!
        display.format(date)
    } catch (_: Exception) { iso.take(10) }

    private fun logout() {
        gymApp.authRepository.logout()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
