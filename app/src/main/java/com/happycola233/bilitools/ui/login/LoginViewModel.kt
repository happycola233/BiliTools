package com.happycola233.bilitools.ui.login

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.StringProvider
import com.happycola233.bilitools.data.AuthRepository
import com.happycola233.bilitools.data.CaptchaParams
import com.happycola233.bilitools.data.CaptchaResult
import com.happycola233.bilitools.data.CountryInfo
import com.happycola233.bilitools.data.model.QrLoginStatus
import com.happycola233.bilitools.data.model.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LoginTab {
    Qr,
    Password,
    Sms,
}

data class CountryOption(
    val id: Int,
    val label: String,
)

data class LoginUiState(
    val qrBitmap: Bitmap? = null,
    val qrStatusText: String = "",
    val isPolling: Boolean = false,
    val isLoggedIn: Boolean = false,
    val userInfo: UserInfo? = null,
    val errorText: String? = null,
    val activeTab: LoginTab = LoginTab.Qr,
    val countries: List<CountryOption> = emptyList(),
    val selectedCountryId: Int = 86,
    val smsCaptchaKey: String? = null,
    val isSendingSms: Boolean = false,
    val isLoggingIn: Boolean = false,
)

sealed class LoginEvent {
    data class ShowCaptcha(val params: CaptchaParams) : LoginEvent()
    data class Message(val text: String) : LoginEvent()
}

private enum class CaptchaPurpose {
    SmsSend,
    PasswordLogin,
}

private data class PendingCaptcha(
    val purpose: CaptchaPurpose,
    val token: String,
)

private data class PendingSms(
    val cid: Int,
    val tel: String,
)

