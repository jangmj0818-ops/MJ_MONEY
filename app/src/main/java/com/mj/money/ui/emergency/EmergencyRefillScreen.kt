package com.mj.money.ui.emergency

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mj.money.domain.formatAmount
import com.mj.money.domain.fullDayLabel
import com.mj.money.ui.AppViewModelProvider
import com.mj.money.ui.components.LocalDatePickerDialog
import com.mj.money.ui.components.ThousandsSeparatorTransformation
import com.mj.money.ui.theme.AmountStyle

@Composable
fun EmergencyRefillScreen(
    onDone: () -> Unit,
    viewModel: EmergencyRefillViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val form = state.form
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

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
            Text("비상금 채워 넣기", style = MaterialTheme.typography.titleLarge)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(
                value = form.digits,
                onValueChange = viewModel::onAmountChange,
                label = { Text("금액") },
                placeholder = { Text("100,000") },
                suffix = { Text("원") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.merge(AmountStyle),
                visualTransformation = ThousandsSeparatorTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            )

            Text(
                "어디에 넣었어요?",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp),
            )
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.pockets.forEach { pocket ->
                    FilterChip(
                        selected = pocket.pocket.id == state.selectedPocketId,
                        onClick = { viewModel.onPocketSelect(pocket.pocket.id) },
                        label = { Text("${pocket.pocket.name} ${pocket.balance.formatAmount()}") },
                    )
                }
            }

            Row(
                modifier = Modifier.padding(start = 16.dp, end = 20.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(
                    onClick = { showDatePicker = true },
                    label = { Text(form.date.fullDayLabel()) },
                    leadingIcon = { Icon(Icons.Outlined.CalendarToday, contentDescription = null, Modifier.size(18.dp)) },
                )
                OutlinedTextField(
                    value = form.memo,
                    onValueChange = viewModel::onMemoChange,
                    placeholder = { Text("메모 (선택)") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )
            }

            ListItem(
                headlineContent = { Text("이번 달 예산에서 빼기") },
                supportingContent = {
                    Text(
                        if (form.countsAgainstBudget) "용돈에서 채운 거라면 켜두세요. 채운 만큼 이번 달에 쓴 돈으로 계산해요."
                        else "보너스처럼 따로 생긴 돈으로 채웠다면 꺼두세요. 예산은 그대로예요."
                    )
                },
                trailingContent = {
                    Switch(checked = form.countsAgainstBudget, onCheckedChange = viewModel::onCountsAgainstBudgetChange)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp),
            )
        }

        state.error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Button(
            onClick = viewModel::save,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(52.dp),
        ) {
            Text("저장", style = MaterialTheme.typography.titleMedium)
        }
    }

    if (showDatePicker) {
        LocalDatePickerDialog(
            date = form.date,
            onConfirm = viewModel::onDateChange,
            onDismiss = { showDatePicker = false },
        )
    }
}
