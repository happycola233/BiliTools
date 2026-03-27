package com.happycola233.bilitools.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.happycola233.bilitools.R
import java.io.File

class UpdateInstallActivity : AppCompatActivity() {
    private var permissionSettingsOpened = false
    private var installLaunched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        continueInstallFlow()
    }

    private fun continueInstallFlow() {
        if (installLaunched) {
            finish()
            return
        }

        val apkPath = intent.getStringExtra(EXTRA_APK_PATH)
        val apkFile = apkPath?.let(::File)
        if (apkFile == null || !apkFile.exists()) {
            Toast.makeText(this, getString(R.string.update_install_file_missing), Toast.LENGTH_SHORT)
                .show()
            finish()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            if (!permissionSettingsOpened) {
                permissionSettingsOpened = true
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:$packageName"),
                    ),
                )
                return
            }
            finish()
            return
        }

        val apkUri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            apkFile,
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        installLaunched = true
        runCatching {
            startActivity(installIntent)
        }.onFailure {
            Toast.makeText(this, getString(R.string.update_install_launch_failed), Toast.LENGTH_SHORT)
                .show()
        }
        finish()
    }

    companion object {
        private const val EXTRA_APK_PATH = "extra_apk_path"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

        fun createIntent(
            context: Context,
            apkPath: String,
        ): Intent {
            return Intent(context, UpdateInstallActivity::class.java).apply {
                putExtra(EXTRA_APK_PATH, apkPath)
            }
        }

        fun launch(
            context: Context,
            apkPath: String,
        ): Boolean {
            val intent = createIntent(context, apkPath).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
        }
    }
}
