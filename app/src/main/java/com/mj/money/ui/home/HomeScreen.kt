package com.mj.money.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mj.money.domain.BudgetSnapshot
import com.mj.money.domain.BudgetStatus
import com.mj.money.domain.RecurringItem
import com.mj.money.domain.formatAmount
import com.mj.money.domain.formatWon
import com.mj.money.domain.rangeText
import com.mj.money.domain.titleText
import com.mj.money.ui.AppViewModelProvider
import com.mj.money.ui.components.BudgetDialog
import com.mj.money.ui.components.EmptyMessage
import com.mj.money.ui.components.ProgressMeter
import com.mj.money.ui.components.SectionHeader
import com.mj.money.ui.components.Totals
import com.mj.money.ui.components.TransactionItem
import com.mj.money.ui.theme.AmountStyle
import com.mj.money.ui.theme.HeroAmountStyle
import com.mj.money.ui.theme.WarningFill

@Composable
fun HomeScreen(
    onOpenEntry: (Long) -> Unit,
    onSeeAll: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenEmergency: () -> Unit,
    onOpenReview: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showBudgetDialog by rememberSaveable { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)) {
        item { PeriodHeader(state) }
        if (state.needsReviewCount > 0) {
            item { ReviewBanner(state.needsReviewCount, onOpenReview) }
        }
        item {
            val snapshot = state.snapshot
            if (snapshot != null) {
                BudgetOverview(
                    snapshot = snapshot,
                    emergencyUsed = state.emergencyUsed,
                    emergencyRefillCounted = state.emergencyRefillCounted,
                    onEditBudget = { showBudgetDialog = true },
                )
            } else if (state.loaded) {
                NoBudgetOverview(state.totals, onSetBudget = { showBudgetDialog = true })
            }
        }
        if (state.loaded) {
            item { EmergencyCard(state, onOpenEmergency) }
        }
        if (state.upcomingFixed.isNotEmpty()) {
            item { UpcomingFixedCard(state.upcomingFixed, onOpenRecurring) }
        }
        item {
            SectionHeader(
                title = "최근 내역",
                actionLabel = "전체 보기",
                onAction = onSeeAll,
                modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 24.dp),
            )
        }
        if (state.loaded && state.recent.isEmpty()) {
            item {
                EmptyMessage(
                    title = "첫 기록을 남겨 보세요",
                    body = "오른쪽 아래 + 버튼으로 지출이나 수입을 기록할 수 있어요.",
                )
            }
        }
        items(state.recent, key = { it.id }) { row ->
            TransactionItem(row, onClick = { onOpenEntry(row.id) }, showDate = true)
        }
    }

    if (showBudgetDialog) {
        BudgetDialog(
            initial = state.settings.monthlyBudget,
            onConfirm = viewModel::setBudget,
            onDismiss = { showBudgetDialog = false },
        )
    }
}

