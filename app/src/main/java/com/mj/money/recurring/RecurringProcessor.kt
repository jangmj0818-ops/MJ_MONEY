package com.mj.money.recurring

import androidx.room.withTransaction
import com.mj.money.data.db.MjMoneyDatabase
import com.mj.money.data.db.RecurringExpenseEntity
import com.mj.money.data.db.TransactionEntity
import com.mj.money.data.db.TxSource
import com.mj.money.data.db.TxStatus
import com.mj.money.data.db.TxType
import com.mj.money.domain.PeriodCalculator
import com.mj.money.domain.RecurringSchedule
import com.mj.money.domain.toEpochMillis
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalTime

/** 고정지출을 내역에 기록한다. 앱 시작과 매일 작업이 동시에 돌아도 중복 기록되지 않게 잠근다. */
class RecurringProcessor(
    private val db: MjMoneyDatabase,
    private val periods: PeriodCalculator,
) {
    private val lock = Mutex()

    /** 결제일이 지난 고정지출을 자동으로 기록한다. */
    suspend fun recordDue(today: LocalDate = LocalDate.now()) = lock.withLock {
        val dao = db.recurringExpenseDao()
        for (expense in dao.getActive()) {
            val dates = RecurringSchedule.pendingAutoRecordDates(expense, today)
            if (dates.isEmpty()) continue
            db.withTransaction {
                dates.forEach { recordIfMissing(expense, it) }
                dao.update(expense.copy(lastRecordedEpochDay = dates.last().toEpochDay()))
            }
        }
    }

    /** 목록에서 '기록' 버튼을 눌렀을 때 그 결제일로 기록한다. */
    suspend fun recordNow(expenseId: Long, dueDate: LocalDate) = lock.withLock {
        val dao = db.recurringExpenseDao()
        val expense = dao.getById(expenseId) ?: return@withLock
        db.withTransaction {
            recordIfMissing(expense, dueDate)
            val last = maxOf(expense.lastRecordedEpochDay ?: Long.MIN_VALUE, dueDate.toEpochDay())
            dao.update(expense.copy(lastRecordedEpochDay = last))
        }
    }

    /** 같은 가계부 한 달 안에 이미 기록이 있으면 건너뛴다. */
    private suspend fun recordIfMissing(expense: RecurringExpenseEntity, dueDate: LocalDate) {
        val transactionDao = db.transactionDao()
        val period = periods.periodOf(dueDate)
        if (transactionDao.existsForRecurringBetween(expense.id, period.startMillis(), period.endMillis())) return

        val now = System.currentTimeMillis()
        transactionDao.insert(
            TransactionEntity(
                type = TxType.EXPENSE,
                amount = expense.amount,
                occurredAt = dueDate.atTime(RECORD_TIME).toEpochMillis(),
                categoryId = expense.categoryId,
                paymentMethodId = expense.paymentMethodId,
                merchant = expense.name,
                source = TxSource.RECURRING,
                status = TxStatus.CONFIRMED,
                recurringRuleId = expense.id,
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    private companion object {
        val RECORD_TIME: LocalTime = LocalTime.of(9, 0)
    }
}
