package com.mj.money.ui

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mj.money.ui.emergency.EmergencyCountScreen
import com.mj.money.ui.emergency.EmergencyRefillScreen
import com.mj.money.ui.emergency.EmergencyScreen
import com.mj.money.ui.entry.EntryScreen
import com.mj.money.ui.history.HistoryScreen
import com.mj.money.ui.home.HomeScreen
import com.mj.money.ui.manage.CategoryEditScreen
import com.mj.money.ui.manage.CategoryManageScreen
import com.mj.money.ui.manage.PaymentEditScreen
import com.mj.money.ui.manage.PaymentManageScreen
import com.mj.money.ui.recurring.RecurringEditScreen
import com.mj.money.ui.review.ReviewScreen
import com.mj.money.ui.recurring.RecurringScreen
import com.mj.money.ui.settings.NotificationLogScreen
import com.mj.money.ui.settings.SettingsScreen
import com.mj.money.ui.stats.CategoryDetailScreen
import com.mj.money.ui.stats.PaymentDetailScreen
import com.mj.money.ui.stats.StatsScreen

private object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val RECURRING = "recurring"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val ENTRY = "entry?id={id}&emergency={emergency}"
    const val RECURRING_EDIT = "recurring_edit?id={id}"
    const val NOTIFICATION_LOG = "notification_log"
    const val EMERGENCY = "emergency"
    const val EMERGENCY_REFILL = "emergency_refill"
    const val EMERGENCY_COUNT = "emergency_count"
    const val CATEGORY_DETAIL = "category_detail?categoryId={categoryId}&month={month}"
    const val PAYMENT_DETAIL = "payment_detail?paymentMethodId={paymentMethodId}&month={month}"
    const val CATEGORY_MANAGE = "category_manage"
    const val CATEGORY_EDIT = "category_edit?id={id}&type={type}"
    const val PAYMENT_MANAGE = "payment_manage"
    const val PAYMENT_EDIT = "payment_edit?id={id}"
    const val REVIEW = "review"

    fun paymentDetail(paymentMethodId: Long?, month: java.time.YearMonth): String =
        "payment_detail?paymentMethodId=${paymentMethodId ?: -1}&month=$month"

    fun categoryEdit(id: Long? = null, type: String = "EXPENSE"): String = "category_edit?id=${id ?: -1}&type=$type"

    fun paymentEdit(id: Long? = null): String = "payment_edit?id=${id ?: -1}"

    fun categoryDetail(categoryId: Long?, month: java.time.YearMonth): String =
        "category_detail?categoryId=${categoryId ?: -1}&month=$month"

    fun entry(id: Long? = null, emergency: Boolean = false): String =
        "entry?id=${id ?: -1}&emergency=$emergency"

    fun recurringEdit(id: Long? = null): String = if (id == null) "recurring_edit" else "recurring_edit?id=$id"
}

private val optionalIdArgument: List<NamedNavArgument> = listOf(
    navArgument("id") {
        type = NavType.LongType
        defaultValue = -1L
    },
)

private enum class TopTab(val route: String, val label: String, val icon: ImageVector) {
    Home(Routes.HOME, "홈", Icons.Outlined.Home),
    History(Routes.HISTORY, "내역", Icons.AutoMirrored.Outlined.List),
    Recurring(Routes.RECURRING, "고정", Icons.Outlined.Autorenew),
    Stats(Routes.STATS, "통계", Icons.Outlined.PieChart),
    Settings(Routes.SETTINGS, "설정", Icons.Outlined.Settings),
}

