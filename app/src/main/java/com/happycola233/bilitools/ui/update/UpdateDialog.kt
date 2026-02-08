package com.happycola233.bilitools.ui.update

import android.content.Intent
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.net.Uri
import android.text.method.LinkMovementMethod
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.ReleaseInfo
import com.happycola233.bilitools.databinding.DialogUpdateReleaseBinding
import io.noties.markwon.Markwon
import kotlin.math.min

object UpdateDialog {
    fun show(
        activity: AppCompatActivity,
        release: ReleaseInfo,
        currentVersion: String,
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        val dialogBinding = DialogUpdateReleaseBinding.inflate(activity.layoutInflater)
        dialogBinding.updateVersionText.text = activity.getString(
            R.string.update_dialog_version_diff,
            currentVersion,
            release.tagName.removePrefix("v").removePrefix("V"),
        )
        applyVersionGradient(dialogBinding.updateVersionText)

        val markdown = release.bodyMarkdown.takeIf { it.isNotBlank() }
            ?: activity.getString(R.string.update_dialog_notes_empty)
        val markwon = Markwon.create(activity)
        markwon.setMarkdown(dialogBinding.updateNotesText, markdown)
        dialogBinding.updateNotesText.movementMethod = LinkMovementMethod.getInstance()

        val maxScrollHeight = (activity.resources.displayMetrics.heightPixels * 0.45f).toInt()

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(dialogBinding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.update_dialog_open_release) { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl))
                runCatching {
                    activity.startActivity(intent)
                }.onFailure {
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.update_dialog_open_release_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            .create()

        dialog.setOnShowListener {
            dialogBinding.updateNotesScroll.post {
                val measuredHeight = dialogBinding.updateNotesScroll.measuredHeight
                if (measuredHeight <= 0) return@post
                val targetHeight = min(measuredHeight, maxScrollHeight)
                dialogBinding.updateNotesScroll.updateLayoutParams<FrameLayout.LayoutParams> {
                    height = targetHeight
                }
            }
        }
        dialog.show()
    }

    private fun applyVersionGradient(textView: TextView) {
        val colors = intArrayOf(
            Color.parseColor("#0894FF"),
            Color.parseColor("#C75DF7"),
            Color.parseColor("#FD305A"),
            Color.parseColor("#FF7E13"),
        )
        textView.doOnLayout {
            val content = textView.text?.toString().orEmpty()
            if (content.isBlank()) return@doOnLayout
            val textWidth = textView.paint.measureText(content).coerceAtLeast(1f)
            val startX = ((textView.width - textWidth) / 2f).coerceAtLeast(0f)
            val endX = startX + textWidth
            textView.paint.shader = LinearGradient(
                startX,
                0f,
                endX,
                0f,
                colors,
                floatArrayOf(0f, 0.34f, 0.67f, 1f),
                Shader.TileMode.CLAMP,
            )
            textView.invalidate()
        }
    }
}
