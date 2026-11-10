package com.saltgarden.kechengbiao.ui.import

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.saltgarden.kechengbiao.ui.theme.GlassDialog
import com.saltgarden.kechengbiao.ui.theme.GlassDialogButton

/**
 * 合肥工业大学原生登录（同 HFUT-Schedule 的登录方式）：
 * 学号 + 密码 + 验证码，登录成功后自动抓取并解析整学期课表（逐周教室）。
 */
@Composable
fun HfutLoginDialog(
    state: ImportUiState,
    onDismiss: () -> Unit,
    onSubmit: (user: String, password: String, captcha: String, remember: Boolean) -> Unit,
    onRefreshCaptcha: () -> Unit,
    onWebFallback: () -> Unit,
    savedCredentials: suspend () -> Pair<String, String> = { "" to "" },
) {
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var captcha by remember { mutableStateOf(state.hfutCaptchaText) }
    var remember by remember { mutableStateOf(true) }

    // 预填上次账号密码
    LaunchedEffect(Unit) {
        val (u, p) = savedCredentials()
        if (user.isBlank()) user = u
        if (password.isBlank()) password = p
    }
    // 验证码识别结果回填（用户仍可手工修正）
    LaunchedEffect(state.hfutCaptchaText) {
        if (state.hfutCaptchaText.isNotBlank()) captcha = state.hfutCaptchaText
    }

    GlassDialog(onDismissRequest = onDismiss, maxWidth = 420.dp) {
        Text("登录合肥工业大学", style = MaterialTheme.typography.titleLarge)
        Text(
            "统一身份认证登录后自动抓取并解析课表（含每周真实教室），无需网页操作。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = user,
            onValueChange = { user = it.filter { c -> c.isLetterOrDigit() }.take(20) },
            label = { Text("学号") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.hfutNeedCaptcha) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = captcha,
                    onValueChange = { captcha = it.filter { ch -> ch.isLetterOrDigit() }.take(6) },
                    label = { Text("验证码") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                val bmp = state.hfutCaptcha
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "点击刷新验证码",
                        modifier = Modifier
                            .size(width = 104.dp, height = 46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White)
                            .clickable(onClick = onRefreshCaptcha),
                    )
                } else {
                    Box(
                        Modifier
                            .width(104.dp)
                            .height(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(onClick = onRefreshCaptcha),
                        contentAlignment = Alignment.Center,
                    ) { Text("刷新", style = MaterialTheme.typography.labelSmall) }
                }
            }
            Text(
                "验证码已自动识别，识别不准时点图片刷新或手工修改",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // 记住账号密码（仅保存在本机应用私有目录）
        Row(
            Modifier.fillMaxWidth().clickable { remember = !remember },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = remember, onCheckedChange = { remember = it })
            Text("记住账号密码", style = MaterialTheme.typography.bodySmall)
        }
        state.hfutError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (state.hfutBusy) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                Text(
                    "  正在登录并抓取课表…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlassDialogButton("取消", onClick = onDismiss)
            GlassDialogButton(
                "登录并导入",
                enabled = !state.hfutBusy && user.isNotBlank() && password.isNotBlank() &&
                    (!state.hfutNeedCaptcha || captcha.isNotBlank()),
                primary = true,
                onClick = { onSubmit(user, password, captcha, remember) },
            )
        }
        Text(
            "登录不成功时可改用网页登录",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onWebFallback)
                .padding(top = 2.dp),
        )
    }
}
