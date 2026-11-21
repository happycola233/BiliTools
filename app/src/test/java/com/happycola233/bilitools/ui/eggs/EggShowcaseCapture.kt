package com.happycola233.bilitools.ui.eggs

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import java.io.File

/** 彩蛋效果图统一输出到仓库的 .tmp/eggs 目录，只用于人工查看。 */
internal fun SemanticsNodeInteraction.saveEggShowcase(relativePath: String) {
    val output = File("../.tmp/eggs/$relativePath.png")
    output.parentFile!!.mkdirs()
    output.outputStream().use {
        captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
    }
}
