package com.mj.money.recurring

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mj.money.MjMoneyApplication
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** 매일 한 번: 고정지출 자동 기록 + 전날 알림 */
class RecurringWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as MjMoneyApplication).container
        val today = LocalDate.now()
        container.recurringProcessor.recordDue(today)
        RecurringNotifier.sendDailyReminders(
            applicationContext,
            container.database.recurringExpenseDao().getActive(),
            today,
        )
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "recurring-daily"
        private val RUN_AT: LocalTime = LocalTime.of(9, 0)

        /** 이미 예약돼 있으면 그대로 둔다. 첫 실행은 다음 오전 9시쯤. */
        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(RUN_AT)
            if (!next.isAfter(now)) next = next.plusDays(1)

            val request = PeriodicWorkRequestBuilder<RecurringWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
