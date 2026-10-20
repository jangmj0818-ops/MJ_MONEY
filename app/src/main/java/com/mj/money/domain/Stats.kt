package com.mj.money.domain

import com.mj.money.data.db.ExpensePoint
import com.mj.money.data.db.TransactionRow
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** 한 달 동안 한 카테고리의 지출 */
data class CategoryShare(
    val categoryId: Long?,
    val total: Long,
    /** 지난달 같은 카테고리 지출 */
    val previous: Long,
    val count: Int,
)

/** 가계부 한 달의 카테고리별 지출 (큰 순서), 지난달 금액과 함께 */
fun categoryShares(points: List<ExpensePoint>, periods: PeriodCalculator, month: YearMonth): List<CategoryShare> {
    val byMonth = points.groupBy { periods.periodOf(it.occurredAt.toLocalDate()).label }
    val previous = byMonth[month.minusMonths(1)].orEmpty()
        .groupBy { it.categoryId }
        .mapValues { (_, list) -> list.sumOf { it.amount } }
    return byMonth[month].orEmpty()
        .groupBy { it.categoryId }
        .map { (id, list) -> CategoryShare(id, list.sumOf { it.amount }, previous[id] ?: 0, list.size) }
        .sortedByDescending { it.total }
}

/** 달마다의 지출 합계 (months 순서대로, 지출이 없으면 0) */
fun monthlyTotals(points: List<ExpensePoint>, periods: PeriodCalculator, months: List<YearMonth>): List<Pair<YearMonth, Long>> {
    val sums = points.groupBy { periods.periodOf(it.occurredAt.toLocalDate()).label }
        .mapValues { (_, list) -> list.sumOf { it.amount } }
    return months.map { it to (sums[it] ?: 0L) }
}

/**
 * 지난달과 비교할 금액.
 * 보고 있는 달이 아직 진행 중이면 지난달도 같은 날수까지만 더한다. (월초에 "덜 썼다"로 잘못 보이지 않게)
 * @return 비교 금액과, 같은 날수까지만 더했는지 여부
 */
fun previousComparable(
    points: List<ExpensePoint>,
    periods: PeriodCalculator,
    month: YearMonth,
    today: LocalDate,
): Pair<Long, Boolean> {
    val current = periods.periodFor(month)
    val previous = periods.periodFor(month.minusMonths(1))
    val inProgress = today in current
    val cutoff = if (inProgress) {
        previous.start.plusDays(ChronoUnit.DAYS.between(current.start, today) + 1)
    } else {
        previous.endExclusive
    }
    val amount = points
        .filter {
            val date = it.occurredAt.toLocalDate()
            !date.isBefore(previous.start) && date.isBefore(minOf(cutoff, previous.endExclusive))
        }
        .sumOf { it.amount }
    return amount to inProgress
}

/** 도넛 조각. categoryIds가 여러 개면 '그 외'로 묶인 조각이다. */
data class DonutGroup(
    val categoryIds: List<Long?>,
    val total: Long,
    val isOther: Boolean,
)

/**
 * 도넛은 한눈에 보는 용도라 6조각을 넘기지 않는다.
 * 큰 카테고리 maxNamed개를 보여주고 나머지는 '그 외'로 묶는다.
 * '기타'·미분류처럼 원래 '그 외'인 것은 항상 '그 외'에 넣는다.
 */
fun donutGroups(
    shares: List<CategoryShare>,
    isAlwaysOther: (Long?) -> Boolean,
    maxNamed: Int = 5,
): List<DonutGroup> {
    val candidates = shares.filter { it.total > 0 }
    var named = candidates.filterNot { isAlwaysOther(it.categoryId) }.take(maxNamed)
    var rest = candidates - named.toSet()
    // 남은 게 '그 외'가 아닌 카테고리 하나뿐이면 굳이 묶지 않고 그대로 보여준다. (조각 수는 같다)
    if (rest.size == 1 && !isAlwaysOther(rest.single().categoryId)) {
        named = named + rest
        rest = emptyList()
    }
    val groups = named.map { DonutGroup(listOf(it.categoryId), it.total, isOther = false) }
    return if (rest.isEmpty()) groups else groups + DonutGroup(rest.map { it.categoryId }, rest.sumOf { it.total }, isOther = true)
}

/** 하루 지출을 0~4단계로 나누는 기준 */
data class HeatScale(val thresholds: List<Long>) {
    fun level(amount: Long): Int = if (amount <= 0) 0 else 1 + thresholds.count { amount > it }
}

/**
 * 하루 예산이 있으면 그 절반·1배·2배를 경계로 삼는다. (예산 대비로 읽히게)
 * 없으면 이 달 가장 많이 쓴 날의 1/4·2/4·3/4를 경계로 삼는다.
 */
fun heatScale(dailyBudget: Long, maxDaily: Long): HeatScale =
    if (dailyBudget > 0) {
        HeatScale(listOf(dailyBudget / 2, dailyBudget, dailyBudget * 2))
    } else {
        HeatScale(listOf(maxDaily / 4, maxDaily / 2, maxDaily * 3 / 4))
    }

/** 한 달 동안 한 결제수단으로 쓴 돈 */
data class PaymentShare(val paymentMethodId: Long?, val total: Long, val count: Int)

fun paymentShares(points: List<ExpensePoint>, periods: PeriodCalculator, month: YearMonth): List<PaymentShare> =
    points.filter { periods.periodOf(it.occurredAt.toLocalDate()).label == month }
        .groupBy { it.paymentMethodId }
        .map { (id, list) -> PaymentShare(id, list.sumOf { it.amount }, list.size) }
        .sortedByDescending { it.total }

/**
 * 카드 실적은 카드사 기준인 달력의 한 달(1일 ~ 말일)로 센다.
 * 보고 있는 가계부 달이 진행 중이면 오늘이 속한 달, 지난 달이면 그 가계부 달이 끝나는 달.
 */
fun cardMonthFor(period: BudgetPeriod, today: LocalDate): YearMonth =
    if (today in period) YearMonth.from(today) else YearMonth.from(period.endInclusive)

/** 달력 한 달 동안 그 결제수단으로 쓴 돈 */
fun cardSpent(points: List<ExpensePoint>, paymentMethodId: Long, month: YearMonth): Long =
    points.filter { it.paymentMethodId == paymentMethodId && YearMonth.from(it.occurredAt.toLocalDate()) == month }
        .sumOf { it.amount }

/** 새 카테고리 색: 아직 안 쓴 팔레트 번호 중 가장 앞의 것, 다 쓰였으면 회색(0) */
fun nextColorSlot(usedSlots: Collection<Int>): Int = (1..8).firstOrNull { it !in usedSlots } ?: 0

data class MerchantTotal(val name: String, val total: Long, val count: Int)

/** 많이 쓴 곳: 가맹점(없으면 메모) 기준으로 묶는다. */
fun topMerchants(rows: List<TransactionRow>, limit: Int = 3): List<MerchantTotal> =
    rows.groupBy { (it.merchant ?: it.memo)?.trim()?.takeIf(String::isNotEmpty) ?: NO_MEMO }
        .map { (name, list) -> MerchantTotal(name, list.sumOf { it.amount }, list.size) }
        .sortedByDescending { it.total }
        .take(limit)

const val NO_MEMO = "메모 없음"
