package com.mj.money.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 목표 대비 비율을 보여주는 단순한 막대. 빈 칸은 채움과 같은 파랑 계열의 연한 색. */
@Composable
fun ProgressMeter(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    fill: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(track),
    ) {
        val clamped = fraction.coerceIn(0f, 1f)
        if (clamped > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(clamped)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(fill),
            )
        }
    }
}
