package com.mj.money.budget

import com.mj.money.data.db.MjMoneyDatabase
import com.mj.money.data.db.PaymentKind
import com.mj.money.data.db.TransactionRow
import com.mj.money.data.db.TxStatus
import com.mj.money.data.db.TxType
import com.mj.money.data.settings.BudgetSettings
import com.mj.money.data.settings.SettingsRepository
import com.mj.money.domain.BudgetCalculator
import com.mj.money.domain.BudgetPeriod
import com.mj.money.domain.BudgetSnapshot
import com.mj.money.domain.FixedCharge
import com.mj.money.domain.PeriodCalculator
import com.mj.money.domain.RecurringItem
import com.mj.money.domain.buildRecurringItems
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

/** 이번 달 예산 상황을 한 번에 묶은 것 (홈 화면, 예산 알림이 같이 쓴다) */
data class BudgetView(
    val period: BudgetPeriod,
    val today: LocalDate,
    val settings: BudgetSettings,
    val transactions: List<TransactionRow>,
    val fixedItems: List<RecurringItem>,
    /** 예산을 정하지 않았으면 null */
    val snapshot: BudgetSnapshot?,
    /** 이번 달 비상금에서 쓴 돈 (예산에서 빠진다) */
    val emergencyUsed: Long,
    /** 이번 달 '예산에서 빼기'로 비상금에 채운 돈 (쓴 돈에 들어간다) */
    val emergencyRefillCounted: Long,
)

class BudgetTracker(
    private val db: MjMoneyDatabase,
    private val settings: SettingsRepository,
    private val periods: PeriodCalculator,
) {
    fun observe(today: LocalDate): Flow<BudgetView> {
        val period = periods.periodOf(today)
        val from = period.startMillis()
        val to = period.endMillis()
        val transactionDao = db.transactionDao()

        return combine(
            settings.budget,
            transactionDao.observeBetween(from, to),
            db.recurringExpenseDao().observeRows(),
            transactionDao.observeRecurringIdsBetween(from, to),
            db.emergencyDao().observeBudgetedRefills(from, to),
        ) { budget, transactions, recurringRows, recordedIds, budgetedRefills ->
            val fixedItems = buildRecurringItems(recurringRows, recordedIds.toSet(), period, today)
            val expenses = transactions.filter { it.type == TxType.EXPENSE && it.status != TxStatus.CANCELED }
            val (emergencyPaid, regular) = expenses.partition { it.paymentMethodKind == PaymentKind.EMERGENCY }

            val snapshot = if (budget.monthlyBudget > 0) {
                BudgetCalculator.calculate(
                    budget = budget.monthlyBudget,
                    includeFixed = budget.includeFixed,
                    period = period,
                    today = today,
                    variableSpent = regular.filter { it.recurringRuleId == null }.sumOf { it.amount } + budgetedRefills,
                    fixedSpent = regular.filter { it.recurringRuleId != null }.sumOf { it.amount },
                    fixedCharges = fixedItems
                        .filterNot { it.inTrial }
                        .map { FixedCharge(it.expense.amount, it.dueDate, it.recorded) },
                )
            } else {
                null
            }
            BudgetView(
                period = period,
                today = today,
                settings = budget,
                transactions = transactions,
                fixedItems = fixedItems,
                snapshot = snapshot,
                emergencyUsed = emergencyPaid.sumOf { it.amount },
                emergencyRefillCounted = budgetedRefills,
            )
        }
    }
}
