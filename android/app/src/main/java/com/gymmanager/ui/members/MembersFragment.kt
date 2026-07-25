package com.gymmanager.ui.members

import android.os.Bundle
import android.view.*
import android.widget.ArrayAdapter
import androidx.appcompat.widget.SearchView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gymmanager.R
import com.gymmanager.data.model.CreateMemberRequest
import com.gymmanager.data.model.Plan
import com.gymmanager.databinding.DialogAddMemberBinding
import com.gymmanager.databinding.FragmentMembersBinding
import com.gymmanager.gymApp
import com.gymmanager.utils.NetworkResult
import com.gymmanager.utils.hide
import com.gymmanager.utils.show
import com.gymmanager.utils.showSnackbar
import com.gymmanager.utils.showSnackbarError

class MembersFragment : Fragment() {

    private var _binding: FragmentMembersBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MembersViewModel by lazy {
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return MembersViewModel(
                    requireContext().gymApp.memberRepository,
                    requireContext().gymApp.planRepository,
                ) as T
            }
        })[MembersViewModel::class.java]
    }

    private lateinit var adapter: MembersAdapter
    private var availablePlans: List<Plan> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMembersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = MembersAdapter(
            onItemClick      = { member ->
                findNavController().navigate(
                    R.id.action_membersFragment_to_memberDetailFragment,
                    bundleOf("memberId" to member.id),
                )
            },
            onItemLongClick  = { member -> confirmDelete(member.id, member.fullName) },
        )
        binding.rvMembers.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { viewModel.loadMembers() }

        binding.fabAddMember.setOnClickListener { showAddMemberDialog() }

        // Search
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.loadMembers(newText)
                return true
            }
        })

        viewModel.plans.observe(viewLifecycleOwner) { plans ->
            availablePlans = plans
        }

        viewModel.members.observe(viewLifecycleOwner) { result ->
            binding.swipeRefresh.isRefreshing = false
            when (result) {
                is NetworkResult.Loading -> binding.progressBar.show()
                is NetworkResult.Success -> {
                    binding.progressBar.hide()
                    adapter.submitList(result.data)
                    binding.tvEmpty.visibility =
                        if (result.data.isEmpty()) View.VISIBLE else View.GONE
                }
                is NetworkResult.Error -> {
                    binding.progressBar.hide()
                    binding.root.showSnackbarError(result.message)
                }
            }
        }

        viewModel.actionResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is NetworkResult.Success -> binding.root.showSnackbar("Done!")
                is NetworkResult.Error   -> binding.root.showSnackbarError(result.message)
                else -> Unit
            }
        }
    }

    private fun showAddMemberDialog() {
        if (availablePlans.isEmpty()) {
            binding.root.showSnackbarError("No plans available. Create a plan first.")
            return
        }
        val dialogBinding = DialogAddMemberBinding.inflate(layoutInflater)

        // Populate plan spinner
        val planNames = availablePlans.map { "${it.name} (${it.durationDays}d – ${"%.2f".format(it.fee)})" }
        dialogBinding.spinnerPlan.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, planNames
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.title_add_member)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val fullName = dialogBinding.etFullName.text?.toString()?.trim() ?: ""
                val phone    = dialogBinding.etPhone.text?.toString()?.trim() ?: ""
                val email    = dialogBinding.etEmail.text?.toString()?.trim()
                val selectedPlan = availablePlans[dialogBinding.spinnerPlan.selectedItemPosition]
                val status   = if (dialogBinding.switchActive.isChecked) "Active" else "Inactive"

                if (fullName.isBlank() || phone.isBlank()) {
                    binding.root.showSnackbarError("Name and phone are required.")
                    return@setPositiveButton
                }

                viewModel.createMember(
                    CreateMemberRequest(fullName, phone, email?.ifBlank { null }, selectedPlan.id, status)
                )
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun confirmDelete(memberId: Int, name: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.title_delete_member)
            .setMessage(getString(R.string.msg_delete_member_confirm, name))
            .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.deleteMember(memberId) }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
