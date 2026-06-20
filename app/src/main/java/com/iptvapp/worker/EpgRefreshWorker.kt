package com.iptvapp.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.iptvapp.R
import com.iptvapp.data.local.IptvDatabase
import com.iptvapp.data.local.PreferencesManager
import com.iptvapp.data.repository.XtreamRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlin.coroutines.cancellation.CancellationException

@HiltWorker
class EpgRefreshWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: XtreamRepository,
    private val db: IptvDatabase,
    private val prefs: PreferencesManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        createNotificationChannel()

        val missingOnly = inputData.getBoolean(KEY_MISSING_ONLY, false)
        val notificationManager =
            appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        return try {
            db.epgDao().deleteExpiredEpg()

            setProgress(
                Data.Builder()
                    .putInt(KEY_PROGRESS, 0)
                    .putString(KEY_STATUS, "Loading channel list...")
                    .build()
            )

            val allChannels = db.channelDao().getFavoriteChannels().first()

            val channelsToRefresh = if (missingOnly) {
                val idsWithEpg = db.epgDao().getStreamIdsWithEpg().toSet()
                allChannels.filter { it.streamId !in idsWithEpg }
            } else {
                allChannels
            }

            val total = channelsToRefresh.size

            if (total == 0) {
                updateProgress(notificationManager, 100, "EPG already up to date")
                prefs.setLastEpgRefreshTime(System.currentTimeMillis())
                return Result.success(
                    Data.Builder()
                        .putInt(KEY_PROGRESS, 100)
                        .putString(KEY_STATUS, "EPG already up to date")
                        .build()
                )
            }

            channelsToRefresh.forEachIndexed { index, channel ->
                if (isStopped) {
                    updateProgress(notificationManager, 0, "EPG refresh canceled")
                    return Result.success()
                }

                val beforeStatus = "Refreshing ${channel.name}"
                val beforePercent = (index * 100) / total

                setProgress(
                    Data.Builder()
                        .putInt(KEY_PROGRESS, beforePercent)
                        .putString(KEY_STATUS, beforeStatus)
                        .build()
                )

                repository.fetchEpg(channel.streamId)

                val done = index + 1
                val percent = (done * 100) / total
                val status = "Refreshing EPG $done of $total ($percent%)"

                setProgress(
                    Data.Builder()
                        .putInt(KEY_PROGRESS, percent)
                        .putString(KEY_STATUS, status)
                        .build()
                )

                updateProgress(notificationManager, percent, status)
            }

            prefs.setLastEpgRefreshTime(System.currentTimeMillis())

            setProgress(
                Data.Builder()
                    .putInt(KEY_PROGRESS, 100)
                    .putString(KEY_STATUS, "EPG refresh complete")
                    .build()
            )

            updateProgress(notificationManager, 100, "EPG refresh complete")

            Result.success(
                Data.Builder()
                    .putInt(KEY_PROGRESS, 100)
                    .putString(KEY_STATUS, "EPG refresh complete")
                    .build()
            )
        } catch (e: CancellationException) {
            throw e // let WorkManager handle cancellation; don't treat as failure
        } catch (e: Exception) {
            val errorText = "EPG refresh failed: ${e.javaClass.simpleName}: ${e.message ?: "Unknown error"}"
            Log.e("EpgRefreshWorker", errorText, e)

            updateProgress(notificationManager, 0, errorText)

            // Transient (network/server) failures should reschedule with
            // WorkManager backoff rather than fail permanently. Give up only
            // after a few attempts so a genuinely dead config doesn't loop.
            if (runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure(
                    Data.Builder()
                        .putInt(KEY_PROGRESS, 0)
                        .putString(KEY_STATUS, errorText)
                        .build()
                )
            }
        }
    }

    private fun updateProgress(
        notificationManager: NotificationManager,
        progress: Int,
        status: String
    ) {
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Refreshing EPG")
            .setContentText(status)
            .setOnlyAlertOnce(true)
            .setOngoing(progress in 1..99)
            .setProgress(100, progress.coerceIn(0, 100), false)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Android 13+ POST_NOTIFICATIONS may be denied — never fail the
            // EPG refresh just because the progress notification can't show.
        } catch (_: Exception) {
            // Keep the worker running even if notification display fails.
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "EPG Refresh",
                NotificationManager.IMPORTANCE_LOW
            )

            val notificationManager =
                appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "epg_refresh_work"
        const val KEY_PROGRESS = "progress"
        const val KEY_STATUS = "status"
        const val KEY_MISSING_ONLY = "missing_only"

        private const val CHANNEL_ID = "epg_refresh_channel"
        private const val NOTIFICATION_ID = 3001
        private const val MAX_ATTEMPTS = 3
    }
}
