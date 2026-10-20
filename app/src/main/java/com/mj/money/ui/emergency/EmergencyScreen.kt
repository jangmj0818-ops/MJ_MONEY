package com.mj.money.ui.emergency

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mj.money.data.db.EmergencyMoveRow
import com.mj.money.data.db.EmergencyMoveType
import com.mj.money.domain.formatAmount
import com.mj.money.domain.formatWon
import com.mj.money.domain.shortLabel
import com.mj.money.domain.toLocalDate
import com.mj.money.ui.AppViewModelProvider
import com.mj.money.ui.components.AmountDialog
import com.mj.money.ui.components.EmptyMessage
import com.mj.money.ui.components.ProgressMeter
import com.mj.money.ui.theme.AmountStyle
import com.mj.money.ui.theme.HeroAmountStyle

@Composable
fun EmergencyScreen(
    onBack: () -> Unit,
    onUse: () -> Unit,
    onRefill: () -> Unit,
    onCount: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    viewModel: EmergencyViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showTargetDialog by rememberSaveable { mutableStateOf(false) }
    var moveToDelete by rememberSaveable { mutableStateOf<Long?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
            Text("비상금", style = MaterialTheme.typography.titleLarge)
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        "지금 있는 비상금",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(state.total.formatWon(), style = HeroAmountStyle)
                    ProgressMeter(
                        fraction = if (state.target > 0) state.total.toFloat() / state.target else 0f,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (state.shortfall > 0) {
                                "목표 ${state.target.formatWon()}까지 ${state.shortfall.formatWon()} 남았어요"
                            } else {
                                "목표 ${state.target.formatWon()}만큼 모였어요"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { showTargetDialog = true }) { Text("목표 수정") }
                    }
                }
            }

            if (state.loaded && state.pockets.isEmpty()) {
                item {
                    EmptyMessage(
                        title = "비상금을 장소별로 적어 보세요",
                        body = "서랍, 봉투, 차처럼 현금을 둔 곳과 금액을 적어두면 어디에 얼마 있는지 한눈에 볼 수 있어요.",
                    )
                }
                item {
                    Button(
                        onClick = onCount,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                    ) { Text("장소별로 적기") }
                }
            } else if (state.loaded) {
                item {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = onUse, modifier = Modifier.weight(1f)) { Text("꺼내 쓰기") }
                        OutlinedButton(onClick = onRefill, modifier = Modifier.weight(1f)) { Text("채워 넣기") }
                        OutlinedButton(onClick = onCount, modifier = Modifier.weight(1f)) { Text("세어 보기") }
                    }
                }
                item { SectionTitle("어디에 있나") }
                items(state.pockets, key = { "pocket-${it.pocket.id}" }) { pocket ->
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(pocket.pocket.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(pocket.balance.formatAmount(), style = MaterialTheme.typography.bodyLarge.merge(AmountStyle))
                    }
                }

                item { SectionTitle("기록") }
                if (state.moves.isEmpty()) {
                    item {
                        Text(
                            "아직 기록이 없어요.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                }
                items(state.moves, key = { "move-${it.move.id}" }) { row ->
                    MoveRow(row, onClick = {
                        val transactionId = row.move.transactionId
                        if (transactionId != null) onOpenTransaction(transactionId) else moveToDelete = row.move.id
                    })
                }
            }
        }
    }

    if (showTargetDialog) {
        AmountDialog(
            title = "비상금 목표",
            description = "비상금을 이만큼은 유지하고 싶어요. 모자라면 홈에서 알려드려요.",
            placeholder = "800,000",
            initial = state.target,
            onConfirm = viewModel::setTarget,
            onDismiss = { showTargetDialog = false },
        )
    }
    moveToDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { moveToDelete = null },
            title = { Text("이 기록을 지울까요?") },
            text = { Text("지우면 그 장소의 금액도 기록 전으로 돌아가요.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMove(id)
                    moveToDelete = null
                }) { Text("지우기", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { moveToDelete = null }) { Text("취소") } },
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Column(Modifier.padding(top = 16.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun MoveRow(row: EmergencyMoveRow, onClick: () -> Unit) {
    val move = row.move
    val title = when (move.type) {
        EmergencyMoveType.USE -> listOfNotNull(row.categoryEmoji, row.transactionMemo ?: row.categoryName ?: "비상금 지출")
            .joinToString(" ")
        EmergencyMoveType.REFILL -> move.memo?.let { "채워 넣음 · $it" } ?: "채워 넣음"
        EmergencyMoveType.COUNT -> "세어 보고 맞춤"
    }
    val subtitle = buildList {
        add(move.occurredAt.toLocalDate().shortLabel())
        add(if (move.type == EmergencyMoveType.USE) "${row.pocketName}에서" else row.pocketName)
        if (move.type == EmergencyMoveType.REFILL && move.countsAgainstBudget) add("예산에서 뺌")
    }.joinToString(" · ")
    val amount = if (move.delta > 0) "+${move.delta.formatAmount()}" else "-${(-move.delta).formatAmount()}"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            amount,
            style = MaterialTheme.typography.bodyLarge.merge(AmountStyle),
            color = if (move.delta > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
