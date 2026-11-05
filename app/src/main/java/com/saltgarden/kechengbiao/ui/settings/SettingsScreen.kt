package com.saltgarden.kechengbiao.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.saltgarden.kechengbiao.core.time.EventTime
import com.saltgarden.kechengbiao.core.time.WeekCalculator
import com.saltgarden.kechengbiao.data.TableEntity
import com.saltgarden.kechengbiao.ui.theme.GlassDialog
import com.saltgarden.kechengbiao.ui.theme.GlassDialogButton
import com.saltgarden.kechengbiao.ui.theme.GlassIconButton
import com.saltgarden.kechengbiao.ui.theme.glassSurface
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit, onGoImport: () -> Unit = {}) {
    val table by vm.table.collectAsState()
    val tables by vm.tables.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }
    var showNodeTimesEditor by remember { mutableStateOf(false) }
    var showTablePicker by remember { mutableStateOf(false) }
    var showRangeEditor by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<TableEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<TableEntity?>(null) }

    val timelineStart by vm.timelineStart.collectAsState()
    val timelineEnd by vm.timelineEnd.collectAsState()
    val showLunchBreak by vm.showLunchBreak.collectAsState()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 课表管理
            GlassSection {
                SectionTitle("课表")
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { showTablePicker = true }, modifier = Modifier.weight(1f)) {
                        Text("当前：${table?.name ?: "-"}")
                    }
                    Button(onClick = vm::addTable) { Text("新建") }
                }
                Spacer(Modifier.height(8.dp))
                // 导入入口（原顶栏按钮移入设置）
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onGoImport)
                        .padding(vertical = 8.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("导入课表", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "从教务系统一键抓取",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 学期与节奏
            GlassSection {
                SectionTitle("学期")
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("开学（第1周周一）：${termText(vm)}")
                }
                Spacer(Modifier.height(6.dp))
                StepperRow(
                    "每日节数", table?.nodeCount ?: 12,
                    onMinus = { vm.setNodeCount((table?.nodeCount ?: 12) - 1) },
                    onPlus = { vm.setNodeCount((table?.nodeCount ?: 12) + 1) }
                )
                StepperRow(
                    "总周数", table?.maxWeek ?: 25,
                    onMinus = { vm.setMaxWeek((table?.maxWeek ?: 25) - 1) },
                    onPlus = { vm.setMaxWeek((table?.maxWeek ?: 25) + 1) }
                )
            }

            // 显示
            GlassSection {
                SectionTitle("显示")
                SwitchRow("显示周六周日", table?.showWeekend ?: true) { vm.setShowWeekend(it) }
                SwitchRow("显示非本周课程", table?.showOtherWeek ?: false) { vm.setShowOtherWeek(it) }
                SwitchRow("显示午休时段（12点-14点）", showLunchBreak) { vm.setShowLunchBreak(it) }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { showRangeEditor = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("时间轴显示范围：${rangeLabel(timelineStart, timelineEnd)}")
                }
            }

            // 作息时间
            GlassSection {
                SectionTitle("作息时间")
                Text(
                    "共 ${table?.nodeTimes?.split(",")?.size ?: 0} 条：${table?.nodeTimes?.take(40)}…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { showNodeTimesEditor = true }) { Text("编辑作息") }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = vm.termStartDate()
                ?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        vm.setTermStart(
                            Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        )
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } }
        ) {
            DatePicker(state = state)
        }
    }

    if (showNodeTimesEditor) {
        NodeTimesEditorDialog(
            nodeCount = table?.nodeCount ?: 12,
            initialCsv = table?.nodeTimes ?: WeekCalculator.defaultNodeTimes(),
            onDismiss = { showNodeTimesEditor = false },
            onConfirm = { csv ->
                vm.setNodeTimes(csv)
                showNodeTimesEditor = false
            },
        )
    }

    if (showRangeEditor) {
        TimelineRangeEditorDialog(
            initialStart = timelineStart,
            initialEnd = timelineEnd,
            onDismiss = { showRangeEditor = false },
            onConfirm = { s, e ->
                vm.setTimelineRange(s, e)
                showRangeEditor = false
            },
        )
    }

    if (showTablePicker) {
        AlertDialog(
            onDismissRequest = { showTablePicker = false },
            title = { Text("选择课表") },
            text = {
                Column {
                    tables.forEach { t ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            // 点击课表名切换使用，切换后直接返回课表页
                            Text(
                                t.name + if (t.id == table?.id) "（使用中）" else "",
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (t.id != table?.id) vm.switchTable(t.id)
                                        showTablePicker = false
                                        onBack()
                                    }
                                    .padding(vertical = 4.dp)
                            )
                            TextButton(onClick = {
                                renameText = t.name
                                renameTarget = t
                            }) { Text("重命名") }
                            // 红色垃圾桶：删除入口，点击后二次确认
                            IconButton(onClick = { deleteTarget = t }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "删除课表",
                                    tint = Color(0xFFE53935),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTablePicker = false }) { Text("关闭") }
            }
        )
    }

    // 课表重命名
    renameTarget?.let { t ->
        GlassDialog(onDismissRequest = { renameTarget = null }) {
            Text("重命名课表", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = renameText,
                onValueChange = { renameText = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassDialogButton("取消", onClick = { renameTarget = null })
                GlassDialogButton(
                    "保存",
                    enabled = renameText.isNotBlank(),
                    primary = true,
                    onClick = {
                        vm.renameTable(t.id, renameText.trim())
                        renameTarget = null
                    }
                )
            }
        }
    }

    // 删除课表二次确认
    deleteTarget?.let { t ->
        GlassDialog(onDismissRequest = { deleteTarget = null }) {
            Text("删除课表", style = MaterialTheme.typography.titleLarge)
            Text(
                "确定删除「${t.name}」及其全部课程？删除后不可恢复。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassDialogButton("取消", onClick = { deleteTarget = null })
                GlassDialogButton(
                    "删除",
                    destructive = true,
                    onClick = {
                        deleteTarget = null
                        if (tables.size > 1) vm.deleteTable(t.id)
                    }
                )
            }
        }
    }
}

