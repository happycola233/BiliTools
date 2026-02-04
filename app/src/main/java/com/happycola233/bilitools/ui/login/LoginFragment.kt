package com.happycola233.bilitools.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.text.HtmlCompat
import coil.decode.SvgDecoder
import coil.load
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.appContainer
import com.happycola233.bilitools.databinding.FragmentLoginBinding
import com.happycola233.bilitools.ui.AppViewModelFactory
import com.happycola233.bilitools.ui.ExternalDownloadContract
import com.happycola233.bilitools.ui.MainActivity
import com.happycola233.bilitools.ui.history.HistoryActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.R as MaterialR
import kotlin.math.abs
import java.util.Locale
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LoginViewModel by viewModels {
        AppViewModelFactory(requireContext().appContainer)
    }

    private var countryAdapter: ArrayAdapter<String>? = null
    private var countryOptions: List<CountryOption> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.root.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            v.isNestedScrollingEnabled = v.canScrollVertically(-1) || v.canScrollVertically(1)
        }
        setupTabs()
        setupActions()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { collectState() }
                launch { collectEvents() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        view?.post {
            viewModel.prepareLogin()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        countryAdapter = null
    }

    private fun setupTabs() {
        binding.loginTabs.addTab(
            binding.loginTabs.newTab().setText(R.string.login_tab_qr),
        )
        binding.loginTabs.addTab(
            binding.loginTabs.newTab().setText(R.string.login_tab_password),
        )
        binding.loginTabs.addTab(
            binding.loginTabs.newTab().setText(R.string.login_tab_sms),
        )
        binding.loginTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val selected = when (tab.position) {
                    1 -> LoginTab.Password
                    2 -> LoginTab.Sms
                    else -> LoginTab.Qr
                }
                viewModel.setTab(selected)
                showPanel(selected)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        showPanel(LoginTab.Qr)
    }

    private fun setupActions() {
        binding.qrRefresh.setOnClickListener { viewModel.refreshQr() }
        binding.logout.setOnClickListener { showLogoutConfirmation() }
        binding.historyEntryContainer.setOnClickListener {
            if (!viewModel.state.value.isLoggedIn) return@setOnClickListener
            startActivity(Intent(requireContext(), HistoryActivity::class.java))
        }
        binding.favoriteEntryContainer.setOnClickListener {
            if (!viewModel.state.value.isLoggedIn) return@setOnClickListener
            val mid = viewModel.state.value.userInfo?.mid ?: return@setOnClickListener
            jumpToParse("https://space.bilibili.com/$mid/favlist")
        }
        binding.watchLaterEntryContainer.setOnClickListener {
            if (!viewModel.state.value.isLoggedIn) return@setOnClickListener
            jumpToParse("https://www.bilibili.com/watchlater")
        }

        binding.pwdLogin.setOnClickListener {
            viewModel.requestPasswordLogin(
                binding.pwdAccountInput.text?.toString().orEmpty(),
                binding.pwdPasswordInput.text?.toString().orEmpty(),
            )
        }

        binding.smsSend.setOnClickListener {
            val cid = viewModel.state.value.selectedCountryId
            viewModel.requestSmsCode(
                cid,
                binding.smsPhoneInput.text?.toString().orEmpty(),
            )
        }
        binding.smsLogin.setOnClickListener {
            val cid = viewModel.state.value.selectedCountryId
            viewModel.loginWithSms(
                cid,
                binding.smsPhoneInput.text?.toString().orEmpty(),
                binding.smsCodeInput.text?.toString().orEmpty(),
            )
        }

        binding.smsCountryDropdown.setOnItemClickListener { _, _, position, _ ->
            countryOptions.getOrNull(position)?.let { viewModel.setCountryId(it.id) }
        }
        setupDropdown(binding.smsCountryLayout, binding.smsCountryDropdown)
    }

    private suspend fun collectState() {
        viewModel.state.collect { state ->
            binding.profileCard.visibility = if (state.isLoggedIn) View.VISIBLE else View.GONE
            binding.historyEntryCard.visibility = if (state.isLoggedIn) View.VISIBLE else View.GONE
            binding.favoriteEntryCard.visibility = if (state.isLoggedIn) View.VISIBLE else View.GONE
            binding.watchLaterEntryCard.visibility = if (state.isLoggedIn) View.VISIBLE else View.GONE
            binding.loginCard.visibility = if (state.isLoggedIn) View.GONE else View.VISIBLE

            binding.qrStatus.text = state.qrStatusText
            binding.qrLoading.visibility =
                if (state.isPolling && state.qrBitmap == null) View.VISIBLE else View.GONE
            binding.qrImage.setImageBitmap(state.qrBitmap)

            binding.loginError.visibility =
                if (state.errorText.isNullOrBlank()) View.GONE else View.VISIBLE
            binding.loginError.text = state.errorText.orEmpty()

            binding.pwdLogin.isEnabled = !state.isLoggingIn
            binding.smsSend.isEnabled = !state.isSendingSms
            binding.smsLogin.isEnabled = !state.isLoggingIn

            countryOptions = state.countries
            val labels = countryOptions.map { it.label }
            if (countryAdapter == null) {
                countryAdapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_dropdown_item_1line,
                    labels,
                )
                binding.smsCountryDropdown.setAdapter(countryAdapter)
            } else {
                countryAdapter?.clear()
                countryAdapter?.addAll(labels)
                countryAdapter?.notifyDataSetChanged()
            }
            val selectedLabel = countryOptions.firstOrNull { it.id == state.selectedCountryId }?.label
            if (selectedLabel != null) {
                binding.smsCountryDropdown.setText(selectedLabel, false)
            }

            binding.logout.isEnabled = state.isLoggedIn

            val info = state.userInfo
            if (state.isLoggedIn && info != null) {
                val coverUrl = info.topPhotoUrl
                if (coverUrl.isNullOrBlank()) {
                    binding.accountCover.setImageDrawable(null)
                } else {
                binding.accountCover.load(coverUrl)
                }
                binding.accountAvatar.load(info.avatarUrl)
                binding.accountName.text = info.name
                binding.accountMid.text = getString(R.string.login_user_mid, info.mid)
                bindLevelBadge(info.level, info.isSeniorMember)
                val rawSign = info.sign?.trim().orEmpty()
                val signText = if (rawSign.isNotBlank()) {
                    HtmlCompat.fromHtml(rawSign, HtmlCompat.FROM_HTML_MODE_COMPACT)
                        .toString()
                        .trim()
                } else {
                    ""
                }
                if (signText.isNotBlank()) {
                    binding.accountSign.visibility = View.VISIBLE
                    binding.accountSign.text = signText
                } else {
                    binding.accountSign.visibility = View.GONE
                }
                val vipLabel = info.vipLabelImageUrl
                val vipText = info.vipLabel?.takeIf { it.isNotBlank() }
                val hasVip = !vipLabel.isNullOrBlank() || !vipText.isNullOrBlank()
                if (!vipLabel.isNullOrBlank()) {
                    binding.accountVipLabel.visibility = View.VISIBLE
                    binding.accountVipLabel.load(vipLabel)
                } else {
                    binding.accountVipLabel.visibility = View.GONE
                }
                if (hasVip) {
                    binding.accountVipBadge.visibility = View.VISIBLE
                    binding.accountVipBadge.load("file:///android_asset/big-vip.svg") {
                        decoderFactory(SvgDecoder.Factory())
                    }
                } else {
                    binding.accountVipBadge.visibility = View.GONE
                    binding.accountVipBadge.setImageDrawable(null)
                }
                binding.accountStatFollowing.text =
                    (info.following ?: 0).toString()
                binding.accountStatFollower.text =
                    (info.follower ?: 0).toString()
                binding.accountStatDynamic.text =
                    (info.dynamic ?: 0).toString()
                binding.accountStatCoins.text = formatCoins(info.coins)
            } else {
                binding.accountCover.setImageDrawable(null)
                binding.accountAvatar.setImageResource(R.drawable.default_avatar)
                binding.accountName.text = getString(R.string.login_user_guest)
                binding.accountMid.text = getString(R.string.login_user_mid_unknown)
                bindLevelBadge(null, false)
                binding.accountSign.visibility = View.GONE
                binding.accountVipLabel.visibility = View.GONE
                binding.accountVipBadge.visibility = View.GONE
                binding.accountVipBadge.setImageDrawable(null)
                binding.accountStatFollowing.text = "-"
                binding.accountStatFollower.text = "-"
                binding.accountStatDynamic.text = "-"
                binding.accountStatCoins.text = "-"
            }

            showPanel(state.activeTab)
        }
    }

    private suspend fun collectEvents() {
        viewModel.events.collect { event ->
            when (event) {
                is LoginEvent.ShowCaptcha -> showCaptchaDialog(event.params)
                is LoginEvent.Message -> Toast.makeText(
                    requireContext(),
                    event.text,
                    Toast.LENGTH_SHORT,
                ).show()
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
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCaptchaCancel() {
                viewModel.cancelCaptcha()
            }
        }
        dialog.show(parentFragmentManager, "geetest")
    }

    private fun showPanel(tab: LoginTab) {
        binding.panelQr.visibility = if (tab == LoginTab.Qr) View.VISIBLE else View.GONE
        binding.panelPassword.visibility =
            if (tab == LoginTab.Password) View.VISIBLE else View.GONE
        binding.panelSms.visibility = if (tab == LoginTab.Sms) View.VISIBLE else View.GONE
        if (tab == LoginTab.Sms) {
            viewModel.ensureCountries()
        }
    }

    private fun setupDropdown(
        layout: TextInputLayout,
        dropdown: MaterialAutoCompleteTextView,
    ) {
        dropdown.threshold = 0
        dropdown.showSoftInputOnFocus = false
        // UX: tap field or end icon to toggle menu; tap again closes; drag should not open menu.
        val touchSlop = ViewConfiguration.get(dropdown.context).scaledTouchSlop
        fun createToggleTouchListener(): View.OnTouchListener {
            var downX = 0f
            var downY = 0f
            var dragging = false
            var wasShowing = false
            return View.OnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.x
                        downY = event.y
                        dragging = false
                        wasShowing = dropdown.isPopupShowing
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!dragging) {
                            val dx = event.x - downX
                            val dy = event.y - downY
                            if (dx * dx + dy * dy > touchSlop * touchSlop) {
                                dragging = true
                                v.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                        }
                        !dragging
                    }
                    MotionEvent.ACTION_UP -> {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        if (!dragging) {
                            if (wasShowing) {
                                dropdown.dismissDropDown()
                            } else {
                                val adapter = dropdown.adapter
                                if (adapter != null && adapter.count > 0) {
                                    dropdown.requestFocus()
                                    dropdown.showDropDown()
                                }
                            }
                            true
                        } else {
                            false
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        false
                    }
                    else -> false
                }
            }
        }
        dropdown.setOnTouchListener(createToggleTouchListener())
        layout.setOnTouchListener(createToggleTouchListener())
        layout.findViewById<View>(MaterialR.id.text_input_end_icon)
            ?.setOnTouchListener(createToggleTouchListener())
        dropdown.setOnClickListener(null)
        layout.setOnClickListener(null)
        layout.setEndIconOnClickListener(null)
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

    private fun formatCoins(value: Double?): String {
        if (value == null) return "-"
        val rounded = value.toLong().toDouble()
        return if (abs(value - rounded) < 0.01) {
            rounded.toLong().toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }
    }

    private fun bindLevelBadge(level: Int?, isSeniorMember: Boolean) {
        if (level == null && !isSeniorMember) {
            binding.accountLevel.visibility = View.GONE
            binding.accountLevel.setImageDrawable(null)
            binding.accountLevel.contentDescription =
                getString(R.string.login_user_level_unknown)
            return
        }
        val badgeLevel = if (isSeniorMember) 6 else level?.coerceIn(0, 6) ?: 0
        val assetFile = if (isSeniorMember) "LV6_flash.svg" else "LV$badgeLevel.svg"
        val assetPath = "file:///android_asset/user_level_icon/$assetFile"
        binding.accountLevel.visibility = View.VISIBLE
        binding.accountLevel.contentDescription =
            getString(R.string.login_user_level, badgeLevel)
        binding.accountLevel.load(assetPath) {
            decoderFactory(SvgDecoder.Factory())
        }
    }
}
