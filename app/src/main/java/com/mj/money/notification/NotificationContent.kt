package com.mj.money.notification

import android.app.Notification
import androidx.core.app.NotificationCompat

/** 알림에서 읽어낸 글자. 앱마다 금액을 넣는 칸이 달라서 여러 칸을 모두 읽는다. */
data class NotificationContent(
    val title: String?,
    val text: String?,
    val subText: String?,
) {
    companion object {
        fun from(notification: Notification): NotificationContent {
            val extras = notification.extras
            fun read(key: String) = extras.getCharSequence(key)?.toString()?.trim()?.takeIf(String::isNotEmpty)

            val title = read(Notification.EXTRA_TITLE) ?: read(Notification.EXTRA_TITLE_BIG)
            val lines = buildList {
                // 펼친 본문이 있으면 그것을, 없으면 한 줄 본문을
                (read(Notification.EXTRA_BIG_TEXT) ?: read(Notification.EXTRA_TEXT))?.let(::add)
                // 여러 줄 알림 (InboxStyle)
                extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { add(it.toString()) }
                // 대화형 알림 (MessagingStyle): 마지막 메시지
                NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
                    ?.messages?.lastOrNull()?.text?.let { add(it.toString()) }
                // 아무것도 없으면 상태 표시줄 문구라도
                if (isEmpty()) notification.tickerText?.let { add(it.toString()) }
            }.map(String::trim).filter(String::isNotEmpty).distinct()

            val sub = listOfNotNull(
                read(Notification.EXTRA_SUB_TEXT),
                read(Notification.EXTRA_SUMMARY_TEXT),
                read(Notification.EXTRA_INFO_TEXT),
            ).distinct()

            return NotificationContent(
                title = title,
                text = lines.joinToString("\n").ifEmpty { null },
                subText = sub.joinToString(" · ").ifEmpty { null },
            )
        }
    }
}
