package com.mj.money.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.money.data.db.TransactionRow
import com.mj.money.data.db.TxStatus
import com.mj.money.data.db.TxType
import com.mj.money.domain.formatAmount
import com.mj.money.domain.shortLabel
import com.mj.money.domain.toLocalDate
import com.mj.money.ui.theme.AmountStyle

@Composable
fun TransactionItem(
    row: TransactionRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDate: Boolean = false,
) {
    val title = row.merchant ?: row.memo ?: row.categoryName ?: fallbackTitle(row.type)
    val subtitle = buildList {
        if (showDate) add(row.occurredAt.toLocalDate().shortLabel())
        if (row.categoryName != null && row.categoryName != title) add(row.categoryName)
        if (row.paymentMethodName != null) add(row.paymentMethodName)
    }.joinToString(" · ")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(row.categoryEmoji ?: fallbackEmoji(row.type), fontSize = 20.sp)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (row.status == TxStatus.NEEDS_REVIEW) ReviewBadge()
            }
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = signedAmount(row),
            style = MaterialTheme.typography.bodyLarge.merge(AmountStyle),
            fontWeight = FontWeight.Medium,
            color = if (row.type == TxType.INCOME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (row.status == TxStatus.CANCELED) TextDecoration.LineThrough else null,
        )
    }
}

@Composable
private fun ReviewBadge() {
    Text(
        "확인 필요",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private fun signedAmount(row: TransactionRow): String = when (row.type) {
    TxType.EXPENSE -> "-${row.amount.formatAmount()}"
    TxType.INCOME -> "+${row.amount.formatAmount()}"
    TxType.TRANSFER -> row.amount.formatAmount()
}

private fun fallbackTitle(type: TxType): String = when (type) {
    TxType.TRANSFER -> "이체"
    else -> "미분류"
}

private fun fallbackEmoji(type: TxType): String = when (type) {
    TxType.TRANSFER -> "🔁"
    else -> "❔"
}
