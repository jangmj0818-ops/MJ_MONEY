package com.mj.money.emergency

import androidx.room.withTransaction
import com.mj.money.data.db.EmergencyMoveEntity
import com.mj.money.data.db.EmergencyMoveRow
import com.mj.money.data.db.EmergencyMoveType
import com.mj.money.data.db.EmergencyPocketEntity
import com.mj.money.data.db.MjMoneyDatabase
import com.mj.money.data.db.PocketBalance
import com.mj.money.domain.CountPlan
import kotlinx.coroutines.flow.Flow

/** 비상금: 장소별 금액과 꺼내 쓰기·채워 넣기·세어 보기 기록 */
class EmergencyRepository(private val db: MjMoneyDatabase) {
    private val dao = db.emergencyDao()

    fun observePockets(): Flow<List<PocketBalance>> = dao.observePockets()

    fun observeMoves(): Flow<List<EmergencyMoveRow>> = dao.observeMoves()

    suspend fun refill(pocketId: Long, amount: Long, occurredAt: Long, memo: String?, countsAgainstBudget: Boolean) {
        dao.insertMove(
            EmergencyMoveEntity(
                pocketId = pocketId,
                type = EmergencyMoveType.REFILL,
                delta = amount,
                occurredAt = occurredAt,
                memo = memo,
                countsAgainstBudget = countsAgainstBudget,
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun applyCount(plan: CountPlan) = db.withTransaction {
        val now = System.currentTimeMillis()
        fun countMove(pocketId: Long, delta: Long) = EmergencyMoveEntity(
            pocketId = pocketId,
            type = EmergencyMoveType.COUNT,
            delta = delta,
            occurredAt = now,
            createdAt = now,
        )
        plan.newPockets.forEach { (name, amount) ->
            val id = dao.insertPocket(EmergencyPocketEntity(name = name, sortOrder = dao.nextPocketOrder()))
            if (amount != 0L) dao.insertMove(countMove(id, amount))
        }
        plan.renames.forEach { (id, name) -> dao.renamePocket(id, name) }
        plan.adjustments.forEach { (id, delta) -> dao.insertMove(countMove(id, delta)) }
        plan.hides.forEach { dao.hidePocket(it) }
    }

    /**
     * 비상금으로 낸 지출과 비상금 기록을 맞춘다. 내역을 저장하는 DB 트랜잭션 안에서 부른다.
     * pocketId가 null이면 비상금 지출이 아니므로 연결된 기록을 지운다.
     */
    suspend fun syncExpense(transactionId: Long, pocketId: Long?, amount: Long, occurredAt: Long) {
        val existing = dao.getByTransactionId(transactionId)
        when {
            pocketId == null -> if (existing != null) dao.deleteMove(existing.id)
            existing == null -> dao.insertMove(
                EmergencyMoveEntity(
                    pocketId = pocketId,
                    type = EmergencyMoveType.USE,
                    delta = -amount,
                    occurredAt = occurredAt,
                    transactionId = transactionId,
                    createdAt = System.currentTimeMillis(),
                )
            )
            else -> dao.updateMove(existing.copy(pocketId = pocketId, delta = -amount, occurredAt = occurredAt))
        }
    }

    suspend fun pocketIdForTransaction(transactionId: Long): Long? = dao.getByTransactionId(transactionId)?.pocketId

    suspend fun deleteForTransaction(transactionId: Long) = dao.deleteByTransactionId(transactionId)

    suspend fun deleteMove(id: Long) = dao.deleteMove(id)
}
