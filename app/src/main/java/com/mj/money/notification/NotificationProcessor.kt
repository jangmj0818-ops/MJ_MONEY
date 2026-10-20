package com.mj.money.notification

import androidx.room.withTransaction
import com.mj.money.data.db.CategoryEntity
import com.mj.money.data.db.CategoryType
import com.mj.money.data.db.MerchantRuleEntity
import com.mj.money.data.db.MjMoneyDatabase
import com.mj.money.data.db.NotificationLogEntity
import com.mj.money.data.db.PaymentKind
import com.mj.money.data.db.PaymentMethodEntity
import com.mj.money.data.db.TransactionEntity
import com.mj.money.data.db.TxSource
import com.mj.money.data.db.TxStatus
import com.mj.money.data.db.TxType
import com.mj.money.domain.PeriodCalculator
import com.mj.money.domain.RecurringSchedule
import com.mj.money.domain.betterMerchant
import com.mj.money.domain.builtInCategoryHint
import com.mj.money.domain.findMerchantRule
import com.mj.money.domain.isGenericMerchant
import com.mj.money.domain.normalizeMerchant
import com.mj.money.domain.toLocalDate
import com.mj.money.notification.parse.Direction
import com.mj.money.notification.parse.MethodHint
import com.mj.money.notification.parse.MethodKind
import com.mj.money.notification.parse.ParseOutcome
import com.mj.money.notification.parse.PaymentParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * 모은 결제 알림을 해석해서 내역으로 저장한다.
 *
 * 한 번의 결제에 알림이 여러 개 오므로(카드사 앱 + 카카오톡 + 페이) 이렇게 처리한다.
 * 1. 취소면 원래 결제를 취소로 바꾼다.
 * 2. 3분 안에 같은 금액의 알림 거래가 있으면 합친다. (결제수단은 카드 > 계좌 > 페이, 가맹점은 더 자세한 쪽)
 * 3. 금액·결제수단이 같고 결제일이 가까운 고정지출이면 그 내역과 연결한다.
 * 4. 직접 입력한 같은 날·같은 금액 내역이 있으면 연결만 한다.
 * 5. 아니면 새 거래를 만든다. 카테고리를 알면 확정, 모르면 '확인 필요'.
 *
 * 결과는 알림 로그의 parseResult에 남는다. (null = 아직 처리 안 함)
 */
