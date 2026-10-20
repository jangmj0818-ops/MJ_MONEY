package com.mj.money.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mj.money.domain.formatWon

/**
 * "지난달 같은 기간보다 12,000원 더 썼어요" 같은 비교 한 줄.
 * 지출은 늘면 나쁜 쪽이라 증가엔 빨강 위 화살표, 감소엔 파랑 아래 화살표. 글자는 기본 색.
 */
@Composable
fun ComparisonLine(current: Long, previous: Long, partial: Boolean, modifier: Modifier = Modifier) {
    if (previous <= 0) return
    val diff = current - previous
    val base = if (partial) "지난달 같은 기간보다" else "지난달보다"
    val colors = MaterialTheme.colorScheme
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        when {
            diff > 0 -> Icon(Icons.Outlined.ArrowUpward, contentDescription = null, tint = colors.error, modifier = Modifier.size(16.dp))
            diff < 0 -> Icon(Icons.Outlined.ArrowDownward, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
        }
        if (diff != 0L) Spacer(Modifier.width(4.dp))
        Text(
            when {
                diff > 0 -> "$base ${diff.formatWon()} 더 썼어요"
                diff < 0 -> "$base ${(-diff).formatWon()} 덜 썼어요"
                else -> "$base 똑같이 썼어요"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
    }
}
