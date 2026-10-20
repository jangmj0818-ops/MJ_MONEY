package com.mj.money.recurring

import android.content.Context
import androidx.core.content.edit
import com.mj.money.alerts.AppNotifier
import com.mj.money.data.db.RecurringExpenseEntity
import com.mj.money.domain.RecurringSchedule
import com.mj.money.domain.formatWon
import java.time.LocalDate
import java.time.YearMonth

/** 결제 전날, 무료 체험 종료 전날 알림 */
object RecurringNotifier {

    private const val PREFS = "recurring_notifier"
    private const val KEY_LAST_REMINDER_DAY = "last_reminder_day"

    /** 하루에 한 번만 보낸다. (매일 작업이 하루에 두 번 돌아도 중복 알림이 없도록) */
    fun sendDailyReminders(context: Context, expenses: List<RecurringExpenseEntity>, today: LocalDate) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getLong(KEY_LAST_REMINDER_DAY, Long.MIN_VALUE) == today.toEpochDay()) return
        prefs.edit { putLong(KEY_LAST_REMINDER_DAY, today.toEpochDay()) }

        val tomorrow = today.plusDays(1)
        for (expense in expenses) {
            val dueTomorrow = RecurringSchedule.dueDateIn(YearMonth.from(tomorrow), expense.dayOfMonth) == tomorrow
            if (expense.remindDayBefore && dueTomorrow && !RecurringSchedule.isInTrial(expense, tomorrow)) {
                AppNotifier.notify(
                    context,
                    AppNotifier.Channel.RECURRING,
                    id = (expense.id * 2).toInt(),
                    title = "내일 ${expense.name} 결제일이에요",
                    text = "${expense.amount.formatWon()}이 결제될 예정이에요.",
                )
            }
            if (expense.trialEndEpochDay == tomorrow.toEpochDay()) {
                AppNotifier.notify(
                    context,
                    AppNotifier.Channel.RECURRING,
                    id = (expense.id * 2 + 1).toInt(),
                    title = "${expense.name} 무료 체험이 내일 끝나요",
                    text = "계속 쓰지 않을 거라면 오늘 해지하세요.",
                )
            }
        }
    }
}
