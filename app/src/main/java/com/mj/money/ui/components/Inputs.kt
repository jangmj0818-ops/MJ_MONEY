package com.mj.money.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mj.money.data.db.PaymentMethodEntity
import com.mj.money.domain.formatAmount
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalDatePickerDialog(
    date: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // DatePicker는 UTC 자정 기준 millis를 쓴다.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    onConfirm(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                }
                onDismiss()
            }) { Text("확인") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    ) {
        DatePicker(state = state)
    }
}

@Composable
fun PaymentMethodChips(
    paymentMethods: List<PaymentMethodEntity>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        paymentMethods.forEach { method ->
            FilterChip(
                selected = method.id == selectedId,
                onClick = { onSelect(method.id) },
                label = { Text(method.name) },
            )
        }
    }
}

/** 숫자만 들어있는 입력값을 12,000처럼 보여준다. (입력값에는 앞자리 0이 없어야 한다) */
object ThousandsSeparatorTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        if (digits.isEmpty()) return TransformedText(text, OffsetMapping.Identity)

        val formatted = digits.toLong().formatAmount()
        val totalCommas = (digits.length - 1) / 3
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val digitsAfter = digits.length - offset
                val commasAfter = if (digitsAfter > 0) (digitsAfter - 1) / 3 else 0
                return offset + totalCommas - commasAfter
            }

            override fun transformedToOriginal(offset: Int): Int =
                formatted.take(offset).count { it.isDigit() }
        }
        return TransformedText(AnnotatedString(formatted), mapping)
    }
}

/** 금액 입력 필드용: 숫자만 남기고 앞자리 0을 없앤다. */
fun sanitizeAmountInput(input: String, maxDigits: Int = 11): String =
    input.filter { it.isDigit() }.trimStart('0').take(maxDigits)
