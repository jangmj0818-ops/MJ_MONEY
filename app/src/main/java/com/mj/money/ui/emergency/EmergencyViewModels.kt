package com.mj.money.ui.emergency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mj.money.AppContainer
import com.mj.money.data.db.EmergencyMoveRow
import com.mj.money.data.db.PocketBalance
import com.mj.money.domain.PocketCountInput
import com.mj.money.domain.planCount
import com.mj.money.domain.toEpochMillis
import com.mj.money.ui.components.sanitizeAmountInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

// ---------- 비상금 화면 ----------

data class EmergencyUiState(
    val pockets: List<PocketBalance> = emptyList(),
    val moves: List<EmergencyMoveRow> = emptyList(),
    val target: Long = 0,
    val loaded: Boolean = false,
) {
    val total: Long get() = pockets.sumOf { it.balance }
    val shortfall: Long get() = (target - total).coerceAtLeast(0)
}

class EmergencyViewModel(container: AppContainer) : ViewModel() {
    private val repository = container.emergency
    private val settings = container.settings

    val uiState: StateFlow<EmergencyUiState> = combine(
        repository.observePockets(),
        repository.observeMoves(),
        settings.emergencyTarget,
    ) { pockets, moves, target ->
        EmergencyUiState(pockets, moves, target, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EmergencyUiState())

    fun setTarget(amount: Long) {
        viewModelScope.launch { settings.setEmergencyTarget(amount) }
    }

    fun deleteMove(id: Long) {
        viewModelScope.launch { repository.deleteMove(id) }
    }
}

// ---------- 채워 넣기 ----------

data class RefillForm(
    val digits: String = "",
    val pocketId: Long? = null,
    val date: LocalDate = LocalDate.now(),
    val memo: String = "",
    val countsAgainstBudget: Boolean = true,
) {
    val amount: Long get() = digits.toLongOrNull() ?: 0L
}

data class RefillUiState(
    val form: RefillForm = RefillForm(),
    val pockets: List<PocketBalance> = emptyList(),
    val error: String? = null,
    val finished: Boolean = false,
) {
    /** 장소가 하나뿐이면 고르지 않아도 그 장소로 본다. */
    val selectedPocketId: Long? get() = form.pocketId ?: pockets.singleOrNull()?.pocket?.id
}

class EmergencyRefillViewModel(container: AppContainer) : ViewModel() {
    private val repository = container.emergency
    private val form = MutableStateFlow(RefillForm())
    private val error = MutableStateFlow<String?>(null)
    private val finished = MutableStateFlow(false)
    private var saving = false

    val uiState: StateFlow<RefillUiState> = combine(form, repository.observePockets(), error, finished) { f, pockets, e, done ->
        RefillUiState(f, pockets, e, done)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RefillUiState())

    fun onAmountChange(input: String) {
        form.update { it.copy(digits = sanitizeAmountInput(input)) }
        error.value = null
    }

    fun onPocketSelect(id: Long) {
        form.update { it.copy(pocketId = id) }
        error.value = null
    }

    fun onDateChange(date: LocalDate) = form.update { it.copy(date = date) }

    fun onMemoChange(memo: String) = form.update { it.copy(memo = memo.take(100)) }

    fun onCountsAgainstBudgetChange(value: Boolean) = form.update { it.copy(countsAgainstBudget = value) }

    fun save() {
        val state = uiState.value
        val f = state.form
        val pocketId = state.selectedPocketId
        when {
            f.amount <= 0 -> error.value = "금액을 입력해 주세요"
            pocketId == null -> error.value = "어디에 넣었는지 골라 주세요"
            !saving -> {
                saving = true
                viewModelScope.launch {
                    val today = LocalDate.now()
                    val occurredAt = if (f.date == today) System.currentTimeMillis() else f.date.atTime(LocalTime.NOON).toEpochMillis()
                    repository.refill(pocketId, f.amount, occurredAt, f.memo.trim().ifEmpty { null }, f.countsAgainstBudget)
                    finished.value = true
                }
            }
        }
    }
}

// ---------- 세어 보기 ----------

data class CountRow(
    val key: Long,
    val pocketId: Long?,
    val originalName: String,
    val name: String,
    val recorded: Long,
    val digits: String,
    val removed: Boolean = false,
) {
    val actual: Long get() = digits.toLongOrNull() ?: 0L
}

data class CountUiState(
    val rows: List<CountRow> = emptyList(),
    val isFirstSetup: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val finished: Boolean = false,
) {
    val total: Long get() = rows.filterNot { it.removed }.sumOf { it.actual }
    val usedNames: Set<String> get() = rows.filterNot { it.removed }.map { it.name.trim() }.toSet()
}

class EmergencyCountViewModel(container: AppContainer) : ViewModel() {
    private val repository = container.emergency
    private val state = MutableStateFlow(CountUiState())
    val uiState: StateFlow<CountUiState> = state.asStateFlow()
    private var nextNewKey = -1L
    private var saving = false

    init {
        viewModelScope.launch {
            val pockets = repository.observePockets().first()
            val rows = pockets.map {
                CountRow(
                    key = it.pocket.id,
                    pocketId = it.pocket.id,
                    originalName = it.pocket.name,
                    name = it.pocket.name,
                    recorded = it.balance,
                    digits = if (it.balance > 0) it.balance.toString() else "",
                )
            }
            state.value = CountUiState(rows = rows, isFirstSetup = pockets.isEmpty(), loaded = true)
        }
    }

    private fun updateRow(key: Long, change: (CountRow) -> CountRow) {
        state.update { s -> s.copy(rows = s.rows.map { if (it.key == key) change(it) else it }, error = null) }
    }

    fun onNameChange(key: Long, name: String) = updateRow(key) { it.copy(name = name.take(20)) }

    fun onAmountChange(key: Long, input: String) = updateRow(key) { it.copy(digits = sanitizeAmountInput(input)) }

    fun addRow(name: String = "") {
        state.update {
            it.copy(rows = it.rows + CountRow(nextNewKey--, null, "", name, 0, ""), error = null)
        }
    }

    /** 새 줄은 바로 없애고, 원래 있던 장소는 저장할 때 숨긴다. */
    fun removeRow(key: Long) {
        state.update { s ->
            val row = s.rows.first { it.key == key }
            val rows = if (row.pocketId == null) s.rows - row else s.rows.map { if (it.key == key) it.copy(removed = true) else it }
            s.copy(rows = rows)
        }
    }

    fun restoreRow(key: Long) = updateRow(key) { it.copy(removed = false) }

    fun save() {
        val rows = state.value.rows
        if (rows.any { !it.removed && it.name.isBlank() && it.actual > 0 }) {
            state.update { it.copy(error = "장소 이름을 적어 주세요") }
            return
        }
        if (saving) return
        saving = true
        val plan = planCount(
            rows.map { PocketCountInput(it.pocketId, it.originalName, it.name, it.recorded, it.actual, it.removed) }
        )
        viewModelScope.launch {
            if (!plan.isEmpty) repository.applyCount(plan)
            state.update { it.copy(finished = true) }
        }
    }
}
