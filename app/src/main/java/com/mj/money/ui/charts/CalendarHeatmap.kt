package com.mj.money.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mj.money.domain.BudgetPeriod
import com.mj.money.domain.HeatScale
import com.mj.money.domain.formatWon
import java.time.LocalDate

private val WEEKDAYS = listOf("일", "월", "화", "수", "목", "금", "토")

/**
 * 가계부 한 달(예: 9.25 ~ 10.24)을 달력으로 보여주고, 날마다 쓴 금액을 파랑 진하기로 칠한다.
 * 날짜를 누르면 onSelect(날짜).
 */
@Composable
fun CalendarHeatmap(
    period: BudgetPeriod,
    daily: Map<LocalDate, Long>,
    scale: HeatScale,
    today: LocalDate,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    // 일요일부터 시작하는 주 단위로 채운다.
    val firstCell = period.start.minusDays((period.start.dayOfWeek.value % 7).toLong())
    val weeks = generateSequence(firstCell) { it.plusWeeks(1) }
        .takeWhile { !it.isAfter(period.endInclusive) }
        .toList()

    Column(modifier = modifier) {
        Row(Modifier.fillMaxWidth()) {
            WEEKDAYS.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        weeks.forEach { weekStart ->
            Row(Modifier.fillMaxWidth()) {
                (0 until 7).forEach { offset ->
                    val date = weekStart.plusDays(offset.toLong())
                    if (date in period) {
                        DayCell(
                            date = date,
                            amount = daily[date] ?: 0L,
                            level = scale.level(daily[date] ?: 0L),
                            isToday = date == today,
                            isSelected = date == selected,
                            showMonth = date == period.start || date.dayOfMonth == 1,
                            onClick = { onSelect(date) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    amount: Long,
    level: Int,
    isToday: Boolean,
    isSelected: Boolean,
    showMonth: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val fill = if (level == 0) colors.surfaceContainer else ChartColors.heat(level)
    val textColor = if (level == 0) colors.onSurfaceVariant else ChartColors.textOn(fill)
    val shape = RoundedCornerShape(6.dp)
    val border = when {
        isSelected -> Modifier.border(2.dp, colors.onSurface, shape)
        isToday -> Modifier.border(1.dp, colors.primary, shape)
        else -> Modifier
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(shape)
            .background(fill)
            .then(border)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "${date.monthValue}월 ${date.dayOfMonth}일, 지출 ${amount.formatWon()}"
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (showMonth) "${date.monthValue}/${date.dayOfMonth}" else "${date.dayOfMonth}",
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else null,
        )
    }
}

/** 히트맵 범례: 적음 □■■■■ 많음 + 기준 설명 */
@Composable
fun HeatLegend(basis: String?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (basis != null) {
            Text(
                basis,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        Text("적음", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        Box(
            Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.surfaceContainer),
        )
        (1..4).forEach { level ->
            Box(
                Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ChartColors.heat(level)),
            )
        }
        Text("많음", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
    }
}