private data class PendingPwd(
    val username: String,
    val password: String,
)

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val strings: StringProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(
        LoginUiState(
            qrStatusText = strings.get(R.string.login_status_idle),
            isLoggedIn = authRepository.isLoggedIn(),
        ),
    )
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<LoginEvent>()
    val events: SharedFlow<LoginEvent> = _events.asSharedFlow()

    private var pollJob: Job? = null
    private var pendingCaptcha: PendingCaptcha? = null
    private var pendingSms: PendingSms? = null
    private var pendingPwd: PendingPwd? = null

    init {
        if (authRepository.isLoggedIn()) {
            refreshUserInfo()
        }
    }

    fun prepareLogin() {
        if (!_state.value.isLoggedIn && _state.value.qrBitmap == null) {
            refreshQr()
        }
        if (_state.value.countries.isEmpty()) {
            loadCountries()
        }
    }

    fun ensureCountries() {
        if (_state.value.countries.isEmpty()) {
            loadCountries()
        }
    }

    fun setTab(tab: LoginTab) {
        _state.update { it.copy(activeTab = tab, errorText = null) }
    }

    fun setCountryId(id: Int) {
        _state.update { it.copy(selectedCountryId = id) }
    }

    fun refreshQr() {
        pollJob?.cancel()
        viewModelScope.launch {
            _state.update {
                it.copy(
                    qrStatusText = strings.get(R.string.login_status_generating),
                    isPolling = true,
                    errorText = null,
                )
            }
            runCatching {
                val qrInfo = authRepository.generateQr()
                val bitmap = withContext(Dispatchers.Default) {
                    QrCodeGenerator.generate(qrInfo.qrUrl, 520)
                }
                _state.update {
                    it.copy(
                        qrBitmap = bitmap,
                        qrStatusText = strings.get(R.string.login_status_scan),
                        isPolling = true,
                        errorText = null,
                    )
                }
                startPolling(qrInfo.qrKey)
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        qrStatusText = strings.get(R.string.login_status_qr_failed),
                        isPolling = false,
                        errorText = err.message,
                    )
                }
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        _state.update {
            it.copy(
                isPolling = false,
                qrStatusText = strings.get(R.string.login_status_polling_stopped),
            )
        }
    }

    fun logout() {
        authRepository.logout()
        _state.update {
            it.copy(
                isLoggedIn = false,
                userInfo = null,
                smsCaptchaKey = null,
                qrBitmap = null,
                qrStatusText = strings.get(R.string.login_status_signed_out),
            )
        }
        prepareLogin()
    }

    fun requestSmsCode(cid: Int, tel: String) {
        if (tel.isBlank()) {
            setError(strings.get(R.string.login_error_phone_empty))
            return
        }
        pendingSms = PendingSms(cid, tel)
        requestCaptcha(CaptchaPurpose.SmsSend)
    }

    fun loginWithSms(cid: Int, tel: String, code: String) {
        if (tel.isBlank()) {
            setError(strings.get(R.string.login_error_phone_empty))
            return
        }
        if (code.isBlank()) {
            setError(strings.get(R.string.login_error_sms_code_empty))
            return
        }
        val captchaKey = _state.value.smsCaptchaKey
        if (captchaKey.isNullOrBlank()) {
            setError(strings.get(R.string.login_error_sms_request_first))
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoggingIn = true, errorText = null) }
            runCatching {
                authRepository.smsLogin(cid, tel, code, captchaKey)
            }.onSuccess {
                handleLoginSuccess()
            }.onFailure { err ->
                setError(err.message ?: strings.get(R.string.login_error_failed))
            }
        }
    }

    fun requestPasswordLogin(username: String, password: String) {
        if (username.isBlank()) {
            setError(strings.get(R.string.login_error_account_empty))
            return
        }
        if (password.isBlank()) {
            setError(strings.get(R.string.login_error_password_empty))
            return
        }
        pendingPwd = PendingPwd(username, password)
        requestCaptcha(CaptchaPurpose.PasswordLogin)
    }

    fun submitCaptcha(result: CaptchaResult) {
        val pending = pendingCaptcha ?: return
        pendingCaptcha = null
        when (pending.purpose) {
            CaptchaPurpose.SmsSend -> sendSmsWithCaptcha(result, pending.token)
            CaptchaPurpose.PasswordLogin -> loginWithCaptcha(result, pending.token)
        }
    }

    fun cancelCaptcha() {
        pendingCaptcha = null
        _state.update { it.copy(isSendingSms = false, isLoggingIn = false) }
    }

    private fun requestCaptcha(purpose: CaptchaPurpose) {
        viewModelScope.launch {
            updateCaptchaLoading(purpose, true)
            runCatching { authRepository.getCaptchaParams() }
                .onSuccess { params ->
                    pendingCaptcha = PendingCaptcha(purpose, params.token)
                    _events.emit(LoginEvent.ShowCaptcha(params))
                }
                .onFailure { err ->
                    updateCaptchaLoading(purpose, false)
                    setError(err.message ?: strings.get(R.string.login_error_captcha_failed))
                }
        }
    }

    private fun sendSmsWithCaptcha(result: CaptchaResult, token: String) {
        val request = pendingSms
        if (request == null) {
            setError(strings.get(R.string.login_error_sms_request_first))
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSendingSms = true, errorText = null) }
            runCatching {
                authRepository.sendSmsCode(request.cid, request.tel, result, token)
            }.onSuccess { key ->
                _state.update {
                    it.copy(
                        smsCaptchaKey = key,
                        isSendingSms = false,
                        errorText = null,
                    )
                }
                _events.emit(LoginEvent.Message(strings.get(R.string.login_sms_sent)))
            }.onFailure { err ->
                setError(err.message ?: strings.get(R.string.login_error_failed))
                _state.update { it.copy(isSendingSms = false) }
            }
        }
    }

    private fun loginWithCaptcha(result: CaptchaResult, token: String) {
        val request = pendingPwd
        if (request == null) {
            setError(strings.get(R.string.login_error_account_empty))
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoggingIn = true, errorText = null) }
            runCatching {
                authRepository.pwdLogin(request.username, request.password, result, token)
            }.onSuccess {
                handleLoginSuccess()
            }.onFailure { err ->
                setError(err.message ?: strings.get(R.string.login_error_failed))
            }
        }
    }

    private fun handleLoginSuccess() {
        _state.update {
            it.copy(
                isLoggingIn = false,
                isSendingSms = false,
                smsCaptchaKey = null,
                qrStatusText = strings.get(R.string.login_status_signed_in),
                isLoggedIn = true,
                errorText = null,
            )
        }
        refreshUserInfo()
        stopPolling()
    }

    private fun updateCaptchaLoading(purpose: CaptchaPurpose, loading: Boolean) {
        when (purpose) {
            CaptchaPurpose.SmsSend ->
                _state.update { it.copy(isSendingSms = loading, errorText = null) }
            CaptchaPurpose.PasswordLogin ->
                _state.update { it.copy(isLoggingIn = loading, errorText = null) }
        }
    }

    private fun setError(message: String) {
        _state.update { it.copy(isLoggingIn = false, isSendingSms = false, errorText = message) }
    }

    private fun loadCountries() {
        viewModelScope.launch {
            val zone = runCatching { authRepository.getZoneCode() }.getOrDefault(86)
            val list = runCatching { authRepository.getCountryList() }.getOrDefault(emptyList())
            _state.update {
                it.copy(
                    countries = list.toOptions(),
                    selectedCountryId = zone,
                )
            }
        }
    }

    private fun startPolling(qrKey: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                val result = authRepository.pollQr(qrKey)
                val statusText = when (result.status) {
                    QrLoginStatus.Waiting -> strings.get(R.string.login_status_waiting)
                    QrLoginStatus.Scanned -> strings.get(R.string.login_status_scanned)
                    QrLoginStatus.Success -> strings.get(R.string.login_status_signed_in)
                    QrLoginStatus.Expired -> strings.get(R.string.login_status_expired)
                    QrLoginStatus.Error -> strings.get(R.string.login_status_failed_retry)
                }
                _state.update {
                    it.copy(
                        qrStatusText = statusText,
                        isLoggedIn = authRepository.isLoggedIn(),
                        errorText = null,
                    )
                }
                when (result.status) {
                    QrLoginStatus.Success -> {
                        _state.update {
                            it.copy(
                                isPolling = false,
                                qrStatusText = strings.get(R.string.login_status_signed_in),
                                isLoggedIn = true,
                            )
                        }
                        refreshUserInfo()
                        break
                    }
                    QrLoginStatus.Expired -> {
                        _state.update {
                            it.copy(
                                isPolling = false,
                                qrStatusText = strings.get(R.string.login_status_expired),
                            )
                        }
                        break
                    }
                    QrLoginStatus.Error -> {
                        _state.update {
                            it.copy(
                                isPolling = false,
                                qrStatusText = strings.get(R.string.login_status_failed_retry),
                            )
                        }
                        break
                    }
                    else -> {
                        // keep polling
                    }
                }
                delay(2000)
            }
        }
    }

    private fun refreshUserInfo() {
        viewModelScope.launch {
            val info = runCatching { authRepository.getUserInfo() }.getOrNull()
            _state.update { it.copy(userInfo = info) }
        }
    }

    private fun List<CountryInfo>.toOptions(): List<CountryOption> {
        return if (isEmpty()) {
            listOf(CountryOption(86, "+86"))
        } else {
            sortedBy { it.id }.map { entry ->
                CountryOption(entry.id, "+${entry.id}")
            }
        }
    }
}
