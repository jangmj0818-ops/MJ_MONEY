package com.mj.money.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.money.domain.formatCompact
import java.time.YearMonth

data class MonthBar(
    val month: YearMonth,
    val value: Long,
    /** 보고 있는 달. 이 막대만 색을 칠하고 값을 단다. */
    val emphasized: Boolean,
)

/**
 * 월별 지출 막대. 보고 있는 달만 강조하고 나머지는 회색으로 둔다. (강조 형식)
 * 기준선(예산)이 있으면 얇은 실선으로 긋는다. 막대를 누르면 그 달로 이동.
 */
@Composable
fun MonthlyBars(
    bars: List<MonthBar>,
    color: Color,
    onBarClick: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    reference: Long? = null,
    referenceLabel: String? = null,
) {
    val textMeasurer = rememberTextMeasurer()
    val muted = ChartColors.deemphasis()
    val baseline = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val strongLabel = MaterialTheme.colorScheme.onSurface
    val maxValue = maxOf(bars.maxOfOrNull { it.value } ?: 0L, reference ?: 0L).coerceAtLeast(1L)
    val description = bars.joinToString(", ") { "${it.month.monthValue}월 ${it.value.formatCompact()}원" }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .semantics { contentDescription = "월별 지출: $description" }
                .pointerInput(bars) {
                    detectTapGestures { tap ->
                        val slot = size.width / bars.size.toFloat()
                        bars.getOrNull((tap.x / slot).toInt())?.let { onBarClick(it.month) }
                    }
                },
        ) {
            val topRoom = 22.dp.toPx() // 값 라벨 자리
            val chartHeight = size.height - topRoom
            val slot = size.width / bars.size
            val barWidth = minOf(24.dp.toPx(), slot * 0.5f)
            val radius = 4.dp.toPx()
            fun y(value: Long) = topRoom + chartHeight * (1f - value.toFloat() / maxValue)

            // 기준선 (예산): 얇은 실선 + 오른쪽 위 라벨
            if (reference != null && reference > 0) {
                val ry = y(reference)
                drawLine(labelColor.copy(alpha = 0.6f), Offset(0f, ry), Offset(size.width, ry), strokeWidth = 1.dp.toPx())
                if (referenceLabel != null) {
                    val layout = textMeasurer.measure(referenceLabel, TextStyle(fontSize = 11.sp, color = labelColor))
                    drawText(layout, topLeft = Offset(size.width - layout.size.width, ry - layout.size.height - 2.dp.toPx()))
                }
            }

            bars.forEachIndexed { i, bar ->
                if (bar.value <= 0) return@forEachIndexed
                val left = slot * i + (slot - barWidth) / 2f
                val top = y(bar.value)
                val fill = if (bar.emphasized) color else muted
                // 위쪽만 둥글게: 둥근 사각형을 그리고 아래 모서리를 네모로 덮는다.
                drawRoundRect(fill, Offset(left, top), Size(barWidth, size.height - top), CornerRadius(radius))
                val squareTop = maxOf(top, size.height - radius)
                drawRect(fill, Offset(left, squareTop), Size(barWidth, size.height - squareTop))

                if (bar.emphasized) {
                    val layout = textMeasurer.measure(
                        bar.value.formatCompact(),
                        TextStyle(fontSize = 12.sp, color = strongLabel, fontWeight = FontWeight.Medium),
                    )
                    val x = (left + barWidth / 2f - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
                    drawText(layout, topLeft = Offset(x, top - layout.size.height - 2.dp.toPx()))
                }
            }
            // 바닥선
            drawLine(baseline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        }
        Row(Modifier.padding(top = 4.dp)) {
            bars.forEach { bar ->
                Text(
                    "${bar.month.monthValue}월",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (bar.emphasized) strongLabel else labelColor,
                    fontWeight = if (bar.emphasized) FontWeight.SemiBold else null,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
