package com.mj.money.budget

import android.content.Context
import androidx.core.content.edit
import com.mj.money.alerts.AppNotifier
import com.mj.money.domain.BudgetPeriod
import com.mj.money.domain.BudgetSnapshot
import com.mj.money.domain.dateTicker
import com.mj.money.domain.formatWon
import com.mj.money.domain.titleText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/**
 * 이번 달 지출이 예산의 80%, 100%를 넘으면 한 번씩 알린다.
 * 내역이 바뀔 때마다(직접 입력, 고정지출 자동 기록 등) 다시 확인한다.
 */
class BudgetMonitor(
    private val context: Context,
    private val tracker: BudgetTracker,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope) {
        scope.launch {
            dateTicker()
                .flatMapLatest { today -> tracker.observe(today) }
                .collect { view -> view.snapshot?.let { check(view.period, it) } }
        }
    }

    private fun check(period: BudgetPeriod, snapshot: BudgetSnapshot) {
        val level = when {
            snapshot.spent >= snapshot.budget -> 100
            snapshot.spent * 10 >= snapshot.budget * 8 -> 80
            else -> return
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "level_${period.label}"
        if (prefs.getInt(key, 0) >= level) return
        prefs.edit { putInt(key, level) }

        val month = period.titleText()
        val (title, text) = if (level == 100) {
            "$month 예산을 다 썼어요" to
                "예산 ${snapshot.budget.formatWon()} 중 ${snapshot.spent.formatWon()}을 썼어요."
        } else {
            val daily = if (snapshot.dailyAllowance > 0) " 하루 ${snapshot.dailyAllowance.formatWon()}씩 쓰면 돼요." else ""
            "$month 예산의 80%를 썼어요" to
                "남은 예산은 ${snapshot.remaining.coerceAtLeast(0).formatWon()}이에요.$daily"
        }
        AppNotifier.notify(context, AppNotifier.Channel.BUDGET, NOTIFICATION_ID, title, text)
    }

    private companion object {
        const val PREFS = "budget_alerts"
        const val NOTIFICATION_ID = 1_000_000
    }
}
