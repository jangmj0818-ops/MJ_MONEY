package com.mj.money.ui.manage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mj.money.data.db.PaymentKind
import com.mj.money.data.db.PaymentMethodEntity
import com.mj.money.domain.formatWon
import com.mj.money.ui.AppViewModelProvider
import com.mj.money.ui.components.DayOfMonthDialog
import com.mj.money.ui.components.FieldLabel
import com.mj.money.ui.components.SettingRow
import com.mj.money.ui.components.ThousandsSeparatorTransformation
import com.mj.money.ui.components.isCard
import com.mj.money.ui.components.label
import com.mj.money.ui.theme.AmountStyle

@Composable
fun PaymentManageScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onAdd: () -> Unit,
    viewModel: PaymentManageViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        ManageTopBar("결제수단", onBack)
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            items(state.visible, key = { it.id }) { method ->
                val index = state.visible.indexOf(method)
                // 비상금은 비상금 화면에서 관리하므로 편집하지 않는다.
                val editable = method.kind != PaymentKind.EMERGENCY
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (editable) Modifier.clickable { onEdit(method.id) } else Modifier)
                        .padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(method.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            method.summary(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MoveButtons(
                        canMoveUp = index > 0,
                        canMoveDown = index < state.visible.lastIndex,
                        onMoveUp = { viewModel.move(method, -1) },
                        onMoveDown = { viewModel.move(method, 1) },
                    )
                }
            }
            item {
                OutlinedButton(
                    onClick = onAdd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) { Text("+ 결제수단 추가") }
            }
            if (state.hidden.isNotEmpty()) {
                item { HiddenHeader("숨긴 결제수단", "입력 화면에는 안 보이고, 지난 내역과 통계에는 남아 있어요.") }
                items(state.hidden, key = { "hidden-${it.id}" }) { method ->
                    Row(
                        modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            method.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { viewModel.unhide(method) }) { Text("다시 보이기") }
                    }
                }
            }
        }
    }
}

private fun PaymentMethodEntity.summary(): String = buildList {
    add(kind.label())
    if (kind == PaymentKind.EMERGENCY) add("홈 → 비상금에서 관리해요")
    billingDay?.let { add("결제일 ${it}일") }
    monthlyTarget?.let { add("실적 목표 ${it.formatWon()}") }
    matchKeyword?.let { add("알림 키워드 '$it'") }
}.joinToString(" · ")

@Composable
fun PaymentEditScreen(
    onDone: () -> Unit,
    viewModel: PaymentEditViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val form = state.form
    var showDayPicker by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.finished) {
        if (state.finished) onDone()
    }

    Column(
        Modifier
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
            Text(if (state.isEdit) "결제수단 수정" else "결제수단 추가", style = MaterialTheme.typography.titleLarge)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(
                value = form.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("이름") },
                placeholder = { Text("카카오페이 계좌") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            )

            FieldLabel("종류")
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PaymentKind.entries.filter { it != PaymentKind.EMERGENCY }.forEach { kind ->
                    FilterChip(
                        selected = form.kind == kind,
                        onClick = { viewModel.onKindChange(kind) },
                        label = { Text(kind.label()) },
                    )
                }
            }

            if (form.kind == PaymentKind.CREDIT_CARD) {
                SettingRow(
                    title = "결제일",
                    value = form.billingDay?.let { "매월 ${it}일" } ?: "정하지 않음",
                    supporting = "카드값이 빠져나가는 날이에요.",
                    onClick = { showDayPicker = true },
                )
            }
            if (form.kind.isCard) {
                FieldLabel("전월 실적 목표 (선택)")
                OutlinedTextField(
                    value = form.targetDigits,
                    onValueChange = viewModel::onTargetChange,
                    placeholder = { Text("300,000") },
                    suffix = { Text("원") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.merge(AmountStyle),
                    visualTransformation = ThousandsSeparatorTransformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("카드 혜택을 받으려면 한 달(1일~말일)에 써야 하는 금액이에요.") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                )
            }

            FieldLabel("알림 키워드 (선택)")
            OutlinedTextField(
                value = form.matchKeyword,
                onValueChange = viewModel::onKeywordChange,
                placeholder = { Text("카드 끝 4자리 등") },
                singleLine = true,
                supportingText = { Text("결제 알림을 자동으로 인식할 때 이 결제수단을 찾는 데 써요.") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            )

            if (state.isEdit) {
                OutlinedButton(
                    onClick = viewModel::hide,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) { Text("숨기기") }
            }
        }

        SaveBar(error = state.error, label = if (state.isEdit) "수정 완료" else "추가", onSave = viewModel::save)
    }

    if (showDayPicker) {
        DayOfMonthDialog(
            selected = form.billingDay ?: 0,
            onSelect = viewModel::onBillingDayChange,
            onDismiss = { showDayPicker = false },
            title = "카드 결제일",
        )
    }
}
