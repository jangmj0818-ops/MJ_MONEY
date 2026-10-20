package com.mj.money.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** 금액 하나를 입력받는 다이얼로그. 비워두고 저장하면 0. */
@Composable
fun AmountDialog(
    title: String,
    description: String,
    placeholder: String,
    initial: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var digits by rememberSaveable { mutableStateOf(if (initial > 0) initial.toString() else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = digits,
                    onValueChange = { digits = sanitizeAmountInput(it) },
                    placeholder = { Text(placeholder) },
                    suffix = { Text("원") },
                    singleLine = true,
                    visualTransformation = ThousandsSeparatorTransformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(digits.toLongOrNull() ?: 0L)
                onDismiss()
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** 한 달 예산 입력. 비워두고 저장하면 예산을 쓰지 않는다. */
@Composable
fun BudgetDialog(
    initial: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) = AmountDialog(
    title = "한 달 예산",
    description = "가계부 한 달(25일 ~ 다음 달 24일) 동안 쓸 금액이에요.",
    placeholder = "300,000",
    initial = initial,
    onConfirm = onConfirm,
    onDismiss = onDismiss,
)
