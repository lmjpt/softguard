package kr.woorijip.softguard.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kr.woorijip.softguard.SoftGuardApp
import kr.woorijip.softguard.core.history.DayKey
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** 하루 1회, 180일이 지난 이력을 지운다 (설계문서 §8.8). */
class LogPruneWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val graph = SoftGuardApp.graph(applicationContext)
        val cutoff = DayKey.cutoff(LocalDate.now(graph.zone), RETENTION_DAYS)
        val removed = try {
            graph.db.eventLogDao().pruneBefore(cutoff)
        } catch (e: Exception) {
            graph.diag.log("이력 정리 실패: ${e.message}")
            return Result.retry()
        }
        if (removed > 0) graph.diag.log("이력 정리: ${removed}건 삭제 (기준 $cutoff)")
        return Result.success()
    }

    companion object {
        const val RETENTION_DAYS = 180
        private const val UNIQUE_NAME = "log-prune"

        fun ensureScheduled(context: Context) {
            val request = PeriodicWorkRequestBuilder<LogPruneWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
