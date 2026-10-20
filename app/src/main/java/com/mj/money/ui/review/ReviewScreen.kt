package com.mj.money.ui.review

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mj.money.AppContainer
import com.mj.money.data.db.TransactionRow
import com.mj.money.ui.AppViewModelProvider
import com.mj.money.ui.components.EmptyMessage
import com.mj.money.ui.components.TransactionItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ReviewViewModel(container: AppContainer) : ViewModel() {
    val items: StateFlow<List<TransactionRow>?> = container.database.transactionDao().observeNeedsReview()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** 알림에서 자동 기록했지만 카테고리를 몰라서 확인이 필요한 내역 */
@Composable
fun ReviewScreen(
    onBack: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    viewModel: ReviewViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
            Text("확인 필요", style = MaterialTheme.typography.titleLarge)
        }
        val list = items ?: return@Column
        if (list.isEmpty()) {
            EmptyMessage("확인할 내역이 없어요", "알림에서 자동으로 기록한 내역은 모두 확인했어요.")
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Text(
                    "알림에서 자동으로 기록했는데 카테고리를 몰라서 확인이 필요해요. 눌러서 카테고리를 고르면 확정되고, 같은 가게는 다음부터 자동으로 분류해요. 내 계좌로 옮긴 돈이면 '이체'로 바꿔주세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            items(list, key = { it.id }) { row ->
                TransactionItem(row, onClick = { onOpenEntry(row.id) }, showDate = true)
            }
        }
    }
}
