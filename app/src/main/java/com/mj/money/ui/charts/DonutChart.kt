package com.mj.money.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class DonutSlice(
    val key: String,
    val value: Long,
    val color: Color,
    /** 조각 위에 올리는 짧은 라벨 (이모지). 색만으로 구분하지 않게 해준다. */
    val label: String?,
)

/**
 * 도넛 차트. 조각 사이는 2dp 배경색 틈으로 나누고, 충분히 큰 조각엔 이모지를 올린다.
 * 조각을 누르면 onSliceClick(key).
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    centerTitle: String,
    centerValue: String,
    description: String,
    onSliceClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 200.dp,
    thickness: Dp = 30.dp,
) {
    val textMeasurer = rememberTextMeasurer()
    val total = slices.sumOf { it.value }.toFloat()
    // 각 조각의 시작 각도(12시 방향 = 0도, 시계방향)와 크기
    val sweeps = slices.map { if (total > 0) it.value / total * 360f else 0f }
    val starts = sweeps.runningFold(0f) { acc, s -> acc + s }

    Box(
        modifier = modifier.size(diameter),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = description }
                .pointerInput(slices) {
                    detectTapGestures { tap ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val distance = hypot(tap.x - center.x, tap.y - center.y)
                        val outer = size.width / 2f
                        val inner = outer - thickness.toPx() - 12.dp.toPx() // 안쪽으로 조금 넉넉히
                        if (distance < inner || distance > outer + 12.dp.toPx()) return@detectTapGestures
                        val degrees = (Math.toDegrees(atan2((tap.y - center.y).toDouble(), (tap.x - center.x).toDouble())) + 90 + 360) % 360
                        val index = starts.indices.lastOrNull { it < slices.size && degrees >= starts[it] } ?: return@detectTapGestures
                        onSliceClick(slices[index].key)
                    }
                },
        ) {
            val stroke = thickness.toPx()
            val radius = size.minDimension / 2f - stroke / 2f
            val topLeft = Offset(size.width / 2f - radius, size.height / 2f - radius)
            val arcSize = Size(radius * 2, radius * 2)
            // 2dp 틈을 각도로 바꾼다.
            val gapDegrees = if (slices.size > 1) (2.dp.toPx() / radius) * (180f / PI.toFloat()) else 0f

            slices.forEachIndexed { i, slice ->
                val sweep = sweeps[i] - gapDegrees
                if (sweep <= 0f) return@forEachIndexed
                drawArc(
                    color = slice.color,
                    startAngle = -90f + starts[i] + gapDegrees / 2,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
            }
            // 이모지 라벨: 조각이 충분히 클 때만 (작은 조각에 억지로 넣지 않는다)
            slices.forEachIndexed { i, slice ->
                val label = slice.label ?: return@forEachIndexed
                if (sweeps[i] < 26f) return@forEachIndexed
                val mid = Math.toRadians((-90f + starts[i] + sweeps[i] / 2).toDouble())
                val layout = textMeasurer.measure(label, TextStyle(fontSize = 14.sp))
                val cx = size.width / 2f + radius * cos(mid).toFloat()
                val cy = size.height / 2f + radius * sin(mid).toFloat()
                drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                centerTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                centerValue,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}
