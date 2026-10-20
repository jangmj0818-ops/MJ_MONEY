package com.mj.money.ui.manage

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mj.money.AppContainer
import com.mj.money.data.db.CategoryEntity
import com.mj.money.data.db.CategoryType
import com.mj.money.data.db.PaymentKind
import com.mj.money.data.db.PaymentMethodEntity
import com.mj.money.domain.nextColorSlot
import com.mj.money.ui.components.isCard
import com.mj.money.ui.components.sanitizeAmountInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------- 카테고리 목록 ----------

data class CategoryManageUiState(
    val type: CategoryType = CategoryType.EXPENSE,
    val visible: List<CategoryEntity> = emptyList(),
    val hidden: List<CategoryEntity> = emptyList(),
)

class CategoryManageViewModel(container: AppContainer) : ViewModel() {
    private val catalog = container.catalog
    private val type = MutableStateFlow(CategoryType.EXPENSE)

    val uiState: StateFlow<CategoryManageUiState> = combine(type, container.database.categoryDao().observeAll()) { type, all ->
        val mine = all.filter { it.type == type }
        CategoryManageUiState(type, mine.filterNot { it.hidden }, mine.filter { it.hidden })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryManageUiState())

    fun setType(value: CategoryType) {
        type.value = value
    }

    fun move(category: CategoryEntity, offset: Int) {
        val list = uiState.value.visible
        val index = list.indexOfFirst { it.id == category.id }
        val other = list.getOrNull(index + offset) ?: return
        viewModelScope.launch { catalog.swapCategories(category, other) }
    }

    fun unhide(category: CategoryEntity) {
        viewModelScope.launch { catalog.saveCategory(category.copy(hidden = false)) }
    }
}

// ---------- 카테고리 편집 ----------

data class CategoryForm(val name: String = "", val emoji: String = "", val slot: Int = 0)

data class CategoryEditUiState(
    val form: CategoryForm = CategoryForm(),
    val type: CategoryType = CategoryType.EXPENSE,
    val isEdit: Boolean = false,
    val error: String? = null,
    val finished: Boolean = false,
)

class CategoryEditViewModel(savedStateHandle: SavedStateHandle, container: AppContainer) : ViewModel() {
    private val catalog = container.catalog
    private val categoryDao = container.database.categoryDao()
    private val editingId: Long? = savedStateHandle.get<Long>("id")?.takeIf { it > 0 }
    private var original: CategoryEntity? = null
    private val state = MutableStateFlow(
        CategoryEditUiState(
            type = savedStateHandle.get<String>("type")?.let { runCatching { CategoryType.valueOf(it) }.getOrNull() }
                ?: CategoryType.EXPENSE,
            isEdit = editingId != null,
        )
    )
    val uiState: StateFlow<CategoryEditUiState> = state.asStateFlow()
    private var saving = false

    init {
        viewModelScope.launch {
            if (editingId != null) {
                val category = categoryDao.getById(editingId) ?: return@launch state.update { it.copy(finished = true) }
                original = category
                state.update {
                    it.copy(type = category.type, form = CategoryForm(category.name, category.emoji, category.colorSlot))
                }
            } else {
                val usedSlots = categoryDao.observeAll().first()
                    .filter { it.type == state.value.type && !it.hidden }
                    .map { it.colorSlot }
                state.update { it.copy(form = CategoryForm(emoji = "🏷️", slot = nextColorSlot(usedSlots))) }
            }
        }
    }

    fun onNameChange(value: String) = state.update { it.copy(form = it.form.copy(name = value.take(12)), error = null) }

    fun onEmojiChange(value: String) = state.update { it.copy(form = it.form.copy(emoji = value.trim().take(8)), error = null) }

    fun onSlotChange(slot: Int) = state.update { it.copy(form = it.form.copy(slot = slot)) }

    fun save() = persist(hidden = original?.hidden ?: false)

    /** 숨기면 입력 화면에서 사라지고, 지난 내역과 통계에는 그대로 남는다. */
    fun hide() = persist(hidden = true)

    private fun persist(hidden: Boolean) {
        val s = state.value
        when {
            s.form.name.isBlank() -> return state.update { it.copy(error = "이름을 입력해 주세요") }
            s.form.emoji.isBlank() -> return state.update { it.copy(error = "아이콘(이모지)을 넣어 주세요") }
        }
        if (saving) return
        saving = true
        viewModelScope.launch {
            val base = original
            val entity = base?.copy(name = s.form.name.trim(), emoji = s.form.emoji, colorSlot = s.form.slot, hidden = hidden)
                ?: CategoryEntity(
                    name = s.form.name.trim(),
                    type = s.type,
                    emoji = s.form.emoji,
                    color = 0,
                    sortOrder = 0,
                    colorSlot = s.form.slot,
                )
            catalog.saveCategory(entity)
            state.update { it.copy(finished = true) }
        }
    }
}

