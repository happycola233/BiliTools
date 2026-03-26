package com.happycola233.bilitools.ui.me

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.appContainer
import com.happycola233.bilitools.databinding.FragmentMeBinding
import com.happycola233.bilitools.ui.AppViewModelFactory
import com.happycola233.bilitools.ui.ExternalDownloadContract
import com.happycola233.bilitools.ui.MainActivity
import com.happycola233.bilitools.ui.history.HistoryActivity
import com.happycola233.bilitools.ui.login.LoginActivity
import com.happycola233.bilitools.ui.login.LoginViewModel
import com.happycola233.bilitools.ui.settings.SettingsActivity

class MeFragment : Fragment(R.layout.fragment_me) {
    private var _binding: FragmentMeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LoginViewModel by viewModels {
        AppViewModelFactory(requireContext().appContainer)
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        _binding = FragmentMeBinding.bind(view)

        binding.meCompose.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
        )
        binding.meCompose.setContent {
            val settingsRepository = remember { requireContext().appContainer.settingsRepository }
            val settings by settingsRepository.settings.collectAsState()
            val loginState by viewModel.state.collectAsState()

            BiliToolsMeContent(
                settings = settings,
                loginState = loginState,
                onOpenLogin = {
                    startActivity(Intent(requireContext(), LoginActivity::class.java))
                },
                onOpenHistory = {
                    if (!viewModel.state.value.isLoggedIn) return@BiliToolsMeContent
                    startActivity(Intent(requireContext(), HistoryActivity::class.java))
                },
                onOpenFavorite = {
                    if (!viewModel.state.value.isLoggedIn) return@BiliToolsMeContent
                    val mid = viewModel.state.value.userInfo?.mid ?: return@BiliToolsMeContent
                    jumpToParse("https://space.bilibili.com/$mid/favlist")
                },
                onOpenWatchLater = {
                    if (!viewModel.state.value.isLoggedIn) return@BiliToolsMeContent
                    jumpToParse("https://www.bilibili.com/watchlater")
                },
                onOpenSettings = {
                    startActivity(Intent(requireContext(), SettingsActivity::class.java))
                },
                onConfirmLogout = ::showLogoutConfirmation,
            )
        }

        viewModel.refreshLoginState()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshLoginState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun showLogoutConfirmation() {
        if (!viewModel.state.value.isLoggedIn) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.login_logout_confirm_title)
            .setMessage(R.string.login_logout_confirm_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.login_logout) { _, _ ->
                viewModel.logout()
            }
            .show()
    }

    private fun jumpToParse(url: String) {
        val intent = Intent(requireContext(), MainActivity::class.java).apply {
            putExtra(ExternalDownloadContract.EXTRA_URL, url)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }
}
