package com.mj.money.ui.recurring

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mj.money.AppContainer
import com.mj.money.data.db.CategoryEntity
import com.mj.money.data.db.CategoryType
import com.mj.money.data.db.PaymentKind
import com.mj.money.data.db.PaymentMethodEntity
import com.mj.money.data.db.RecurringExpenseEntity
import com.mj.money.data.db.RecurringStatus
import com.mj.money.domain.RecurringSchedule
import com.mj.money.ui.components.sanitizeAmountInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class RecurringForm(
    val name: String = "",
    val digits: String = "",
    val dayOfMonth: Int = LocalDate.now().dayOfMonth,
    val categoryId: Long? = null,
    val paymentMethodId: Long? = null,
    val autoRecord: Boolean = true,
    val remindDayBefore: Boolean = true,
    val trialEnd: LocalDate? = null,
) {
    val amount: Long get() = digits.toLongOrNull() ?: 0L
}

data class RecurringEditUiState(
    val form: RecurringForm = RecurringForm(),
    val isEdit: Boolean = false,
    val status: RecurringStatus = RecurringStatus.ACTIVE,
    val categories: List<CategoryEntity> = emptyList(),
    val paymentMethods: List<PaymentMethodEntity> = emptyList(),
    val error: String? = null,
    val finished: Boolean = false,
) {
    /** 다음 결제일 (무료 체험 중이면 체험이 끝난 뒤의 첫 결제일) */
    val nextChargeDate: LocalDate
        get() {
            val after = maxOf(LocalDate.now(), form.trialEnd ?: LocalDate.MIN)
            return RecurringSchedule.nextDueDate(form.dayOfMonth, after)
        }
}

