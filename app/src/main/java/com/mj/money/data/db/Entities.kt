package com.mj.money.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TxType { EXPENSE, INCOME, TRANSFER }

enum class TxSource { MANUAL, NOTIFICATION, RECURRING }

enum class TxStatus { CONFIRMED, NEEDS_REVIEW, CANCELED }

enum class CategoryType { EXPENSE, INCOME }

/** EMERGENCY: 비상금. 이 결제수단으로 낸 지출은 예산 계산에서 빠진다. */
enum class PaymentKind { CREDIT_CARD, CHECK_CARD, ACCOUNT, CASH, PAY, EMERGENCY }

enum class RecurringStatus { ACTIVE, CANCELED }

/** USE: 꺼내 씀, REFILL: 채워 넣음, COUNT: 세어 보고 맞춤 */
enum class EmergencyMoveType { USE, REFILL, COUNT }

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: CategoryType,
    val emoji: String,
    /** 예전에 쓰던 ARGB 색상. 지금은 colorSlot을 쓴다. (열을 지우려면 테이블을 다시 만들어야 해서 남겨둔다) */
    val color: Long,
    val sortOrder: Int,
    val hidden: Boolean = false,
    /** 차트 색 번호: 1~8 = 팔레트, 0 = 회색 */
    @ColumnInfo(defaultValue = "0") val colorSlot: Int = 0,
)

@Entity(tableName = "payment_methods")
data class PaymentMethodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: PaymentKind,
    val sortOrder: Int,
    /** 신용카드 결제일 (매월 N일) */
    val billingDay: Int? = null,
    /** 신용카드 전월 실적 목표 (원) */
    val monthlyTarget: Long? = null,
    /** 알림과 결제수단을 연결하는 키워드 (예: 카드 끝 4자리) */
    val matchKeyword: String? = null,
    val hidden: Boolean = false,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = PaymentMethodEntity::class,
            parentColumns = ["id"],
            childColumns = ["paymentMethodId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("occurredAt"), Index("categoryId"), Index("paymentMethodId")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TxType,
    /** 항상 양수 (원). 지출/수입 구분은 type으로 한다. */
    val amount: Long,
    /** epoch millis */
    val occurredAt: Long,
    val categoryId: Long?,
    val paymentMethodId: Long?,
    /** 카카오페이·네이버페이처럼 경유한 결제수단 */
    val viaPaymentMethodId: Long? = null,
    val merchant: String? = null,
    val memo: String? = null,
    val source: TxSource = TxSource.MANUAL,
    val status: TxStatus = TxStatus.CONFIRMED,
    val recurringRuleId: Long? = null,
    val notificationLogId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "notification_logs", indices = [Index("postedAt")])
data class NotificationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val title: String?,
    val text: String?,
    val subText: String?,
    /** epoch millis */
    val postedAt: Long,
    /** 어떤 수집 규칙에 걸렸는지 (known_app / kakaotalk / amount) */
    val matchedRule: String,
    val parseResult: String? = null,
    val transactionId: Long? = null,
)

/** 매월 나가는 고정지출 (구독료 등) */
@Entity(
    tableName = "recurring_expenses",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = PaymentMethodEntity::class,
            parentColumns = ["id"],
            childColumns = ["paymentMethodId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("categoryId"), Index("paymentMethodId")],
)
data class RecurringExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Long,
    val categoryId: Long?,
    val paymentMethodId: Long?,
    /** 매월 결제일 (1~31). 그 날짜가 없는 달은 말일로 본다. */
    val dayOfMonth: Int,
    val autoRecord: Boolean = true,
    val remindDayBefore: Boolean = true,
    /** 무료 체험 마지막 날 (epoch day). 이 날까지의 결제일은 기록하지 않는다. */
    val trialEndEpochDay: Long? = null,
    val status: RecurringStatus = RecurringStatus.ACTIVE,
    /** 해지한 시각 (epoch millis) */
    val canceledAt: Long? = null,
    /** 등록하거나 다시 구독한 날 (epoch day). 자동 기록은 이 날 다음 결제일부터 한다. */
    val startEpochDay: Long,
    /** 마지막으로 자동 기록한 결제일 (epoch day) */
    val lastRecordedEpochDay: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/** 가맹점 → 카테고리 학습 (사용자가 자동 기록의 카테고리를 고르면 기억한다) */
@Entity(tableName = "merchant_rules")
data class MerchantRuleEntity(
    /** normalizeMerchant()로 정리한 가맹점 이름 */
    @PrimaryKey val merchantKey: String,
    val categoryId: Long,
    val updatedAt: Long,
)

/** 비상금을 보관하는 장소 (서랍, 봉투, 차 …) */
@Entity(tableName = "emergency_pockets")
data class EmergencyPocketEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int,
    val hidden: Boolean = false,
)

/** 비상금의 움직임. 장소별 금액은 delta를 더해서 구한다. */
@Entity(
    tableName = "emergency_moves",
    foreignKeys = [
        ForeignKey(
            entity = EmergencyPocketEntity::class,
            parentColumns = ["id"],
            childColumns = ["pocketId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("pocketId"), Index("occurredAt"), Index("transactionId")],
)
data class EmergencyMoveEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pocketId: Long,
    val type: EmergencyMoveType,
    /** 이 장소의 금액 변화 (꺼내 쓰면 음수) */
    val delta: Long,
    /** epoch millis */
    val occurredAt: Long,
    val memo: String? = null,
    /** 꺼내 쓰기로 기록된 지출 (내역의 거래 id) */
    val transactionId: Long? = null,
    /** 채워 넣은 돈을 그달 예산에서 쓴 것으로 볼지 */
    val countsAgainstBudget: Boolean = false,
    val createdAt: Long,
)

data class PocketBalance(
    @Embedded val pocket: EmergencyPocketEntity,
    val balance: Long,
)

data class EmergencyMoveRow(
    @Embedded val move: EmergencyMoveEntity,
    val pocketName: String,
    val categoryName: String?,
    val categoryEmoji: String?,
    val transactionMemo: String?,
)

/** 고정지출 목록용: 고정지출 + 카테고리/결제수단 이름 */
data class RecurringRow(
    @Embedded val expense: RecurringExpenseEntity,
    val categoryName: String?,
    val categoryEmoji: String?,
    val paymentMethodName: String?,
)

/** 내역 화면용: 거래 + 카테고리/결제수단 이름 */
data class TransactionRow(
    val id: Long,
    val type: TxType,
    val amount: Long,
    val occurredAt: Long,
    val merchant: String?,
    val memo: String?,
    val status: TxStatus,
    val source: TxSource,
    val categoryId: Long?,
    val categoryName: String?,
    val categoryEmoji: String?,
    val paymentMethodId: Long?,
    val paymentMethodName: String?,
    val paymentMethodKind: PaymentKind?,
    /** 고정지출에서 기록된 거래면 그 고정지출 id */
    val recurringRuleId: Long?,
)

data class CountByKey(val key: String, val count: Int)

data class NotificationLogSummary(
    val packageName: String,
    val matchedRule: String,
    val count: Int,
    val lastPostedAt: Long,
)

/** 통계용: 지출 한 건의 날짜·금액·카테고리·결제수단 */
data class ExpensePoint(
    val occurredAt: Long,
    val amount: Long,
    val categoryId: Long?,
    val paymentMethodId: Long? = null,
)
