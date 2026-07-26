package com.gymmanager.ui.master

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.gymmanager.R
import com.gymmanager.databinding.FragmentMasterBinding
import com.gymmanager.gymApp
import com.gymmanager.ui.auth.LoginActivity

/**
 * "More" tab — entry point for Expenses, Staff, Settings, and Logout.
 * Each tile navigates to its own Fragment via the nav graph.
 */
class MasterFragment : Fragment() {

    private var _binding: FragmentMasterBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMasterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Show gym name and owner name
        binding.tvGymName.text   = requireContext().gymApp.tokenManager.getUserName() ?: "Your Gym"
        binding.tvOwnerEmail.text = requireContext().gymApp.tokenManager.getUserEmail() ?: ""

        binding.cardExpenses.setOnClickListener {
            findNavController().navigate(R.id.action_masterFragment_to_expensesFragment)
        }

        binding.cardStaff.setOnClickListener {
            findNavController().navigate(R.id.action_masterFragment_to_staffFragment)
        }

        binding.cardSettings.setOnClickListener {
            findNavController().navigate(R.id.action_masterFragment_to_settingsFragment)
        }

        binding.btnLogout.setOnClickListener {
            requireContext().gymApp.authRepository.logout()
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
