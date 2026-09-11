package com.happycola233.bilitools.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView
import com.kyant.backdrop.isRuntimeShaderSupported

/**
 * 液态玻璃的折射依赖 Android 13+ 的 RuntimeShader 和硬件加速。
 * 底栏、面板与设置页共用此判断；能力不足时只切换渲染方式，不改写用户偏好。
 */
@Composable
internal fun isLiquidGlassSupported(): Boolean =
    isRuntimeShaderSupported() && LocalView.current.isHardwareAccelerated
