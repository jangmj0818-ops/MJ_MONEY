package com.mj.money.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mj.money.AppContainer
import com.mj.money.data.db.PocketBalance
import com.mj.money.data.db.TransactionRow
import com.mj.money.data.settings.BudgetSettings
import com.mj.money.domain.BudgetPeriod
import com.mj.money.domain.BudgetSnapshot
import com.mj.money.domain.RecurringItem
import com.mj.money.domain.dateTicker
import com.mj.money.ui.components.Totals
import com.mj.money.ui.components.totals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HomeUiState(
    val period: BudgetPeriod,
    val today: LocalDate,
    val settings: BudgetSettings = BudgetSettings(),
    val snapshot: BudgetSnapshot? = null,
    val totals: Totals = Totals(0, 0),
    /** 아직 안 나간 고정지출 (결제일 순, 최대 3개) */
    val upcomingFixed: List<RecurringItem> = emptyList(),
    val recent: List<TransactionRow> = emptyList(),
    val emergencyPockets: List<PocketBalance> = emptyList(),
    val emergencyTarget: Long = 0,
    val emergencyUsed: Long = 0,
    val emergencyRefillCounted: Long = 0,
    /** 알림에서 자동 기록했는데 카테고리 확인이 필요한 건수 */
    val needsReviewCount: Int = 0,
    val loaded: Boolean = false,
) {
    val emergencyTotal: Long get() = emergencyPockets.sumOf { it.balance }
}

class HomeViewModel(container: AppContainer) : ViewModel() {
    private val settings = container.settings

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HomeUiState> = dateTicker().flatMapLatest { today ->
        combine(
            container.budgetTracker.observe(today),
            container.database.transactionDao().observeRecent(limit = 5),
            container.emergency.observePockets(),
            settings.emergencyTarget,
            container.database.transactionDao().observeNeedsReviewCount(),
        ) { view, recent, pockets, emergencyTarget, needsReview ->
            HomeUiState(
                period = view.period,
                today = today,
                settings = view.settings,
                snapshot = view.snapshot,
                totals = view.transactions.totals(),
                upcomingFixed = view.fixedItems
                    .filter { !it.recorded && !it.inTrial }
                    .sortedBy { it.dueDate }
                    .take(3),
                recent = recent,
                emergencyPockets = pockets,
                emergencyTarget = emergencyTarget,
                emergencyUsed = view.emergencyUsed,
                emergencyRefillCounted = view.emergencyRefillCounted,
                needsReviewCount = needsReview,
                loaded = true,
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        LocalDate.now().let { HomeUiState(container.periodCalculator.periodOf(it), it) },
    )

    fun setBudget(amount: Long) {
        viewModelScope.launch { settings.setMonthlyBudget(amount) }
    }
}
