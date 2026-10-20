package com.mj.money.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mj.money.domain.formatAmount
import com.mj.money.domain.formatCompact
import com.mj.money.domain.formatWon
import com.mj.money.ui.AppViewModelProvider
import com.mj.money.ui.charts.ChartColors
import com.mj.money.ui.charts.DonutChart
import com.mj.money.ui.charts.DonutSlice
import com.mj.money.ui.charts.MonthBar
import com.mj.money.ui.charts.MonthlyBars
import com.mj.money.ui.components.ComparisonLine
import com.mj.money.ui.components.EmptyMessage
import com.mj.money.ui.components.PeriodNavigator
import com.mj.money.ui.components.ProgressMeter
import com.mj.money.ui.theme.AmountStyle
import com.mj.money.ui.theme.HeroAmountStyle
import java.time.YearMonth
import kotlin.math.roundToInt

@Composable
fun StatsScreen(
    onOpenCategory: (categoryId: Long?, month: YearMonth) -> Unit,
    onOpenPaymentMethod: (paymentMethodId: Long?, month: YearMonth) -> Unit,
    viewModel: StatsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)) {
        item {
            PeriodNavigator(state.period, viewModel::previousPeriod, viewModel::nextPeriod)
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("총 지출", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(state.total.formatWon(), style = HeroAmountStyle)
                ComparisonLine(state.total, state.previousTotal, state.previousIsPartial)
            }
        }

        if (state.loaded && state.total == 0L) {
            item { EmptyMessage("이 달에는 지출이 없어요", "지출을 기록하면 카테고리별로 모아서 보여드려요.") }
        } else if (state.loaded) {
            item { CategoryDonut(state, onOpenCategory) }
            item { SectionTitle("카테고리") }
            items(state.categories, key = { "cat-${it.categoryId}" }) { stat ->
                CategoryRow(
                    stat = stat,
                    share = stat.total.toFloat() / state.total,
                    onClick = { onOpenCategory(stat.categoryId, state.period.label) },
                )
            }
            item { SectionTitle("결제수단별") }
            items(state.payments, key = { "pay-${it.paymentMethodId}" }) { payment ->
                PaymentRow(
                    stat = payment,
                    share = payment.total.toFloat() / state.total,
                    cardMonth = state.cardMonth,
                    onClick = { onOpenPaymentMethod(payment.paymentMethodId, state.period.label) },
                )
            }
        }

        if (state.loaded) {
            item { SectionTitle("월별 지출") }
            item {
                MonthlyBars(
                    bars = state.months.map { (month, total) -> MonthBar(month, total, month == state.period.label) },
                    color = ChartColors.category(1),
                    onBarClick = viewModel::selectMonth,
                    reference = state.budget.takeIf { it > 0 },
                    referenceLabel = "예산 ${state.budget.formatCompact()}",
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Text(
                    "막대를 누르면 그 달 통계로 바뀌어요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun CategoryDonut(state: StatsUiState, onOpenCategory: (Long?, YearMonth) -> Unit) {
    val top = state.categories.firstOrNull() ?: return
    val topShare = (top.total * 100f / state.total).roundToInt()
    val slices = state.donut.map { piece ->
        DonutSlice(
            key = piece.key,
            value = piece.total,
            color = if (piece.isOther) ChartColors.other() else ChartColors.category(piece.slot),
            label = piece.emoji,
        )
    }
    val description = state.categories.joinToString(", ") {
        "${it.name} ${(it.total * 100f / state.total).roundToInt()}%"
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        DonutChart(
            slices = slices,
            centerTitle = "가장 많이 · $topShare%",
            centerValue = "${top.emoji} ${top.name}",
            description = "카테고리별 지출 비중: $description",
            onSliceClick = { key ->
                state.donut.firstOrNull { it.key == key && !it.isOther }?.let { onOpenCategory(it.categoryId, state.period.label) }
            },
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

/**
 * 결제수단 한 줄: 이름, 금액, 비중 막대. 결제수단은 하나의 계열이라 막대는 모두 같은 색이다.
 * 카드 실적 목표가 있으면 달력 기준 실적도 보여준다.
 */
@Composable
private fun PaymentRow(stat: PaymentStat, share: Float, cardMonth: YearMonth, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stat.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(stat.total.formatAmount(), style = MaterialTheme.typography.bodyLarge.merge(AmountStyle))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
        }
        ProgressMeter(
            fraction = share,
            height = 6.dp,
            fill = ChartColors.category(1),
            track = ChartColors.category(1).copy(alpha = 0.18f),
            modifier = Modifier.padding(top = 4.dp, end = 12.dp),
        )
        if (stat.target != null && stat.cardSpent != null) {
            val percent = (stat.cardSpent * 100 / stat.target).toInt()
            Text(
                "${cardMonth.monthValue}월 실적 ${stat.cardSpent.formatAmount()} / ${stat.target.formatWon()} ($percent%)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** 범례를 겸하는 카테고리 줄: 색 견본 + 이모지·이름 + 비중 + 금액. 누르면 카테고리 상세. */
@Composable
private fun CategoryRow(stat: CategoryStat, share: Float, onClick: () -> Unit) {
    val swatch = if (stat.slot == 0) ChartColors.other() else ChartColors.category(stat.slot)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(swatch),
        )
        Text(
            "${stat.emoji} ${stat.name}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        )
        Text(
            "${(share * 100).roundToInt()}%",
            style = MaterialTheme.typography.bodyMedium.merge(AmountStyle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(stat.total.formatAmount(), style = MaterialTheme.typography.bodyLarge.merge(AmountStyle))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
        )
    }
}
