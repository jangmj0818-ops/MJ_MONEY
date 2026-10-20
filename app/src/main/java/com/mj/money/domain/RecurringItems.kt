package com.mj.money.domain

import com.mj.money.data.db.RecurringExpenseEntity
import com.mj.money.data.db.RecurringRow
import com.mj.money.data.db.RecurringStatus
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 가계부 한 달 안에서 본 고정지출 하나 */
data class RecurringItem(
    val row: RecurringRow,
    val dueDate: LocalDate,
    val recorded: Boolean,
    val daysUntil: Long,
    val inTrial: Boolean,
) {
    val expense: RecurringExpenseEntity get() = row.expense
}

/** 사용 중인 고정지출들을 이 달의 결제일·기록 여부와 함께 정리한다. */
fun buildRecurringItems(
    rows: List<RecurringRow>,
    recordedIds: Set<Long>,
    period: BudgetPeriod,
    today: LocalDate,
): List<RecurringItem> = rows
    .filter { it.expense.status == RecurringStatus.ACTIVE }
    .map { row ->
        val due = RecurringSchedule.dueDateIn(period, row.expense.dayOfMonth)
        RecurringItem(
            row = row,
            dueDate = due,
            recorded = row.expense.id in recordedIds,
            daysUntil = ChronoUnit.DAYS.between(today, due),
            inTrial = RecurringSchedule.isInTrial(row.expense, due),
        )
    }
