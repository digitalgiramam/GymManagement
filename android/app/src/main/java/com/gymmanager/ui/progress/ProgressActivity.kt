package com.gymmanager.ui.progress

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gymmanager.data.model.Goal
import com.gymmanager.data.model.GoalRequest
import com.gymmanager.data.model.ProgressEntry
import com.gymmanager.data.model.ProgressEntryRequest
import com.gymmanager.databinding.ActivityProgressBinding
import com.gymmanager.databinding.DialogAddGoalBinding
import com.gymmanager.databinding.DialogAddProgressBinding
import com.gymmanager.databinding.ItemGoalRowBinding
import com.gymmanager.databinding.ItemProgressRowBinding
import com.gymmanager.gymApp
import com.gymmanager.utils.NetworkResult
import com.gymmanager.utils.showSnackbar
import com.gymmanager.utils.showSnackbarError
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Shared screen for progress tracking (weight/BMI/measurements + goals).
 * Two modes, chosen by whether [EXTRA_MEMBER_ID] is present in the intent:
 *  - Staff/trainer mode: viewing & recording for a specific member
 *  - Self mode (Member Portal): the logged-in member managing their own progress
 */
class ProgressActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MEMBER_ID   = "extra_member_id"
        const val EXTRA_MEMBER_NAME = "extra_member_name"
    }

    private lateinit var binding: ActivityProgressBinding
    private var memberId: Int = ProgressViewModel.SELF

    private val viewModel: ProgressViewModel by lazy {
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ProgressViewModel(gymApp.progressRepository, memberId) as T
            }
        })[ProgressViewModel::class.java]
    }

    private var goalTargetDate: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        memberId = intent.getIntExtra(EXTRA_MEMBER_ID, ProgressViewModel.SELF)

        binding = ActivityProgressBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.title = intent.getStringExtra(EXTRA_MEMBER_NAME)?.let { "$it — Progress" } ?: "My Progress"
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.fabAddEntry.setOnClickListener { showAddEntryDialog() }
        binding.btnAddGoal.setOnClickListener { showAddGoalDialog() }
        binding.swipeRefresh.setOnRefreshListener { viewModel.load() }

        observeViewModel()
        viewModel.load()
    }

    private fun observeViewModel() {
        viewModel.entries.observe(this) { result ->
            binding.swipeRefresh.isRefreshing = false
            when (result) {
                is NetworkResult.Loading -> binding.progressBar.visibility = View.VISIBLE
                is NetworkResult.Success -> {
                    binding.progressBar.visibility = View.GONE
                    renderEntries(result.data)
                }
                is NetworkResult.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.root.showSnackbarError(result.message)
                }
            }
        }

        viewModel.goals.observe(this) { result ->
            if (result is NetworkResult.Success) renderGoals(result.data)
            else if (result is NetworkResult.Error) binding.root.showSnackbarError(result.message)
        }

        viewModel.actionResult.observe(this) { result ->
            when (result) {
                is NetworkResult.Success -> binding.root.showSnackbar("Saved.")
                is NetworkResult.Error   -> binding.root.showSnackbarError(result.message)
                else -> {}
            }
            if (result != null) viewModel.clearActionResult()
        }
    }

    private fun renderEntries(entries: List<ProgressEntry>) {
        val chronological = entries.sortedBy { it.entryDate }

        binding.chartWeight.setData(
            chronological.mapNotNull { e -> e.weightKg?.let { ProgressChartView.Point(shortDate(e.entryDate), it.toFloat()) } }
        )
        binding.chartBmi.setData(
            chronological.mapNotNull { e -> e.bmi?.let { ProgressChartView.Point(shortDate(e.entryDate), it.toFloat()) } }
        )

        binding.tvLatestWeight.text = entries.firstOrNull()?.weightKg?.let { "%.1f kg".format(it) } ?: "—"
        binding.tvLatestBmi.text    = entries.firstOrNull()?.bmi?.let { "%.1f".format(it) } ?: "—"

        binding.containerEntries.removeAllViews()
        binding.tvEntriesEmpty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE

        entries.forEach { entry ->
            val row = ItemProgressRowBinding.inflate(layoutInflater, binding.containerEntries, false)
            row.tvDate.text   = shortDate(entry.entryDate)
            row.tvWeight.text = entry.weightKg?.let { "Weight: %.1f kg".format(it) } ?: "Weight: —"
            row.tvBmi.text    = entry.bmi?.let { "BMI: %.1f".format(it) } ?: ""

            val measurements = listOfNotNull(
                entry.chestCm?.let  { "Chest: %.1fcm".format(it) },
                entry.waistCm?.let  { "Waist: %.1fcm".format(it) },
                entry.hipsCm?.let   { "Hips: %.1fcm".format(it) },
                entry.armsCm?.let   { "Arms: %.1fcm".format(it) },
                entry.thighsCm?.let { "Thighs: %.1fcm".format(it) },
            ).joinToString("  •  ")
            row.tvMeasurements.text = measurements
            row.tvMeasurements.visibility = if (measurements.isBlank()) View.GONE else View.VISIBLE

            row.tvNotes.text = entry.notes ?: ""
            row.tvNotes.visibility = if (entry.notes.isNullOrBlank()) View.GONE else View.VISIBLE

            row.tvRecordedBy.text = entry.recordedByName?.let { "Logged by $it" } ?: "Self-logged"
            row.btnDelete.setOnClickListener { confirmDeleteEntry(entry) }

            binding.containerEntries.addView(row.root)
        }
    }

    private fun renderGoals(goals: List<Goal>) {
        binding.containerGoals.removeAllViews()
        binding.tvGoalsEmpty.visibility = if (goals.isEmpty()) View.VISIBLE else View.GONE

        goals.forEach { goal ->
            val row = ItemGoalRowBinding.inflate(layoutInflater, binding.containerGoals, false)
            row.tvGoalDescription.text = goal.description
            row.tvGoalTarget.text = listOfNotNull(
                goal.targetWeightKg?.let { "Target: %.1fkg".format(it) },
                goal.targetDate?.let { "By ${it.take(10)}" },
            ).joinToString("  •  ")

            row.tvGoalStatus.text = goal.status
            row.tvGoalStatus.setBackgroundResource(
                when (goal.status) {
                    "ACHIEVED"  -> com.gymmanager.R.drawable.bg_status_active
                    "ABANDONED" -> com.gymmanager.R.drawable.bg_status_overdue
                    else        -> com.gymmanager.R.drawable.bg_status_partial
                }
            )

            row.chipAchieved.setOnClickListener {
                val next = if (goal.status == "ACHIEVED") "ACTIVE" else "ACHIEVED"
                viewModel.setGoalStatus(goal.id, next)
            }
            row.btnDeleteGoal.setOnClickListener { confirmDeleteGoal(goal) }

            binding.containerGoals.addView(row.root)
        }
    }

    private fun shortDate(iso: String): String = try {
        val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            isLenient = true
            timeZone = TimeZone.getTimeZone("UTC")
        }.parse(iso.take(19))
        SimpleDateFormat("dd MMM", Locale.US).format(parsed!!)
    } catch (_: Exception) { iso.take(10) }

    private fun confirmDeleteEntry(entry: ProgressEntry) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete entry")
            .setMessage("Remove this progress entry from ${shortDate(entry.entryDate)}?")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteEntry(entry.id) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteGoal(goal: Goal) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete goal")
            .setMessage("Remove \"${goal.description}\"?")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteGoal(goal.id) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddEntryDialog() {
        val db = DialogAddProgressBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(this)
            .setTitle("Add Progress Entry")
            .setView(db.root)
            .setPositiveButton("Save") { _, _ ->
                val weight  = db.etWeight.text?.toString()?.trim()?.toDoubleOrNull()
                val chest   = db.etChest.text?.toString()?.trim()?.toDoubleOrNull()
                val waist   = db.etWaist.text?.toString()?.trim()?.toDoubleOrNull()
                val hips    = db.etHips.text?.toString()?.trim()?.toDoubleOrNull()
                val arms    = db.etArms.text?.toString()?.trim()?.toDoubleOrNull()
                val thighs  = db.etThighs.text?.toString()?.trim()?.toDoubleOrNull()
                val notes   = db.etNotes.text?.toString()?.trim()

                if (weight == null && chest == null && waist == null && hips == null && arms == null && thighs == null) {
                    binding.root.showSnackbarError("Enter at least one measurement.")
                    return@setPositiveButton
                }

                viewModel.addEntry(
                    ProgressEntryRequest(
                        weightKg = weight, chestCm = chest, waistCm = waist,
                        hipsCm = hips, armsCm = arms, thighsCm = thighs,
                        notes = notes?.ifBlank { null },
                    )
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddGoalDialog() {
        val db = DialogAddGoalBinding.inflate(layoutInflater)
        goalTargetDate = null

        db.etTargetDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                goalTargetDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
                db.etTargetDate.setText(goalTargetDate)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Set a Goal")
            .setView(db.root)
            .setPositiveButton("Save") { _, _ ->
                val description   = db.etGoalDescription.text?.toString()?.trim() ?: ""
                val targetWeight  = db.etTargetWeight.text?.toString()?.trim()?.toDoubleOrNull()

                if (description.isBlank()) {
                    binding.root.showSnackbarError("Describe the goal.")
                    return@setPositiveButton
                }

                viewModel.addGoal(
                    GoalRequest(
                        description = description,
                        targetWeightKg = targetWeight,
                        targetDate = goalTargetDate,
                    )
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
