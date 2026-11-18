package com.avalon.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 手绘线性图标：统一 24dp 视口、2dp 圆头描边，跟随 Icon 的 tint 着色。
 * 全部在 Kotlin 内定义，不引入额外图标依赖。
 */
internal object AvalonIcons {

    val Add: ImageVector by lazy {
        lineIcon("Add") {
            moveTo(12f, 5f)
            lineTo(12f, 19f)
            moveTo(5f, 12f)
            lineTo(19f, 12f)
        }
    }

    val Minus: ImageVector by lazy {
        lineIcon("Minus") {
            moveTo(5f, 12f)
            lineTo(19f, 12f)
        }
    }

    /** 拖动排序把手：两道横杠握点。 */
    val DragHandle: ImageVector by lazy {
        lineIcon("DragHandle") {
            moveTo(4f, 9f)
            lineTo(20f, 9f)
            moveTo(4f, 15f)
            lineTo(20f, 15f)
        }
    }

    /** 延迟曲线，表示一次连通性测试。 */
    val Pulse: ImageVector by lazy {
        lineIcon("Pulse") {
            moveTo(3f, 12f)
            lineTo(7f, 12f)
            lineTo(9.8f, 5.5f)
            lineTo(14.2f, 18.5f)
            lineTo(17f, 12f)
            lineTo(21f, 12f)
        }
    }

    val Open: ImageVector by lazy {
        lineIcon("Open") {
            moveTo(19.5f, 13.5f)
            lineTo(19.5f, 18f)
            curveTo(19.5f, 18.8f, 18.8f, 19.5f, 18f, 19.5f)
            lineTo(6f, 19.5f)
            curveTo(5.2f, 19.5f, 4.5f, 18.8f, 4.5f, 18f)
            lineTo(4.5f, 6f)
            curveTo(4.5f, 5.2f, 5.2f, 4.5f, 6f, 4.5f)
            lineTo(10.5f, 4.5f)
            moveTo(14.5f, 4.5f)
            lineTo(19.5f, 4.5f)
            lineTo(19.5f, 9.5f)
            moveTo(19.2f, 4.8f)
            lineTo(11.5f, 12.5f)
        }
    }

    /** 多行清单：追番记录页标识。 */
    val Checklist: ImageVector by lazy {
        lineIcon("Checklist") {
            moveTo(9.5f, 6f)
            lineTo(20f, 6f)
            moveTo(9.5f, 12f)
            lineTo(20f, 12f)
            moveTo(9.5f, 18f)
            lineTo(20f, 18f)
            moveTo(3.6f, 5.4f)
            lineTo(5f, 6.8f)
            lineTo(7.4f, 4.2f)
            moveTo(3.6f, 11.4f)
            lineTo(5f, 12.8f)
            lineTo(7.4f, 10.2f)
            moveTo(3.6f, 17.4f)
            lineTo(5f, 18.8f)
            lineTo(7.4f, 16.2f)
        }
    }

    /** 摊开的书本：漫画记录页标识。 */
    val Book: ImageVector by lazy {
        lineIcon("Book") {
            // 左页：中缝向左展开的书页轮廓。
            moveTo(12f, 6.5f)
            curveTo(10.9f, 5.3f, 9.3f, 4.5f, 7f, 4.5f)
            lineTo(3f, 4.5f)
            lineTo(3f, 18.5f)
            lineTo(7f, 18.5f)
            curveTo(9.3f, 18.5f, 10.9f, 19.3f, 12f, 20.5f)
            // 右页：与左页镜像。
            moveTo(12f, 6.5f)
            curveTo(13.1f, 5.3f, 14.7f, 4.5f, 17f, 4.5f)
            lineTo(21f, 4.5f)
            lineTo(21f, 18.5f)
            lineTo(17f, 18.5f)
            curveTo(14.7f, 18.5f, 13.1f, 19.3f, 12f, 20.5f)
        }
    }

    /** 经纬地球：网站连接页标识。 */
    val Globe: ImageVector by lazy {
        lineIcon("Globe") {
            moveTo(12f, 3.5f)
            curveTo(16.7f, 3.5f, 20.5f, 7.3f, 20.5f, 12f)
            curveTo(20.5f, 16.7f, 16.7f, 20.5f, 12f, 20.5f)
            curveTo(7.3f, 20.5f, 3.5f, 16.7f, 3.5f, 12f)
            curveTo(3.5f, 7.3f, 7.3f, 3.5f, 12f, 3.5f)
            close()
            moveTo(3.5f, 12f)
            lineTo(20.5f, 12f)
            moveTo(12f, 3.5f)
            curveTo(14.4f, 6.1f, 15.7f, 9f, 15.7f, 12f)
            curveTo(15.7f, 15f, 14.4f, 17.9f, 12f, 20.5f)
            curveTo(9.6f, 17.9f, 8.3f, 15f, 8.3f, 12f)
            curveTo(8.3f, 9f, 9.6f, 6.1f, 12f, 3.5f)
            close()
        }
    }

    private fun lineIcon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = "Avalon.$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        ).build()
}
