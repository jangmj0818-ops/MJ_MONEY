package com.mj.money.ui.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mj.money.AppContainer
import com.mj.money.data.db.RecurringRow
import com.mj.money.data.db.RecurringStatus
import com.mj.money.domain.BudgetPeriod
import com.mj.money.domain.RecurringItem
import com.mj.money.domain.buildRecurringItems
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class RecurringUiState(
    val period: BudgetPeriod,
    val monthlyTotal: Long = 0,
    val activeCount: Int = 0,
    val upcoming: List<RecurringItem> = emptyList(),
    val recorded: List<RecurringItem> = emptyList(),
    val canceled: List<RecurringRow> = emptyList(),
    val canceledMonthlyTotal: Long = 0,
    val loaded: Boolean = false,
) {
    val isEmpty: Boolean get() = activeCount == 0 && canceled.isEmpty()

    /** 알림을 받아야 하는 항목이 있는지 (알림 권한 안내 배너용) */
    val wantsNotifications: Boolean
        get() = (upcoming + recorded).any { it.expense.remindDayBefore || it.expense.trialEndEpochDay != null }
}

class RecurringViewModel(container: AppContainer) : ViewModel() {
    private val processor = container.recurringProcessor
    private val today = LocalDate.now()
    private val period = container.periodCalculator.periodOf(today)

    val uiState: StateFlow<RecurringUiState> = combine(
        container.database.recurringExpenseDao().observeRows(),
        container.database.transactionDao().observeRecurringIdsBetween(period.startMillis(), period.endMillis()),
    ) { rows, recordedIds ->
        val items = buildRecurringItems(rows, recordedIds.toSet(), period, today)
        val canceled = rows.filter { it.expense.status == RecurringStatus.CANCELED }
        RecurringUiState(
            period = period,
            monthlyTotal = items.sumOf { it.expense.amount },
            activeCount = items.size,
            upcoming = items.filterNot { it.recorded }.sortedBy { it.dueDate },
            recorded = items.filter { it.recorded }.sortedBy { it.dueDate },
            canceled = canceled.sortedByDescending { it.expense.canceledAt },
            canceledMonthlyTotal = canceled.sumOf { it.expense.amount },
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecurringUiState(period))

    fun recordNow(item: RecurringItem) {
        viewModelScope.launch { processor.recordNow(item.expense.id, item.dueDate) }
    }
}
