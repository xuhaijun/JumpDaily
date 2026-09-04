package com.jumpdaily.jump.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import com.jumpdaily.jump.camera.PoseSkeleton
import com.jumpdaily.jump.ui.theme.Mint
import com.jumpdaily.jump.ui.theme.PinkSecondary
import com.jumpdaily.jump.ui.theme.PurplePrimary

/**
 * 人体骨架叠加层：把 [skeleton] 的归一化关键点映射到全屏（cover 变换 + 镜像），
 * 绘制连线与关节点，让孩子在相机画面上实时看到自己的跳绳姿态。
 *
 * 视觉升级：骨架不再是扁平单色线，而是一条「会发光的跳绳伙伴」——
 * 连线沿身体纵向做 紫→粉→薄荷 糖果渐变，并先垫一层柔光晕；关节点是
 * 外圈柔光 + 白色高亮内核，跳起来像一串小星星。整体更鲜活、更抓孩子的眼睛。
 *
 * 对齐说明（v1 近似）：PreviewView 默认 FILL_CENTER（cover 填充+裁切），
 * 这里用同样的 cover 变换把归一化坐标映射到屏幕中心矩形；[srcAspect] 取送入
 * 检测器（已旋转校正）的位图宽高比（竖屏 3:4 = 0.75）。前置预览已镜像，故默认
 * [mirror]=true 让骨架与镜像后的画面左右一致。
 *
 * @param skeleton 最新一帧关键点（归一化 x,y∈[0,1]）；空列表时不绘制。
 * @param mirror   是否水平镜像（前置摄像头预览已镜像，骨架需同步镜像以保持对齐）。
 * @param srcAspect 送入检测器的位图宽高比（宽/高），默认 3:4。
 * @param color    柔光晕的基础色（默认薄荷绿），仅用于发光垫层，连线/关节用渐变。
 */
@Composable
fun PoseOverlay(
    skeleton: MutableState<List<Pair<Float, Float>>>,
    modifier: Modifier = Modifier,
    mirror: Boolean = true,
    srcAspect: Float = 3f / 4f,
    color: Color = Color(0xFF7BE0C4)
) {
    val pts = skeleton.value
    if (pts.isEmpty()) return
    Canvas(modifier.fillMaxSize()) {
        val tgtAspect = size.width / size.height
        val drawW: Float
        val drawH: Float
        if (tgtAspect > srcAspect) {
            drawW = size.width
            drawH = size.width / srcAspect
        } else {
            drawH = size.height
            drawW = size.height * srcAspect
        }
        val offX = (size.width - drawW) / 2f
        val offY = (size.height - drawH) / 2f

        fun map(x: Float, y: Float): Offset {
            val mx = if (mirror) 1f - x else x
            return Offset(offX + mx * drawW, offY + y * drawH)
        }

        /** 按关节纵向位置取糖果渐变色（头紫→腰粉→脚薄荷），让影子伙伴更有活力。 */
        fun jointColor(t: Float): Color = when {
            t < 0.5f -> lerp(PurplePrimary, PinkSecondary, t / 0.5f)
            else -> lerp(PinkSecondary, Mint, (t - 0.5f) / 0.5f)
        }

        // 连线渐变（沿身体纵向：紫→粉→薄荷）
        val lineBrush = Brush.verticalGradient(
            colorStops = arrayOf(
                0f to PurplePrimary,
                0.5f to PinkSecondary,
                1f to Mint
            ),
            startY = offY,
            endY = offY + drawH
        )

        // 1) 先垫一层柔光晕（粗、半透明），制造发光质感
        for ((a, b) in PoseSkeleton.CONNECTIONS) {
            if (a < pts.size && b < pts.size) {
                drawLine(
                    color.copy(alpha = 0.30f),
                    map(pts[a].first, pts[a].second),
                    map(pts[b].first, pts[b].second),
                    strokeWidth = 22f,
                    cap = StrokeCap.Round
                )
            }
        }
        // 2) 再画清晰渐变连线（盖在柔光之上）
        for ((a, b) in PoseSkeleton.CONNECTIONS) {
            if (a < pts.size && b < pts.size) {
                drawLine(
                    brush = lineBrush,
                    map(pts[a].first, pts[a].second),
                    map(pts[b].first, pts[b].second),
                    strokeWidth = 9f,
                    cap = StrokeCap.Round,
                    alpha = 0.95f
                )
            }
        }
        // 3) 关节点：外圈柔光晕 + 白色高亮内核 + 渐变色小点，像一串会闪的小星星
        for (p in pts) {
            val c = map(p.first, p.second)
            val t = ((c.y - offY) / drawH).coerceIn(0f, 1f)
            drawCircle(jointColor(t).copy(alpha = 0.35f), radius = 18f, center = c)
            drawCircle(Color.White, radius = 7f, center = c)
            drawCircle(jointColor(t), radius = 4f, center = c)
        }
    }
}