/** 알림 자동 기록 중 확인이 필요한 건수 */
@Composable
private fun ReviewBanner(count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(18.dp),
        )
        Text(
            "자동 기록 확인 필요 ${count}건",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
private fun PeriodHeader(state: HomeUiState) {
    val snapshot = state.snapshot
    Row(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(state.period.titleText(), style = MaterialTheme.typography.titleLarge)
        Text(
            buildString {
                append("  ${state.period.rangeText()}")
                if (snapshot != null) append(" · ${snapshot.totalDays}일 중 ${snapshot.elapsedDays}일째")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp),
        )
    }
}

@Composable
private fun BudgetOverview(
    snapshot: BudgetSnapshot,
    emergencyUsed: Long,
    emergencyRefillCounted: Long,
    onEditBudget: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(
            if (snapshot.includeFixed) "이번 달 지출" else "이번 달 지출 (고정지출 제외)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(snapshot.spent.formatWon(), style = HeroAmountStyle)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "예산 ${snapshot.budget.formatWon()} 중 ${snapshot.usedPercent}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onEditBudget) { Text("예산 수정") }
        }

        BudgetMeter(snapshot, Modifier.padding(top = 4.dp))
        MeterLegend(snapshot, Modifier.padding(top = 6.dp))
        StatusLine(snapshot, Modifier.padding(top = 12.dp))

        Row(
            modifier = Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatTile(
                label = "남은 예산",
                value = snapshot.remaining.formatWon(),
                caption = if (snapshot.upcomingFixed > 0) "나갈 고정지출 ${snapshot.upcomingFixed.formatWon()} 뺀 금액" else null,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "하루 권장",
                value = snapshot.dailyAllowance.formatWon(),
                caption = "남은 ${snapshot.daysLeft}일 기준",
                modifier = Modifier.weight(1f),
            )
        }
        val notes = buildList {
            if (!snapshot.includeFixed && snapshot.separateFixedSpent > 0) {
                add("고정지출 ${snapshot.separateFixedSpent.formatWon()}은 예산과 따로 봐요.")
            }
            if (emergencyUsed > 0) add("비상금에서 쓴 ${emergencyUsed.formatWon()}은 예산에서 뺐어요.")
            if (emergencyRefillCounted > 0) add("비상금에 채운 ${emergencyRefillCounted.formatWon()}이 쓴 돈에 들어 있어요.")
        }
        notes.forEach {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun EmergencyCard(state: HomeUiState, onClick: () -> Unit) {
    val total = state.emergencyTotal
    val target = state.emergencyTarget
    val shortfall = (target - total).coerceAtLeast(0)
    Column(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 24.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Savings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                "비상금",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
            if (state.emergencyPockets.isNotEmpty()) {
                Text(total.formatWon(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        if (state.emergencyPockets.isEmpty()) {
            Text(
                "흩어져 있는 현금을 장소별로 적어두면 어디에 얼마 있는지 한눈에 볼 수 있어요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            ProgressMeter(
                fraction = if (target > 0) total.toFloat() / target else 0f,
                height = 6.dp,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                if (shortfall > 0) "목표 ${target.formatWon()} · ${shortfall.formatWon()} 부족" else "목표 ${target.formatWon()} 채움",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun statusFill(status: BudgetStatus): Color = when (status) {
    BudgetStatus.ON_TRACK -> MaterialTheme.colorScheme.primary
    BudgetStatus.AHEAD_OF_PACE -> WarningFill
    BudgetStatus.OVER -> MaterialTheme.colorScheme.error
}

/**
 * 예산 막대: [쓴 돈][나갈 고정지출][남은 칸] + 오늘 적정선 세로선.
 * 채움 색이 상태(여유·주의·초과)를 나타내고, 빈 칸은 같은 파랑 계열의 연한 색이다.
 */
@Composable
private fun BudgetMeter(snapshot: BudgetSnapshot, modifier: Modifier = Modifier) {
    val fill = statusFill(snapshot.status)
    val reserved = fill.copy(alpha = 0.35f)
    val track = MaterialTheme.colorScheme.primaryContainer
    val marker = MaterialTheme.colorScheme.onSurface
    val budget = snapshot.budget.toFloat()
    val spentFraction = (snapshot.spent / budget).coerceIn(0f, 1f)
    val reservedFraction = (snapshot.upcomingFixed / budget).coerceIn(0f, 1f - spentFraction)
    val paceFraction = (snapshot.expectedByToday / budget).coerceIn(0f, 1f)
    val description = "예산의 ${snapshot.usedPercent}% 사용, 오늘 적정선은 ${(paceFraction * 100).toInt()}%"

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .semantics { contentDescription = description },
    ) {
        val barHeight = 12.dp.toPx()
        val top = (size.height - barHeight) / 2
        val width = size.width
        val pill = CornerRadius(barHeight / 2)
        val end = CornerRadius(4.dp.toPx())
        val gap = 2.dp.toPx()

        drawRoundRect(track, topLeft = Offset(0f, top), size = Size(width, barHeight), cornerRadius = pill)
        val clip = Path().apply { addRoundRect(RoundRect(0f, top, width, top + barHeight, pill)) }
        clipPath(clip) {
            val spentWidth = width * spentFraction
            if (spentWidth > 0f) {
                drawRoundRect(fill, topLeft = Offset(0f, top), size = Size(spentWidth, barHeight), cornerRadius = end)
            }
            if (reservedFraction > 0f) {
                val start = if (spentWidth > 0f) spentWidth + gap else 0f
                val reservedWidth = (width * reservedFraction - (start - spentWidth)).coerceAtLeast(0f)
                drawRoundRect(reserved, topLeft = Offset(start, top), size = Size(reservedWidth, barHeight), cornerRadius = end)
            }
        }
        val x = (width * paceFraction).coerceIn(1.dp.toPx(), width - 1.dp.toPx())
        drawLine(marker, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
private fun MeterLegend(snapshot: BudgetSnapshot, modifier: Modifier = Modifier) {
    val fill = statusFill(snapshot.status)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem("쓴 돈") { Swatch(fill) }
        if (snapshot.upcomingFixed > 0) LegendItem("나갈 고정지출") { Swatch(fill.copy(alpha = 0.35f)) }
        LegendItem("오늘 적정선") {
            Box(
                Modifier
                    .width(2.dp)
                    .height(12.dp)
                    .background(MaterialTheme.colorScheme.onSurface),
            )
        }
    }
}

@Composable
private fun LegendItem(label: String, mark: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        mark()
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Swatch(color: Color) {
    Box(
        Modifier
            .size(10.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color),
    )
}

/** 상태는 색만으로 보여주지 않고 아이콘과 문장을 함께 쓴다. */
@Composable
private fun StatusLine(snapshot: BudgetSnapshot, modifier: Modifier = Modifier) {
    val (icon: ImageVector, message: String) = when (snapshot.status) {
        BudgetStatus.ON_TRACK -> {
            val margin = snapshot.expectedByToday - snapshot.spent
            Icons.Outlined.CheckCircle to
                if (margin > 0) "오늘 적정선보다 ${margin.formatWon()} 덜 썼어요" else "딱 오늘 적정선만큼 썼어요"
        }
        BudgetStatus.AHEAD_OF_PACE ->
            Icons.Outlined.Info to "오늘 적정선보다 ${(snapshot.spent - snapshot.expectedByToday).formatWon()} 더 썼어요"
        BudgetStatus.OVER -> Icons.Outlined.ErrorOutline to
            if (snapshot.spent > snapshot.budget) {
                "예산보다 ${(snapshot.spent - snapshot.budget).formatWon()} 더 썼어요"
            } else {
                "나갈 고정지출까지 합치면 ${(-snapshot.remaining).formatWon()} 모자라요"
            }
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = statusFill(snapshot.status), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StatTile(label: String, value: String, caption: String?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (caption != null) {
            Text(
                caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun NoBudgetOverview(totals: Totals, onSetBudget: () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(
            "이번 달 지출",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(totals.expense.formatWon(), style = HeroAmountStyle)
        Column(
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(16.dp),
        ) {
            Text("한 달 예산을 정해 보세요", style = MaterialTheme.typography.titleSmall)
            Text(
                "남은 돈과 하루에 쓸 수 있는 돈을 알려드려요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            FilledTonalButton(onClick = onSetBudget, modifier = Modifier.padding(top = 12.dp)) {
                Text("예산 정하기")
            }
        }
    }
}

@Composable
private fun UpcomingFixedCard(items: List<RecurringItem>, onOpenRecurring: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 24.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onOpenRecurring)
            .padding(vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "다가오는 고정지출",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenRecurring) { Text("전체") }
        }
        items.forEach { item ->
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(item.row.categoryEmoji ?: "🔁", fontSize = 18.sp)
                Text(
                    item.expense.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp),
                )
                Text(
                    when {
                        item.daysUntil > 0 -> "D-${item.daysUntil}"
                        item.daysUntil == 0L -> "오늘"
                        else -> "지남"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Text(
                    item.expense.amount.formatAmount(),
                    style = MaterialTheme.typography.bodyLarge.merge(AmountStyle),
                )
            }
        }
    }
}
