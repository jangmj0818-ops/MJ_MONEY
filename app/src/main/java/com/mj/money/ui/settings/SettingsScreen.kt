package com.mj.money.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mj.money.domain.formatWon
import com.mj.money.domain.toLocalDateTime
import com.mj.money.notification.NotificationDiagnostics
import com.mj.money.notification.PaymentNotificationListener
import java.time.format.DateTimeFormatter
import com.mj.money.ui.AppViewModelProvider
import com.mj.money.ui.components.BudgetDialog

@Composable
fun SettingsScreen(
    onOpenNotificationLog: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenPaymentMethods: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val context = LocalContext.current
    val logCount by viewModel.logCount.collectAsStateWithLifecycle()
    val latestLogAt by viewModel.latestLogAt.collectAsStateWithLifecycle()
    val budget by viewModel.budget.collectAsStateWithLifecycle()
    var accessGranted by remember { mutableStateOf(PaymentNotificationListener.isAccessGranted(context)) }
    var connected by remember { mutableStateOf(NotificationDiagnostics.isConnected(context)) }
    var batteryUnrestricted by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    var showBudgetDialog by rememberSaveable { mutableStateOf(false) }

    // 시스템 설정에서 돌아왔을 때 권한·연결·배터리 상태를 다시 확인한다.
    LifecycleResumeEffect(Unit) {
        accessGranted = PaymentNotificationListener.isAccessGranted(context)
        connected = NotificationDiagnostics.isConnected(context)
        batteryUnrestricted = isIgnoringBatteryOptimizations(context)
        onPauseOrDispose { }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            "설정",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
        )

        GroupTitle("예산")
        ListItem(
            headlineContent = { Text("한 달 예산") },
            supportingContent = { Text(if (budget.monthlyBudget > 0) budget.monthlyBudget.formatWon() else "정하지 않았어요") },
            modifier = Modifier.clickable { showBudgetDialog = true },
        )
        ListItem(
            headlineContent = { Text("예산에 고정지출 포함") },
            supportingContent = {
                Text(
                    if (budget.includeFixed) "고정지출도 예산에서 나간다고 보고, 나갈 고정지출을 미리 빼고 계산해요."
                    else "고정지출은 빼고 나머지 지출만 예산과 비교해요."
                )
            },
            trailingContent = {
                Switch(checked = budget.includeFixed, onCheckedChange = viewModel::setIncludeFixed)
            },
            modifier = Modifier.clickable { viewModel.setIncludeFixed(!budget.includeFixed) },
        )

        GroupTitle("관리")
        ListItem(
            headlineContent = { Text("카테고리") },
            supportingContent = { Text("추가, 이름·아이콘·색 바꾸기, 순서, 숨기기") },
            modifier = Modifier.clickable(onClick = onOpenCategories),
        )
        ListItem(
            headlineContent = { Text("결제수단") },
            supportingContent = { Text("추가, 카드 결제일·실적 목표, 순서, 숨기기") },
            modifier = Modifier.clickable(onClick = onOpenPaymentMethods),
        )

        GroupTitle("결제 알림 인식")
        ListItem(
            headlineContent = { Text("알림 접근 권한") },
            supportingContent = {
                Text(
                    if (accessGranted) "켜져 있어요. 카드·페이 결제 알림을 모으고 있어요."
                    else "꺼져 있어요. 켜야 결제 알림을 모을 수 있어요."
                )
            },
            trailingContent = {
                if (accessGranted) {
                    Text("켜짐", color = MaterialTheme.colorScheme.primary)
                } else {
                    FilledTonalButton(onClick = { PaymentNotificationListener.openAccessSettings(context) }) {
                        Text("켜기")
                    }
                }
            },
            modifier = Modifier.clickable { PaymentNotificationListener.openAccessSettings(context) },
        )
        if (accessGranted) {
            ListItem(
                headlineContent = { Text("수집 상태") },
                supportingContent = {
                    val last = latestLogAt?.let { "마지막으로 모은 알림 ${it.toLocalDateTime().format(TIME_FORMAT)}" }
                        ?: "아직 모은 알림이 없어요"
                    Text(if (connected) "연결됨 · $last" else "연결이 끊겨 있어요. 눌러서 다시 연결해요.")
                },
                modifier = Modifier.clickable {
                    PaymentNotificationListener.requestRebind(context)
                    connected = NotificationDiagnostics.isConnected(context)
                },
            )
        }
        ListItem(
            headlineContent = { Text("배터리 사용 제한 없음") },
            supportingContent = {
                Text(
                    if (batteryUnrestricted) "켜져 있어요. 절전 때문에 알림 수집이 멈추지 않아요."
                    else "절전 때문에 알림 수집이 멈출 수 있어요. 켜두는 걸 권해요."
                )
            },
            trailingContent = {
                if (batteryUnrestricted) {
                    Text("켜짐", color = MaterialTheme.colorScheme.primary)
                } else {
                    FilledTonalButton(onClick = { requestIgnoreBatteryOptimizations(context) }) { Text("켜기") }
                }
            },
            modifier = Modifier.clickable { if (!batteryUnrestricted) requestIgnoreBatteryOptimizations(context) },
        )
        ListItem(
            headlineContent = { Text("모은 알림 원문") },
            supportingContent = { Text("${logCount}건 · 자동 인식 규칙을 만드는 데 써요") },
            modifier = Modifier.clickable(onClick = onOpenNotificationLog),
        )

        GroupTitle("가계부")
        ListItem(
            headlineContent = { Text("한 달 기준") },
            supportingContent = { Text("매월 25일 ~ 다음 달 24일 (끝나는 달 이름으로 표시)") },
        )
    }

    if (showBudgetDialog) {
        BudgetDialog(
            initial = budget.monthlyBudget,
            onConfirm = viewModel::setMonthlyBudget,
            onDismiss = { showBudgetDialog = false },
        )
    }
}

private val TIME_FORMAT = DateTimeFormatter.ofPattern("M.d HH:mm")

private fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

/** 시스템의 "배터리 사용량 최적화 중지" 확인 창을 띄운다. */
@SuppressLint("BatteryLife") // 개인용 앱이라 스토어 정책과 무관하다.
private fun requestIgnoreBatteryOptimizations(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

@Composable
private fun GroupTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp),
    )
}