class NotificationProcessor(
    private val db: MjMoneyDatabase,
    private val periods: PeriodCalculator,
) {
    private val lock = Mutex()
    private val logDao get() = db.notificationLogDao()
    private val transactionDao get() = db.transactionDao()

    /** 아직 처리하지 않은 알림을 오래된 것부터 처리한다. (앱 시작 때, 지난 알림 소급) */
    suspend fun processPending() = lock.withLock {
        logDao.getUnprocessed().forEach { processLocked(it) }
    }

    /** 방금 모은 알림 하나를 처리한다. */
    suspend fun process(logId: Long) = lock.withLock {
        val log = logDao.getById(logId) ?: return@withLock
        if (log.parseResult == null) processLocked(log)
    }

    private suspend fun processLocked(log: NotificationLogEntity) {
        val outcome = PaymentParser.parse(log.packageName, log.title, log.text, log.subText)
        val (result, transactionId) = try {
            // 목록 조회(Flow)는 트랜잭션 밖에서 미리 읽어둔다.
            val ctx = if (outcome is ParseOutcome.Ignore) null else Context.load(db)
            db.withTransaction {
                when (outcome) {
                    is ParseOutcome.Ignore -> "ignored:${outcome.reason}" to null
                    is ParseOutcome.MerchantOnly -> {
                        fillMerchantNearby(log.postedAt, outcome, ctx!!)
                        MERCHANT_ONLY to null
                    }
                    is ParseOutcome.Payment -> handlePayment(log, outcome, ctx!!)
                }
            }
        } catch (e: Exception) {
            "error:${e::class.simpleName}" to null
        }
        logDao.setResult(log.id, result, transactionId)
    }

    /** 한 번 처리하는 동안 쓰는 결제수단·카테고리·학습 목록 */
    private class Context(
        val methods: List<PaymentMethodEntity>,
        val categories: List<CategoryEntity>,
        val rules: Map<String, Long>,
    ) {
        companion object {
            suspend fun load(db: MjMoneyDatabase) = Context(
                methods = db.paymentMethodDao().observeAll().first(),
                categories = db.categoryDao().observeAll().first(),
                rules = db.merchantRuleDao().getAll().associate { it.merchantKey to it.categoryId },
            )
        }
    }

    private suspend fun handlePayment(log: NotificationLogEntity, p: ParseOutcome.Payment, ctx: Context): Pair<String, Long?> {
        val at = log.postedAt
        val now = System.currentTimeMillis()
        val method = resolveMethod(p.method, ctx.methods)
        val via = p.via?.let { resolveMethod(it, ctx.methods) }?.takeIf { it.id != method?.id }

        if (p.direction == Direction.CANCEL) {
            val target = transactionDao.findCancelTarget(p.amount, at - CANCEL_WINDOW, at + MERGE_WINDOW)
                ?: return "cancel_unmatched" to null
            transactionDao.update(target.copy(status = TxStatus.CANCELED, updatedAt = now))
            return "canceled" to target.id
        }
        val type = if (p.direction == Direction.INCOME) TxType.INCOME else TxType.EXPENSE

        // 1) 같은 결제의 다른 알림이 이미 어떤 거래와 연결됐으면 그 거래에 붙인다.
        //    (그 거래가 직접 입력·고정지출이면 사용자가 정한 내용을 바꾸지 않는다)
        siblingTransaction(log, p)?.let { sibling ->
            if (sibling.source == TxSource.NOTIFICATION) transactionDao.update(merge(sibling, method, via, p, ctx, now))
            return "merged" to sibling.id
        }
        // 같은 결제의 알림 거래가 시각으로만 찾아지는 경우 (예: 연결 정보가 없는 예전 거래)
        transactionDao.findNotificationMatch(type, p.amount, at - MERGE_WINDOW, at + MERGE_WINDOW, at)?.let { existing ->
            transactionDao.update(merge(existing, method, via, p, ctx, now))
            return "merged" to existing.id
        }

        // 2) 고정지출과 연결
        if (type == TxType.EXPENSE) {
            linkRecurring(log, p, method, via, now)?.let { return it }
        }

        // 3) 직접 입력한 내역과 겹치면 연결만
        val day = at.toLocalDate()
        val zone = ZoneId.systemDefault()
        transactionDao.findManualMatch(
            type, p.amount,
            day.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            day.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli(),
            at,
        )?.let { manual ->
            transactionDao.update(manual.copy(notificationLogId = log.id, updatedAt = now))
            return "manual" to manual.id
        }

        // 4) 새 거래
        val (orderMerchant, orderItem) = nearbyOrder(at)
        val merchant = if (isGenericMerchant(p.merchant) && orderMerchant != null) orderMerchant else p.merchant
        val categoryId = resolveCategory(merchant, p.categoryHint, type, ctx)
        val id = transactionDao.insert(
            TransactionEntity(
                type = type,
                amount = p.amount,
                occurredAt = at,
                categoryId = categoryId,
                paymentMethodId = method?.id,
                viaPaymentMethodId = via?.id,
                merchant = merchant,
                memo = orderItem.takeIf { merchant == orderMerchant },
                source = TxSource.NOTIFICATION,
                status = if (categoryId != null) TxStatus.CONFIRMED else TxStatus.NEEDS_REVIEW,
                notificationLogId = log.id,
                createdAt = now,
                updatedAt = now,
            )
        )
        return "created" to id
    }

    /** 3분 안에 금액·종류가 같은 다른 알림이 이미 연결된 거래 */
    private suspend fun siblingTransaction(log: NotificationLogEntity, p: ParseOutcome.Payment): TransactionEntity? {
        val siblings = logDao.findLinkedBetween(log.postedAt - MERGE_WINDOW, log.postedAt + MERGE_WINDOW, log.postedAt, log.id)
        for (sibling in siblings) {
            val parsed = PaymentParser.parse(sibling.packageName, sibling.title, sibling.text, sibling.subText)
            if (parsed is ParseOutcome.Payment && parsed.amount == p.amount && parsed.direction == p.direction) {
                sibling.transactionId?.let { id -> transactionDao.getById(id) }?.let { return it }
            }
        }
        return null
    }

    /** 개발용: 알림으로 만든 거래를 지우고 모든 알림을 처음부터 다시 처리한다. (사용자가 고친 자동 기록도 사라진다) */
    suspend fun reprocessAll(): Int {
        val deleted = lock.withLock {
            db.withTransaction {
                val count = transactionDao.deleteAllFromNotifications()
                transactionDao.unlinkNotifications()
                logDao.resetAllResults()
                count
            }
        }
        processPending()
        return deleted
    }

    /** 같은 결제의 두 알림을 합친다. 이미 사용자가 정한 값(카테고리 등)은 바꾸지 않는다. */
    private fun merge(
        existing: TransactionEntity,
        method: PaymentMethodEntity?,
        via: PaymentMethodEntity?,
        p: ParseOutcome.Payment,
        ctx: Context,
        now: Long,
    ): TransactionEntity {
        val existingMethod = ctx.methods.firstOrNull { it.id == existing.paymentMethodId }
        val chosen = if (rank(method) > rank(existingMethod)) method else existingMethod
        // 고르지 않은 쪽이 페이였다면 '경유'로 남긴다.
        val viaCandidates = listOfNotNull(
            via,
            ctx.methods.firstOrNull { it.id == existing.viaPaymentMethodId },
            method?.takeIf { it.kind == PaymentKind.PAY },
            existingMethod?.takeIf { it.kind == PaymentKind.PAY },
        )
        val viaChosen = viaCandidates.firstOrNull { it.id != chosen?.id }
        val merchant = betterMerchant(existing.merchant, p.merchant)
        val categoryId = existing.categoryId ?: resolveCategory(merchant, p.categoryHint, existing.type, ctx)
        val status = if (existing.status == TxStatus.NEEDS_REVIEW && categoryId != null) TxStatus.CONFIRMED else existing.status
        return existing.copy(
            paymentMethodId = chosen?.id,
            viaPaymentMethodId = viaChosen?.id,
            merchant = merchant,
            categoryId = categoryId,
            status = status,
            updatedAt = now,
        )
    }

    /** 결제수단 우선순위: 실제로 돈이 나간 곳일수록 높다. */
    private fun rank(method: PaymentMethodEntity?): Int = when (method?.kind) {
        PaymentKind.CREDIT_CARD, PaymentKind.CHECK_CARD -> 3
        PaymentKind.ACCOUNT -> 2
        PaymentKind.PAY -> 1
        else -> 0
    }

    private suspend fun linkRecurring(
        log: NotificationLogEntity,
        p: ParseOutcome.Payment,
        method: PaymentMethodEntity?,
        via: PaymentMethodEntity?,
        now: Long,
    ): Pair<String, Long?>? {
        val date = log.postedAt.toLocalDate()
        val match = db.recurringExpenseDao().getActive()
            .filter { it.amount == p.amount }
            .filter { method == null || it.paymentMethodId == null || it.paymentMethodId == method.id || it.paymentMethodId == via?.id }
            .map { rule ->
                val month = YearMonth.from(date)
                val due = listOf(month.minusMonths(1), month, month.plusMonths(1))
                    .map { RecurringSchedule.dueDateIn(it, rule.dayOfMonth) }
                    .minBy { abs(ChronoUnit.DAYS.between(it, date)) }
                rule to due
            }
            .filter { (_, due) -> abs(ChronoUnit.DAYS.between(due, date)) <= RECURRING_DAYS }
            .minByOrNull { (_, due) -> abs(ChronoUnit.DAYS.between(due, date)) }
            ?: return null

        val (rule, due) = match
        val period = periods.periodOf(due)
        val existing = transactionDao.getForRecurringBetween(rule.id, period.startMillis(), period.endMillis())
        if (existing != null) {
            if (existing.notificationLogId == null) {
                transactionDao.update(existing.copy(notificationLogId = log.id, updatedAt = now))
            }
            return "recurring" to existing.id
        }
        // 자동 기록보다 알림이 먼저 오면 여기서 기록하고, 자동 기록이 다시 하지 않게 표시한다.
        val id = transactionDao.insert(
            TransactionEntity(
                type = TxType.EXPENSE,
                amount = p.amount,
                occurredAt = log.postedAt,
                categoryId = rule.categoryId,
                paymentMethodId = rule.paymentMethodId ?: method?.id,
                viaPaymentMethodId = via?.id?.takeIf { it != rule.paymentMethodId },
                merchant = rule.name,
                source = TxSource.RECURRING,
                status = TxStatus.CONFIRMED,
                recurringRuleId = rule.id,
                notificationLogId = log.id,
                createdAt = now,
                updatedAt = now,
            )
        )
        val last = maxOf(rule.lastRecordedEpochDay ?: Long.MIN_VALUE, due.toEpochDay())
        db.recurringExpenseDao().update(rule.copy(lastRecordedEpochDay = last))
        return "recurring_new" to id
    }

    /** 가까운 시각의 금액 없는 주문 알림에서 가맹점·상품 이름을 가져온다. */
    private suspend fun nearbyOrder(at: Long): Pair<String?, String?> {
        val order = logDao.findMerchantOnlyBetween(at - ORDER_WINDOW, at + ORDER_WINDOW, at)
            .firstNotNullOfOrNull { PaymentParser.parse(it.packageName, it.title, it.text, it.subText) as? ParseOutcome.MerchantOnly }
        return order?.merchant to order?.item
    }

    /** 주문 알림이 결제 알림보다 늦게 오면, 이미 만든 거래의 가맹점 이름을 채운다. */
    private suspend fun fillMerchantNearby(at: Long, order: ParseOutcome.MerchantOnly, ctx: Context) {
        val target = transactionDao.findNotificationTransactionsBetween(at - ORDER_WINDOW, at + ORDER_WINDOW, at)
            .firstOrNull { isGenericMerchant(it.merchant) } ?: return
        val categoryId = target.categoryId ?: resolveCategory(order.merchant, null, target.type, ctx)
        transactionDao.update(
            target.copy(
                merchant = order.merchant,
                memo = target.memo ?: order.item,
                categoryId = categoryId,
                status = if (target.status == TxStatus.NEEDS_REVIEW && categoryId != null) TxStatus.CONFIRMED else target.status,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    private fun resolveMethod(hint: MethodHint, methods: List<PaymentMethodEntity>): PaymentMethodEntity? {
        val usable = methods.filter { it.kind != PaymentKind.EMERGENCY }
        // 사용자가 결제수단에 적어둔 알림 키워드(카드 끝 4자리 등)가 가장 정확하다.
        hint.last4?.let { last4 -> usable.firstOrNull { it.matchKeyword?.contains(last4) == true }?.let { return it } }
        return when (hint.kind) {
            MethodKind.KAKAOBANK_CARD -> usable.firstOrNull { it.kind == PaymentKind.CHECK_CARD && it.name.contains("카카오뱅크") }
            MethodKind.KAKAOBANK_ACCOUNT -> usable.firstOrNull { it.kind == PaymentKind.ACCOUNT && it.name.contains("카카오뱅크") }
            MethodKind.KAKAOPAY -> usable.firstOrNull { it.name.contains("카카오페이") }
            MethodKind.NAVERPAY -> usable.firstOrNull { it.name.contains("네이버페이") }
            MethodKind.SAMSUNG_CARD -> usable.firstOrNull { it.name.contains("삼성카드") }
            MethodKind.NONE -> null
        }
    }

    /** 학습한 가맹점 → 알림이 알려준 카테고리 → 이름으로 짐작 순서로 찾는다. */
    private fun resolveCategory(merchant: String?, hint: String?, type: TxType, ctx: Context): Long? {
        val wanted = if (type == TxType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
        val candidates = ctx.categories.filter { it.type == wanted && !it.hidden }
        merchant?.let { m ->
            findMerchantRule(m, ctx.rules)?.takeIf { id -> candidates.any { it.id == id } }?.let { return it }
        }
        val name = hint ?: merchant?.let(::builtInCategoryHint) ?: return null
        return candidates.firstOrNull { it.name == name }?.id
    }

    /**
     * 사용자가 자동 기록의 카테고리를 골랐을 때: 가맹점을 기억하고,
     * 같은 가맹점의 다른 '확인 필요' 지출에도 바로 적용한다.
     */
    suspend fun learnMerchant(merchant: String, categoryId: Long) = lock.withLock {
        val key = normalizeMerchant(merchant)
        if (key.length < 2) return@withLock
        db.withTransaction {
            db.merchantRuleDao().upsert(MerchantRuleEntity(key, categoryId, System.currentTimeMillis()))
            val rule = mapOf(key to categoryId)
            transactionDao.getNeedsReviewWithMerchant()
                .filter { it.type == TxType.EXPENSE && findMerchantRule(it.merchant!!, rule) != null }
                .forEach {
                    transactionDao.update(
                        it.copy(categoryId = categoryId, status = TxStatus.CONFIRMED, updatedAt = System.currentTimeMillis())
                    )
                }
        }
    }

    private companion object {
        const val MERCHANT_ONLY = "merchant_only"
        const val MERGE_WINDOW = 3 * 60 * 1000L
        const val ORDER_WINDOW = 5 * 60 * 1000L
        const val CANCEL_WINDOW = 7 * 24 * 60 * 60 * 1000L
        const val RECURRING_DAYS = 3L
    }
}