// ---------- 결제수단 목록 ----------

data class PaymentManageUiState(
    val visible: List<PaymentMethodEntity> = emptyList(),
    val hidden: List<PaymentMethodEntity> = emptyList(),
)

class PaymentManageViewModel(container: AppContainer) : ViewModel() {
    private val catalog = container.catalog

    val uiState: StateFlow<PaymentManageUiState> = container.database.paymentMethodDao().observeAll()
        .map { all -> PaymentManageUiState(all.filterNot { it.hidden }, all.filter { it.hidden }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaymentManageUiState())

    fun move(method: PaymentMethodEntity, offset: Int) {
        val list = uiState.value.visible
        val index = list.indexOfFirst { it.id == method.id }
        val other = list.getOrNull(index + offset) ?: return
        viewModelScope.launch { catalog.swapPaymentMethods(method, other) }
    }

    fun unhide(method: PaymentMethodEntity) {
        viewModelScope.launch { catalog.savePaymentMethod(method.copy(hidden = false)) }
    }
}

// ---------- 결제수단 편집 ----------

data class PaymentForm(
    val name: String = "",
    val kind: PaymentKind = PaymentKind.CREDIT_CARD,
    val billingDay: Int? = null,
    val targetDigits: String = "",
    val matchKeyword: String = "",
)

data class PaymentEditUiState(
    val form: PaymentForm = PaymentForm(),
    val isEdit: Boolean = false,
    val error: String? = null,
    val finished: Boolean = false,
)

class PaymentEditViewModel(savedStateHandle: SavedStateHandle, container: AppContainer) : ViewModel() {
    private val catalog = container.catalog
    private val dao = container.database.paymentMethodDao()
    private val editingId: Long? = savedStateHandle.get<Long>("id")?.takeIf { it > 0 }
    private var original: PaymentMethodEntity? = null
    private val state = MutableStateFlow(PaymentEditUiState(isEdit = editingId != null))
    val uiState: StateFlow<PaymentEditUiState> = state.asStateFlow()
    private var saving = false

    init {
        if (editingId != null) {
            viewModelScope.launch {
                val method = dao.getById(editingId) ?: return@launch state.update { it.copy(finished = true) }
                original = method
                state.update {
                    it.copy(
                        form = PaymentForm(
                            name = method.name,
                            kind = method.kind,
                            billingDay = method.billingDay,
                            targetDigits = method.monthlyTarget?.toString().orEmpty(),
                            matchKeyword = method.matchKeyword.orEmpty(),
                        )
                    )
                }
            }
        }
    }

    private fun updateForm(change: (PaymentForm) -> PaymentForm) = state.update { it.copy(form = change(it.form), error = null) }

    fun onNameChange(value: String) = updateForm { it.copy(name = value.take(20)) }

    fun onKindChange(kind: PaymentKind) = updateForm { it.copy(kind = kind) }

    fun onBillingDayChange(day: Int?) = updateForm { it.copy(billingDay = day) }

    fun onTargetChange(input: String) = updateForm { it.copy(targetDigits = sanitizeAmountInput(input)) }

    fun onKeywordChange(value: String) = updateForm { it.copy(matchKeyword = value.take(20)) }

    fun save() = persist(hidden = original?.hidden ?: false)

    fun hide() = persist(hidden = true)

    private fun persist(hidden: Boolean) {
        val f = state.value.form
        if (f.name.isBlank()) return state.update { it.copy(error = "이름을 입력해 주세요") }
        if (saving) return
        saving = true
        viewModelScope.launch {
            // 카드가 아니면 결제일·실적 목표는 쓰지 않는다.
            val billingDay = if (f.kind == PaymentKind.CREDIT_CARD) f.billingDay else null
            val target = if (f.kind.isCard) f.targetDigits.toLongOrNull()?.takeIf { it > 0 } else null
            val keyword = f.matchKeyword.trim().ifEmpty { null }
            val base = original
            val entity = base?.copy(
                name = f.name.trim(), kind = f.kind, billingDay = billingDay,
                monthlyTarget = target, matchKeyword = keyword, hidden = hidden,
            ) ?: PaymentMethodEntity(
                name = f.name.trim(), kind = f.kind, sortOrder = 0, billingDay = billingDay,
                monthlyTarget = target, matchKeyword = keyword,
            )
            catalog.savePaymentMethod(entity)
            state.update { it.copy(finished = true) }
        }
    }
}
