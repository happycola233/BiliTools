package com.happycola233.bilitools.ui.login

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.appContainer
import com.happycola233.bilitools.ui.AppViewModelFactory
import com.happycola233.bilitools.ui.applySettingsThemeOverlays
import com.happycola233.bilitools.ui.me.BiliToolsLoginContent
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    private val viewModel: LoginViewModel by viewModels {
        AppViewModelFactory(applicationContext.appContainer)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        applySettingsThemeOverlays()
        super.onCreate(savedInstanceState)

        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
        setContentView(composeView)

        composeView.setContent {
            val settingsRepository = remember { applicationContext.appContainer.settingsRepository }
            val settings by settingsRepository.settings.collectAsState()
            val loginState by viewModel.state.collectAsState()

            LaunchedEffect(loginState.isLoggedIn) {
                if (loginState.isLoggedIn) {
                    finish()
                }
            }

            BiliToolsLoginContent(
                settings = settings,
                state = loginState,
                onExit = ::finish,
                onPrepareLogin = viewModel::prepareLogin,
                onRefreshQr = viewModel::refreshQr,
                onTabChange = viewModel::setTab,
                onRequestPasswordLogin = viewModel::requestPasswordLogin,
                onRequestSmsCode = viewModel::requestSmsCode,
                onLoginWithSms = viewModel::loginWithSms,
                onSetCountryId = viewModel::setCountryId,
            )
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                collectEvents()
            }
        }
    }

    private suspend fun collectEvents() {
        viewModel.events.collect { event ->
            when (event) {
                is LoginEvent.ShowCaptcha -> showCaptchaDialog(event.params)
                is LoginEvent.Message -> Toast.makeText(
                    this,
                    event.text,
                    Toast.LENGTH_SHORT,
                ).show()
                is LoginEvent.RiskVerificationRequired -> showRiskVerificationDialog(event.message)
            }
        }
    }

    private fun showCaptchaDialog(params: com.happycola233.bilitools.data.CaptchaParams) {
        val dialog = GeetestDialogFragment.newInstance(params.gt, params.challenge)
        dialog.listener = object : GeetestDialogFragment.Listener {
            override fun onCaptchaSuccess(result: com.happycola233.bilitools.data.CaptchaResult) {
                viewModel.submitCaptcha(result)
            }

            override fun onCaptchaError(message: String?) {
                viewModel.cancelCaptcha()
                if (!message.isNullOrBlank()) {
                    Toast.makeText(this@LoginActivity, message, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCaptchaCancel() {
                viewModel.cancelCaptcha()
            }
        }
        dialog.show(supportFragmentManager, "geetest")
    }

    private fun showRiskVerificationDialog(message: String?) {
        val content = message?.takeIf { it.isNotBlank() }
            ?: getString(R.string.login_error_failed)
        MaterialAlertDialogBuilder(this)
            .setMessage(content)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.login_tab_sms) { _, _ ->
                viewModel.openRiskSmsTab()
            }
            .show()
    }
}
