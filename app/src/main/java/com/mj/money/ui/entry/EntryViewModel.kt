package com.mj.money.ui.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.mj.money.AppContainer
import com.mj.money.data.db.CategoryEntity
import com.mj.money.data.db.CategoryType
import com.mj.money.data.db.PaymentKind
import com.mj.money.data.db.PaymentMethodEntity
import com.mj.money.data.db.PocketBalance
import com.mj.money.data.db.TransactionEntity
import com.mj.money.data.db.TxSource
import com.mj.money.data.db.TxStatus
import com.mj.money.data.db.TxType
import com.mj.money.domain.toEpochMillis
import com.mj.money.domain.toLocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

data class EntryForm(
    val type: TxType = TxType.EXPENSE,
    val digits: String = "",
    val date: LocalDate = LocalDate.now(),
    val memo: String = "",
    val categoryId: Long? = null,
    val paymentMethodId: Long? = null,
    /** 결제수단이 비상금일 때 꺼낸 장소 */
    val emergencyPocketId: Long? = null,
) {
    val amount: Long get() = digits.toLongOrNull() ?: 0L
}

data class EntryUiState(
    val form: EntryForm = EntryForm(),
    val isEdit: Boolean = false,
    val categories: List<CategoryEntity> = emptyList(),
    val paymentMethods: List<PaymentMethodEntity> = emptyList(),
    val pockets: List<PocketBalance> = emptyList(),
    val error: String? = null,
    val finished: Boolean = false,
) {
    private val emergencyMethodId: Long? get() = paymentMethods.firstOrNull { it.kind == PaymentKind.EMERGENCY }?.id

    /** 비상금으로 낸 지출이면 장소 고르기를 보여준다. */
    val isEmergency: Boolean
        get() = form.type == TxType.EXPENSE && form.paymentMethodId != null && form.paymentMethodId == emergencyMethodId
}

