package com.mj.money.ui.recurring

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mj.money.data.db.CategoryEntity
import com.mj.money.data.db.RecurringStatus
import com.mj.money.domain.fullDayLabel
import com.mj.money.ui.AppViewModelProvider
import com.mj.money.ui.components.DayOfMonthDialog
import com.mj.money.ui.components.FieldLabel
import com.mj.money.ui.components.LocalDatePickerDialog
import com.mj.money.ui.components.SettingRow
import com.mj.money.ui.components.SwitchRow
import com.mj.money.ui.components.PaymentMethodChips
import com.mj.money.ui.components.ThousandsSeparatorTransformation
import com.mj.money.ui.theme.AmountStyle
import java.time.LocalDate

private enum class ConfirmAction { Delete, Cancel }

@Composable
fun RecurringEditScreen(
    onDone: () -> Unit,
    viewModel: RecurringEditViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val form = state.form
    val context = LocalContext.current
    var showDayPicker by rememberSaveable { mutableStateOf(false) }
    var showTrialPicker by rememberSaveable { mutableStateOf(false) }
    var confirm by rememberSaveable { mutableStateOf<ConfirmAction?>(null) }

    // 알림이 필요한 설정으로 저장할 때 알림 권한을 한 번 묻는다. 거절해도 저장은 된다.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.save()
    }
    val onSave = {
        val needsNotification = form.remindDayBefore || form.trialEnd != null
        val permissionMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsNotification && permissionMissing && form.name.isNotBlank() && form.amount > 0) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.save()
        }
    }

    LaunchedEffect(state.finished) {
        if (state.finished) onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDone) { Icon(Icons.Filled.Close, contentDescription = "닫기") }
            Text(
                if (state.isEdit) "고정지출 수정" else "고정지출 추가",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (state.isEdit) {
                IconButton(onClick = { confirm = ConfirmAction.Delete }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "삭제")
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("이름") },
                    placeholder = { Text("넷플릭스") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.digits,
                    onValueChange = viewModel::onAmountChange,
                    label = { Text("금액") },
                    placeholder = { Text("17,000") },
                    suffix = { Text("원") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.merge(AmountStyle),
                    visualTransformation = ThousandsSeparatorTransformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SettingRow(
                title = "결제일",
                value = "매월 ${form.dayOfMonth}일",
                onClick = { showDayPicker = true },
            )

            FieldLabel("카테고리")
            CategoryChips(state.categories, form.categoryId, viewModel::onCategorySelect)

            FieldLabel("결제수단")
            PaymentMethodChips(state.paymentMethods, form.paymentMethodId, viewModel::onPaymentMethodSelect)

            Spacer(Modifier.height(8.dp))
            SwitchRow(
                title = "결제일에 자동 기록",
                supporting = if (form.autoRecord) {
                    val next = state.nextChargeDate
                    "다음 결제일(${next.monthValue}월 ${next.dayOfMonth}일)부터 내역에 자동으로 추가해요"
                } else {
                    "결제일이 지나면 목록에서 직접 기록할 수 있어요"
                },
                checked = form.autoRecord,
                onCheckedChange = viewModel::onAutoRecordChange,
            )
            SwitchRow(
                title = "결제 전날 알림",
                supporting = "결제일 하루 전에 알려드려요",
                checked = form.remindDayBefore,
                onCheckedChange = viewModel::onRemindChange,
            )
            SettingRow(
                title = "무료 체험 종료일",
                value = form.trialEnd?.fullDayLabel() ?: "없음",
                supporting = "체험 기간엔 기록하지 않고, 끝나기 전날 알려드려요",
                onClick = { showTrialPicker = true },
                trailing = if (form.trialEnd != null) {
                    { IconButton(onClick = { viewModel.onTrialEndChange(null) }) { Icon(Icons.Filled.Close, "체험 종료일 지우기") } }
                } else {
                    null
                },
            )

            if (state.isEdit) {
                Box(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    if (state.status == RecurringStatus.ACTIVE) {
                        OutlinedButton(onClick = { confirm = ConfirmAction.Cancel }, modifier = Modifier.fillMaxWidth()) {
                            Text("해지했어요")
                        }
                    } else {
                        OutlinedButton(onClick = viewModel::resubscribe, modifier = Modifier.fillMaxWidth()) {
                            Text("다시 구독하기")
                        }
                    }
                }
            }
        }

        state.error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
        }
        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(52.dp),
        ) {
            Text(if (state.isEdit) "수정 완료" else "저장", style = MaterialTheme.typography.titleMedium)
        }
    }

    if (showDayPicker) {
        DayOfMonthDialog(
            selected = form.dayOfMonth,
            onSelect = viewModel::onDayChange,
            onDismiss = { showDayPicker = false },
        )
    }
    if (showTrialPicker) {
        LocalDatePickerDialog(
            date = form.trialEnd ?: LocalDate.now().plusMonths(1),
            onConfirm = viewModel::onTrialEndChange,
            onDismiss = { showTrialPicker = false },
        )
    }
    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = {
                Text(if (action == ConfirmAction.Delete) "이 고정지출을 삭제할까요?" else "해지한 구독으로 옮길까요?")
            },
            text = {
                Text(
                    if (action == ConfirmAction.Delete) {
                        "이미 기록된 내역은 그대로 남아요. 해지한 거라면 '해지했어요'를 쓰면 아끼는 금액을 볼 수 있어요."
                    } else {
                        "자동 기록과 알림이 멈추고, 매달 아끼는 금액으로 보여드려요."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    if (action == ConfirmAction.Delete) viewModel.delete() else viewModel.cancelSubscription()
                }) {
                    Text(
                        if (action == ConfirmAction.Delete) "삭제" else "해지로 옮기기",
                        color = if (action == ConfirmAction.Delete) MaterialTheme.colorScheme.error else Color.Unspecified,
                    )
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("취소") } },
        )
    }
}

@Composable
private fun CategoryChips(categories: List<CategoryEntity>, selectedId: Long?, onSelect: (Long) -> Unit) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categories.forEach { category ->
            FilterChip(
                selected = category.id == selectedId,
                onClick = { onSelect(category.id) },
                label = { Text("${category.emoji} ${category.name}") },
            )
        }
    }
}

