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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mj.money.domain.formatAmount
import com.mj.money.domain.formatWon
import com.mj.money.ui.AppViewModelProvider
import com.mj.money.ui.components.ThousandsSeparatorTransformation
import com.mj.money.ui.theme.AmountStyle

private val SUGGESTED_POCKETS = listOf("지갑", "서랍", "봉투", "차", "금고")

/** 세어 보기: 장소별로 실제 금액을 적으면 기록과의 차이만큼 맞춘다. 처음 등록할 때도 이 화면을 쓴다. */
@Composable
fun EmergencyCountScreen(
    onDone: () -> Unit,
    viewModel: EmergencyCountViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.finished) {
        if (state.finished) onDone()
    }
    // 처음 등록할 때는 빈 줄 하나로 시작한다.
    LaunchedEffect(state.loaded) {
        if (state.loaded && state.rows.isEmpty()) viewModel.addRow()
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
            Text(if (state.isFirstSetup) "비상금 등록" else "세어 보기", style = MaterialTheme.typography.titleLarge)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                if (state.isFirstSetup) {
                    "현금을 둔 곳과 금액을 적어주세요. 장소는 나중에 더하거나 뺄 수 있어요."
                } else {
                    "지금 실제로 있는 금액을 적어주세요. 기록과 다르면 차이만큼 맞춰둘게요."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )

            state.rows.forEach { row ->
                if (row.removed) {
                    Row(
                        modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            row.originalName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration = TextDecoration.LineThrough,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { viewModel.restoreRow(row.key) }) { Text("되돌리기") }
                    }
                } else {
                    Row(
                        modifier = Modifier.padding(start = 20.dp, end = 4.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = row.name,
                            onValueChange = { viewModel.onNameChange(row.key, it) },
                            label = { Text("장소") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier.weight(0.42f),
                        )
                        OutlinedTextField(
                            value = row.digits,
                            onValueChange = { viewModel.onAmountChange(row.key, it) },
                            label = { Text("금액") },
                            suffix = { Text("원") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.merge(AmountStyle),
                            visualTransformation = ThousandsSeparatorTransformation,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            supportingText = if (row.pocketId != null) {
                                { Text("기록 ${row.recorded.formatAmount()}") }
                            } else {
                                null
                            },
                            modifier = Modifier
                                .weight(0.58f)
                                .padding(start = 8.dp),
                        )
                        IconButton(onClick = { viewModel.removeRow(row.key) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "${row.name} 빼기")
                        }
                    }
                }
            }

            val suggestions = SUGGESTED_POCKETS.filterNot { it in state.usedNames }
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                suggestions.forEach { name ->
                    AssistChip(onClick = { viewModel.addRow(name) }, label = { Text("+ $name") })
                }
                AssistChip(onClick = { viewModel.addRow() }, label = { Text("+ 직접 추가") })
            }

            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("합계", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(state.total.formatWon(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
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
}
