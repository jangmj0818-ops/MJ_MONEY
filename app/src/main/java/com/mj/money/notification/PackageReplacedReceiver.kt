package com.mj.money.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 앱이 업데이트되면 알림 수집기를 다시 연결한다. (업데이트 뒤 연결이 끊긴 채 알림을 놓치는 문제) */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            PaymentNotificationListener.requestRebind(context)
        }
    }
}
