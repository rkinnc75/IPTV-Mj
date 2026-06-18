package com.iptvapp.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.iptvapp.data.local.IptvDatabase
import com.iptvapp.data.local.PreferencesManager
import com.iptvapp.databinding.ActivitySettingsBinding
import com.iptvapp.worker.EpgRefreshWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var workManager: WorkManager
    private var currentEpgWorkId: UUID? = null

    @Inject
    lateinit var prefs: PreferencesManager

    @Inject
    lateinit var db: IptvDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnWhatsNew.setOnClickListener { showChangelog() }

        workManager = WorkManager.getInstance(this)

        binding.btnBack.setOnClickListener { finish() }

        loadSettings()
        observeEpgRefreshWork()

        binding.btnSaveEpg.setOnClickListener {
            lifecycleScope.launch {
                prefs.setEpgUrl(binding.etEpgUrl.text.toString().trim())
                Toast.makeText(this@SettingsActivity, "EPG URL saved", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnRefreshEpg.setOnClickListener {
            startEpgRefresh()
        }

        binding.btnCancelEpgRefresh.setOnClickListener {
            workManager.cancelUniqueWork(EpgRefreshWorker.UNIQUE_WORK_NAME)
            binding.tvEpgRefreshStatus.text = "EPG refresh canceled."
            binding.btnRefreshEpg.isEnabled = true
            binding.btnCancelEpgRefresh.visibility = View.GONE
        }

        binding.cbRefreshMissingOnly.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                prefs.setEpgRefreshMissingOnly(isChecked)
            }
        }

        binding.cbUsaOnlyChannels.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                prefs.setUsaOnlyChannels(isChecked)
            }
        }

        binding.rgAutoEpgRefresh.setOnCheckedChangeListener { _, checkedId ->
            lifecycleScope.launch {
                val hours = when (checkedId) {
                    binding.rbAuto6.id -> 6
                    binding.rbAuto12.id -> 12
                    binding.rbAuto24.id -> 24
                    else -> 0
                }

                prefs.setEpgAutoRefreshHours(hours)
                scheduleAutoEpgRefresh(hours)

                val msg = if (hours == 0) "Auto EPG refresh off" else "Auto EPG refresh every $hours hours"
                Toast.makeText(this@SettingsActivity, msg, Toast.LENGTH_SHORT).show()
            }
        }

        binding.rgFormat.setOnCheckedChangeListener { _, checkedId ->
            lifecycleScope.launch {
                val format = when (checkedId) {
                    binding.rbTs.id -> "ts"
                    else -> "m3u8"
                }

                prefs.setPreferredFormat(format)
                Toast.makeText(this@SettingsActivity, "Format set to $format", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showChangelog() {
        val text = try {
            assets.open("CHANGELOG.md").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "Changelog not available."
        }
        AlertDialog.Builder(this)
            .setTitle("What's New")
            .setMessage(text)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            binding.etEpgUrl.setText(prefs.epgUrl.first())

            when (prefs.preferredFormat.first()) {
                "ts" -> binding.rbTs.isChecked = true
                else -> binding.rbM3u8.isChecked = true
            }

            binding.cbRefreshMissingOnly.isChecked = prefs.epgRefreshMissingOnly.first()
            binding.cbUsaOnlyChannels.isChecked = prefs.usaOnlyChannels.first()

            when (prefs.epgAutoRefreshHours.first()) {
                6 -> binding.rbAuto6.isChecked = true
                12 -> binding.rbAuto12.isChecked = true
                24 -> binding.rbAuto24.isChecked = true
                else -> binding.rbAutoOff.isChecked = true
            }

            updateLastRefreshText()
            updateCacheAgeText()
        }
    }

    private fun startEpgRefresh() {
        lifecycleScope.launch {
            val missingOnly = prefs.epgRefreshMissingOnly.first()

            val request = OneTimeWorkRequestBuilder<EpgRefreshWorker>()
                .setInputData(
                    workDataOf(EpgRefreshWorker.KEY_MISSING_ONLY to missingOnly)
                )
                .build()

            currentEpgWorkId = request.id

            binding.progressEpgRefresh.visibility = View.VISIBLE
            binding.progressEpgRefresh.progress = 0
            binding.tvEpgRefreshStatus.text = "EPG refresh queued..."
            binding.btnRefreshEpg.isEnabled = false
            binding.btnCancelEpgRefresh.visibility = View.VISIBLE

            workManager.enqueueUniqueWork(
                EpgRefreshWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )

            observeCurrentEpgWork(request.id)
        }
    }

    private fun observeCurrentEpgWork(workId: UUID) {
        workManager.getWorkInfoByIdLiveData(workId).observe(this) { info ->
            if (info == null) return@observe

            val progress = info.progress.getInt(EpgRefreshWorker.KEY_PROGRESS, 0)
            val status = info.progress.getString(EpgRefreshWorker.KEY_STATUS)
                ?: info.outputData.getString(EpgRefreshWorker.KEY_STATUS)
                ?: "EPG refresh state: ${info.state}"

            binding.progressEpgRefresh.visibility = View.VISIBLE
            binding.progressEpgRefresh.progress = progress
            binding.tvEpgRefreshStatus.text = status

            val running =
                info.state == WorkInfo.State.RUNNING ||
                    info.state == WorkInfo.State.ENQUEUED

            binding.btnRefreshEpg.isEnabled = !running
            binding.btnCancelEpgRefresh.visibility =
                if (running) View.VISIBLE else View.GONE

            if (info.state.isFinished) {
                lifecycleScope.launch {
                    updateLastRefreshText()
                    updateCacheAgeText()
                }
            }
        }
    }

    private fun observeEpgRefreshWork() {
        workManager.getWorkInfosForUniqueWorkLiveData(EpgRefreshWorker.UNIQUE_WORK_NAME)
            .observe(this) { infos ->
                val info = infos.firstOrNull {
                    it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
                } ?: return@observe

                val progress = info.progress.getInt(EpgRefreshWorker.KEY_PROGRESS, 0)
                val status = info.progress.getString(EpgRefreshWorker.KEY_STATUS) ?: ""

                binding.progressEpgRefresh.visibility = View.VISIBLE
                binding.progressEpgRefresh.progress = progress

                if (status.isNotBlank()) {
                    binding.tvEpgRefreshStatus.text = status
                }

                binding.btnRefreshEpg.isEnabled = false
                binding.btnCancelEpgRefresh.visibility = View.VISIBLE
            }
    }

    private fun scheduleAutoEpgRefresh(hours: Int) {
        if (hours == 0) {
            workManager.cancelUniqueWork(AUTO_EPG_WORK_NAME)
            return
        }

        val request = PeriodicWorkRequestBuilder<EpgRefreshWorker>(hours.toLong(), TimeUnit.HOURS)
            .setInputData(
                workDataOf(EpgRefreshWorker.KEY_MISSING_ONLY to true)
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            AUTO_EPG_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private suspend fun updateLastRefreshText() {
        val time = prefs.lastEpgRefreshTime.first()

        binding.tvLastEpgRefresh.text = if (time == 0L) {
            "Last EPG Refresh: Never"
        } else {
            val formatted = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
                .format(Date(time))
            "Last EPG Refresh: $formatted"
        }
    }

    private suspend fun updateCacheAgeText() {
        val oldest = db.epgDao().getOldestEpgStartTimestamp()
        val newest = db.epgDao().getNewestEpgStopTimestamp()
        val nowSeconds = System.currentTimeMillis() / 1000

        binding.tvEpgCacheAge.text = when {
            oldest == null || newest == null -> "EPG Cache Age: Unknown"
            newest < nowSeconds -> "EPG Cache Age: Expired"
            else -> {
                val hoursAhead = (newest - nowSeconds) / 3600
                "EPG Cache: covers about $hoursAhead hours ahead"
            }
        }
    }

    companion object {
        private const val AUTO_EPG_WORK_NAME = "auto_epg_refresh_work"
    }
}
