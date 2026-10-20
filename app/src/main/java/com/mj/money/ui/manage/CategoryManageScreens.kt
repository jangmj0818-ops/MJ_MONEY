package com.mj.money.ui.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mj.money.data.db.CategoryEntity
import com.mj.money.data.db.CategoryType
import com.mj.money.ui.AppViewModelProvider
import com.mj.money.ui.charts.ChartColors
import com.mj.money.ui.components.ColorSlotPicker
import com.mj.money.ui.components.FieldLabel

private val SUGGESTED_EMOJIS = listOf(
    "🍚", "☕", "🍺", "🚌", "🚗", "⛽", "🛍️", "💊", "🏥", "🎬", "🎨", "🎮", "▶️", "✨", "📚", "👶",
    "🐶", "💇", "🏠", "📱", "✈️", "🏋️", "🎁", "📦", "💰", "💵", "🪙", "📥",
)

@Composable
fun CategoryManageScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onAdd: (CategoryType) -> Unit,
    viewModel: CategoryManageViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        ManageTopBar("카테고리", onBack)
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                val options = listOf(CategoryType.EXPENSE to "지출", CategoryType.INCOME to "수입")
                SingleChoiceSegmentedButtonRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    options.forEachIndexed { index, (type, label) ->
                        SegmentedButton(
                            selected = state.type == type,
                            onClick = { viewModel.setType(type) },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                            icon = {},
                            label = { Text(label) },
                        )
                    }
                }
            }
            items(state.visible, key = { it.id }) { category ->
                val index = state.visible.indexOf(category)
                CategoryRow(
                    category = category,
                    onClick = { onEdit(category.id) },
                    canMoveUp = index > 0,
                    canMoveDown = index < state.visible.lastIndex,
                    onMoveUp = { viewModel.move(category, -1) },
                    onMoveDown = { viewModel.move(category, 1) },
                )
            }
            item {
                OutlinedButton(
                    onClick = { onAdd(state.type) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) { Text("+ 카테고리 추가") }
            }
            if (state.hidden.isNotEmpty()) {
                item { HiddenHeader("숨긴 카테고리", "입력 화면에는 안 보이고, 지난 내역과 통계에는 남아 있어요.") }
                items(state.hidden, key = { "hidden-${it.id}" }) { category ->
                    Row(
                        modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${category.emoji} ${category.name}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { viewModel.unhide(category) }) { Text("다시 보이기") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: CategoryEntity,
    onClick: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val swatch = if (category.colorSlot == 0) ChartColors.other() else ChartColors.category(category.colorSlot)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(swatch),
        )
        Text(category.emoji, fontSize = 20.sp, modifier = Modifier.padding(start = 12.dp))
        Text(
            category.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        )
        MoveButtons(canMoveUp, canMoveDown, onMoveUp, onMoveDown)
    }
}

@Composable
fun CategoryEditScreen(
    onDone: () -> Unit,
    viewModel: CategoryEditViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val form = state.form

    LaunchedEffect(state.finished) {
        if (state.finished) onDone()
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDone) { Icon(Icons.Filled.Close, contentDescription = "닫기") }
            Text(
                (if (state.isEdit) "카테고리 수정" else "카테고리 추가") + " · " + if (state.type == CategoryType.EXPENSE) "지출" else "수입",
                style = MaterialTheme.typography.titleLarge,
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            // 미리보기: 입력 화면에서 보이는 모양
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) { Text(form.emoji, fontSize = 26.sp) }
                Text(form.name.ifBlank { "이름" }, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
            }

            OutlinedTextField(
                value = form.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("이름") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            )

            FieldLabel("아이콘")
            OutlinedTextField(
                value = form.emoji,
                onValueChange = viewModel::onEmojiChange,
                placeholder = { Text("이모지를 입력하거나 아래에서 골라요") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            )
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SUGGESTED_EMOJIS.forEach { emoji ->
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable { viewModel.onEmojiChange(emoji) },
                        contentAlignment = Alignment.Center,
                    ) { Text(emoji, fontSize = 20.sp) }
                }
            }

            FieldLabel("차트 색")
            ColorSlotPicker(selected = form.slot, onSelect = viewModel::onSlotChange)
            Text(
                "통계 도넛과 목록에서 이 색으로 보여요. 회색은 '그 외'로 묶여요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )

            if (state.isEdit) {
                OutlinedButton(
                    onClick = viewModel::hide,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) { Text("숨기기") }
                Text(
                    "숨겨도 지난 내역과 통계에는 그대로 남아요. 목록 아래에서 다시 보이게 할 수 있어요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }

        SaveBar(error = state.error, label = if (state.isEdit) "수정 완료" else "추가", onSave = viewModel::save)
    }
}

// ---------- 관리 화면 공용 ----------

@Composable
internal fun ManageTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
internal fun MoveButtons(canMoveUp: Boolean, canMoveDown: Boolean, onMoveUp: () -> Unit, onMoveDown: () -> Unit) {
    IconButton(onClick = onMoveUp, modifier = Modifier.alpha(if (canMoveUp) 1f else 0.25f)) {
        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "위로")
    }
    IconButton(onClick = onMoveDown, modifier = Modifier.alpha(if (canMoveDown) 1f else 0.25f)) {
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "아래로")
    }
}

@Composable
internal fun HiddenHeader(title: String, description: String) {
    Column(Modifier.padding(top = 8.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp),
        )
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
        )
    }
}

@Composable
internal fun SaveBar(error: String?, label: String, onSave: () -> Unit) {
    error?.let {
        Text(
            it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Button(
        onClick = onSave,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(52.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}
