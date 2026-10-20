package com.mj.money.notification

import android.content.Context
import androidx.core.content.edit

/**
 * 알림 수집이 안 될 때 원인을 가리기 위한 기록. 알림 내용은 남기지 않는다.
 * - 결제 관련 앱에서 알림을 몇 번 받았는지 (받긴 했는데 걸러진 건지, 아예 못 받은 건지)
 * - 수집기가 언제 연결되고 끊겼는지
 * - 저장하다 난 마지막 오류
 */
object NotificationDiagnostics {
    private const val PREFS = "notification_diagnostics"
    private const val KEY_CONNECTED_AT = "connectedAt"
    private const val KEY_DISCONNECTED_AT = "disconnectedAt"
    private const val KEY_LAST_ERROR = "lastError"
    private const val SEEN_PREFIX = "seen:"
    private const val SEEN_AT_PREFIX = "seenAt:"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun recordSeen(context: Context, packageName: String) {
        val prefs = prefs(context)
        prefs.edit {
            putInt(SEEN_PREFIX + packageName, prefs.getInt(SEEN_PREFIX + packageName, 0) + 1)
            putLong(SEEN_AT_PREFIX + packageName, System.currentTimeMillis())
        }
    }

    fun recordConnection(context: Context, connected: Boolean) {
        prefs(context).edit { putLong(if (connected) KEY_CONNECTED_AT else KEY_DISCONNECTED_AT, System.currentTimeMillis()) }
    }

    fun recordError(context: Context, message: String) {
        prefs(context).edit { putString(KEY_LAST_ERROR, "${System.currentTimeMillis()} $message") }
    }

    /** 마지막으로 연결된 뒤 끊기지 않았으면 true */
    fun isConnected(context: Context): Boolean {
        val prefs = prefs(context)
        return prefs.getLong(KEY_CONNECTED_AT, 0) > prefs.getLong(KEY_DISCONNECTED_AT, 0)
    }

    /** 개발용 요약: 연결 시각, 앱별 받은 횟수, 마지막 오류 */
    fun summary(context: Context): String {
        val prefs = prefs(context)
        val seen = prefs.all.keys.filter { it.startsWith(SEEN_PREFIX) }.sorted().joinToString(",") { key ->
            val pkg = key.removePrefix(SEEN_PREFIX)
            "$pkg:${prefs.getInt(key, 0)}@${prefs.getLong(SEEN_AT_PREFIX + pkg, 0)}"
        }
        return "connectedAt=${prefs.getLong(KEY_CONNECTED_AT, 0)} disconnectedAt=${prefs.getLong(KEY_DISCONNECTED_AT, 0)} " +
            "seen=[$seen] lastError=${prefs.getString(KEY_LAST_ERROR, null)}"
    }
}
