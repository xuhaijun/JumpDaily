package com.jumpdaily.jump.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import android.widget.Toast
import android.view.Gravity
import java.util.Calendar
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jumpdaily.jump.data.local.entities.Child
import com.jumpdaily.jump.di.AppContainer
import com.jumpdaily.jump.ui.components.ChildAvatar
import com.jumpdaily.jump.ui.components.CuteButton
import com.jumpdaily.jump.ui.components.safeColor
import com.jumpdaily.jump.ui.theme.InkSoft
import com.jumpdaily.jump.ui.viewmodel.ChildrenViewModel
import com.jumpdaily.jump.ui.viewmodel.SessionViewModel
import kotlinx.coroutines.delay

private val AVATARS = listOf("🐰", "🐼", "🐯", "🦁", "🐱", "🐶", "🐸", "🚀", "⭐", "🌟", "🦊", "🐥")
private val COLORS = listOf("#FF8FD3", "#6A3FE8", "#FFC861", "#7BE0C4", "#6FC8FF", "#FF5C7A")

/** 宝贝名字最大字数：名字过长会让首页头部与卡片排版破版，故在新增/编辑处统一限制。 */
private const val MAX_CHILD_NAME = 10

@Composable
fun ChildrenScreen(session: SessionViewModel, container: AppContainer) {
    val vm: ChildrenViewModel = viewModel(factory = container.childrenFactory)
    val children by vm.children.collectAsStateWithLifecycle()
    val current by session.currentChild.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    // 编辑中的宝贝（null=不编辑）；用 Child? 承载初始值，复用同一弹框
    var editing by remember { mutableStateOf<Child?>(null) }
    // 待删除确认的对象
    var deleting by remember { mutableStateOf<Child?>(null) }
    // 顶部成功提示（添加/编辑/删除后展示，自动消失）
    var feedback by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    // 操作成功反馈改为 Toast 提示，显示在顶部（约 2.2 秒后自动复位状态）
    LaunchedEffect(feedback) {
        if (feedback != null) {
            val toast = Toast.makeText(context, feedback, Toast.LENGTH_SHORT)
            toast.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 140)
            toast.show()
            delay(2200)
            feedback = null
        }
    }

    Box(Modifier.fillMaxSize().padding(20.dp)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("我的宝贝们", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)

            // 没有宝贝时显示空态引导，提示点右下角 + 添加；否则列表占满剩余空间并支持滚动
            if (children.isEmpty()) {
                EmptyChildrenState(Modifier.fillMaxWidth().weight(1f))
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(children, key = { it.id }) { child ->
                        ChildCard(
                            child = child,
                            selected = child.id == current?.id,
                            onSelect = { session.setCurrentChild(child.id) },
                            onEdit = { editing = child },
                            onDelete = { deleting = child }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Filled.Add, "添加孩子", tint = MaterialTheme.colorScheme.onPrimary)
        }
    }

    // 新增宝贝弹框
    if (showAdd) {
        ChildEditDialog(
            initial = null,
            onDismiss = { showAdd = false },
            onConfirm = { name, avatar, color, birthYear ->
                vm.add(name, avatar, color, birthYear)
                showAdd = false
                feedback = "添加成功 🎉"
            }
        )
    }

    // 编辑宝贝弹框（预填当前宝贝信息）
    editing?.let { child ->
        ChildEditDialog(
            initial = child,
            onDismiss = { editing = null },
            onConfirm = { name, avatar, color, birthYear ->
                vm.update(child.copy(name = name, avatar = avatar, themeColor = color, birthYear = birthYear))
                editing = null
                feedback = "已保存 ✏️"
            }
        )
    }

    // 删除确认弹框
    deleting?.let { child ->
        ConfirmDeleteDialog(
            child = child,
            onDismiss = { deleting = null },
            onConfirm = {
                // 若删除的是当前使用中的宝贝，自动切换到列表里剩下的第一个，避免数据悬空
                val remaining = children.filter { it.id != child.id }
                if (child.id == current?.id && remaining.isNotEmpty()) {
                    session.setCurrentChild(remaining.first().id)
                }
                vm.delete(child)
                deleting = null
                feedback = "已删除 🗑️"
            }
        )
    }
}

/** 空态引导：还没有宝贝时居中提示，引导点右下角 + 添加第一个宝贝。 */
@Composable
private fun EmptyChildrenState(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("🐰", fontSize = 64.sp)
            Text("还没有添加宝贝哦~", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("点右下角的 + 添加第一个宝贝吧", fontSize = 14.sp, color = InkSoft)
        }
    }
}

