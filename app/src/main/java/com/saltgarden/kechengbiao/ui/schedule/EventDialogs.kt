package com.saltgarden.kechengbiao.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.saltgarden.kechengbiao.core.time.EventTime
import com.saltgarden.kechengbiao.data.EventEntity
import com.saltgarden.kechengbiao.ui.theme.GlassDialog
import com.saltgarden.kechengbiao.ui.theme.GlassDialogButton

/**
 * 一次性日程（不做周次重复）：按具体日期 + 真实起止时间存储。
 * 时间默认来自滑动选择（半小时粒度），可在弹窗中手动输入更精确的时间。
 */
@Composable
fun EventDetailDialog(
    event: EventEntity,
    dateLabel: String,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    GlassDialog(onDismissRequest = onDismiss) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                event.title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            // 右上角红色垃圾桶：删除入口，点击后二次确认（与课程详情弹窗保持一致）
            IconButton(onClick = { confirmDelete = true }) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除日程",
                    tint = Color(0xFFE53935),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EventInfoRow("日期", dateLabel)
            EventInfoRow(
                "时间",
                "${EventTime.format(event.startMinute)} - ${EventTime.format(event.endMinute)}"
            )
            if (event.location.isNotBlank()) EventInfoRow("地点", event.location)
            if (event.remark.isNotBlank()) EventInfoRow("备注", event.remark)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GlassDialogButton("取消", onClick = onDismiss)
            GlassDialogButton("编辑", onClick = onEdit, primary = true)
        }
    }

    if (confirmDelete) {
        GlassDialog(onDismissRequest = { confirmDelete = false }) {
            Text("删除日程", style = MaterialTheme.typography.titleLarge)
            Text(
                "确定删除「${event.title}」？删除后不可恢复。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassDialogButton("取消", onClick = { confirmDelete = false })
                GlassDialogButton(
                    "删除",
                    destructive = true,
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
fun EventEditDialog(
    dateLabel: String,
    initial: EventEntity,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (EventEntity) -> Unit,
) {
    var title by remember { mutableStateOf(initial.title) }
    var location by remember { mutableStateOf(initial.location) }
    var remark by remember { mutableStateOf(initial.remark) }
    var startText by remember { mutableStateOf(EventTime.format(initial.startMinute)) }
    var endText by remember { mutableStateOf(EventTime.format(initial.endMinute)) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    GlassDialog(onDismissRequest = onDismiss) {
        Text(
            if (isNew) "添加日程" else "编辑日程",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            dateLabel,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("事件内容（如 小组会议）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = startText,
                onValueChange = { startText = filterTimeInput(it) },
                label = { Text("开始") },
                placeholder = { Text("HH:mm") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { showStartPicker = true }) {
                        Icon(Icons.Filled.Schedule, contentDescription = "选择开始时间")
                    }
                },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = endText,
                onValueChange = { endText = filterTimeInput(it) },
                label = { Text("结束") },
                placeholder = { Text("HH:mm") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { showEndPicker = true }) {
                        Icon(Icons.Filled.Schedule, contentDescription = "选择结束时间")
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            "滑动选择为半小时粒度；可在此精确输入（24 小时制 HH:mm）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = location,
            onValueChange = { location = it },
            label = { Text("地点（可选）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = remark,
            onValueChange = { remark = it },
            label = { Text("备注（可选）") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GlassDialogButton("取消", onClick = onDismiss)
            GlassDialogButton(
                "保存",
                enabled = title.isNotBlank(),
                primary = true,
                onClick = {
                    val s = EventTime.parseMinutes(startText) ?: initial.startMinute
                    val parsedEnd = EventTime.parseMinutes(endText)
                    // 结束必须晚于开始：不合法时按一个课时（45 分钟）补齐
                    val e = if (parsedEnd != null && parsedEnd > s) parsedEnd else s + 45
                    onConfirm(
                        initial.copy(
                            title = title.trim(),
                            location = location.trim(),
                            remark = remark.trim(),
                            startMinute = s,
                            endMinute = e,
                        )
                    )
                }
            )
        }
    }

    if (showStartPicker) {
        GlassTimePickerDialog(
            title = "选择开始时间",
            initialMinutes = EventTime.parseMinutes(startText) ?: initial.startMinute,
            onDismiss = { showStartPicker = false },
            onConfirm = {
                startText = EventTime.format(it)
                showStartPicker = false
            },
        )
    }
    if (showEndPicker) {
        GlassTimePickerDialog(
            title = "选择结束时间",
            initialMinutes = EventTime.parseMinutes(endText) ?: initial.endMinute,
            onDismiss = { showEndPicker = false },
            onConfirm = { endText = EventTime.format(it); showEndPicker = false },
        )
    }
}

/** 只允许输入时间字符：数字与冒号，最多 5 位 */
private fun filterTimeInput(text: String): String =
    text.filter { it.isDigit() || it == ':' }.take(5)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlassTimePickerDialog(
    title: String,
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (minutes: Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = (initialMinutes / 60).coerceIn(0, 23),
        initialMinute = (initialMinutes % 60).coerceIn(0, 59),
        is24Hour = true,
    )
    GlassDialog(onDismissRequest = onDismiss, maxWidth = 340.dp) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        TimePicker(state = state)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GlassDialogButton("取消", onClick = onDismiss)
            GlassDialogButton(
                "确定",
                primary = true,
                onClick = { onConfirm(state.hour * 60 + state.minute) },
            )
        }
    }
}

@Composable
private fun EventInfoRow(label: String, value: String) {
    Row {
        Text(
            "$label：",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
