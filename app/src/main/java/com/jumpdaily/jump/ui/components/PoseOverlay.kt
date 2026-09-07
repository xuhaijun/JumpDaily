package com.jumpdaily.jump.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.jumpdaily.jump.camera.PoseSkeleton
import com.jumpdaily.jump.ui.theme.Mint
import com.jumpdaily.jump.ui.theme.PinkSecondary
import com.jumpdaily.jump.ui.theme.PurplePrimary

/**
 * 人体骨架叠加层：把 [skeleton] 的归一化关键点映射到全屏（cover 变换 + 镜像），
 * 绘制连线与关节点，让孩子在相机画面上实时看到自己的跳绳姿态。
 *
 * 视觉：骨架是一条「会发光的跳绳伙伴」——连线沿身体纵向做 紫→粉→薄荷 糖果渐变，
 * 并先垫一层柔光晕；关节点是柔光 + 白核 + 彩色点，像一串会闪的小星星。
 *
 * ## 性能要点（2026-09-07 重构，解决摄像头页卡顿）
 * 1. **不在组合体里读 [skeleton]**：骨架每帧（约 15~30fps）更新，若在组合阶段读，
 *    会让读取它的整棵子树（甚至整页含相机 AndroidView）反复重组 —— 这是原来的卡顿根因。
 *    现在把读取下沉到 Canvas 的**绘制阶段**，state 变化只会 invalidate 重绘，
 *    不触发组合/布局，开销从「重组整页」降到「一次 draw」。
 * 2. **连线合并成一条 Path**：原来每条连线 2 次 drawLine（32 条 × 2 = 64 次调用），
 *    现在 1 次构建 + 2 次 drawPath，绘制调用从 64 降到 2。
 * 3. **关节点从 3 层降到 2 层**：33 × 3 = 99 次 drawCircle → 66 次。
 *
 * @param skeleton  最新一帧关键点（归一化 x,y∈[0,1]）；空列表时不绘制。
 * @param mirror    是否水平镜像（前置预览已镜像，骨架需同步镜像以保持对齐）。
 * @param srcAspect 送入检测器的位图宽高比（宽/高），默认 3:4。
 * @param color     柔光晕基础色，仅用于发光垫层。
 * @param pulse     计数瞬间的发光脉冲（0~1，可空）：每跳一下影子伙伴「亮」一下，正反馈更带感。
 */
@Composable
fun PoseOverlay(
    skeleton: State<List<Pair<Float, Float>>>,
    modifier: Modifier = Modifier,
    mirror: Boolean = true,
    srcAspect: Float = 3f / 4f,
    color: Color = Color(0xFF7BE0C4),
    pulse: State<Float>? = null
) {
    Canvas(modifier.fillMaxSize()) {
        // ⚠️ 必须在这里（绘制阶段）读 state，切勿提到上面的组合体里
        val pts = skeleton.value
        if (pts.isEmpty()) return@Canvas
        val boost = (pulse?.value ?: 0f).coerceIn(0f, 1f)

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

        /** 按关节纵向位置取糖果渐变色（头紫→腰粉→脚薄荷）。 */
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

        // 1) 一次构建整副骨架的连线 Path（后续两个描边共用，避免 64 次 drawLine）
        val path = Path()
        for ((a, b) in PoseSkeleton.CONNECTIONS) {
            if (a < pts.size && b < pts.size) {
                val pa = map(pts[a].first, pts[a].second)
                val pb = map(pts[b].first, pts[b].second)
                path.moveTo(pa.x, pa.y)
                path.lineTo(pb.x, pb.y)
            }
        }
        // 2) 柔光垫层：计数瞬间随 pulse 加粗、提亮，像影子伙伴被点亮
        drawPath(
            path,
            color = color.copy(alpha = 0.30f + 0.28f * boost),
            style = Stroke(width = 22f + 10f * boost, cap = StrokeCap.Round)
        )
        // 3) 清晰渐变连线（盖在柔光之上）
        drawPath(
            path,
            brush = lineBrush,
            style = Stroke(width = 9f, cap = StrokeCap.Round),
            alpha = 0.95f
        )
        // 4) 关节点：柔光圈 + 白核 + 彩点（两层，比原来的三层少 1/3 绘制量）
        for (p in pts) {
            val c = map(p.first, p.second)
            val t = ((c.y - offY) / drawH).coerceIn(0f, 1f)
            drawCircle(jointColor(t).copy(alpha = 0.35f + 0.35f * boost), radius = 15f + 5f * boost, center = c)
            drawCircle(Color.White, radius = 6f, center = c)
            drawCircle(jointColor(t), radius = 3.5f, center = c)
        }
    }
}