class RecurringEditViewModel(
    savedStateHandle: SavedStateHandle,
    container: AppContainer,
) : ViewModel() {
    private val dao = container.database.recurringExpenseDao()
    private val categoryDao = container.database.categoryDao()
    private val editingId: Long? = savedStateHandle.get<Long>("id")?.takeIf { it > 0 }
    private var original: RecurringExpenseEntity? = null
    private var saving = false

    private val form = MutableStateFlow(RecurringForm())
    private val status = MutableStateFlow(RecurringStatus.ACTIVE)
    private val error = MutableStateFlow<String?>(null)
    private val finished = MutableStateFlow(false)

    val uiState: StateFlow<RecurringEditUiState> = combine(
        combine(form, status, ::Pair),
        categoryDao.observeVisible(),
        container.database.paymentMethodDao().observeVisible(),
        error,
        finished,
    ) { (form, status), categories, paymentMethods, error, finished ->
        RecurringEditUiState(
            form = form,
            isEdit = editingId != null,
            status = status,
            categories = categories.filter { it.type == CategoryType.EXPENSE },
            // 구독료를 비상금으로 내지는 않으므로 뺀다.
            paymentMethods = paymentMethods.filter { it.kind != PaymentKind.EMERGENCY },
            error = error,
            finished = finished,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecurringEditUiState(isEdit = editingId != null))

    init {
        viewModelScope.launch {
            if (editingId != null) {
                val expense = dao.getById(editingId)
                if (expense == null) {
                    finished.value = true
                    return@launch
                }
                original = expense
                status.value = expense.status
                form.value = RecurringForm(
                    name = expense.name,
                    digits = expense.amount.toString(),
                    dayOfMonth = expense.dayOfMonth,
                    categoryId = expense.categoryId,
                    paymentMethodId = expense.paymentMethodId,
                    autoRecord = expense.autoRecord,
                    remindDayBefore = expense.remindDayBefore,
                    trialEnd = expense.trialEndEpochDay?.let(LocalDate::ofEpochDay),
                )
            } else {
                // 새로 추가할 때는 구독에 가장 흔한 카테고리와 마지막으로 쓴 결제수단을 기본값으로
                val defaultCategory = categoryDao.observeVisible().first()
                    .firstOrNull { it.type == CategoryType.EXPENSE && it.name == DEFAULT_CATEGORY }
                val lastPayment = container.database.transactionDao().lastManualExpensePaymentMethodId()
                form.update {
                    it.copy(
                        categoryId = it.categoryId ?: defaultCategory?.id,
                        paymentMethodId = it.paymentMethodId ?: lastPayment,
                    )
                }
            }
        }
    }

    fun onNameChange(name: String) {
        form.update { it.copy(name = name.take(MAX_NAME)) }
        error.value = null
    }

    fun onAmountChange(input: String) {
        form.update { it.copy(digits = sanitizeAmountInput(input)) }
        error.value = null
    }

    fun onDayChange(day: Int) = form.update { it.copy(dayOfMonth = day.coerceIn(1, 31)) }

    fun onCategorySelect(id: Long) = form.update { it.copy(categoryId = id) }

    fun onPaymentMethodSelect(id: Long) =
        form.update { it.copy(paymentMethodId = if (it.paymentMethodId == id) null else id) }

    fun onAutoRecordChange(enabled: Boolean) = form.update { it.copy(autoRecord = enabled) }

    fun onRemindChange(enabled: Boolean) = form.update { it.copy(remindDayBefore = enabled) }

    fun onTrialEndChange(date: LocalDate?) = form.update { it.copy(trialEnd = date) }

    fun save() {
        val f = form.value
        when {
            f.name.isBlank() -> {
                error.value = "이름을 입력해 주세요"
                return
            }
            f.amount <= 0 -> {
                error.value = "금액을 입력해 주세요"
                return
            }
        }
        persist { base, now, today ->
            if (base == null) {
                dao.insert(
                    RecurringExpenseEntity(
                        name = f.name.trim(),
                        amount = f.amount,
                        categoryId = f.categoryId,
                        paymentMethodId = f.paymentMethodId,
                        dayOfMonth = f.dayOfMonth,
                        autoRecord = f.autoRecord,
                        remindDayBefore = f.remindDayBefore,
                        trialEndEpochDay = f.trialEnd?.toEpochDay(),
                        startEpochDay = today.toEpochDay(),
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            } else {
                dao.update(
                    base.copy(
                        name = f.name.trim(),
                        amount = f.amount,
                        categoryId = f.categoryId,
                        paymentMethodId = f.paymentMethodId,
                        dayOfMonth = f.dayOfMonth,
                        autoRecord = f.autoRecord,
                        remindDayBefore = f.remindDayBefore,
                        trialEndEpochDay = f.trialEnd?.toEpochDay(),
                        // 자동 기록을 새로 켰다면 지난 결제일까지 거슬러 기록하지 않도록 오늘부터 시작한다.
                        startEpochDay = if (!base.autoRecord && f.autoRecord) today.toEpochDay() else base.startEpochDay,
                        updatedAt = now,
                    )
                )
            }
        }
    }

    /** 해지: 자동 기록과 알림을 멈추고 '해지한 구독'으로 옮긴다. */
    fun cancelSubscription() = persist { base, now, _ ->
        base ?: return@persist
        dao.update(base.copy(status = RecurringStatus.CANCELED, canceledAt = now, updatedAt = now))
    }

    /** 다시 구독: 해지 기간의 결제일은 기록하지 않도록 오늘부터 다시 시작한다. */
    fun resubscribe() = persist { base, now, today ->
        base ?: return@persist
        dao.update(
            base.copy(
                status = RecurringStatus.ACTIVE,
                canceledAt = null,
                startEpochDay = today.toEpochDay(),
                updatedAt = now,
            )
        )
    }

    fun delete() = persist { base, _, _ ->
        base ?: return@persist
        dao.deleteById(base.id)
    }

    private fun persist(block: suspend (base: RecurringExpenseEntity?, now: Long, today: LocalDate) -> Unit) {
        if (saving) return
        saving = true
        viewModelScope.launch {
            block(original, System.currentTimeMillis(), LocalDate.now())
            finished.value = true
        }
    }

    private companion object {
        const val DEFAULT_CATEGORY = "OTT·구독"
        const val MAX_NAME = 40
    }
}
