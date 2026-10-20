package com.mj.money.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mj.money.AppContainer
import com.mj.money.data.db.TransactionRow
import com.mj.money.data.db.TxStatus
import com.mj.money.data.db.TxType
import com.mj.money.domain.BudgetPeriod
import com.mj.money.domain.MerchantTotal
import com.mj.money.domain.monthlyTotals
import com.mj.money.domain.previousComparable
import com.mj.money.domain.topMerchants
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

data class CategoryDetailUiState(
    val period: BudgetPeriod,
    val name: String = "",
    val emoji: String = "",
    val slot: Int = 0,
    val total: Long = 0,
    val previousTotal: Long = 0,
    val previousIsPartial: Boolean = false,
    val months: List<Pair<YearMonth, Long>> = emptyList(),
    val merchants: List<MerchantTotal> = emptyList(),
    val transactions: List<TransactionRow> = emptyList(),
    val loaded: Boolean = false,
)

class CategoryDetailViewModel(
    savedStateHandle: SavedStateHandle,
    container: AppContainer,
) : ViewModel() {
    private val periods = container.periodCalculator
    private val transactionDao = container.database.transactionDao()

    /** -1이면 미분류 */
    private val categoryId: Long? = savedStateHandle.get<Long>("categoryId")?.takeIf { it > 0 }
    private val month = MutableStateFlow(
        savedStateHandle.get<String>("month")?.takeIf { it.isNotEmpty() }?.let(YearMonth::parse)
            ?: periods.periodOf(LocalDate.now()).label
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<CategoryDetailUiState> = month.flatMapLatest { month ->
        val months = (5 downTo 0).map { month.minusMonths(it.toLong()) }
        val period = periods.periodFor(month)
        combine(
            transactionDao.observeExpensePoints(periods.periodFor(months.first()).startMillis(), period.endMillis()),
            transactionDao.observeBetween(period.startMillis(), period.endMillis()),
            container.database.categoryDao().observeAll(),
        ) { points, rows, categories ->
            val category = categories.firstOrNull { it.id == categoryId }
            val mine = points.filter { it.categoryId == categoryId }
            val transactions = rows.filter {
                it.categoryId == categoryId && it.type == TxType.EXPENSE && it.status != TxStatus.CANCELED
            }
            val (previous, partial) = previousComparable(mine, periods, month, LocalDate.now())
            CategoryDetailUiState(
                period = period,
                name = category?.name ?: "미분류",
                emoji = category?.emoji ?: "❔",
                slot = category?.colorSlot ?: 0,
                total = transactions.sumOf { it.amount },
                previousTotal = previous,
                previousIsPartial = partial,
                months = monthlyTotals(mine, periods, months),
                merchants = topMerchants(transactions),
                transactions = transactions,
                loaded = true,
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CategoryDetailUiState(periods.periodFor(month.value)),
    )

    fun selectMonth(target: YearMonth) {
        month.value = target
    }
}
