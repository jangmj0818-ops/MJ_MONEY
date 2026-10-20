package com.mj.money.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mj.money.AppContainer
import com.mj.money.data.db.TransactionRow
import com.mj.money.data.db.TxStatus
import com.mj.money.data.db.TxType
import com.mj.money.domain.BudgetPeriod
import com.mj.money.domain.HeatScale
import com.mj.money.domain.heatScale
import com.mj.money.domain.toLocalDate
import com.mj.money.ui.components.Totals
import com.mj.money.ui.components.totals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class HistoryMode { CALENDAR, LIST }

data class DayGroup(
    val date: LocalDate,
    val expense: Long,
    val items: List<TransactionRow>,
)

data class HistoryUiState(
    val period: BudgetPeriod,
    val totals: Totals = Totals(0, 0),
    val days: List<DayGroup> = emptyList(),
    val mode: HistoryMode = HistoryMode.CALENDAR,
    val selectedDate: LocalDate? = null,
    val heatScale: HeatScale = HeatScale(emptyList()),
    /** 히트맵 기준이 된 하루 예산 (없으면 0) */
    val dailyBudget: Long = 0,
    val today: LocalDate = LocalDate.now(),
    val loaded: Boolean = false,
) {
    val dailyExpense: Map<LocalDate, Long> get() = days.associate { it.date to it.expense }
    val selectedDay: DayGroup? get() = days.firstOrNull { it.date == selectedDate }
}

class HistoryViewModel(container: AppContainer) : ViewModel() {
    private val dao = container.database.transactionDao()
    private val periods = container.periodCalculator
    private val today = LocalDate.now()
    private val label = MutableStateFlow(periods.periodOf(today).label)
    private val mode = MutableStateFlow(HistoryMode.CALENDAR)
    private val selectedDate = MutableStateFlow<LocalDate?>(today)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HistoryUiState> = label.flatMapLatest { label ->
        val period = periods.periodFor(label)
        combine(
            dao.observeBetween(period.startMillis(), period.endMillis()),
            container.settings.budget,
            mode,
            selectedDate,
        ) { rows, budget, mode, selected ->
            val days = rows.groupByDay()
            val totalDays = ChronoUnit.DAYS.between(period.start, period.endExclusive)
            val dailyBudget = if (budget.monthlyBudget > 0) budget.monthlyBudget / totalDays else 0L
            HistoryUiState(
                period = period,
                totals = rows.totals(),
                days = days,
                mode = mode,
                selectedDate = selected?.takeIf { it in period },
                heatScale = heatScale(dailyBudget, days.maxOfOrNull { it.expense } ?: 0L),
                dailyBudget = dailyBudget,
                today = today,
                loaded = true,
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HistoryUiState(periods.periodFor(label.value)),
    )

    fun previousPeriod() = changePeriod(label.value.minusMonths(1))

    fun nextPeriod() = changePeriod(label.value.plusMonths(1))

    private fun changePeriod(target: java.time.YearMonth) {
        label.value = target
        // 이번 달로 돌아오면 오늘을, 다른 달이면 선택을 비운다.
        selectedDate.value = today.takeIf { it in periods.periodFor(target) }
    }

    fun setMode(value: HistoryMode) {
        mode.value = value
    }

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }
}

private fun List<TransactionRow>.groupByDay(): List<DayGroup> =
    groupBy { it.occurredAt.toLocalDate() }.map { (date, items) ->
        DayGroup(
            date = date,
            expense = items
                .filter { it.type == TxType.EXPENSE && it.status != TxStatus.CANCELED }
                .sumOf { it.amount },
            items = items,
        )
    }
