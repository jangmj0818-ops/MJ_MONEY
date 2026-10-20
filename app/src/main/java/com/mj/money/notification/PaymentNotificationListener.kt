package com.mj.money.notification

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.mj.money.MjMoneyApplication
import com.mj.money.data.db.NotificationLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 카드·페이 결제 알림을 읽어 원문 로그로 저장한다. (자동 기록은 6단계에서 추가) */
class PaymentNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val insertLock = Mutex()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg == packageName) return
        val notification = sbn.notification ?: return
        if (NotificationCapturePolicy.isFinanceRelated(pkg)) NotificationDiagnostics.recordSeen(this, pkg)

        // 그룹 요약은 보통 개별 알림과 겹치지만, 대상 앱은 요약만 보내는 경우도 있어서 모은다.
        val isSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
        if (isSummary && !NotificationCapturePolicy.isKnownApp(pkg)) return

        val content = NotificationContent.from(notification)
        val rule = NotificationCapturePolicy.match(pkg, content.title, content.text, content.subText) ?: return
        val container = (application as MjMoneyApplication).container
        val dao = container.database.notificationLogDao()

        scope.launch {
            try {
                val logId = insertLock.withLock {
                    // 같은 알림이 갱신되며 여러 번 들어오면 한 번만 저장한다.
                    val since = sbn.postTime - DUPLICATE_WINDOW_MILLIS
                    if (dao.existsSince(pkg, content.title.orEmpty(), content.text.orEmpty(), since)) return@withLock null
                    dao.insert(
                        NotificationLogEntity(
                            packageName = pkg,
                            title = content.title,
                            text = content.text,
                            subText = content.subText,
                            postedAt = sbn.postTime,
                            matchedRule = if (isSummary) "$rule(summary)" else rule,
                        )
                    )
                }
                // 바로 해석해서 내역으로 저장한다.
                logId?.let { container.notificationProcessor.process(it) }
            } catch (e: Exception) {
                NotificationDiagnostics.recordError(this@PaymentNotificationListener, "${e::class.simpleName}: ${e.message}")
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationDiagnostics.recordConnection(this, connected = true)
    }

    /** 시스템이 연결을 끊으면 바로 다시 연결해 달라고 한다. (앱 업데이트 뒤 끊긴 채로 남는 경우가 있다) */
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NotificationDiagnostics.recordConnection(this, connected = false)
        requestRebind(this)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val DUPLICATE_WINDOW_MILLIS = 10 * 60 * 1000L

        fun isAccessGranted(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        /** 권한이 있으면 수집기를 다시 연결해 달라고 시스템에 요청한다. 이미 연결돼 있으면 아무 일도 없다. */
        fun requestRebind(context: Context) {
            if (!isAccessGranted(context)) return
            NotificationListenerService.requestRebind(ComponentName(context, PaymentNotificationListener::class.java))
        }

        /** 알림 접근 설정 화면을 연다. Android 11부터는 이 앱의 스위치로 바로 이동한다. */
        fun openAccessSettings(context: Context) {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    ComponentName(context, PaymentNotificationListener::class.java).flattenToString(),
                )
            } else {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            }
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
