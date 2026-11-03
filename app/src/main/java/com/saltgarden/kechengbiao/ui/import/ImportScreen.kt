package com.saltgarden.kechengbiao.ui.import

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.saltgarden.kechengbiao.core.time.WeekCalculator
import com.saltgarden.kechengbiao.importer.webview.WebViewGrabActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(vm: ImportViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()

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
            when (state.step) {
                ImportStep.Preview -> PreviewStep(vm, state)
                else -> UrlStep(vm, state)
            }
            if (state.busy) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

/** 唯一的导入入口：输入教务系统网址 → 网页登录 → 打开课表页后抓取 */
@Composable
private fun UrlStep(vm: ImportViewModel, state: ImportUiState) {
    val context = LocalContext.current
    val grabLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // 大页面 Intent 承载不了，优先读进程内共享的抓取结果
        val html = result.data?.getStringExtra(WebViewGrabActivity.EXTRA_HTML)
            ?: WebViewGrabActivity.lastHtml
        if (!html.isNullOrBlank()) vm.importHtml(html)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("从教务系统导入", style = MaterialTheme.typography.titleMedium)
        Text(
            "在打开的网页中登录教务系统（支持统一认证 / WebVPN / CAS），" +
                "进入「我的课表」页面后点击底部「抓取本页课表」。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.host,
            onValueChange = vm::updateHost,
            label = { Text("教务系统网址") },
            placeholder = { Text("https://webvpn.hfut.edu.cn/login") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                val intent = Intent(context, WebViewGrabActivity::class.java)
                if (state.host.isNotBlank()) {
                    intent.putExtra(WebViewGrabActivity.EXTRA_URL, state.host)
                }
                grabLauncher.launch(intent)
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("打开网页登录并抓取") }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PreviewStep(vm: ImportViewModel, state: ImportUiState) {
    val parsed = state.parsed ?: return
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
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
