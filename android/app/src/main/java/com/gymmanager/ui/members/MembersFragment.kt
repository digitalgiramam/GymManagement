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
import com.gymmanager.data.model.Staff
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
                val app = requireContext().gymApp
                @Suppress("UNCHECKED_CAST")
                return MembersViewModel(
                    app.memberRepository,
                    app.planRepository,
                    app.staffRepository,
                ) as T
            }
        })[MembersViewModel::class.java]
    }

    private lateinit var adapter: MembersAdapter
    private var availablePlans: List<Plan>    = emptyList()
    private var availableTrainers: List<Staff> = emptyList()

    private val genderOptions      = listOf("Male", "Female", "Other")
    private val bloodGroupOptions  = listOf("Unknown", "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")
    private val referralOptions    = listOf(
        "Walk-in", "Friend/Family Referral", "Social Media",
        "Online Search", "Advertisement", "Existing Member", "Other",
    )

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
            onItemClick     = { member ->
                findNavController().navigate(
                    R.id.action_membersFragment_to_memberDetailFragment,
                    bundleOf("memberId" to member.id),
                )
            },
            onItemLongClick = { member -> showMemberOptions(member) },
        )
        binding.rvMembers.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { viewModel.loadMembers() }
        binding.fabAddMember.setOnClickListener { showAddMemberDialog() }

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

        viewModel.trainers.observe(viewLifecycleOwner) { trainers ->
            availableTrainers = trainers
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

    // ── Long-press options ─────────────────────────────────────────────────
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

    // ── Trainer spinner helpers ────────────────────────────────────────────

    /** Entries: ["(No trainer)", "Alice (TRAINER)", …] */
    private fun buildTrainerSpinnerEntries(): Array<String> {
        val none = listOf("(No trainer)")
        return (none + availableTrainers.map { it.fullName }).toTypedArray()
    }

    /** Returns the trainerIdx's Staff, or null if the first "(No trainer)" is selected. */
    private fun selectedTrainerId(position: Int): Int? =
        if (position == 0) null else availableTrainers.getOrNull(position - 1)?.id

    /** Returns the spinner position for a given trainerId (0 = no trainer). */
    private fun trainerSpinnerPosition(trainerId: Int?): Int {
        if (trainerId == null) return 0
        val idx = availableTrainers.indexOfFirst { it.id == trainerId }
        return if (idx >= 0) idx + 1 else 0
    }

    // ── Simple dropdown helpers (gender / blood group / referral source) ────
    private fun android.widget.Spinner.setSimpleOptions(options: List<String>, selected: String?) {
        adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, options,
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        val idx = options.indexOf(selected).coerceAtLeast(0)
        setSelection(idx)
    }

    private fun android.widget.Spinner.selectedOrNull(options: List<String>): String? {
        val value = options.getOrNull(selectedItemPosition)
        return if (value == "Unknown") null else value
    }

    // ── Edit member dialog ─────────────────────────────────────────────────
    private fun showEditMemberDialog(member: Member) {
        if (availablePlans.isEmpty()) {
            binding.root.showSnackbarError("No plans available.")
            return
        }

        val db      = DialogEditMemberBinding.inflate(layoutInflater)
        val isoFmt  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
        val dispFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        db.etFullName.setText(member.fullName)
        db.etPhone.setText(member.phone)
        db.etEmail.setText(member.email ?: "")
        db.etLocation.setText(member.location ?: "")
        db.switchActive.isChecked = member.status == "Active"

        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        try { cal.time = isoFmt.parse(member.joinDate) ?: Date() } catch (_: Exception) {}
        db.etJoinDate.setText(dispFmt.format(cal.time))

        // Plan spinner
        val planNames = availablePlans.map { "${it.name} (${it.durationDays}d)" }
        db.spinnerPlan.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, planNames,
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        db.spinnerPlan.setSelection(availablePlans.indexOfFirst { it.id == member.planId }.coerceAtLeast(0))

        // Trainer spinner
        db.spinnerTrainer.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, buildTrainerSpinnerEntries(),
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        db.spinnerTrainer.setSelection(trainerSpinnerPosition(member.trainerId))

        db.etJoinDate.setOnClickListener {
            DatePickerDialog(
                requireContext(),
                { _, y, m, d ->
                    cal.set(y, m, d, 0, 0, 0); cal.set(Calendar.MILLISECOND, 0)
                    db.etJoinDate.setText(dispFmt.format(cal.time))
                },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),
            ).show()
        }

        // ── Personal & health fields ────────────────────────────────────────
        val dobCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        var dobSet = false
        member.dateOfBirth?.let {
            try { dobCal.time = isoFmt.parse(it) ?: return@let; dobSet = true; db.etDob.setText(dispFmt.format(dobCal.time)) } catch (_: Exception) {}
        }
        db.etDob.setOnClickListener {
            DatePickerDialog(
                requireContext(),
                { _, y, m, d ->
                    dobCal.set(y, m, d, 0, 0, 0); dobCal.set(Calendar.MILLISECOND, 0)
                    dobSet = true
                    db.etDob.setText(dispFmt.format(dobCal.time))
                },
                dobCal.get(Calendar.YEAR), dobCal.get(Calendar.MONTH), dobCal.get(Calendar.DAY_OF_MONTH),
            ).show()
        }

        db.spinnerGender.setSimpleOptions(genderOptions, member.gender)
        db.spinnerBloodGroup.setSimpleOptions(bloodGroupOptions, member.bloodGroup ?: "Unknown")
        db.spinnerReferralSource.setSimpleOptions(referralOptions, member.referralSource)
        db.etEmergencyName.setText(member.emergencyContactName ?: "")
        db.etEmergencyPhone.setText(member.emergencyContactPhone ?: "")
        db.etHealthNotes.setText(member.healthNotes ?: "")

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
                val email     = db.etEmail.text?.toString()?.trim()
                val location  = db.etLocation.text?.toString()?.trim()
                val status    = if (db.switchActive.isChecked) "Active" else "Inactive"
                val plan      = availablePlans[db.spinnerPlan.selectedItemPosition]
                val trainerId = selectedTrainerId(db.spinnerTrainer.selectedItemPosition)
                val password  = db.etPassword.text?.toString()?.trim()?.ifBlank { null }

                viewModel.updateMember(
                    member.id,
                    UpdateMemberRequest(
                        fullName  = fullName,
                        phone     = phone,
                        email     = email?.ifBlank { null },
                        location  = location?.ifBlank { null },
                        planId    = plan.id,
                        status    = status,
                        joinDate  = isoFmt.format(cal.time),
                        trainerId = trainerId,
                        password  = password,
                        dateOfBirth = if (dobSet) isoFmt.format(dobCal.time) else null,
                        gender = db.spinnerGender.selectedOrNull(genderOptions),
                        bloodGroup = db.spinnerBloodGroup.selectedOrNull(bloodGroupOptions),
                        emergencyContactName = db.etEmergencyName.text?.toString()?.trim()?.ifBlank { null },
                        emergencyContactPhone = db.etEmergencyPhone.text?.toString()?.trim()?.ifBlank { null },
                        referralSource = db.spinnerReferralSource.selectedOrNull(referralOptions),
                        healthNotes = db.etHealthNotes.text?.toString()?.trim()?.ifBlank { null },
                    )
                )
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    // ── Add member dialog ──────────────────────────────────────────────────
    private fun showAddMemberDialog() {
        if (availablePlans.isEmpty()) {
            binding.root.showSnackbarError("No plans available. Create a plan first.")
            return
        }
        val db = DialogAddMemberBinding.inflate(layoutInflater)

        val symbol    = requireContext().gymApp.tokenManager.getCurrencySymbol()
        val planNames = availablePlans.map { "${it.name} (${it.durationDays}d – ${it.fee.toCurrencyString(symbol)})" }
        db.spinnerPlan.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, planNames,
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        db.spinnerTrainer.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, buildTrainerSpinnerEntries(),
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        db.spinnerGender.setSimpleOptions(genderOptions, null)
        db.spinnerBloodGroup.setSimpleOptions(bloodGroupOptions, "Unknown")
        db.spinnerReferralSource.setSimpleOptions(referralOptions, null)

        val isoFmt  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
        val dispFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val dobCal  = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        var dobSet  = false
        db.etDob.setOnClickListener {
            DatePickerDialog(
                requireContext(),
                { _, y, m, d ->
                    dobCal.set(y, m, d, 0, 0, 0); dobCal.set(Calendar.MILLISECOND, 0)
                    dobSet = true
                    db.etDob.setText(dispFmt.format(dobCal.time))
                },
                dobCal.get(Calendar.YEAR), dobCal.get(Calendar.MONTH), dobCal.get(Calendar.DAY_OF_MONTH),
            ).show()
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.title_add_member)
            .setView(db.root)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val fullName  = db.etFullName.text?.toString()?.trim() ?: ""
                val phone     = db.etPhone.text?.toString()?.trim() ?: ""
                val email     = db.etEmail.text?.toString()?.trim()
                val location  = db.etLocation.text?.toString()?.trim()
                val plan      = availablePlans[db.spinnerPlan.selectedItemPosition]
                val status    = if (db.switchActive.isChecked) "Active" else "Inactive"
                val trainerId = selectedTrainerId(db.spinnerTrainer.selectedItemPosition)
                val password  = db.etPassword.text?.toString()?.trim()?.ifBlank { null }

                if (fullName.isBlank() || phone.isBlank()) {
                    binding.root.showSnackbarError("Name and phone are required.")
                    return@setPositiveButton
                }

                viewModel.createMember(
                    CreateMemberRequest(
                        fullName  = fullName,
                        phone     = phone,
                        email     = email?.ifBlank { null },
                        location  = location?.ifBlank { null },
                        planId    = plan.id,
                        status    = status,
                        trainerId = trainerId,
                        password  = password,
                        dateOfBirth = if (dobSet) isoFmt.format(dobCal.time) else null,
                        gender = db.spinnerGender.selectedOrNull(genderOptions),
                        bloodGroup = db.spinnerBloodGroup.selectedOrNull(bloodGroupOptions),
                        emergencyContactName = db.etEmergencyName.text?.toString()?.trim()?.ifBlank { null },
                        emergencyContactPhone = db.etEmergencyPhone.text?.toString()?.trim()?.ifBlank { null },
                        referralSource = db.spinnerReferralSource.selectedOrNull(referralOptions),
                        healthNotes = db.etHealthNotes.text?.toString()?.trim()?.ifBlank { null },
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
        viewModel.loadTrainers()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
