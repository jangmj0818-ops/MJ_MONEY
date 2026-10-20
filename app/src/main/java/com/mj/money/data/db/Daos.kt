package com.mj.money.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

private const val ROW_SELECT = """
    SELECT t.id, t.type, t.amount, t.occurredAt, t.merchant, t.memo, t.status, t.source,
           t.categoryId, c.name AS categoryName, c.emoji AS categoryEmoji,
           t.paymentMethodId, p.name AS paymentMethodName, p.kind AS paymentMethodKind, t.recurringRuleId
    FROM transactions t
    LEFT JOIN categories c ON c.id = t.categoryId
    LEFT JOIN payment_methods p ON p.id = t.paymentMethodId
"""

@Dao
interface TransactionDao {
    @Query("$ROW_SELECT WHERE t.occurredAt >= :from AND t.occurredAt < :to ORDER BY t.occurredAt DESC, t.id DESC")
    fun observeBetween(from: Long, to: Long): Flow<List<TransactionRow>>

    @Query("$ROW_SELECT ORDER BY t.occurredAt DESC, t.id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionRow>>

    /** 기간 안의 모든 지출 (비상금·고정지출 포함, 취소 제외) */
    @Query(
        """
        SELECT occurredAt, amount, categoryId, paymentMethodId FROM transactions
        WHERE type = 'EXPENSE' AND status != 'CANCELED' AND occurredAt >= :from AND occurredAt < :to
        """
    )
    fun observeExpensePoints(from: Long, to: Long): Flow<List<ExpensePoint>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    /** 새 입력의 기본 결제수단. 비상금은 어쩌다 쓰는 것이라 기본값으로 삼지 않는다. */
    @Query(
        """
        SELECT t.paymentMethodId FROM transactions t
        JOIN payment_methods p ON p.id = t.paymentMethodId
        WHERE t.source = 'MANUAL' AND t.type = 'EXPENSE' AND p.kind != 'EMERGENCY'
        ORDER BY t.createdAt DESC LIMIT 1
        """
    )
    suspend fun lastManualExpensePaymentMethodId(): Long?

    /** 기간 안에 기록된 고정지출 id들 (고정지출 목록의 '기록 완료' 표시용) */
    @Query(
        """
        SELECT DISTINCT recurringRuleId FROM transactions
        WHERE recurringRuleId IS NOT NULL AND status != 'CANCELED'
          AND occurredAt >= :from AND occurredAt < :to
        """
    )
    fun observeRecurringIdsBetween(from: Long, to: Long): Flow<List<Long>>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM transactions
            WHERE recurringRuleId = :recurringId AND status != 'CANCELED'
              AND occurredAt >= :from AND occurredAt < :to
        )
        """
    )
    suspend fun existsForRecurringBetween(recurringId: Long, from: Long, to: Long): Boolean

    // ---------- 알림 자동 기록용 ----------

    /** 같은 결제의 다른 알림: 알림으로 만든 거래 중 금액·종류가 같고 시각이 가까운 것 */
    @Query(
        """
        SELECT * FROM transactions
        WHERE source = 'NOTIFICATION' AND type = :type AND amount = :amount AND status != 'CANCELED'
          AND occurredAt BETWEEN :from AND :to
        ORDER BY ABS(occurredAt - :around) LIMIT 1
        """
    )
    suspend fun findNotificationMatch(type: TxType, amount: Long, from: Long, to: Long, around: Long): TransactionEntity?

    /** 직접 입력한 같은 금액의 거래 (아직 알림과 연결되지 않은 것) */
    @Query(
        """
        SELECT * FROM transactions
        WHERE source = 'MANUAL' AND type = :type AND amount = :amount AND status != 'CANCELED'
          AND notificationLogId IS NULL AND occurredAt BETWEEN :from AND :to
        ORDER BY ABS(occurredAt - :around) LIMIT 1
        """
    )
    suspend fun findManualMatch(type: TxType, amount: Long, from: Long, to: Long, around: Long): TransactionEntity?

    /** 취소 알림의 원래 결제 */
    @Query(
        """
        SELECT * FROM transactions
        WHERE type = 'EXPENSE' AND amount = :amount AND status != 'CANCELED' AND occurredAt BETWEEN :from AND :to
        ORDER BY occurredAt DESC LIMIT 1
        """
    )
    suspend fun findCancelTarget(amount: Long, from: Long, to: Long): TransactionEntity?

    /** 가맹점 이름을 채울 거래: 알림으로 만든 거래 중 시각이 가까운 것 */
    @Query(
        """
        SELECT * FROM transactions
        WHERE source = 'NOTIFICATION' AND occurredAt BETWEEN :from AND :to
        ORDER BY ABS(occurredAt - :around)
        """
    )
    suspend fun findNotificationTransactionsBetween(from: Long, to: Long, around: Long): List<TransactionEntity>

    @Query(
        """
        SELECT * FROM transactions
        WHERE recurringRuleId = :recurringId AND status != 'CANCELED' AND occurredAt >= :from AND occurredAt < :to
        LIMIT 1
        """
    )
    suspend fun getForRecurringBetween(recurringId: Long, from: Long, to: Long): TransactionEntity?

    @Query("SELECT COUNT(*) FROM transactions WHERE status = 'NEEDS_REVIEW'")
    fun observeNeedsReviewCount(): Flow<Int>

    /** 개발용: 알림 처리를 다시 할 때 알림으로 만든 거래를 지우고, 직접 입력·고정지출 거래의 알림 연결을 푼다. */
    @Query("DELETE FROM transactions WHERE source = 'NOTIFICATION'")
    suspend fun deleteAllFromNotifications(): Int

    @Query("UPDATE transactions SET notificationLogId = NULL WHERE source != 'NOTIFICATION'")
    suspend fun unlinkNotifications()

    /** 개발용: 출처·상태별 거래 수 */
    @Query("SELECT source || '/' || status AS `key`, COUNT(*) AS count FROM transactions GROUP BY `key`")
    suspend fun sourceStatusSummary(): List<CountByKey>

    @Query("$ROW_SELECT WHERE t.status = 'NEEDS_REVIEW' ORDER BY t.occurredAt DESC, t.id DESC")
    fun observeNeedsReview(): Flow<List<TransactionRow>>

    /** 같은 가맹점의 확인 필요 거래들 (학습한 카테고리를 한꺼번에 적용할 때) */
    @Query("SELECT * FROM transactions WHERE status = 'NEEDS_REVIEW' AND merchant IS NOT NULL")
    suspend fun getNeedsReviewWithMerchant(): List<TransactionEntity>

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE hidden = 0 ORDER BY sortOrder")
    fun observeVisible(): Flow<List<CategoryEntity>>

    /** 숨긴 카테고리도 포함 (지난 내역의 통계용) */
    @Query("SELECT * FROM categories ORDER BY sortOrder")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("UPDATE categories SET emoji = :emoji WHERE name = :name AND type = :type")
    suspend fun updateEmoji(name: String, type: CategoryType, emoji: String): Int

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT IFNULL(MAX(sortOrder), -1) + 1 FROM categories")
    suspend fun nextSortOrder(): Int

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)
}

@Dao
interface PaymentMethodDao {
    @Query("SELECT * FROM payment_methods WHERE hidden = 0 ORDER BY sortOrder")
    fun observeVisible(): Flow<List<PaymentMethodEntity>>

    @Query("SELECT * FROM payment_methods WHERE kind = 'EMERGENCY' LIMIT 1")
    suspend fun getEmergency(): PaymentMethodEntity?

    /** 숨긴 것도 포함 (관리 화면, 지난 내역 통계용) */
    @Query("SELECT * FROM payment_methods ORDER BY sortOrder")
    fun observeAll(): Flow<List<PaymentMethodEntity>>

    @Query("SELECT * FROM payment_methods WHERE id = :id")
    suspend fun getById(id: Long): PaymentMethodEntity?

    @Query("SELECT IFNULL(MAX(sortOrder), -1) + 1 FROM payment_methods")
    suspend fun nextSortOrder(): Int

    @Insert
    suspend fun insert(paymentMethod: PaymentMethodEntity): Long

    @Update
    suspend fun update(paymentMethod: PaymentMethodEntity)
}

@Dao
interface EmergencyDao {
    @Query(
        """
        SELECT p.*, IFNULL(SUM(m.delta), 0) AS balance
        FROM emergency_pockets p
        LEFT JOIN emergency_moves m ON m.pocketId = p.id
        WHERE p.hidden = 0
        GROUP BY p.id
        ORDER BY p.sortOrder, p.id
        """
    )
    fun observePockets(): Flow<List<PocketBalance>>

    @Query(
        """
        SELECT m.*, p.name AS pocketName, c.name AS categoryName, c.emoji AS categoryEmoji,
               t.memo AS transactionMemo
        FROM emergency_moves m
        JOIN emergency_pockets p ON p.id = m.pocketId
        LEFT JOIN transactions t ON t.id = m.transactionId
        LEFT JOIN categories c ON c.id = t.categoryId
        ORDER BY m.occurredAt DESC, m.id DESC
        LIMIT 100
        """
    )
    fun observeMoves(): Flow<List<EmergencyMoveRow>>

    /** 기간 안에 '예산에서 빼기'로 채워 넣은 금액 */
    @Query(
        """
        SELECT IFNULL(SUM(delta), 0) FROM emergency_moves
        WHERE type = 'REFILL' AND countsAgainstBudget = 1 AND occurredAt >= :from AND occurredAt < :to
        """
    )
    fun observeBudgetedRefills(from: Long, to: Long): Flow<Long>

    @Query("SELECT * FROM emergency_moves WHERE transactionId = :transactionId LIMIT 1")
    suspend fun getByTransactionId(transactionId: Long): EmergencyMoveEntity?

    @Query("SELECT IFNULL(MAX(sortOrder), -1) + 1 FROM emergency_pockets")
    suspend fun nextPocketOrder(): Int

    @Insert
    suspend fun insertPocket(pocket: EmergencyPocketEntity): Long

    @Query("UPDATE emergency_pockets SET name = :name WHERE id = :id")
    suspend fun renamePocket(id: Long, name: String)

    @Query("UPDATE emergency_pockets SET hidden = 1 WHERE id = :id")
    suspend fun hidePocket(id: Long)

    @Insert
    suspend fun insertMove(move: EmergencyMoveEntity): Long

    @Update
    suspend fun updateMove(move: EmergencyMoveEntity)

    @Query("DELETE FROM emergency_moves WHERE id = :id")
    suspend fun deleteMove(id: Long)

    @Query("DELETE FROM emergency_moves WHERE transactionId = :transactionId")
    suspend fun deleteByTransactionId(transactionId: Long)
}

@Dao
interface RecurringExpenseDao {
    @Query(
        """
        SELECT r.*, c.name AS categoryName, c.emoji AS categoryEmoji, p.name AS paymentMethodName
        FROM recurring_expenses r
        LEFT JOIN categories c ON c.id = r.categoryId
        LEFT JOIN payment_methods p ON p.id = r.paymentMethodId
        ORDER BY r.dayOfMonth, r.id
        """
    )
    fun observeRows(): Flow<List<RecurringRow>>

    @Query("SELECT * FROM recurring_expenses WHERE id = :id")
    suspend fun getById(id: Long): RecurringExpenseEntity?

    @Query("SELECT * FROM recurring_expenses WHERE status = 'ACTIVE'")
    suspend fun getActive(): List<RecurringExpenseEntity>

    @Insert
    suspend fun insert(expense: RecurringExpenseEntity): Long

    @Update
    suspend fun update(expense: RecurringExpenseEntity)

    @Query("DELETE FROM recurring_expenses WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface NotificationLogDao {
    @Insert
    suspend fun insert(log: NotificationLogEntity): Long

    @Query("SELECT * FROM notification_logs ORDER BY postedAt DESC LIMIT 500")
    fun observeRecent(): Flow<List<NotificationLogEntity>>

    @Query("SELECT COUNT(*) FROM notification_logs")
    fun observeCount(): Flow<Int>

    @Query("SELECT MAX(postedAt) FROM notification_logs")
    fun observeLatestPostedAt(): Flow<Long?>

    /** 같은 알림이 갱신되며 여러 번 들어오는 경우를 거르기 위한 확인 */
    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM notification_logs
            WHERE packageName = :packageName AND IFNULL(title, '') = :title
              AND IFNULL(text, '') = :text AND postedAt >= :since
        )
        """
    )
    suspend fun existsSince(packageName: String, title: String, text: String, since: Long): Boolean

    @Query("DELETE FROM notification_logs")
    suspend fun deleteAll()

    @Query("DELETE FROM notification_logs WHERE packageName = :packageName")
    suspend fun deleteByPackage(packageName: String): Int

    @Query("SELECT * FROM notification_logs WHERE id = :id")
    suspend fun getById(id: Long): NotificationLogEntity?

    /** 앱·규칙별로 몇 건 모였는지 (내용 없이 개수만) */
    @Query(
        """
        SELECT packageName, matchedRule, COUNT(*) AS count, MAX(postedAt) AS lastPostedAt
        FROM notification_logs GROUP BY packageName, matchedRule
        """
    )
    suspend fun summary(): List<NotificationLogSummary>

    /** 개발용: 처리 결과별 개수 */
    @Query("SELECT IFNULL(parseResult, 'pending') AS `key`, COUNT(*) AS count FROM notification_logs GROUP BY `key`")
    suspend fun parseSummary(): List<CountByKey>

    /** 아직 해석하지 않은 알림 (오래된 것부터) */
    @Query("SELECT * FROM notification_logs WHERE parseResult IS NULL ORDER BY postedAt, id")
    suspend fun getUnprocessed(): List<NotificationLogEntity>

    @Query("UPDATE notification_logs SET parseResult = :result, transactionId = :transactionId WHERE id = :id")
    suspend fun setResult(id: Long, result: String, transactionId: Long?)

    /** 가까운 시각에 이미 거래와 연결된 다른 알림 (같은 결제의 다른 알림을 찾을 때) */
    @Query(
        """
        SELECT * FROM notification_logs
        WHERE transactionId IS NOT NULL AND id != :excludeId AND postedAt BETWEEN :from AND :to
        ORDER BY ABS(postedAt - :around)
        """
    )
    suspend fun findLinkedBetween(from: Long, to: Long, around: Long, excludeId: Long): List<NotificationLogEntity>

    /** 개발용: 알림 처리를 처음부터 다시 하려고 결과를 지운다. */
    @Query("UPDATE notification_logs SET parseResult = NULL, transactionId = NULL")
    suspend fun resetAllResults()

    /** 가까운 시각의 '가맹점만 있는 주문 알림' */
    @Query(
        """
        SELECT * FROM notification_logs
        WHERE parseResult = 'merchant_only' AND postedAt BETWEEN :from AND :to
        ORDER BY ABS(postedAt - :around)
        """
    )
    suspend fun findMerchantOnlyBetween(from: Long, to: Long, around: Long): List<NotificationLogEntity>
}

@Dao
interface MerchantRuleDao {
    @Query("SELECT * FROM merchant_rules")
    suspend fun getAll(): List<MerchantRuleEntity>

    @Upsert
    suspend fun upsert(rule: MerchantRuleEntity)
}
