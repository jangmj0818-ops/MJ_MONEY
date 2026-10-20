package com.mj.money.ui.entry

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mj.money.data.db.CategoryEntity
import com.mj.money.data.db.TxType
import com.mj.money.domain.formatAmount
import com.mj.money.domain.formatWon
import com.mj.money.domain.fullDayLabel
import com.mj.money.ui.AppViewModelProvider
import com.mj.money.ui.components.LocalDatePickerDialog
import com.mj.money.ui.components.PaymentMethodChips
import com.mj.money.ui.theme.HeroAmountStyle
import java.time.LocalDate

@Composable
fun EntryScreen(
    onDone: () -> Unit,
    viewModel: EntryViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sourceNote by viewModel.sourceNote.collectAsStateWithLifecycle()
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.finished) {
        if (state.finished) onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDone) { Icon(Icons.Filled.Close, contentDescription = "닫기") }
            TypeSelector(
                selected = state.form.type,
                onSelect = viewModel::onTypeChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            if (state.isEdit) {
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "삭제")
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = state.form.amount.formatWon(),
                style = HeroAmountStyle,
                color = when {
                    state.form.amount == 0L -> MaterialTheme.colorScheme.outline
                    state.form.type == TxType.INCOME -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = if (sourceNote == null) 16.dp else 0.dp),
            )
            sourceNote?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                )
            }

            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(
                    onClick = { showDatePicker = true },
                    label = { Text(dateText(state.form.date)) },
                    leadingIcon = { Icon(Icons.Outlined.CalendarToday, contentDescription = null, Modifier.size(18.dp)) },
                )
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = state.form.memo,
                    onValueChange = viewModel::onMemoChange,
                    placeholder = { Text("메모 (선택)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            if (state.form.type != TxType.TRANSFER) {
                CategoryGrid(
                    categories = state.categories,
                    selectedId = state.form.categoryId,
                    onSelect = viewModel::onCategorySelect,
                )
            }

            Text(
                text = when (state.form.type) {
                    TxType.EXPENSE -> "결제수단"
                    TxType.INCOME -> "입금 계좌"
                    TxType.TRANSFER -> "출금 계좌"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, top = 12.dp),
            )
            PaymentMethodChips(
                paymentMethods = state.paymentMethods,
                selectedId = state.form.paymentMethodId,
                onSelect = viewModel::onPaymentMethodSelect,
            )
            if (state.isEmergency) {
                EmergencyPocketPicker(
                    state = state,
                    onSelect = viewModel::onPocketSelect,
                )
            }
        }

        state.error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
        }
        Keypad(
            onKey = viewModel::onKey,
            onBackspace = viewModel::onBackspace,
            onClear = viewModel::onClearAmount,
        )
        Button(
            onClick = viewModel::save,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(52.dp),
        ) {
            Text(if (state.isEdit) "수정 완료" else "저장", style = MaterialTheme.typography.titleMedium)
        }
    }

    if (showDatePicker) {
        LocalDatePickerDialog(
            date = state.form.date,
            onConfirm = viewModel::onDateChange,
            onDismiss = { showDatePicker = false },
        )
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("이 내역을 삭제할까요?") },
            text = { Text("삭제하면 되돌릴 수 없어요.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete()
                }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("취소") }
            },
        )
    }
}

private fun dateText(date: LocalDate): String {
    val today = LocalDate.now()
    val prefix = when (date) {
        today -> "오늘 · "
        today.minusDays(1) -> "어제 · "
        else -> ""
    }
    return prefix + date.fullDayLabel()
}

@Composable
private fun TypeSelector(selected: TxType, onSelect: (TxType) -> Unit, modifier: Modifier = Modifier) {
    val options = listOf(TxType.EXPENSE to "지출", TxType.INCOME to "수입", TxType.TRANSFER to "이체")
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, (type, label) ->
            SegmentedButton(
                selected = selected == type,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {},
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun CategoryGrid(
    categories: List<CategoryEntity>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        categories.chunked(COLUMNS).forEach { rowItems ->
            Row(Modifier.fillMaxWidth()) {
                rowItems.forEach { category ->
                    CategoryCell(
                        category = category,
                        selected = category.id == selectedId,
                        onClick = { onSelect(category.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(COLUMNS - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private const val COLUMNS = 4

@Composable
private fun CategoryCell(
    category: CategoryEntity,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (selected) colors.primaryContainer else colors.surfaceContainerHigh)
                .then(if (selected) Modifier.border(2.dp, colors.primary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(category.emoji, fontSize = 22.sp)
        }
        Text(
            category.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.primary else colors.onSurface,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** 비상금으로 낼 때: 어느 장소에서 꺼냈는지 */
@Composable
private fun EmergencyPocketPicker(state: EntryUiState, onSelect: (Long) -> Unit) {
    val selectedId = state.form.emergencyPocketId ?: state.pockets.singleOrNull()?.pocket?.id
    Text(
        "어디서 꺼냈어요?",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 12.dp),
    )
    if (state.pockets.isEmpty()) {
        Text(
            "비상금 장소가 아직 없어요. 홈의 비상금 카드에서 먼저 등록해 주세요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        return
    }
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.pockets.forEach { pocket ->
            FilterChip(
                selected = pocket.pocket.id == selectedId,
                onClick = { onSelect(pocket.pocket.id) },
                label = { Text("${pocket.pocket.name} ${pocket.balance.formatAmount()}") },
            )
        }
    }
    Text(
        "내역에는 비상금으로 남고, 이번 달 예산에서는 빠져요.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
}

private const val BACKSPACE = "back"

@Composable
private fun Keypad(onKey: (String) -> Unit, onBackspace: () -> Unit, onClear: () -> Unit) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("00", "0", BACKSPACE),
    )
    Column(Modifier.padding(horizontal = 12.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    KeypadKey(
                        key = key,
                        onClick = { if (key == BACKSPACE) onBackspace() else onKey(key) },
                        onLongClick = if (key == BACKSPACE) onClear else null,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeypadKey(
    key: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        if (key == BACKSPACE) {
            Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = "지우기 (길게 누르면 전체 지우기)")
        } else {
            Text(key, fontSize = 24.sp)
        }
    }
}
