package com.mj.money.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import androidx.room.withTransaction
import com.mj.money.AppContainer
import com.mj.money.MjMoneyApplication
import com.mj.money.data.db.CategoryType
import com.mj.money.data.db.RecurringExpenseEntity
import com.mj.money.notification.PaymentNotificationListener
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * 개발용 (debug 빌드에만 포함): adb로 고정지출·예산을 한 번에 넣는다.
 *
 * 고정지출 가져오기:
 * ```
 * adb shell am broadcast -n com.mj.money/.debug.DebugImportReceiver \
 *     -a com.mj.money.debug.IMPORT_RECURRING --es payload <base64로 인코딩한 JSON 배열>
 * ```
 * JSON 항목: {"name":"음악 구독","amount":10000,"dayOfMonth":15,"paymentMethod":"삼성카드","category":"OTT·구독"}
 *
 * 카테고리·결제수단은 이름으로 찾는다. 하나라도 못 찾으면 아무것도 넣지 않는다.
 * 같은 이름의 고정지출이 이미 있으면 건너뛰므로 여러 번 보내도 중복되지 않는다.
 */
class DebugImportReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as MjMoneyApplication).container
        val work: suspend () -> String = when (intent.action) {
            ACTION_IMPORT_RECURRING -> {
                val payload = intent.getStringExtra(EXTRA_PAYLOAD) ?: return
                suspend {
                    val json = String(Base64.decode(payload, Base64.DEFAULT), Charsets.UTF_8)
                    val (added, skipped) = importRecurring(container, JSONArray(json))
                    "added=$added skipped=$skipped"
                }
            }
            // adb shell am broadcast -n com.mj.money/.debug.DebugImportReceiver \
            //     -a com.mj.money.debug.SET_BUDGET --el amount 300000 [--ez include_fixed true]
            ACTION_SET_BUDGET -> {
                val amount = intent.getLongExtra(EXTRA_AMOUNT, -1)
                if (amount < 0) return
                suspend {
                    container.settings.setMonthlyBudget(amount)
                    if (intent.hasExtra(EXTRA_INCLUDE_FIXED)) {
                        container.settings.setIncludeFixed(intent.getBooleanExtra(EXTRA_INCLUDE_FIXED, true))
                    }
                    "budget=$amount"
                }
            }
            // 알림 수집 진단: 권한 상태와 앱별 수집 개수만 (알림 내용은 읽지 않는다)
            ACTION_NOTIFICATION_STATUS -> suspend {
                val granted = PaymentNotificationListener.isAccessGranted(context)
                val summary = container.database.notificationLogDao().summary().joinToString(",") {
                    "${it.packageName}/${it.matchedRule}:${it.count}@${java.time.Instant.ofEpochMilli(it.lastPostedAt)}"
                }
                val parsed = container.database.notificationLogDao().parseSummary().joinToString(",") { "${it.key}:${it.count}" }
                val tx = container.database.transactionDao().sourceStatusSummary().joinToString(",") { "${it.key}:${it.count}" }
                "access=$granted logs=[$summary] parsed=[$parsed] tx=[$tx] " +
                    com.mj.money.notification.NotificationDiagnostics.summary(context)
            }
            // 모은 알림 원문을 앱 전용 외부 폴더에 JSON으로 내보낸다. (adb pull 뒤 바로 지운다)
            ACTION_EXPORT_LOGS -> suspend {
                val logs = container.database.notificationLogDao().observeRecent().first()
                val json = JSONArray()
                logs.forEach { log ->
                    json.put(
                        JSONObject()
                            .put("id", log.id)
                            .put("packageName", log.packageName)
                            .put("title", log.title ?: JSONObject.NULL)
                            .put("text", log.text ?: JSONObject.NULL)
                            .put("subText", log.subText ?: JSONObject.NULL)
                            .put("postedAt", log.postedAt)
                            .put("matchedRule", log.matchedRule)
                    )
                }
                val file = java.io.File(context.getExternalFilesDir(null), "notification_logs.json")
                file.writeText(json.toString(1), Charsets.UTF_8)
                "exported=${logs.size} path=${file.absolutePath}"
            }
            // 알림 자동 기록을 처음부터 다시: 알림으로 만든 거래를 지우고 모든 알림을 다시 처리한다.
            ACTION_REPROCESS -> suspend { "deletedNotificationTx=${container.notificationProcessor.reprocessAll()}" }
            // 테스트 알림 기록 지우기: --es package com.android.shell
            ACTION_DELETE_LOGS -> {
                val packageName = intent.getStringExtra("package") ?: return
                suspend { "deleted=${container.database.notificationLogDao().deleteByPackage(packageName)}" }
            }
            // 지출 카테고리 아이콘 바꾸기: payload = base64({"AI":"✨","OTT·구독":"▶️"})
            ACTION_SET_CATEGORY_EMOJI -> {
                val payload = intent.getStringExtra(EXTRA_PAYLOAD) ?: return
                suspend {
                    val json = JSONObject(String(Base64.decode(payload, Base64.DEFAULT), Charsets.UTF_8))
                    val dao = container.database.categoryDao()
                    val updated = json.keys().asSequence().sumOf { name ->
                        dao.updateEmoji(name, CategoryType.EXPENSE, json.getString(name))
                    }
                    "updated=$updated"
                }
            }
            // adb shell am broadcast -n com.mj.money/.debug.DebugImportReceiver -a com.mj.money.debug.STATUS
            // 업데이트 뒤 DB가 제대로 올라갔는지 확인용. 거래 내용은 읽지 않는다.
            ACTION_STATUS -> suspend {
                val db = container.database
                val payments = db.paymentMethodDao().observeVisible().first().joinToString(",") { "${it.name}:${it.kind}" }
                val pockets = db.emergencyDao().observePockets().first().size
                val recurring = db.recurringExpenseDao().getActive().size
                val budget = container.settings.budget.first()
                val slots = db.categoryDao().observeAll().first()
                    .filter { it.type == CategoryType.EXPENSE }
                    .joinToString(",") { "${it.emoji}${it.name}:${it.colorSlot}" }
                "db=${db.openHelper.readableDatabase.version} payments=[$payments] pockets=$pockets " +
                    "recurring=$recurring budget=${budget.monthlyBudget} includeFixed=${budget.includeFixed} " +
                    "categories=[$slots]"
            }
            else -> return
        }
        val pending = goAsync()

        container.applicationScope.launch {
            val result = try {
                work()
            } catch (e: Exception) {
                Log.e(TAG, "${intent.action} 실패", e)
                "error=${e.message}"
            }
            Log.i(TAG, result)
            pending.resultData = result
            pending.finish()
        }
    }

    private suspend fun importRecurring(container: AppContainer, items: JSONArray): Pair<Int, Int> {
        val db = container.database
        val categories = db.categoryDao().observeVisible().first().filter { it.type == CategoryType.EXPENSE }
        val payments = db.paymentMethodDao().observeVisible().first()
        val existingNames = db.recurringExpenseDao().getActive().map { it.name }.toSet()
        val today = LocalDate.now().toEpochDay()
        val now = System.currentTimeMillis()

        // 먼저 전부 확인하고, 문제가 없을 때만 넣는다.
        val toInsert = (0 until items.length()).map { i ->
            val item = items.getJSONObject(i)
            val categoryName = item.getString("category")
            val paymentName = item.getString("paymentMethod")
            val category = categories.firstOrNull { it.name.normalized() == categoryName.normalized() }
                ?: error("카테고리를 찾을 수 없어요: $categoryName")
            val payment = payments.firstOrNull { it.name.normalized() == paymentName.normalized() }
                ?: error("결제수단을 찾을 수 없어요: $paymentName")
            RecurringExpenseEntity(
                name = item.getString("name"),
                amount = item.getLong("amount"),
                categoryId = category.id,
                paymentMethodId = payment.id,
                dayOfMonth = item.getInt("dayOfMonth").also { require(it in 1..31) { "결제일이 이상해요: $it" } },
                startEpochDay = today,
                createdAt = now,
                updatedAt = now,
            )
        }
        val (duplicates, fresh) = toInsert.partition { it.name in existingNames }
        db.withTransaction {
            fresh.forEach { db.recurringExpenseDao().insert(it) }
        }
        return fresh.size to duplicates.size
    }

    /** "OTT·구독"과 "OTT 구독"처럼 기호·공백만 다른 이름을 같게 본다. */
    private fun String.normalized(): String = filter { it.isLetterOrDigit() }

    private companion object {
        const val TAG = "MjMoneyDebugImport"
        const val ACTION_IMPORT_RECURRING = "com.mj.money.debug.IMPORT_RECURRING"
        const val ACTION_SET_BUDGET = "com.mj.money.debug.SET_BUDGET"
        const val ACTION_STATUS = "com.mj.money.debug.STATUS"
        const val ACTION_SET_CATEGORY_EMOJI = "com.mj.money.debug.SET_CATEGORY_EMOJI"
        const val ACTION_NOTIFICATION_STATUS = "com.mj.money.debug.NOTIFICATION_STATUS"
        const val ACTION_DELETE_LOGS = "com.mj.money.debug.DELETE_LOGS"
        const val ACTION_EXPORT_LOGS = "com.mj.money.debug.EXPORT_LOGS"
        const val ACTION_REPROCESS = "com.mj.money.debug.REPROCESS"
        const val EXTRA_PAYLOAD = "payload"
        const val EXTRA_AMOUNT = "amount"
        const val EXTRA_INCLUDE_FIXED = "include_fixed"
    }
}
