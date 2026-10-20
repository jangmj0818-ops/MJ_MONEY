package com.mj.money

import android.app.Application
import android.content.Context
import com.mj.money.budget.BudgetMonitor
import com.mj.money.budget.BudgetTracker
import com.mj.money.data.CatalogRepository
import com.mj.money.data.db.MjMoneyDatabase
import com.mj.money.data.settings.SettingsRepository
import com.mj.money.domain.PeriodCalculator
import com.mj.money.emergency.EmergencyRepository
import com.mj.money.notification.NotificationProcessor
import com.mj.money.notification.PaymentNotificationListener
import com.mj.money.recurring.RecurringProcessor
import com.mj.money.recurring.RecurringWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 앱 전체에서 공유하는 객체들 (수동 DI) */
class AppContainer(context: Context) {
    val database: MjMoneyDatabase = MjMoneyDatabase.create(context)

    val settings = SettingsRepository(context)

    // 월 시작일은 25일로 고정 (바꿀 일이 생기면 settings로 옮긴다)
    val periodCalculator = PeriodCalculator(startDay = 25)

    val recurringProcessor = RecurringProcessor(database, periodCalculator)

    val budgetTracker = BudgetTracker(database, settings, periodCalculator)

    val budgetMonitor = BudgetMonitor(context, budgetTracker)

    val emergency = EmergencyRepository(database)

    val catalog = CatalogRepository(database)

    val notificationProcessor = NotificationProcessor(database, periodCalculator)

    /** 화면과 상관없이 끝까지 실행돼야 하는 작업용 */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

class MjMoneyApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // 앱을 오랜만에 열었을 때 밀린 고정지출을 기록하고, 아직 처리하지 않은 결제 알림을 내역으로 만든다.
        // (고정지출을 먼저 기록해야 알림이 그 내역과 연결된다)
        container.applicationScope.launch {
            container.recurringProcessor.recordDue()
            container.notificationProcessor.processPending()
        }
        RecurringWorker.schedule(this)
        container.budgetMonitor.start(container.applicationScope)
        // 알림 수집기가 끊겨 있으면 다시 연결한다.
        PaymentNotificationListener.requestRebind(this)
    }
}