@Composable
private fun ChildCard(
    child: Child,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val color = safeColor(child.themeColor, MaterialTheme.colorScheme.primary)
    Card(
        Modifier.fillMaxWidth().clickable { onSelect() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) color.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface
        ),
        border = if (selected) BorderStroke(2.dp, color) else null
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ChildAvatar(child = child, size = 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(child.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (selected) "当前使用 ✓" else "点击切换", fontSize = 12.sp, color = InkSoft)
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, "编辑", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/**
 * 新增 / 编辑宝贝共用弹框。
 * - initial = null 时为新增，预填默认头像/颜色；否则预填该宝贝现有信息。
 * - 顶部含实时预览（头像 + 颜色 + 名字），选头像/颜色即时可见，解决「点了没反应」。
 * - 选中的头像加主色描边、选中的颜色加白色描边 + ✓，选中状态清晰。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChildEditDialog(
    initial: Child?,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Int?) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var avatar by remember { mutableStateOf(initial?.avatar ?: AVATARS[0]) }
    var color by remember { mutableStateOf(initial?.themeColor ?: COLORS[0]) }
    // 名字为空时点保存才提示，输入后自动消失
    var showNameHint by remember { mutableStateOf(false) }
    // 出生年份：选填，存为文本便于校验，确认时再转 Int?（空=未填）
    var birthYearText by remember { mutableStateOf(initial?.birthYear?.toString() ?: "") }
    var showYearHint by remember { mutableStateOf(false) }
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)

    val screenH = LocalConfiguration.current.screenHeightDp
    Dialog(onDismissRequest = onDismiss) {
        Card(
            Modifier.fillMaxWidth().padding(16.dp).heightIn(max = (screenH - 48).dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(if (initial == null) "添加宝贝" else "编辑宝贝", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

                // 实时预览：所选头像 + 颜色 + 名字即时反映，选头像/颜色「有反应」
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    ChildAvatar(child = Child(name = name.ifBlank { "预览" }, avatar = avatar, themeColor = color), size = 56.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(name.ifBlank { "预览" }, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("头像 + 颜色实时预览", fontSize = 12.sp, color = InkSoft)
                    }
                }

                // 关键：decorationBox 里必须始终调用 inner()，否则空值时可编辑组件不被组合，输入框无法聚焦/输入
                BasicTextField(
                    value = name,
                    onValueChange = { name = it.take(MAX_CHILD_NAME); showNameHint = false },
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(14.dp),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth()) {
                            if (name.isEmpty()) {
                                Text("输入名字", color = InkSoft, fontSize = 16.sp)
                            }
                            inner()
                        }
                    }
                )
                if (showNameHint) {
                    Text("请输入宝贝的名字呀～", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
                // 字数计数器：实时显示「已输入/上限」，超长会在输入时被自动截断（take(MAX)）
                Text(
                    "${name.length}/$MAX_CHILD_NAME",
                    fontSize = 12.sp,
                    color = if (name.length >= MAX_CHILD_NAME) MaterialTheme.colorScheme.error else InkSoft,
                    modifier = Modifier.align(Alignment.End)
                )
                Text("选个头像", fontSize = 14.sp, color = InkSoft)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AVATARS.forEach { a ->
                        val sel = a == avatar
                        Box(
                            Modifier.size(40.dp)
                                .then(if (sel) Modifier.border(BorderStroke(2.dp, Color.White), CircleShape) else Modifier)
                                .clip(CircleShape)
                                .background(if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { avatar = a },
                            contentAlignment = Alignment.Center
                        ) {
                            // 头像始终可见；选中时右下角加白色 ✓ 角标（紫底白勾，保证对比清晰）
                            Text(a, fontSize = 22.sp)
                            if (sel) {
                                Box(
                                    Modifier.align(Alignment.BottomEnd).offset(2.dp, 2.dp).size(16.dp).clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("✓", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }
                Text("选个颜色", fontSize = 14.sp, color = InkSoft)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        COLORS.forEach { c ->
                        val cc = safeColor(c, Color.Gray)
                        val sel = c == color
                        Box(
                            Modifier.size(36.dp)
                                .then(if (sel) Modifier.border(BorderStroke(3.dp, Color.White), CircleShape) else Modifier)
                                .clip(CircleShape).background(cc)
                                .clickable { color = c },
                            contentAlignment = Alignment.Center
                        ) {
                            if (sel) Text("✓", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
                Text("出生年份（选填）", fontSize = 14.sp, color = InkSoft)
                BasicTextField(
                    value = birthYearText,
                    onValueChange = {
                        // 仅保留数字、最多 4 位，避免非年份输入
                        birthYearText = it.filter { ch -> ch.isDigit() }.take(4)
                        showYearHint = false
                    },
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(14.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth()) {
                            if (birthYearText.isEmpty()) {
                                Text("例如 2019（选填）", color = InkSoft, fontSize = 16.sp)
                            }
                            inner()
                        }
                    }
                )
                if (showYearHint) {
                    Text("年份要在 ${currentYear} 年及以前哦～", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
                CuteButton(
                    if (initial == null) "保存" else "保存修改",
                    onClick = {
                        val birthYearVal = birthYearText.toIntOrNull()
                        val yearValid = birthYearVal == null || birthYearVal in 2000..currentYear
                        if (name.isNotBlank() && yearValid) {
                            onConfirm(name, avatar, color, birthYearVal)
                        } else {
                            if (name.isBlank()) showNameHint = true
                            if (!yearValid) showYearHint = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** 删除确认弹框：明确提示会连同跳绳记录一起删除且不可恢复。 */
@Composable
private fun ConfirmDeleteDialog(child: Child, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("删除宝贝？", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "确定要删除「${child.name}」吗？\nTA 的跳绳记录也会一起删除，且无法恢复哦。",
                    fontSize = 14.sp, color = InkSoft
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CuteButton("取消", onClick = onDismiss, modifier = Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    CuteButton("删除", onClick = onConfirm, modifier = Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.error, contentColor = Color.White)
                }
            }
        }
    }
}
