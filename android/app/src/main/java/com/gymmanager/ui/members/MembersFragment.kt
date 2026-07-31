package com.gymmanager.ui.members

import android.app.DatePickerDialog
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
import com.gymmanager.data.model.Member
import com.gymmanager.data.model.Plan
import com.gymmanager.data.model.UpdateMemberRequest
import com.gymmanager.databinding.DialogAddMemberBinding
import com.gymmanager.databinding.DialogEditMemberBinding
import com.gymmanager.databinding.FragmentMembersBinding
import com.gymmanager.gymApp
import com.gymmanager.utils.NetworkResult
import com.gymmanager.utils.hide
import com.gymmanager.utils.toCurrencyString
import com.gymmanager.utils.show
import com.gymmanager.utils.showSnackbar
import com.gymmanager.utils.showSnackbarError
import java.text.SimpleDateFormat
import java.util.*

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
            onItemLongClick  = { member -> showMemberOptions(member) },
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

        viewModel.plans.observe(viewLifecycleOwner) { result ->
            when (result) {
                is NetworkResult.Success -> availablePlans = result.data
                is NetworkResult.Error   -> binding.root.showSnackbarError("Plans failed to load: ${result.message}")
                else -> Unit
            }
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
                is NetworkResult.Success -> binding.root.showSnackbar(getString(R.string.msg_member_updated))
                is NetworkResult.Error   -> binding.root.showSnackbarError(result.message)
                else -> Unit
            }
        }
    }

    // ── Long-press options menu ────────────────────────────────────────────
    private fun showMemberOptions(member: Member) {
        val options = arrayOf(getString(R.string.action_edit), getString(R.string.action_delete))
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(member.fullName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditMemberDialog(member)
                    1 -> confirmDelete(member.id, member.fullName)
                }
            }
            .show()
    }

    // ── Edit member dialog ─────────────────────────────────────────────────
    private fun showEditMemberDialog(member: Member) {
        if (availablePlans.isEmpty()) {
            binding.root.showSnackbarError("No plans available.")
            return
        }

        val db = DialogEditMemberBinding.inflate(layoutInflater)
        val isoFmt  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val dispFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        // Pre-fill existing values
        db.etFullName.setText(member.fullName)
        db.etPhone.setText(member.phone)
        db.etEmail.setText(member.email ?: "")
        db.etLocation.setText(member.location ?: "")
        db.switchActive.isChecked = member.status == "Active"

        // Parse stored joinDate for display
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        try { cal.time = isoFmt.parse(member.joinDate) ?: Date() } catch (_: Exception) {}
        db.etJoinDate.setText(dispFmt.format(cal.time))

        // Plan spinner — select current plan
        val planNames = availablePlans.map { "${it.name} (${it.durationDays}d)" }
        db.spinnerPlan.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, planNames
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        val currentPlanIdx = availablePlans.indexOfFirst { it.id == member.planId }.coerceAtLeast(0)
        db.spinnerPlan.setSelection(currentPlanIdx)

        // Join date picker
        db.etJoinDate.setOnClickListener {
            DatePickerDialog(
                requireContext(),
                { _, y, m, d ->
                    cal.set(y, m, d, 0, 0, 0); cal.set(Calendar.MILLISECOND, 0)
                    db.etJoinDate.setText(dispFmt.format(cal.time))
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH),
            ).show()
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.title_edit_member)
            .setView(db.root)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val fullName = db.etFullName.text?.toString()?.trim() ?: ""
                val phone    = db.etPhone.text?.toString()?.trim() ?: ""
                if (fullName.isBlank() || phone.isBlank()) {
                    binding.root.showSnackbarError("Name and phone are required.")
                    return@setPositiveButton
                }
                val email    = db.etEmail.text?.toString()?.trim()
                val location = db.etLocation.text?.toString()?.trim()
                val status   = if (db.switchActive.isChecked) "Active" else "Inactive"
                val plan     = availablePlans[db.spinnerPlan.selectedItemPosition]

                viewModel.updateMember(
                    member.id,
                    UpdateMemberRequest(
                        fullName = fullName,
                        phone    = phone,
                        email    = email?.ifBlank { null },
                        location = location?.ifBlank { null },
                        planId   = plan.id,
                        status   = status,
                        joinDate = isoFmt.format(cal.time),
                    )
                )
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showAddMemberDialog() {
        if (availablePlans.isEmpty()) {
            binding.root.showSnackbarError("No plans available. Create a plan first.")
            return
        }
        val dialogBinding = DialogAddMemberBinding.inflate(layoutInflater)

        // Populate plan spinner
        val symbol    = requireContext().gymApp.tokenManager.getCurrencySymbol()
        val planNames = availablePlans.map { "${it.name} (${it.durationDays}d – ${it.fee.toCurrencyString(symbol)})" }
        dialogBinding.spinnerPlan.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, planNames
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.title_add_member)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val fullName  = dialogBinding.etFullName.text?.toString()?.trim() ?: ""
                val phone     = dialogBinding.etPhone.text?.toString()?.trim() ?: ""
                val email     = dialogBinding.etEmail.text?.toString()?.trim()
                val location  = dialogBinding.etLocation.text?.toString()?.trim()
                val selectedPlan = availablePlans[dialogBinding.spinnerPlan.selectedItemPosition]
                val status    = if (dialogBinding.switchActive.isChecked) "Active" else "Inactive"

                if (fullName.isBlank() || phone.isBlank()) {
                    binding.root.showSnackbarError("Name and phone are required.")
                    return@setPositiveButton
                }

                viewModel.createMember(
                    CreateMemberRequest(
                        fullName = fullName,
                        phone    = phone,
                        email    = email?.ifBlank { null },
                        location = location?.ifBlank { null },
                        planId   = selectedPlan.id,
                        status   = status,
                    )
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

    override fun onResume() {
        super.onResume()
        viewModel.loadMembers()
        viewModel.loadPlans()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
