package com.mj.money.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mj.money.AppContainer
import com.mj.money.data.db.NotificationLogEntity
import com.mj.money.data.settings.BudgetSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(container: AppContainer) : ViewModel() {
    private val settings = container.settings

    val logCount: StateFlow<Int> = container.database.notificationLogDao().observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val latestLogAt: StateFlow<Long?> = container.database.notificationLogDao().observeLatestPostedAt()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val budget: StateFlow<BudgetSettings> = settings.budget
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetSettings())

    fun setMonthlyBudget(amount: Long) {
        viewModelScope.launch { settings.setMonthlyBudget(amount) }
    }

    fun setIncludeFixed(include: Boolean) {
        viewModelScope.launch { settings.setIncludeFixed(include) }
    }
}

class NotificationLogViewModel(container: AppContainer) : ViewModel() {
    private val dao = container.database.notificationLogDao()

    val logs: StateFlow<List<NotificationLogEntity>?> = dao.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun deleteAll() {
        viewModelScope.launch { dao.deleteAll() }
    }
}
