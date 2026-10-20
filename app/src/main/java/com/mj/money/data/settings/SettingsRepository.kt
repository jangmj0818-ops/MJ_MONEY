package com.mj.money.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class BudgetSettings(
    /** 가계부 한 달 예산 (원). 0이면 정하지 않음. */
    val monthlyBudget: Long = 0,
    /** true면 고정지출도 예산 안에서 나간다고 보고 계산한다. */
    val includeFixed: Boolean = true,
)

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsDataStore

    val budget: Flow<BudgetSettings> = store.data
        .map { BudgetSettings(it[MONTHLY_BUDGET] ?: 0L, it[INCLUDE_FIXED] ?: true) }
        .distinctUntilChanged()

    suspend fun setMonthlyBudget(amount: Long) {
        store.edit { it[MONTHLY_BUDGET] = amount.coerceAtLeast(0) }
    }

    suspend fun setIncludeFixed(include: Boolean) {
        store.edit { it[INCLUDE_FIXED] = include }
    }

    /** 비상금 목표 금액 */
    val emergencyTarget: Flow<Long> = store.data
        .map { it[EMERGENCY_TARGET] ?: DEFAULT_EMERGENCY_TARGET }
        .distinctUntilChanged()

    suspend fun setEmergencyTarget(amount: Long) {
        store.edit { it[EMERGENCY_TARGET] = amount.coerceAtLeast(0) }
    }

    private companion object {
        val MONTHLY_BUDGET = longPreferencesKey("monthly_budget")
        val INCLUDE_FIXED = booleanPreferencesKey("budget_include_fixed")
        val EMERGENCY_TARGET = longPreferencesKey("emergency_target")
        const val DEFAULT_EMERGENCY_TARGET = 800_000L
    }
}
