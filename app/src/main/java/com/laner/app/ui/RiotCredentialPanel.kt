package com.laner.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun RiotCredentialPanel(
    configured: Boolean,
    runtimeOverrideActive: Boolean,
    onSave: (String) -> Unit,
    onClearRuntimeOverride: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (configured) "Riot 数据源 · 已配置" else "Riot 数据源 · 未配置",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = when {
                        runtimeOverrideActive -> "设备本地 Key 生效中"
                        configured -> "构建时 Key 生效中"
                        else -> "点此配置，Key 仅保存在 APP 私有目录",
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = { open = true }) {
                Text(if (configured) "更换" else "配置")
            }
        }
    }

    if (open) {
        var value by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("配置 Riot LoL Esports API Key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it.take(512) },
                        label = { Text("API Key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "不会写入日志、Git 或赛事 provenance。保存后当前页面的数据服务会立即重建。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = value.trim().isNotEmpty(),
                    onClick = {
                        onSave(value)
                        open = false
                    },
                ) { Text("保存并重连") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (runtimeOverrideActive) {
                        OutlinedButton(onClick = {
                            onClearRuntimeOverride()
                            open = false
                        }) { Text("清除本地 Key") }
                    }
                    OutlinedButton(onClick = { open = false }) { Text("取消") }
                }
            },
        )
    }
}
