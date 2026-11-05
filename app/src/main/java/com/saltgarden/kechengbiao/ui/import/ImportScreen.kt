package com.saltgarden.kechengbiao.ui.import

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saltgarden.kechengbiao.core.time.WeekCalculator
import com.saltgarden.kechengbiao.importer.schools.School
import com.saltgarden.kechengbiao.importer.schools.SchoolRegistry
import com.saltgarden.kechengbiao.importer.webview.WebViewGrabActivity
import com.saltgarden.kechengbiao.ui.theme.GlassDialog
import com.saltgarden.kechengbiao.ui.theme.GlassDialogButton
import com.saltgarden.kechengbiao.ui.theme.glassSurface
import com.saltgarden.kechengbiao.ui.theme.pressScale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(vm: ImportViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var selectedSchool by remember { mutableStateOf<School?>(null) }

    val grabLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // 大页面 Intent 承载不了，优先读进程内共享的抓取结果
        val html = result.data?.getStringExtra(WebViewGrabActivity.EXTRA_HTML)
            ?: WebViewGrabActivity.lastHtml
        if (!html.isNullOrBlank()) vm.importHtml(html)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("导入课表") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.step == ImportStep.Choose) onBack() else vm.backToChoose()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (state.step == ImportStep.Preview && state.parsed != null) {
                // 抓取/解析成功：展示识别结果预览（勾选后覆盖或追加导入）
                PreviewStep(vm, state)
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("选择学校", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "登录学校教务系统后自动抓取课表，无需手动输入。每所学校单独适配，持续增加中。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SchoolRegistry.schools.forEach { school ->
                        SchoolCard(school = school, onClick = { selectedSchool = school })
                    }
                    state.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
            if (state.busy) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    selectedSchool?.let { school ->
        SchoolStepsDialog(
            school = school,
            onDismiss = { selectedSchool = null },
            onStart = {
                selectedSchool = null
                val intent = Intent(
                    context, WebViewGrabActivity::class.java
                ).putExtra(WebViewGrabActivity.EXTRA_URL, school.url)
                grabLauncher.launch(intent)
            }
        )
    }
}

/** 学校卡片：名称 + 入口 + 操作路径摘要 */
@Composable
private fun SchoolCard(school: School, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxWidth()
            .pressScale(interaction, pressedScale = 0.97f)
            .glassSurface(shape = RoundedCornerShape(20.dp), elevation = 4.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column {
            Text(
                school.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                school.entrance,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                school.steps.joinToString(" → "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** 点选学校后展示操作步骤，确认后进入网页登录 */
@Composable
private fun SchoolStepsDialog(
    school: School,
    onDismiss: () -> Unit,
    onStart: () -> Unit,
) {
    GlassDialog(onDismissRequest = onDismiss) {
        Text("导入 ${school.name}", style = MaterialTheme.typography.titleLarge)
        Text(
            "请按顺序完成以下步骤：",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            school.steps.forEachIndexed { i, step ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${i + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(step, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GlassDialogButton("取消", onClick = onDismiss)
            GlassDialogButton("开始导入", onClick = onStart, primary = true)
        }
    }
}

@Composable
private fun PreviewStep(vm: ImportViewModel, state: ImportUiState) {
    val parsed = state.parsed ?: return
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("识别到 ${parsed.courses.size} 条课程安排", style = MaterialTheme.typography.titleMedium)
                Text("来源：${parsed.sourceName}", style = MaterialTheme.typography.bodySmall)
            }
            Checkbox(checked = state.selected.size == parsed.courses.size, onCheckedChange = vm::selectAll)
            Text("全选")
        }

        LazyColumn(Modifier.weight(1f).padding(vertical = 8.dp)) {
            itemsIndexed(
                parsed.courses,
                key = { i, _ -> i },
                contentType = { _, _ -> "course" },
            ) { i, c ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.toggle(i) }
                        .padding(vertical = 6.dp)
                ) {
                    Checkbox(checked = i in state.selected, onCheckedChange = { vm.toggle(i) })
                    Column {
                        Text(c.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "周${c.day} 第${c.startNode}-${c.endNode}节 · " +
                                "${WeekCalculator.weeksToCsv(c.weeks)}周" +
                                (if (c.teacher.isNotBlank()) " · ${c.teacher}" else "") +
                                (if (c.room.isNotBlank()) " · ${c.room}" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.confirmImport(false) }, modifier = Modifier.weight(1f)) {
                Text("追加导入")
            }
            Button(onClick = { vm.confirmImport(true) }, modifier = Modifier.weight(1f)) {
                Text("覆盖导入")
            }
        }
        Text(
            "覆盖导入将清空当前课表再写入；追加导入会跳过明显重复的课程。",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp)
        )
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
