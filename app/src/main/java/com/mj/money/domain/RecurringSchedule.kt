package com.mj.money.domain

import com.mj.money.data.db.RecurringExpenseEntity
import com.mj.money.data.db.RecurringStatus
import java.time.LocalDate
import java.time.YearMonth

/** 매월 결제일 계산 */
object RecurringSchedule {

    /** 그 달의 결제일. 29~31일이 없는 달은 말일. */
    fun dueDateIn(month: YearMonth, dayOfMonth: Int): LocalDate =
        month.atDay(minOf(dayOfMonth, month.lengthOfMonth()))

    /** 가계부 한 달(예: 9.25 ~ 10.24) 안의 결제일. 한 달에 정확히 한 번 있다. */
    fun dueDateIn(period: BudgetPeriod, dayOfMonth: Int): LocalDate {
        val startMonth = YearMonth.from(period.start)
        val first = dueDateIn(startMonth, dayOfMonth)
        return if (first in period) first else dueDateIn(startMonth.plusMonths(1), dayOfMonth)
    }

    /** afterExclusive 다음부터 untilInclusive까지의 결제일들 */
    fun dueDatesBetween(dayOfMonth: Int, afterExclusive: LocalDate, untilInclusive: LocalDate): List<LocalDate> {
        val result = mutableListOf<LocalDate>()
        var month = YearMonth.from(afterExclusive)
        while (true) {
            val due = dueDateIn(month, dayOfMonth)
            if (due.isAfter(untilInclusive)) break
            if (due.isAfter(afterExclusive)) result += due
            month = month.plusMonths(1)
        }
        return result
    }

    /** afterExclusive 다음에 오는 첫 결제일 */
    fun nextDueDate(dayOfMonth: Int, afterExclusive: LocalDate): LocalDate {
        val month = YearMonth.from(afterExclusive)
        val thisMonth = dueDateIn(month, dayOfMonth)
        return if (thisMonth.isAfter(afterExclusive)) thisMonth else dueDateIn(month.plusMonths(1), dayOfMonth)
    }

    fun isInTrial(expense: RecurringExpenseEntity, date: LocalDate): Boolean =
        expense.trialEndEpochDay?.let { date.toEpochDay() <= it } ?: false

    /**
     * 자동으로 기록해야 할 결제일들.
     * 등록일, 마지막 기록일, 무료 체험 마지막 날 중 가장 늦은 날의 다음 결제일부터 오늘까지.
     */
    fun pendingAutoRecordDates(expense: RecurringExpenseEntity, today: LocalDate): List<LocalDate> {
        if (expense.status != RecurringStatus.ACTIVE || !expense.autoRecord) return emptyList()
        val after = listOfNotNull(
            expense.startEpochDay,
            expense.lastRecordedEpochDay,
            expense.trialEndEpochDay,
        ).max()
        return dueDatesBetween(expense.dayOfMonth, LocalDate.ofEpochDay(after), today)
    }
}
