package com.happycola233.bilitools.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.happycola233.bilitools.R

/**
 * Google Sans Flex 可变字体，用于纯英文品牌字段与许可证排版。
 *
 * 变体轴（wght/ROND）统一烘焙在 res/font 的 XML font-family 资源里，
 * 因为 Compose 的 Font(resId, variationSettings) 对资源字体不生效。
 */
object BiliToolsFonts {
    /** 600 字重 + ROND=100 圆角变体：BiliTools 名称、版本号、许可证标题等英文标题字段。 */
    val googleSansFlexRond100 = FontFamily(
        Font(R.font.google_sans_flex_600_rond100, weight = FontWeight.Bold),
    )

    /** 完整家族（正文 400 + 加粗 600/ROND=100），用于许可证英文正文。 */
    val googleSansFlex = FontFamily(
        Font(R.font.google_sans_flex_400, weight = FontWeight.Normal),
        Font(R.font.google_sans_flex_600_rond100, weight = FontWeight.Bold),
    )
}
