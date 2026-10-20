package com.mj.money.ui.recurring

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mj.money.data.db.RecurringRow
import com.mj.money.domain.RecurringItem
import com.mj.money.domain.formatAmount
import com.mj.money.domain.formatWon
import com.mj.money.domain.shortLabel
import com.mj.money.domain.titleText
import com.mj.money.domain.toLocalDate
import com.mj.money.alerts.AppNotifier
import com.mj.money.ui.AppViewModelProvider
import com.mj.money.ui.components.EmptyMessage
import com.mj.money.ui.theme.AmountStyle
import com.mj.money.ui.theme.HeroAmountStyle
import java.time.LocalDate

@Composable
fun RecurringScreen(
    onOpenEditor: (Long) -> Unit,
    viewModel: RecurringViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)) {
        item { Summary(state) }

        if (state.wantsNotifications) {
            item { NotificationPermissionBanner() }
        }
        if (state.loaded && state.isEmpty) {
            item {
                EmptyMessage(
                    title = "고정지출을 등록해 보세요",
                    body = "구독료처럼 매달 나가는 돈을 등록하면 결제 전날 알려주고, 결제일에 내역에 자동으로 기록해요.",
                )
            }
        }

        if (state.upcoming.isNotEmpty()) {
            item { SectionTitle("결제 예정") }
            items(state.upcoming, key = { it.expense.id }) { item ->
                RecurringItemRow(item, onClick = { onOpenEditor(item.expense.id) }, onRecord = { viewModel.recordNow(item) })
            }
        }
        if (state.recorded.isNotEmpty()) {
            item { SectionTitle("${state.period.titleText()} 기록 완료") }
            items(state.recorded, key = { it.expense.id }) { item ->
                RecurringItemRow(item, onClick = { onOpenEditor(item.expense.id) }, onRecord = {})
            }
        }
        if (state.canceled.isNotEmpty()) {
            item {
                SectionTitle(
                    title = "해지한 구독",
                    trailing = "월 ${state.canceledMonthlyTotal.formatWon()} 아끼는 중",
                )
            }
            items(state.canceled, key = { it.expense.id }) { row ->
                CanceledRow(row, onClick = { onOpenEditor(row.expense.id) })
            }
        }
    }
}

@Composable
private fun Summary(state: RecurringUiState) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text("고정지출", style = MaterialTheme.typography.headlineSmall)
        Text(
            "월 고정지출",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(state.monthlyTotal.formatWon(), style = HeroAmountStyle)
        if (state.activeCount > 0) {
            Text(
                "${state.activeCount}개 · ${state.period.titleText()} 기록 ${state.recorded.size} · 예정 ${state.upcoming.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, trailing: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun RecurringItemRow(item: RecurringItem, onClick: () -> Unit, onRecord: () -> Unit) {
    val expense = item.expense
    val subtitle = buildList {
        add("매월 ${expense.dayOfMonth}일")
        item.row.paymentMethodName?.let(::add)
        if (expense.autoRecord) add("자동 기록")
    }.joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmojiCircle(item.row.categoryEmoji)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(expense.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                expense.amount.formatAmount(),
                style = MaterialTheme.typography.bodyLarge.merge(AmountStyle),
                fontWeight = FontWeight.Medium,
            )
            StatusLabel(item, onRecord)
        }
    }
}

@Composable
private fun StatusLabel(item: RecurringItem, onRecord: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    when {
        item.recorded -> Text("기록됨", style = MaterialTheme.typography.labelSmall, color = colors.primary)
        item.inTrial -> Badge(
            "체험 중 ~${LocalDate.ofEpochDay(item.expense.trialEndEpochDay!!).shortLabel()}",
            colors.tertiaryContainer,
            colors.onTertiaryContainer,
        )
        item.daysUntil > 0 -> Badge("D-${item.daysUntil}", colors.primaryContainer, colors.onPrimaryContainer)
        else -> FilledTonalButton(
            onClick = onRecord,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            modifier = Modifier.heightIn(min = 28.dp),
        ) {
            Text(if (item.daysUntil == 0L) "오늘 · 기록" else "기록하기", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun Badge(text: String, background: Color, content: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun CanceledRow(row: RecurringRow, onClick: () -> Unit) {
    val canceledMonth = row.expense.canceledAt?.toLocalDate()?.monthValue
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmojiCircle(row.categoryEmoji, Modifier.alpha(0.5f))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                row.expense.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = TextDecoration.LineThrough,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (canceledMonth != null) {
                Text(
                    "${canceledMonth}월 해지",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            "월 ${row.expense.amount.formatAmount()}",
            style = MaterialTheme.typography.bodyMedium.merge(AmountStyle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmojiCircle(emoji: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji ?: "🔁", fontSize = 20.sp)
    }
}

/** 알림 권한이 없으면 결제 전날 알림을 받을 수 없다고 알려준다. */
@Composable
private fun NotificationPermissionBanner() {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var allowed by remember { mutableStateOf(AppNotifier.canNotify(context)) }

    fun openAppNotificationSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = AppNotifier.canNotify(context)
        // 두 번 거절하면 시스템이 더 묻지 않으므로 설정 화면으로 안내한다.
        val canAskAgain = activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true
        if (!granted && !canAskAgain) openAppNotificationSettings()
    }

    LifecycleResumeEffect(Unit) {
        allowed = AppNotifier.canNotify(context)
        onPauseOrDispose { }
    }
    if (allowed) return

    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "알림이 꺼져 있어서 결제 전날 알림을 보낼 수 없어요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = {
            val permissionMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            // 권한은 있는데 앱 알림 자체를 꺼둔 경우는 설정 화면에서만 켤 수 있다.
            if (permissionMissing) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else openAppNotificationSettings()
        }) {
            Text("켜기", color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.SemiBold)
        }
    }
}
