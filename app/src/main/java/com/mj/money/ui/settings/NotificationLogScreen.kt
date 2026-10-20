package com.mj.money.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mj.money.data.db.NotificationLogEntity
import com.mj.money.domain.toLocalDateTime
import com.mj.money.notification.NotificationCapturePolicy
import com.mj.money.ui.AppViewModelProvider
import com.mj.money.ui.components.EmptyMessage
import java.time.format.DateTimeFormatter

private val TIME_FORMAT = DateTimeFormatter.ofPattern("M.d HH:mm")

@Composable
fun NotificationLogScreen(
    onBack: () -> Unit,
    viewModel: NotificationLogViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
            Text("모은 알림 원문", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (!logs.isNullOrEmpty()) {
                TextButton(onClick = { showDeleteConfirm = true }) { Text("전체 삭제") }
            }
        }

        val items = logs
        when {
            items == null -> Unit
            items.isEmpty() -> EmptyMessage(
                title = "아직 모인 알림이 없어요",
                body = "알림 접근 권한을 켜고 카드나 페이로 결제하면 여기에 쌓여요.",
            )
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(items, key = { it.id }) { log ->
                    LogItem(log)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("모은 알림을 모두 삭제할까요?") },
            text = { Text("가계부 내역은 지워지지 않아요. 알림 원문만 삭제돼요.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteAll()
                }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("취소") }
            },
        )
    }
}

@Composable
private fun LogItem(log: NotificationLogEntity) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(
            "${NotificationCapturePolicy.appName(log.packageName)} · ${log.postedAt.toLocalDateTime().format(TIME_FORMAT)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        log.title?.let { Text(it, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp)) }
        log.text?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        log.subText?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "${log.packageName} · ${log.matchedRule}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