class EntryViewModel(
    savedStateHandle: SavedStateHandle,
    container: AppContainer,
) : ViewModel() {
    private val db = container.database
    private val transactionDao = db.transactionDao()
    private val emergency = container.emergency
    private val notificationProcessor = container.notificationProcessor

    /** 자동 기록된 내역이면 어디서 왔는지 (예: "알림에서 자동 기록 · 테스트식당") */
    private val _sourceNote = MutableStateFlow<String?>(null)
    val sourceNote: StateFlow<String?> = _sourceNote
    private val editingId: Long? = savedStateHandle.get<Long>("id")?.takeIf { it > 0 }
    private val startWithEmergency: Boolean = savedStateHandle.get<Boolean>("emergency") ?: false
    private var emergencyMethodId: Long? = null
    private var original: TransactionEntity? = null
    private var saving = false

    private val form = MutableStateFlow(EntryForm())
    private val error = MutableStateFlow<String?>(null)
    private val finished = MutableStateFlow(false)

    val uiState: StateFlow<EntryUiState> = combine(
        combine(form, error, finished, ::Triple),
        db.categoryDao().observeVisible(),
        db.paymentMethodDao().observeVisible(),
        emergency.observePockets(),
    ) { (form, error, finished), categories, paymentMethods, pockets ->
        EntryUiState(
            form = form,
            isEdit = editingId != null,
            categories = categories.filter { it.type.matches(form.type) },
            // 비상금은 지출에만 쓴다. (채우는 건 비상금 화면에서)
            paymentMethods = paymentMethods.filter { form.type == TxType.EXPENSE || it.kind != PaymentKind.EMERGENCY },
            pockets = pockets,
            error = error,
            finished = finished,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EntryUiState(isEdit = editingId != null))

    init {
        viewModelScope.launch {
            emergencyMethodId = db.paymentMethodDao().getEmergency()?.id
            if (editingId != null) {
                val tx = transactionDao.getById(editingId)
                if (tx == null) {
                    finished.value = true
                    return@launch
                }
                original = tx
                _sourceNote.value = when (tx.source) {
                    TxSource.NOTIFICATION -> listOfNotNull("알림에서 자동 기록", tx.merchant).joinToString(" · ")
                    TxSource.RECURRING -> listOfNotNull("고정지출 자동 기록", tx.merchant).joinToString(" · ")
                    TxSource.MANUAL -> null
                }
                form.value = EntryForm(
                    type = tx.type,
                    digits = tx.amount.toString(),
                    date = tx.occurredAt.toLocalDate(),
                    memo = tx.memo.orEmpty(),
                    categoryId = tx.categoryId,
                    paymentMethodId = tx.paymentMethodId,
                    emergencyPocketId = emergency.pocketIdForTransaction(tx.id),
                )
            } else if (startWithEmergency && emergencyMethodId != null) {
                // 비상금 화면의 '꺼내 쓰기'로 들어온 경우
                form.update { it.copy(paymentMethodId = emergencyMethodId) }
            } else {
                // 결제수단은 마지막으로 쓴 것을 기본값으로
                val last = transactionDao.lastManualExpensePaymentMethodId()
                form.update { if (it.paymentMethodId == null) it.copy(paymentMethodId = last) else it }
            }
        }
    }

    fun onKey(key: String) {
        form.update { f ->
            val next = f.digits + key
            when {
                f.digits.isEmpty() && key.startsWith("0") -> f
                next.length > MAX_DIGITS -> f
                else -> f.copy(digits = next)
            }
        }
        error.value = null
    }

    fun onBackspace() = form.update { it.copy(digits = it.digits.dropLast(1)) }

    fun onClearAmount() = form.update { it.copy(digits = "") }

    fun onTypeChange(type: TxType) {
        form.update {
            when {
                it.type == type -> it
                // 비상금은 지출에만 쓰므로 다른 유형으로 바꾸면 결제수단을 비운다.
                type != TxType.EXPENSE && it.paymentMethodId == emergencyMethodId ->
                    it.copy(type = type, categoryId = null, paymentMethodId = null, emergencyPocketId = null)
                else -> it.copy(type = type, categoryId = null)
            }
        }
        error.value = null
    }

    fun onCategorySelect(id: Long) {
        form.update { it.copy(categoryId = id) }
        error.value = null
    }

    /** 이미 선택된 결제수단을 다시 누르면 선택을 해제한다. */
    fun onPaymentMethodSelect(id: Long) {
        form.update { it.copy(paymentMethodId = if (it.paymentMethodId == id) null else id) }
        error.value = null
    }

    fun onPocketSelect(id: Long) {
        form.update { it.copy(emergencyPocketId = id) }
        error.value = null
    }

    fun onDateChange(date: LocalDate) = form.update { it.copy(date = date) }

    fun onMemoChange(memo: String) = form.update { it.copy(memo = memo.take(MAX_MEMO)) }

    fun save() {
        val state = uiState.value
        val f = state.form
        // 장소가 하나뿐이면 고르지 않아도 그 장소로 본다.
        val pocketId = f.emergencyPocketId ?: state.pockets.singleOrNull()?.pocket?.id
        when {
            f.amount <= 0 -> {
                error.value = "금액을 입력해 주세요"
                return
            }
            f.type != TxType.TRANSFER && f.categoryId == null -> {
                error.value = "카테고리를 골라 주세요"
                return
            }
            state.isEmergency && state.pockets.isEmpty() -> {
                error.value = "비상금 장소를 먼저 등록해 주세요 (홈 → 비상금)"
                return
            }
            state.isEmergency && pocketId == null -> {
                error.value = "비상금을 어디서 꺼냈는지 골라 주세요"
                return
            }
        }
        if (saving) return
        saving = true

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val base = original
            val occurredAt = resolveOccurredAt(f.date, base?.occurredAt, now)
            val categoryId = if (f.type == TxType.TRANSFER) null else f.categoryId
            val memo = f.memo.trim().ifEmpty { null }

            db.withTransaction {
                val id = if (base == null) {
                    transactionDao.insert(
                        TransactionEntity(
                            type = f.type,
                            amount = f.amount,
                            occurredAt = occurredAt,
                            categoryId = categoryId,
                            paymentMethodId = f.paymentMethodId,
                            memo = memo,
                            source = TxSource.MANUAL,
                            status = TxStatus.CONFIRMED,
                            createdAt = now,
                            updatedAt = now,
                        )
                    )
                } else {
                    transactionDao.update(
                        base.copy(
                            type = f.type,
                            amount = f.amount,
                            occurredAt = occurredAt,
                            categoryId = categoryId,
                            paymentMethodId = f.paymentMethodId,
                            memo = memo,
                            // 사용자가 직접 확인하고 저장했으니 확정으로 바꾼다.
                            status = if (base.status == TxStatus.NEEDS_REVIEW) TxStatus.CONFIRMED else base.status,
                            updatedAt = now,
                        )
                    )
                    base.id
                }
                emergency.syncExpense(id, if (state.isEmergency) pocketId else null, f.amount, occurredAt)
            }
            // 알림에서 자동 기록한 지출의 카테고리를 정했으면 그 가맹점을 기억한다. (같은 가게의 다른 확인 필요 내역에도 적용)
            val merchant = base?.merchant
            if (base?.source == TxSource.NOTIFICATION && merchant != null && f.type == TxType.EXPENSE && categoryId != null &&
                (base.categoryId != categoryId || base.status == TxStatus.NEEDS_REVIEW)
            ) {
                notificationProcessor.learnMerchant(merchant, categoryId)
            }
            finished.value = true
        }
    }

    fun delete() {
        val id = editingId ?: return
        viewModelScope.launch {
            db.withTransaction {
                transactionDao.deleteById(id)
                emergency.deleteForTransaction(id)
            }
            finished.value = true
        }
    }

    private companion object {
        const val MAX_DIGITS = 11
        const val MAX_MEMO = 100
    }
}

private fun CategoryType.matches(type: TxType): Boolean = when (type) {
    TxType.EXPENSE -> this == CategoryType.EXPENSE
    TxType.INCOME -> this == CategoryType.INCOME
    TxType.TRANSFER -> false
}

/**
 * 날짜만 고르므로 시각은 이렇게 정한다.
 * 수정 중이고 날짜가 그대로면 원래 시각, 오늘이면 지금, 다른 날이면 정오.
 */
private fun resolveOccurredAt(date: LocalDate, originalMillis: Long?, nowMillis: Long): Long {
    if (originalMillis != null && originalMillis.toLocalDate() == date) return originalMillis
    if (date == nowMillis.toLocalDate()) return nowMillis
    return date.atTime(LocalTime.NOON).toEpochMillis()
}
