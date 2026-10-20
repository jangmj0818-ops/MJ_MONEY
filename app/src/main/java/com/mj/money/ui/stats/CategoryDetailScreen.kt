package com.mj.money.ui.stats

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mj.money.domain.MerchantTotal
import com.mj.money.domain.formatAmount
import com.mj.money.domain.formatWon
import com.mj.money.domain.rangeText
import com.mj.money.domain.titleText
import com.mj.money.ui.AppViewModelProvider
import com.mj.money.ui.charts.ChartColors
import com.mj.money.ui.charts.MonthBar
import com.mj.money.ui.charts.MonthlyBars
import com.mj.money.ui.components.ComparisonLine
import com.mj.money.ui.components.ProgressMeter
import com.mj.money.ui.components.TransactionItem
import com.mj.money.ui.theme.AmountStyle
import com.mj.money.ui.theme.HeroAmountStyle

@Composable
fun CategoryDetailScreen(
    onBack: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    viewModel: CategoryDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val color = if (state.slot == 0) ChartColors.other() else ChartColors.category(state.slot)

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
            Text(
                "${state.emoji} ${state.name}",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${state.period.titleText()} · ${state.period.rangeText()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp),
            )
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(state.total.formatWon(), style = HeroAmountStyle)
                    ComparisonLine(state.total, state.previousTotal, state.previousIsPartial)
                    if (state.transactions.isNotEmpty()) {
                        Text(
                            "${state.transactions.size}건",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item { SectionTitle("최근 6개월") }
            item {
                MonthlyBars(
                    bars = state.months.map { (month, total) -> MonthBar(month, total, month == state.period.label) },
                    color = color,
                    onBarClick = viewModel::selectMonth,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }

            if (state.merchants.isNotEmpty()) {
                item { SectionTitle("많이 쓴 곳") }
                val max = state.merchants.first().total
                items(state.merchants, key = { "merchant-${it.name}" }) { merchant ->
                    MerchantRow(merchant, fraction = merchant.total.toFloat() / max, color = color)
                }
            }

            item { SectionTitle("내역") }
            if (state.loaded && state.transactions.isEmpty()) {
                item {
                    Text(
                        "이 달에는 내역이 없어요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
            items(state.transactions, key = { it.id }) { row ->
                TransactionItem(row, onClick = { onOpenEntry(row.id) }, showDate = true)
            }
        }
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
            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun MerchantRow(merchant: MerchantTotal, fraction: Float, color: androidx.compose.ui.graphics.Color) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                merchant.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                "  ${merchant.count}회",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(merchant.total.formatAmount(), style = MaterialTheme.typography.bodyLarge.merge(AmountStyle))
        }
        ProgressMeter(
            fraction = fraction,
            height = 6.dp,
            fill = color,
            track = color.copy(alpha = 0.2f),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
