package com.jumpdaily.jump.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 统一的「权限说明」弹框：在真正向系统申请权限之前，先用大白话告诉孩子/家长为什么要这个权限，
 * 点「授予权限」才真正弹系统授权框。符合 Android 最佳实践，也避免一进页面就硬弹系统框的突兀感。
 */
@Composable
fun PermissionRationaleDialog(
    title: String,
    body: String,
    confirmText: String = "授予权限",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmText, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("暂不") }
        },
        title = { Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                body,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
        }
    )
}

/**
 * 「权限被永久拒绝」提示框：用户曾勾选「不再询问」后，系统授权框不会再出现，
 * 只能引导其到系统设置页手动开启。点「去设置」打开本应用详情页。
 */
@Composable
fun PermissionDeniedDialog(
    title: String,
    body: String,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onOpenSettings) { Text("去设置", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = { Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                body,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
        }
    )
}
