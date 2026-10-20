package com.mj.money.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mj.money.AppContainer
import com.mj.money.MjMoneyApplication
import com.mj.money.ui.emergency.EmergencyCountViewModel
import com.mj.money.ui.emergency.EmergencyRefillViewModel
import com.mj.money.ui.emergency.EmergencyViewModel
import com.mj.money.ui.entry.EntryViewModel
import com.mj.money.ui.history.HistoryViewModel
import com.mj.money.ui.home.HomeViewModel
import com.mj.money.ui.recurring.RecurringEditViewModel
import com.mj.money.ui.review.ReviewViewModel
import com.mj.money.ui.recurring.RecurringViewModel
import com.mj.money.ui.settings.NotificationLogViewModel
import com.mj.money.ui.settings.SettingsViewModel
import com.mj.money.ui.manage.CategoryEditViewModel
import com.mj.money.ui.manage.CategoryManageViewModel
import com.mj.money.ui.manage.PaymentEditViewModel
import com.mj.money.ui.manage.PaymentManageViewModel
import com.mj.money.ui.stats.CategoryDetailViewModel
import com.mj.money.ui.stats.PaymentDetailViewModel
import com.mj.money.ui.stats.StatsViewModel

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { HomeViewModel(container()) }
        initializer { HistoryViewModel(container()) }
        initializer { StatsViewModel(container()) }
        initializer { CategoryDetailViewModel(createSavedStateHandle(), container()) }
        initializer { PaymentDetailViewModel(createSavedStateHandle(), container()) }
        initializer { ReviewViewModel(container()) }
        initializer { CategoryManageViewModel(container()) }
        initializer { CategoryEditViewModel(createSavedStateHandle(), container()) }
        initializer { PaymentManageViewModel(container()) }
        initializer { PaymentEditViewModel(createSavedStateHandle(), container()) }
        initializer { EntryViewModel(createSavedStateHandle(), container()) }
        initializer { EmergencyViewModel(container()) }
        initializer { EmergencyRefillViewModel(container()) }
        initializer { EmergencyCountViewModel(container()) }
        initializer { RecurringViewModel(container()) }
        initializer { RecurringEditViewModel(createSavedStateHandle(), container()) }
        initializer { SettingsViewModel(container()) }
        initializer { NotificationLogViewModel(container()) }
    }
}

private fun CreationExtras.container(): AppContainer =
    (this[APPLICATION_KEY] as MjMoneyApplication).container