@Composable
fun MjMoneyApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTab = TopTab.entries.firstOrNull { it.route == backStackEntry?.destination?.route }

    Scaffold(
        bottomBar = {
            if (currentTab != null) {
                NavigationBar {
                    TopTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == currentTab,
                            onClick = { navController.navigateToTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            when (currentTab) {
                TopTab.Home, TopTab.History ->
                    FloatingActionButton(onClick = { navController.navigate(Routes.entry()) }) {
                        Icon(Icons.Filled.Add, contentDescription = "새 내역 입력")
                    }
                TopTab.Recurring ->
                    FloatingActionButton(onClick = { navController.navigate(Routes.recurringEdit()) }) {
                        Icon(Icons.Filled.Add, contentDescription = "고정지출 추가")
                    }
                else -> Unit
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenEntry = { navController.navigate(Routes.entry(it)) },
                    onSeeAll = { navController.navigateToTab(Routes.HISTORY) },
                    onOpenRecurring = { navController.navigateToTab(Routes.RECURRING) },
                    onOpenEmergency = { navController.navigate(Routes.EMERGENCY) },
                    onOpenReview = { navController.navigate(Routes.REVIEW) },
                )
            }
            composable(Routes.REVIEW) {
                ReviewScreen(
                    onBack = { navController.popIfCurrent(Routes.REVIEW) },
                    onOpenEntry = { navController.navigate(Routes.entry(it)) },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(onOpenEntry = { navController.navigate(Routes.entry(it)) })
            }
            composable(Routes.RECURRING) {
                RecurringScreen(onOpenEditor = { navController.navigate(Routes.recurringEdit(it)) })
            }
            composable(Routes.STATS) {
                StatsScreen(
                    onOpenCategory = { id, month -> navController.navigate(Routes.categoryDetail(id, month)) },
                    onOpenPaymentMethod = { id, month -> navController.navigate(Routes.paymentDetail(id, month)) },
                )
            }
            composable(
                Routes.PAYMENT_DETAIL,
                arguments = listOf(
                    navArgument("paymentMethodId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                    navArgument("month") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) {
                PaymentDetailScreen(
                    onBack = { navController.popIfCurrent(Routes.PAYMENT_DETAIL) },
                    onOpenEntry = { navController.navigate(Routes.entry(it)) },
                )
            }
            composable(Routes.CATEGORY_MANAGE) {
                CategoryManageScreen(
                    onBack = { navController.popIfCurrent(Routes.CATEGORY_MANAGE) },
                    onEdit = { navController.navigate(Routes.categoryEdit(it)) },
                    onAdd = { type -> navController.navigate(Routes.categoryEdit(type = type.name)) },
                )
            }
            composable(
                Routes.CATEGORY_EDIT,
                arguments = optionalIdArgument + navArgument("type") {
                    type = NavType.StringType
                    defaultValue = "EXPENSE"
                },
            ) {
                CategoryEditScreen(onDone = { navController.popIfCurrent(Routes.CATEGORY_EDIT) })
            }
            composable(Routes.PAYMENT_MANAGE) {
                PaymentManageScreen(
                    onBack = { navController.popIfCurrent(Routes.PAYMENT_MANAGE) },
                    onEdit = { navController.navigate(Routes.paymentEdit(it)) },
                    onAdd = { navController.navigate(Routes.paymentEdit()) },
                )
            }
            composable(Routes.PAYMENT_EDIT, arguments = optionalIdArgument) {
                PaymentEditScreen(onDone = { navController.popIfCurrent(Routes.PAYMENT_EDIT) })
            }
            composable(
                Routes.CATEGORY_DETAIL,
                arguments = listOf(
                    navArgument("categoryId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                    navArgument("month") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) {
                CategoryDetailScreen(
                    onBack = { navController.popIfCurrent(Routes.CATEGORY_DETAIL) },
                    onOpenEntry = { navController.navigate(Routes.entry(it)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenNotificationLog = { navController.navigate(Routes.NOTIFICATION_LOG) },
                    onOpenCategories = { navController.navigate(Routes.CATEGORY_MANAGE) },
                    onOpenPaymentMethods = { navController.navigate(Routes.PAYMENT_MANAGE) },
                )
            }
            composable(
                Routes.ENTRY,
                arguments = optionalIdArgument + navArgument("emergency") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ) {
                EntryScreen(onDone = { navController.popIfCurrent(Routes.ENTRY) })
            }
            composable(Routes.EMERGENCY) {
                EmergencyScreen(
                    onBack = { navController.popIfCurrent(Routes.EMERGENCY) },
                    onUse = { navController.navigate(Routes.entry(emergency = true)) },
                    onRefill = { navController.navigate(Routes.EMERGENCY_REFILL) },
                    onCount = { navController.navigate(Routes.EMERGENCY_COUNT) },
                    onOpenTransaction = { navController.navigate(Routes.entry(it)) },
                )
            }
            composable(Routes.EMERGENCY_REFILL) {
                EmergencyRefillScreen(onDone = { navController.popIfCurrent(Routes.EMERGENCY_REFILL) })
            }
            composable(Routes.EMERGENCY_COUNT) {
                EmergencyCountScreen(onDone = { navController.popIfCurrent(Routes.EMERGENCY_COUNT) })
            }
            composable(Routes.RECURRING_EDIT, arguments = optionalIdArgument) {
                RecurringEditScreen(onDone = { navController.popIfCurrent(Routes.RECURRING_EDIT) })
            }
            composable(Routes.NOTIFICATION_LOG) {
                NotificationLogScreen(onBack = { navController.popIfCurrent(Routes.NOTIFICATION_LOG) })
            }
        }
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** 닫기를 빠르게 두 번 눌러도 홈 화면까지 빠지지 않도록 현재 화면일 때만 뒤로 간다. */
private fun NavHostController.popIfCurrent(route: String) {
    if (currentDestination?.route == route) popBackStack()
}
