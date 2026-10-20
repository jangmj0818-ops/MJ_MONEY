package com.mj.money.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 이번 달에 나가는 고정지출 하나 (무료 체험 중인 것은 넣지 않는다) */
data class FixedCharge(
    val amount: Long,
    val dueDate: LocalDate,
    val recorded: Boolean,
)

enum class BudgetStatus { ON_TRACK, AHEAD_OF_PACE, OVER }

data class BudgetSnapshot(
    val budget: Long,
    val includeFixed: Boolean,
    /** 예산과 비교하는 지출 */
    val spent: Long,
    /** 고정지출을 예산에서 뺀 설정일 때, 예산 밖에서 따로 나간 고정지출 */
    val separateFixedSpent: Long,
    /** 아직 나가지 않은 고정지출 (고정지출 포함 설정일 때만) */
    val upcomingFixed: Long,
    /** 오늘까지 이만큼 썼으면 딱 맞는 금액 (오늘 적정선) */
    val expectedByToday: Long,
    /** 남은 예산. 고정지출 포함이면 나갈 고정지출도 미리 뺀다. 음수일 수 있다. */
    val remaining: Long,
    /** 오늘을 포함한 남은 날 수 */
    val daysLeft: Int,
    val elapsedDays: Int,
    val totalDays: Int,
) {
    /** 하루에 써도 되는 금액. 넘지 않도록 백 원 아래는 버린다. (7,183원 → 7,100원) */
    val dailyAllowance: Long get() = if (remaining > 0) remaining / daysLeft / 100 * 100 else 0

    val usedPercent: Int get() = if (budget > 0) (spent * 100 / budget).toInt() else 0

    val status: BudgetStatus
        get() = when {
            spent > budget || remaining < 0 -> BudgetStatus.OVER
            spent > expectedByToday -> BudgetStatus.AHEAD_OF_PACE
            else -> BudgetStatus.ON_TRACK
        }
}

object BudgetCalculator {

    /**
     * 오늘 적정선:
     * - 고정지출 포함: 오늘까지 결제일이 온 고정지출 + (예산 - 고정지출 합계)를 날짜만큼 나눈 몫
     *   → 월말에 큰 고정지출이 있어도 적정선이 같이 올라가서 갑자기 초과로 보이지 않는다.
     * - 고정지출 제외: 예산을 날짜만큼 나눈 몫
     */
    fun calculate(
        budget: Long,
        includeFixed: Boolean,
        period: BudgetPeriod,
        today: LocalDate,
        variableSpent: Long,
        fixedSpent: Long,
        fixedCharges: List<FixedCharge>,
    ): BudgetSnapshot {
        val totalDays = ChronoUnit.DAYS.between(period.start, period.endExclusive).toInt()
        val elapsed = (ChronoUnit.DAYS.between(period.start, today) + 1).toInt().coerceIn(1, totalDays)
        val daysLeft = totalDays - elapsed + 1

        return if (includeFixed) {
            val fixedTotal = fixedCharges.sumOf { it.amount }
            val fixedDueByToday = fixedCharges.filter { !it.dueDate.isAfter(today) }.sumOf { it.amount }
            val upcoming = fixedCharges.filterNot { it.recorded }.sumOf { it.amount }
            val variableBudget = (budget - fixedTotal).coerceAtLeast(0)
            val spent = variableSpent + fixedSpent
            BudgetSnapshot(
                budget = budget,
                includeFixed = true,
                spent = spent,
                separateFixedSpent = 0,
                upcomingFixed = upcoming,
                expectedByToday = fixedDueByToday + variableBudget * elapsed / totalDays,
                remaining = budget - spent - upcoming,
                daysLeft = daysLeft,
                elapsedDays = elapsed,
                totalDays = totalDays,
            )
        } else {
            BudgetSnapshot(
                budget = budget,
                includeFixed = false,
                spent = variableSpent,
                separateFixedSpent = fixedSpent,
                upcomingFixed = 0,
                expectedByToday = budget * elapsed / totalDays,
                remaining = budget - variableSpent,
                daysLeft = daysLeft,
                elapsedDays = elapsed,
                totalDays = totalDays,
            )
        }
    }
}
