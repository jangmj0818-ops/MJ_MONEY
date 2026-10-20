package com.mj.money.alerts

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mj.money.MainActivity
import com.mj.money.R

/** 앱이 보내는 알림 (고정지출, 예산) */
object AppNotifier {

    enum class Channel(val id: String, val title: String, val description: String) {
        RECURRING("recurring", "고정지출 알림", "결제 전날과 무료 체험이 끝나기 전날 알려드려요."),
        BUDGET("budget", "예산 알림", "이번 달 예산의 80%와 100%를 쓰면 알려드려요."),
    }

    fun canNotify(context: Context): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission") // canNotify()로 확인한다.
    fun notify(context: Context, channel: Channel, id: Int, title: String, text: String) {
        if (!canNotify(context)) return
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(channel.id, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(channel.title)
                .setDescription(channel.description)
                .build()
        )
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_stat_recurring)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(id, notification)
    }
}
