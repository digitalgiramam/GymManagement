package com.gymmanager.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.gymmanager.databinding.FragmentDashboardBinding
import com.gymmanager.gymApp
import com.gymmanager.utils.NetworkResult
import com.gymmanager.utils.hide
import com.gymmanager.utils.show
import com.gymmanager.utils.showSnackbarError
import com.gymmanager.utils.toCurrencyString

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by lazy {
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return DashboardViewModel(requireContext().gymApp.dashboardRepository) as T
            }
        })[DashboardViewModel::class.java]
    }

    private val activityAdapter by lazy { RecentActivityAdapter() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvRecentActivity.adapter = activityAdapter
        binding.swipeRefresh.setOnRefreshListener { viewModel.loadStats() }

        viewModel.stats.observe(viewLifecycleOwner) { result ->
            binding.swipeRefresh.isRefreshing = false
            when (result) {
                is NetworkResult.Loading -> binding.progressBar.show()
                is NetworkResult.Success -> {
                    binding.progressBar.hide()
                    val s      = result.data
                    val symbol = requireContext().gymApp.tokenManager.getCurrencySymbol()
                    binding.tvActiveCount.text   = s.totalActiveMembers.toString()
                    binding.tvInactiveCount.text = s.totalInactiveMembers.toString()
                    binding.tvCheckIns.text      = s.todayCheckIns.toString()
                    binding.tvRevenue?.text      = s.currentMonthRevenue.toCurrencyString(symbol)
                    binding.tvExpenses?.text     = s.currentMonthExpenses.toCurrencyString(symbol)
                    binding.tvNetProfit?.text    = s.netProfit.toCurrencyString(symbol)

                    val checkIns = s.last5CheckIns.map {
                        ActivityItem.CheckIn(it.id, it.member?.fullName ?: "—", it.checkedInAt)
                    }
                    activityAdapter.submitList(checkIns)
                }
                is NetworkResult.Error -> {
                    binding.progressBar.hide()
                    binding.root.showSnackbarError(result.message)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadStats()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
