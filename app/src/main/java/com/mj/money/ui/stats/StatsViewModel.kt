package com.mj.money.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mj.money.AppContainer
import com.mj.money.data.db.CategoryEntity
import com.mj.money.domain.BudgetPeriod
import com.mj.money.domain.cardMonthFor
import com.mj.money.domain.cardSpent
import com.mj.money.domain.categoryShares
import com.mj.money.domain.paymentShares
import com.mj.money.domain.donutGroups
import com.mj.money.domain.monthlyTotals
import com.mj.money.domain.previousComparable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth

/** 통계 목록의 카테고리 한 줄 */
data class CategoryStat(
    val categoryId: Long?,
    val name: String,
    val emoji: String,
    /** 도넛에서 '그 외'로 묶였으면 0 (회색) */
    val slot: Int,
    val total: Long,
    val count: Int,
)

/** 도넛 조각 (카테고리 하나 또는 '그 외') */
data class DonutPiece(
    val categoryId: Long?,
    val total: Long,
    val slot: Int,
    val emoji: String?,
    val isOther: Boolean,
) {
    val key: String get() = if (isOther) "other" else "cat-$categoryId"
}

/** 결제수단별 한 줄 (카드 실적 목표가 있으면 실적도) */
data class PaymentStat(
    val paymentMethodId: Long?,
    val name: String,
    val total: Long,
    val count: Int,
    /** 카드 실적 달(달력 기준)에 쓴 돈. 실적 목표가 없으면 null */
    val cardSpent: Long?,
    val target: Long?,
)

data class StatsUiState(
    val period: BudgetPeriod,
    val payments: List<PaymentStat> = emptyList(),
    /** 카드 실적을 세는 달력의 달 */
    val cardMonth: YearMonth = YearMonth.now(),
    val total: Long = 0,
    /** 지난달 비교 금액 (진행 중인 달이면 같은 날수까지) */
    val previousTotal: Long = 0,
    val previousIsPartial: Boolean = false,
    val categories: List<CategoryStat> = emptyList(),
    val donut: List<DonutPiece> = emptyList(),
    val months: List<Pair<YearMonth, Long>> = emptyList(),
    val budget: Long = 0,
    val loaded: Boolean = false,
)

class StatsViewModel(container: AppContainer) : ViewModel() {
    private val periods = container.periodCalculator
    private val transactionDao = container.database.transactionDao()
    private val categoryDao = container.database.categoryDao()
    private val paymentMethodDao = container.database.paymentMethodDao()
    private val settings = container.settings
    private val month = MutableStateFlow(periods.periodOf(LocalDate.now()).label)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<StatsUiState> = month.flatMapLatest { month ->
        val months = (5 downTo 0).map { month.minusMonths(it.toLong()) }
        val from = periods.periodFor(months.first()).startMillis()
        val to = periods.periodFor(month).endMillis()
        combine(
            transactionDao.observeExpensePoints(from, to),
            categoryDao.observeAll(),
            paymentMethodDao.observeAll(),
            settings.budget,
        ) { points, categories, paymentMethods, budget ->
            val today = LocalDate.now()
            val period = periods.periodFor(month)
            val cardMonth = cardMonthFor(period, today)
            val methodsById = paymentMethods.associateBy { it.id }
            val payments = paymentShares(points, periods, month).map { share ->
                val method = share.paymentMethodId?.let(methodsById::get)
                val target = method?.monthlyTarget
                PaymentStat(
                    paymentMethodId = share.paymentMethodId,
                    name = method?.name ?: "결제수단 없음",
                    total = share.total,
                    count = share.count,
                    cardSpent = if (target != null && method != null) cardSpent(points, method.id, cardMonth) else null,
                    target = target,
                )
            }
            val byId: Map<Long, CategoryEntity> = categories.associateBy { it.id }
            val shares = categoryShares(points, periods, month)
            // 색 번호가 0인 카테고리('기타')와 미분류는 항상 '그 외'로 묶는다.
            val groups = donutGroups(shares, isAlwaysOther = { id -> id == null || byId[id]?.colorSlot == 0 })
            val otherMembers = groups.filter { it.isOther }.flatMap { it.categoryIds }.toSet()
            val (previous, partial) = previousComparable(points, periods, month, LocalDate.now())

            StatsUiState(
                period = period,
                payments = payments,
                cardMonth = cardMonth,
                total = shares.sumOf { it.total },
                previousTotal = previous,
                previousIsPartial = partial,
                categories = shares.map { share ->
                    val category = share.categoryId?.let(byId::get)
                    CategoryStat(
                        categoryId = share.categoryId,
                        name = category?.name ?: "미분류",
                        emoji = category?.emoji ?: "❔",
                        slot = if (share.categoryId in otherMembers) 0 else category?.colorSlot ?: 0,
                        total = share.total,
                        count = share.count,
                    )
                },
                donut = groups.map { group ->
                    if (group.isOther) {
                        DonutPiece(null, group.total, slot = 0, emoji = null, isOther = true)
                    } else {
                        val id = group.categoryIds.single()
                        val category = id?.let(byId::get)
                        DonutPiece(id, group.total, category?.colorSlot ?: 0, category?.emoji, isOther = false)
                    }
                },
                months = monthlyTotals(points, periods, months),
                budget = budget.monthlyBudget,
                loaded = true,
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        StatsUiState(periods.periodFor(month.value)),
    )

    fun previousPeriod() = month.update { it.minusMonths(1) }

    fun nextPeriod() = month.update { it.plusMonths(1) }

    fun selectMonth(target: YearMonth) {
        month.value = target
    }
}
