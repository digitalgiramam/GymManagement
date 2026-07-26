package com.gymmanager.ui.staff

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.gymmanager.data.model.CreateStaffRequest
import com.gymmanager.data.model.Staff
import com.gymmanager.databinding.DialogAddStaffBinding
import com.gymmanager.databinding.FragmentStaffBinding
import com.gymmanager.gymApp
import com.gymmanager.utils.NetworkResult
import com.gymmanager.utils.hide
import com.gymmanager.utils.show
import com.gymmanager.utils.showSnackbarError

class StaffFragment : Fragment() {

    private var _binding: FragmentStaffBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StaffViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return StaffViewModel(requireContext().gymApp.staffRepository) as T
            }
        }
    }

    private lateinit var adapter: StaffAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStaffBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = StaffAdapter(onDelete = { staff -> confirmDelete(staff) })
        binding.rvStaff.adapter = adapter

        binding.fabAddStaff.setOnClickListener { showAddStaffDialog() }

        binding.swipeRefresh.setOnRefreshListener { viewModel.loadStaff() }

        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadStaff()
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { binding.swipeRefresh.isRefreshing = it }

        viewModel.staff.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            if (list.isEmpty()) binding.tvEmpty.show() else binding.tvEmpty.hide()
        }

        viewModel.error.observe(viewLifecycleOwner) { msg ->
            if (msg != null) binding.root.showSnackbarError(msg)
        }

        viewModel.actionResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is NetworkResult.Error -> binding.root.showSnackbarError(result.message)
                else -> {}
            }
            if (result != null) viewModel.clearActionResult()
        }
    }

    private fun showAddStaffDialog() {
        val dialogBinding = DialogAddStaffBinding.inflate(layoutInflater)
        val roles = listOf("RECEPTIONIST", "TRAINER", "OWNER")
        dialogBinding.spinnerRole.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, roles
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        AlertDialog.Builder(requireContext())
            .setTitle("Add Staff Member")
            .setView(dialogBinding.root)
            .setPositiveButton("Add") { _, _ ->
                val name  = dialogBinding.etStaffName.text?.toString() ?: ""
                val email = dialogBinding.etStaffEmail.text?.toString() ?: ""
                val phone = dialogBinding.etStaffPhone.text?.toString()
                val role  = roles[dialogBinding.spinnerRole.selectedItemPosition]
                val notes = dialogBinding.etStaffNotes.text?.toString()

                if (name.isBlank())  { binding.root.showSnackbarError("Name is required"); return@setPositiveButton }
                if (email.isBlank()) { binding.root.showSnackbarError("Email is required"); return@setPositiveButton }

                val password = dialogBinding.etStaffPassword.text?.toString()?.trim()
                viewModel.addStaff(
                    CreateStaffRequest(
                        fullName = name,
                        email    = email,
                        phone    = phone?.ifBlank { null },
                        role     = role,
                        notes    = notes?.ifBlank { null },
                        password = password?.ifBlank { null },
                    )
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(staff: Staff) {
        AlertDialog.Builder(requireContext())
            .setTitle("Remove Staff Member")
            .setMessage("Remove ${staff.fullName} from your team?")
            .setPositiveButton("Remove") { _, _ -> viewModel.deleteStaff(staff.id) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
