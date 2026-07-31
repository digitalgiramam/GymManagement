package com.gymmanager.ui.member

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.gymmanager.databinding.ActivityMemberPortalBinding
import com.gymmanager.gymApp
import com.gymmanager.ui.auth.LoginActivity
import com.gymmanager.utils.NetworkResult
import com.gymmanager.utils.hide
import com.gymmanager.utils.show
import com.gymmanager.utils.showSnackbarError
import java.text.SimpleDateFormat
import java.util.*

/**
 * Self-service portal for gym members.
 * Shows: profile, membership expiry, and attendance history.
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMemberPortalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvAttendance.adapter = attendanceAdapter

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
                    val p = result.data
                    binding.tvName.text       = p.fullName
                    binding.tvPhone.text      = p.phone
                    binding.tvEmail.text      = p.email ?: "—"
                    binding.tvPlan.text       = p.plan?.name ?: "—"
                    binding.tvStatus.text     = p.status

                    val expiry = p.daysUntilExpiry
                    binding.tvExpiry.text = when {
                        expiry == null -> "—"
                        expiry < 0    -> "Expired ${-expiry} days ago"
                        expiry == 0   -> "Expires today"
                        else          -> "Expires in $expiry days"
                    }
                    binding.tvExpiryDate.text = p.membershipExpiry?.let {
                        try {
                            val inFmt  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).also { f -> f.timeZone = TimeZone.getTimeZone("UTC") }
                            val outFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                            outFmt.format(inFmt.parse(it) ?: Date())
                        } catch (_: Exception) { it }
                    } ?: ""
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
    }

    private fun logout() {
        gymApp.authRepository.logout()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
