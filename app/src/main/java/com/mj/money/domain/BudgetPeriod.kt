package com.mj.money.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 가계부의 "한 달".
 * 시작일이 25일이면 9.25 ~ 10.24가 한 달이고, 끝나는 달 이름을 따서 10월(label)이라고 부른다.
 */
data class BudgetPeriod(
    val label: YearMonth,
    val start: LocalDate,
    val endExclusive: LocalDate,
) {
    val endInclusive: LocalDate get() = endExclusive.minusDays(1)

    operator fun contains(date: LocalDate): Boolean =
        !date.isBefore(start) && date.isBefore(endExclusive)

    fun startMillis(zone: ZoneId = ZoneId.systemDefault()): Long =
        start.atStartOfDay(zone).toInstant().toEpochMilli()

    fun endMillis(zone: ZoneId = ZoneId.systemDefault()): Long =
        endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
}

class PeriodCalculator(private val startDay: Int) {

    init {
        require(startDay in 1..28) { "월 시작일은 1~28일 사이여야 해요: $startDay" }
    }

    fun periodFor(label: YearMonth): BudgetPeriod {
        val start = if (startDay == 1) label.atDay(1) else label.minusMonths(1).atDay(startDay)
        return BudgetPeriod(label, start, start.plusMonths(1))
    }

    fun periodOf(date: LocalDate): BudgetPeriod {
        val month = YearMonth.from(date)
        val label = if (startDay == 1 || date.dayOfMonth < startDay) month else month.plusMonths(1)
        return periodFor(label)
    }
}
