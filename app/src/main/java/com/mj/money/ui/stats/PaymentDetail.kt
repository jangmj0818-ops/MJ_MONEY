package com.mj.money.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mj.money.AppContainer
import com.mj.money.data.db.PaymentKind
import com.mj.money.data.db.TransactionRow
import com.mj.money.data.db.TxStatus
import com.mj.money.data.db.TxType
import com.mj.money.domain.BudgetPeriod
import com.mj.money.domain.cardMonthFor
import com.mj.money.domain.cardSpent
import com.mj.money.domain.formatWon
import com.mj.money.domain.monthlyTotals
import com.mj.money.domain.previousComparable
import com.mj.money.domain.rangeText
import com.mj.money.domain.titleText
import com.mj.money.ui.AppViewModelProvider
import com.mj.money.ui.charts.ChartColors
import com.mj.money.ui.charts.MonthBar
import com.mj.money.ui.charts.MonthlyBars
import com.mj.money.ui.components.ComparisonLine
import com.mj.money.ui.components.ProgressMeter
import com.mj.money.ui.components.TransactionItem
import com.mj.money.ui.components.label
import com.mj.money.ui.theme.HeroAmountStyle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

data class PaymentDetailUiState(
    val period: BudgetPeriod,
    val name: String = "",
    val kind: PaymentKind? = null,
    val total: Long = 0,
    val previousTotal: Long = 0,
    val previousIsPartial: Boolean = false,
    val months: List<Pair<YearMonth, Long>> = emptyList(),
    val transactions: List<TransactionRow> = emptyList(),
    val cardMonth: YearMonth = YearMonth.now(),
    val cardSpent: Long = 0,
    val target: Long? = null,
    val billingDay: Int? = null,
    val loaded: Boolean = false,
)

class PaymentDetailViewModel(savedStateHandle: SavedStateHandle, container: AppContainer) : ViewModel() {
    private val periods = container.periodCalculator
    private val transactionDao = container.database.transactionDao()

    /** -1이면 결제수단 없이 기록한 지출 */
    private val paymentMethodId: Long? = savedStateHandle.get<Long>("paymentMethodId")?.takeIf { it > 0 }
    private val month = MutableStateFlow(
        savedStateHandle.get<String>("month")?.takeIf { it.isNotEmpty() }?.let(YearMonth::parse)
            ?: periods.periodOf(LocalDate.now()).label
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<PaymentDetailUiState> = month.flatMapLatest { month ->
        val months = (5 downTo 0).map { month.minusMonths(it.toLong()) }
        val period = periods.periodFor(month)
        combine(
            transactionDao.observeExpensePoints(periods.periodFor(months.first()).startMillis(), period.endMillis()),
            transactionDao.observeBetween(period.startMillis(), period.endMillis()),
            container.database.paymentMethodDao().observeAll(),
        ) { points, rows, methods ->
            val method = methods.firstOrNull { it.id == paymentMethodId }
            val mine = points.filter { it.paymentMethodId == paymentMethodId }
            val transactions = rows.filter {
                it.paymentMethodId == paymentMethodId && it.type == TxType.EXPENSE && it.status != TxStatus.CANCELED
            }
            val (previous, partial) = previousComparable(mine, periods, month, LocalDate.now())
            val cardMonth = cardMonthFor(period, LocalDate.now())
            PaymentDetailUiState(
                period = period,
                name = method?.name ?: "결제수단 없음",
                kind = method?.kind,
                total = transactions.sumOf { it.amount },
                previousTotal = previous,
                previousIsPartial = partial,
                months = monthlyTotals(mine, periods, months),
                transactions = transactions,
                cardMonth = cardMonth,
                cardSpent = if (method != null) cardSpent(points, method.id, cardMonth) else 0,
                target = method?.monthlyTarget,
                billingDay = method?.billingDay,
                loaded = true,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaymentDetailUiState(periods.periodFor(month.value)))

    fun selectMonth(target: YearMonth) {
        month.value = target
    }
}

@Composable
fun PaymentDetailScreen(
    onBack: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    viewModel: PaymentDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
            Column(Modifier.weight(1f)) {
                Text(state.name, style = MaterialTheme.typography.titleLarge)
                state.kind?.let {
                    Text(
                        listOfNotNull(it.label(), state.billingDay?.let { day -> "결제일 매월 ${day}일" }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                "${state.period.titleText()} · ${state.period.rangeText()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp),
            )
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(state.total.formatWon(), style = HeroAmountStyle)
                    ComparisonLine(state.total, state.previousTotal, state.previousIsPartial)
                }
            }

            val target = state.target
            if (target != null) {
                item { CardPerformance(state.cardMonth, state.cardSpent, target) }
            }

            item { SectionTitle("최근 6개월") }
            item {
                MonthlyBars(
                    bars = state.months.map { (month, total) -> MonthBar(month, total, month == state.period.label) },
                    color = ChartColors.category(1),
                    onBarClick = viewModel::selectMonth,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }

            item { SectionTitle("내역 ${state.transactions.size}건") }
            items(state.transactions, key = { it.id }) { row ->
                TransactionItem(row, onClick = { onOpenEntry(row.id) }, showDate = true)
            }
        }
    }
}

/** 카드 실적: 달력 한 달(1일~말일) 동안 쓴 돈 / 목표 */
@Composable
private fun CardPerformance(month: YearMonth, spent: Long, target: Long) {
    val remaining = target - spent
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            "카드 실적 · ${month.monthValue}월 1일 ~ ${month.monthValue}월 ${month.lengthOfMonth()}일",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ProgressMeter(
            fraction = spent.toFloat() / target,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            if (remaining > 0) {
                "${spent.formatWon()} / ${target.formatWon()} · 실적까지 ${remaining.formatWon()} 남았어요"
            } else {
                "${spent.formatWon()} / ${target.formatWon()} · 실적을 채웠어요"
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Column(Modifier.padding(top = 16.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp),
        )
    }
}
