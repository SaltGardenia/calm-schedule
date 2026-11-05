package com.saltgarden.kechengbiao.ui.schedule

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.saltgarden.kechengbiao.core.time.EventTime
import com.saltgarden.kechengbiao.core.time.WeekCalculator
import com.saltgarden.kechengbiao.core.time.nodeEndMinute
import com.saltgarden.kechengbiao.core.time.nodeStartMinute
import com.saltgarden.kechengbiao.data.CourseEntity
import com.saltgarden.kechengbiao.ui.theme.GlassDialog
import com.saltgarden.kechengbiao.ui.theme.GlassDialogButton

@Composable
fun CourseDetailDialog(
    course: CourseEntity,
    nodeTimes: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val weeks = WeekCalculator.parseWeeks(course.weeks)

    GlassDialog(onDismissRequest = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                course.name,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            // 右上角红色垃圾桶：删除入口，点击后二次确认
            IconButton(onClick = { confirmDelete = true }) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除课程",
                    tint = Color(0xFFE53935),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val startMin = nodeStartMinute(course.startNode, nodeTimes)
            val endMin = nodeEndMinute(course.endNode, nodeTimes)
            InfoRow(
                "时间",
                "周${course.day} 第${course.startNode}-${course.endNode}节" +
                    "（${EventTime.format(startMin)} - ${EventTime.format(endMin)}）"
            )
            InfoRow("周次", "${WeekCalculator.weeksToCsv(weeks)}周")
            if (course.teacher.isNotBlank()) InfoRow("教师", course.teacher)
            if (course.room.isNotBlank()) InfoRow("地点", course.room)
            if (course.remark.isNotBlank()) InfoRow("备注", course.remark)
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
            Text("删除课程", style = MaterialTheme.typography.titleLarge)
            Text(
                "确定删除「${course.name}」？删除后不可恢复。",
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
fun CourseEditDialog(
    initial: CourseEntity,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (CourseEntity) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var teacher by remember { mutableStateOf(initial.teacher) }
    var room by remember { mutableStateOf(initial.room) }
    var day by remember { mutableStateOf(initial.day) }
    // 文本与数值分离：直接绑定 Int 会导致删除回填、输入累加（111→30）无法编辑
    var startText by remember { mutableStateOf(initial.startNode.toString()) }
    var endText by remember { mutableStateOf(initial.endNode.toString()) }
    val startNode = startText.toIntOrNull()?.coerceIn(1, 30) ?: 1
    val endNode = endText.toIntOrNull()?.coerceIn(1, 30) ?: 1
    var weeksText by remember { mutableStateOf(initial.weeks) }
    var remark by remember { mutableStateOf(initial.remark) }

    GlassDialog(onDismissRequest = onDismiss) {
        Text(
            if (isNew) "添加课程" else "编辑课程",
            style = MaterialTheme.typography.titleLarge,
        )
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("课程名") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = teacher, onValueChange = { teacher = it },
                label = { Text("教师") }, singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = room, onValueChange = { room = it },
                label = { Text("教室") }, singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        Text("星期", style = MaterialTheme.typography.bodySmall)
        // 横向滚动，避免 7 个选项超出弹窗宽度被裁切
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { i, label ->
                FilterChip(
                    selected = day == i + 1,
                    onClick = { day = i + 1 },
                    label = { Text(label) }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = startText,
                onValueChange = { startText = it.filter { c -> c.isDigit() }.take(2) },
                label = { Text("起始节") }, singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = endText,
                onValueChange = { endText = it.filter { c -> c.isDigit() }.take(2) },
                label = { Text("结束节") }, singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            "节次范围 $startNode - $endNode",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = weeksText, onValueChange = { weeksText = it },
            label = { Text("周次（如 1-16 或 1,3,5-15）") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = remark, onValueChange = { remark = it },
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
                enabled = name.isNotBlank(),
                primary = true,
                onClick = {
                    val weeks = WeekCalculator.weeksToCsv(WeekCalculator.parseWeeks(weeksText))
                    onConfirm(
                        initial.copy(
                            name = name.trim(),
                            teacher = teacher.trim(),
                            room = room.trim(),
                            day = day,
                            startNode = minOf(startNode, endNode),
                            endNode = maxOf(startNode, endNode),
                            weeks = weeks.ifBlank { "1-16" },
                            remark = remark.trim(),
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row {
        Text(
            "$label：",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
