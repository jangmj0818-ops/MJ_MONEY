package com.mj.money.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mj.money.domain.dayLabel
import com.mj.money.domain.formatAmount
import com.mj.money.domain.formatWon
import com.mj.money.ui.AppViewModelProvider
import com.mj.money.ui.charts.CalendarHeatmap
import com.mj.money.ui.charts.HeatLegend
import com.mj.money.ui.components.EmptyMessage
import com.mj.money.ui.components.PeriodNavigator
import com.mj.money.ui.components.TransactionItem
import com.mj.money.ui.theme.AmountStyle

@Composable
fun HistoryScreen(
    onOpenEntry: (Long) -> Unit,
    viewModel: HistoryViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)) {
        item {
            PeriodNavigator(state.period, viewModel::previousPeriod, viewModel::nextPeriod)
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SummaryCell("지출", state.totals.expense.formatWon(), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                SummaryCell("수입", state.totals.income.formatWon(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            }
        }
        item {
            ModeSelector(
                mode = state.mode,
                onSelect = viewModel::setMode,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        when (state.mode) {
            HistoryMode.CALENDAR -> {
                item {
                    CalendarHeatmap(
                        period = state.period,
                        daily = state.dailyExpense,
                        scale = state.heatScale,
                        today = state.today,
                        selected = state.selectedDate,
                        onSelect = viewModel::selectDate,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    HeatLegend(
                        basis = if (state.dailyBudget > 0) "하루 예산 ${state.dailyBudget.formatWon()} 기준" else null,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
                val day = state.selectedDay
                when {
                    day != null -> {
                        item(key = "day-${day.date}") { DayHeader(day) }
                        items(day.items, key = { it.id }) { row ->
                            TransactionItem(row, onClick = { onOpenEntry(row.id) })
                        }
                    }
                    state.selectedDate != null -> item {
                        Text(
                            "${state.selectedDate!!.dayLabel()}에는 기록이 없어요.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        )
                    }
                    else -> item {
                        Text(
                            "날짜를 누르면 그날 내역이 보여요.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        )
                    }
                }
            }
            HistoryMode.LIST -> {
                if (state.loaded && state.days.isEmpty()) {
                    item { EmptyMessage("이 달에는 기록이 없어요", "+ 버튼으로 새 내역을 추가할 수 있어요.") }
                }
                state.days.forEach { day ->
                    item(key = "day-${day.date}") { DayHeader(day) }
                    items(day.items, key = { it.id }) { row ->
                        TransactionItem(row, onClick = { onOpenEntry(row.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeSelector(mode: HistoryMode, onSelect: (HistoryMode) -> Unit, modifier: Modifier = Modifier) {
    val options = listOf(HistoryMode.CALENDAR to "달력", HistoryMode.LIST to "리스트")
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = mode == value,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {},
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.merge(AmountStyle),
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
        )
    }
}

@Composable
private fun DayHeader(day: DayGroup) {
    Column(Modifier.padding(top = 12.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                day.date.dayLabel(),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (day.expense > 0) {
                Text(
                    "-${day.expense.formatAmount()}",
                    style = MaterialTheme.typography.bodySmall.merge(AmountStyle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