/** 玻璃分组卡片：设置页的分区容器 */
@Composable
private fun GlassSection(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(22.dp), elevation = 3.dp)
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun StepperRow(label: String, value: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        GlassIconButton(Icons.Filled.Remove, "减少", onClick = onMinus, size = 36.dp, iconSize = 18.dp)
        Text(
            "$value",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(44.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        GlassIconButton(Icons.Filled.Add, "增加", onClick = onPlus, size = 36.dp, iconSize = 18.dp)
    }
}

private fun termText(vm: SettingsViewModel): String {
    val d = vm.termStartDate() ?: return "未设置"
    return d.toString()
}

/** 时间轴范围按钮文案：自定义有效时显示范围，否则显示"自动" */
private fun rangeLabel(start: String, end: String): String {
    val s = EventTime.parseMinutes(start)
    val e = EventTime.parseMinutes(end)
    return if (s != null && e != null && e > s) "${start} - ${end}（自定义）" else "自动（按节数与作息）"
}

/** 时间轴显示范围自定义：留空 = 按节数与作息自动计算 */
@Composable
private fun TimelineRangeEditorDialog(
    initialStart: String,
    initialEnd: String,
    onDismiss: () -> Unit,
    onConfirm: (start: String, end: String) -> Unit,
) {
    var startText by remember { mutableStateOf(initialStart) }
    var endText by remember { mutableStateOf(initialEnd) }
    val s = EventTime.parseMinutes(startText)
    val e = EventTime.parseMinutes(endText)
    // 两端都留空 = 恢复自动；填写时必须成对有效且结束晚于开始
    val valid = (startText.isBlank() && endText.isBlank()) ||
        (s != null && e != null && e > s)

    GlassDialog(onDismissRequest = onDismiss) {
        Text("时间轴显示范围", style = MaterialTheme.typography.titleLarge)
        Text(
            "留空表示按每日节数与作息自动计算；自定义时请输入 24 小时制 HH:mm",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = startText,
                onValueChange = { startText = filterTimeText(it) },
                singleLine = true,
                placeholder = { Text("08:00") },
                modifier = Modifier.weight(1f)
            )
            Text("–", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = endText,
                onValueChange = { endText = filterTimeText(it) },
                singleLine = true,
                placeholder = { Text("21:00") },
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GlassDialogButton("取消", onClick = onDismiss)
            GlassDialogButton(
                "保存",
                enabled = valid,
                primary = true,
                onClick = { onConfirm(startText.trim(), endText.trim()) }
            )
        }
    }
}

/** 作息编辑：按已选节数逐节设置起止时间（不再粘贴 CSV） */
@Composable
private fun NodeTimesEditorDialog(
    nodeCount: Int,
    initialCsv: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val rows = remember(nodeCount, initialCsv) {
        WeekCalculator.ensureNodeTimes(initialCsv, nodeCount).toMutableStateList()
    }
    val allValid = rows.all {
        EventTime.parseMinutes(it.first) != null && EventTime.parseMinutes(it.second) != null
    }
    GlassDialog(onDismissRequest = onDismiss) {
        Text("作息时间", style = MaterialTheme.typography.titleLarge)
        Text(
            "按当前每日节数（$nodeCount 节）逐节设置起止时间",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        rows.forEachIndexed { i, row ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "第${i + 1}节",
                    Modifier.width(44.dp),
                    style = MaterialTheme.typography.labelMedium
                )
                OutlinedTextField(
                    value = row.first,
                    onValueChange = { rows[i] = filterTimeText(it) to row.second },
                    singleLine = true,
                    placeholder = { Text("08:00") },
                    modifier = Modifier.weight(1f)
                )
                Text("–", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = row.second,
                    onValueChange = { rows[i] = row.first to filterTimeText(it) },
                    singleLine = true,
                    placeholder = { Text("08:45") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GlassDialogButton("取消", onClick = onDismiss)
            GlassDialogButton(
                "保存",
                enabled = allValid,
                primary = true,
                onClick = {
                    val csv = rows.joinToString(",") { row ->
                        val s = EventTime.parseMinutes(row.first)!!
                        val e = EventTime.parseMinutes(row.second)!!
                        "${EventTime.format(s)}-${EventTime.format(e)}"
                    }
                    onConfirm(csv)
                }
            )
        }
    }
}

private fun filterTimeText(text: String): String =
    text.filter { it.isDigit() || it == ':' }.take(5)
