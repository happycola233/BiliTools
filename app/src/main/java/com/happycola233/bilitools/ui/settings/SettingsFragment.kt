package com.happycola233.bilitools.ui.settings

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.appContainer
import com.happycola233.bilitools.data.AppThemeColor
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.databinding.FragmentSettingsBinding
import com.happycola233.bilitools.ui.AppViewModelFactory
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels {
        AppViewModelFactory(requireContext().appContainer)
    }

    private var suppressUi = false

    private val openFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@registerForActivityResult

        runCatching {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            requireContext().contentResolver.takePersistableUriPermission(uri, flags)
        }

        val updated = viewModel.setDownloadRootFromTreeUri(uri)
        val messageRes = if (updated) {
            R.string.settings_download_location_updated
        } else {
            R.string.settings_download_location_invalid
        }
        Toast.makeText(requireContext(), getString(messageRes), Toast.LENGTH_SHORT).show()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.root.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            v.isNestedScrollingEnabled = v.canScrollVertically(-1) || v.canScrollVertically(1)
        }

        binding.addMetadataSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!suppressUi) {
                viewModel.setAddMetadata(isChecked)
            }
        }
        binding.confirmCellularSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!suppressUi) {
                viewModel.setConfirmCellularDownload(isChecked)
            }
        }
        binding.parseQuickActionSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!suppressUi) {
                viewModel.setParseQuickActionEnabled(isChecked)
            }
        }
        binding.darkModePureBlackSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!suppressUi) {
                val current = viewModel.settings.value.darkModePureBlack
                if (current == isChecked) return@setOnCheckedChangeListener
                viewModel.setDarkModePureBlack(isChecked)
                requireActivity().recreate()
            }
        }

        binding.themeOptionContainer.setOnClickListener {
            val items = arrayOf(
                getString(R.string.settings_theme_system),
                getString(R.string.settings_theme_light),
                getString(R.string.settings_theme_dark)
            )
            val values = arrayOf(
                AppThemeMode.System,
                AppThemeMode.Light,
                AppThemeMode.Dark
            )
            val currentMode = viewModel.settings.value.themeMode
            val checkedItem = values.indexOf(currentMode)

            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_theme)
                .setSingleChoiceItems(items, checkedItem) { dialog, which ->
                    viewModel.setThemeMode(values[which])
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        binding.themeColorContainer.setOnClickListener {
            val items = arrayOf(
                getString(R.string.settings_theme_color_dynamic),
                getString(R.string.settings_theme_color_blue),
                getString(R.string.settings_theme_color_green),
                getString(R.string.settings_theme_color_orange),
                getString(R.string.settings_theme_color_pink),
            )
            val values = arrayOf(
                AppThemeColor.Dynamic,
                AppThemeColor.Blue,
                AppThemeColor.Green,
                AppThemeColor.Orange,
                AppThemeColor.Pink,
            )
            val currentColor = viewModel.settings.value.themeColor
            val checkedItem = values.indexOf(currentColor)

            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_theme_color)
                .setSingleChoiceItems(items, checkedItem) { dialog, which ->
                    val selected = values[which]
                    if (selected != currentColor) {
                        viewModel.setThemeColor(selected)
                        dialog.dismiss()
                        requireActivity().recreate()
                    } else {
                        dialog.dismiss()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        binding.downloadLocationContainer.setOnClickListener {
            val currentPath = viewModel.settings.value.downloadRootRelativePath
            val initialUri = buildInitialTreeUri(currentPath)
            openFolderLauncher.launch(initialUri)
        }

        val aboutIconSize = binding.aboutDescText.lineHeight
        val aboutIconTint = binding.aboutDescText.currentTextColor
        val aboutText = HtmlCompat.fromHtml(
            getString(R.string.settings_about_desc),
            HtmlCompat.FROM_HTML_MODE_COMPACT,
            Html.ImageGetter {
                ContextCompat.getDrawable(requireContext(), R.drawable.ic_github_invertocat_black)?.apply {
                    mutate().setTint(aboutIconTint)
                    val sourceWidth = intrinsicWidth.takeIf { it > 0 } ?: aboutIconSize
                    val sourceHeight = intrinsicHeight.takeIf { it > 0 } ?: aboutIconSize
                    val iconWidth = (aboutIconSize * sourceWidth / sourceHeight.toFloat())
                        .toInt()
                        .coerceAtLeast(1)
                    setBounds(0, 0, iconWidth, aboutIconSize)
                }
            },
            null
        )
        val aboutSpannable = SpannableStringBuilder(aboutText)
        aboutSpannable.getSpans(0, aboutSpannable.length, ImageSpan::class.java).forEach { imageSpan ->
            val start = aboutSpannable.getSpanStart(imageSpan)
            val end = aboutSpannable.getSpanEnd(imageSpan)
            val flags = aboutSpannable.getSpanFlags(imageSpan)
            aboutSpannable.removeSpan(imageSpan)
            aboutSpannable.setSpan(
                CenteredImageSpan(imageSpan.drawable),
                start,
                end,
                flags
            )
        }
        binding.aboutDescText.text = aboutSpannable
        binding.aboutDescText.movementMethod = android.text.method.LinkMovementMethod.getInstance()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settings.collect { settings ->
                    suppressUi = true
                    binding.addMetadataSwitch.isChecked = settings.addMetadata
                    binding.confirmCellularSwitch.isChecked = settings.confirmCellularDownload
                    binding.parseQuickActionSwitch.isChecked = settings.parseQuickActionEnabled
                    binding.downloadLocationValue.text = settings.downloadRootRelativePath
                    binding.themeValueText.text = when (settings.themeMode) {
                        AppThemeMode.Light -> getString(R.string.settings_theme_light)
                        AppThemeMode.Dark -> getString(R.string.settings_theme_dark)
                        AppThemeMode.System -> getString(R.string.settings_theme_system)
                    }
                    binding.themeColorValueText.text = when (settings.themeColor) {
                        AppThemeColor.Dynamic -> getString(R.string.settings_theme_color_dynamic)
                        AppThemeColor.Blue -> getString(R.string.settings_theme_color_blue)
                        AppThemeColor.Green -> getString(R.string.settings_theme_color_green)
                        AppThemeColor.Orange -> getString(R.string.settings_theme_color_orange)
                        AppThemeColor.Pink -> getString(R.string.settings_theme_color_pink)
                    }
                    binding.darkModePureBlackSwitch.isChecked = settings.darkModePureBlack
                    suppressUi = false
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun buildInitialTreeUri(relativePath: String): Uri? {
        val normalized = relativePath
            .replace('\\', '/')
            .trim()
            .trim('/')
            .takeIf { it.isNotBlank() }
            ?: return null
        val treeId = "primary:$normalized"
        return runCatching {
            DocumentsContract.buildTreeDocumentUri(
                EXTERNAL_STORAGE_PROVIDER,
                treeId,
            )
        }.getOrNull()
    }

    private class CenteredImageSpan(drawable: Drawable) : ImageSpan(drawable) {
        override fun draw(
            canvas: Canvas,
            text: CharSequence,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint,
        ) {
            val fontMetrics = paint.fontMetricsInt
            val transY = y + fontMetrics.descent - drawable.bounds.bottom -
                (fontMetrics.descent - fontMetrics.ascent - drawable.bounds.height()) / 2
            canvas.save()
            canvas.translate(x, transY.toFloat())
            drawable.draw(canvas)
            canvas.restore()
        }
    }

    companion object {
        private const val EXTERNAL_STORAGE_PROVIDER = "com.android.externalstorage.documents"
    }
}
