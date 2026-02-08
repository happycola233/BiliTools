package com.happycola233.bilitools.data

import android.util.Base64
import com.happycola233.bilitools.core.BiliHttpClient
import com.happycola233.bilitools.core.BiliHttpException
import com.happycola233.bilitools.core.CookieStore
import com.happycola233.bilitools.core.WbiSigner
import com.happycola233.bilitools.data.model.QrLoginInfo
import com.happycola233.bilitools.data.model.QrLoginResult
import com.happycola233.bilitools.data.model.QrLoginStatus
import com.happycola233.bilitools.data.model.UserInfo
import com.squareup.moshi.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

class AuthRepository(
    private val httpClient: BiliHttpClient,
    private val cookieStore: CookieStore,
    private val wbiSigner: WbiSigner,
) {
    suspend fun generateQr(): QrLoginInfo {
        val url = "https://passport.bilibili.com/x/passport-login/web/qrcode/generate"
            .toHttpUrl()
        val body = httpClient.get(url)
        val adapter = httpClient.adapter(QrGenerateResponse::class.java)
        val resp = adapter.fromJson(body) ?: throw BiliHttpException("Empty QR response", -1)
        if (resp.code != 0 || resp.data == null) {
            throw BiliHttpException(resp.message, resp.code)
        }
        return QrLoginInfo(resp.data.url, resp.data.qrcodeKey)
    }

    suspend fun pollQr(qrKey: String): QrLoginResult {
        val url = "https://passport.bilibili.com/x/passport-login/web/qrcode/poll"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("qrcode_key", qrKey)
            .build()
        val body = httpClient.get(url)
        val adapter = httpClient.adapter(QrPollResponse::class.java)
        val resp = adapter.fromJson(body) ?: throw BiliHttpException("Empty poll response", -1)
        if (resp.code != 0 || resp.data == null) {
            return QrLoginResult(QrLoginStatus.Error, resp.message)
        }
        return when (resp.data.code) {
            86101 -> QrLoginResult(QrLoginStatus.Waiting, resp.data.message)
            86090 -> QrLoginResult(QrLoginStatus.Scanned, resp.data.message)
            0 -> QrLoginResult(QrLoginStatus.Success, resp.data.message)
            86038 -> QrLoginResult(QrLoginStatus.Expired, resp.data.message)
            else -> QrLoginResult(QrLoginStatus.Error, resp.data.message)
        }
    }

    suspend fun getCaptchaParams(): CaptchaParams {
        val url = "https://passport.bilibili.com/x/passport-login/captcha"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("source", "main-fe-header")
            .build()
        val body = httpClient.get(url)
        val adapter = httpClient.adapter(CaptchaResponse::class.java)
        val resp = adapter.fromJson(body) ?: throw BiliHttpException("Empty captcha response", -1)
        val data = resp.data ?: throw BiliHttpException(resp.message, resp.code)
        val gt = data.geetest?.gt.orEmpty()
        val challenge = data.geetest?.challenge.orEmpty()
        if (gt.isBlank() || challenge.isBlank() || data.token.isBlank()) {
            throw BiliHttpException(resp.message, resp.code)
        }
        return CaptchaParams(token = data.token, gt = gt, challenge = challenge)
    }

    suspend fun getZoneCode(): Int {
        val url = "https://api.bilibili.com/x/web-interface/zone".toHttpUrl()
        val body = httpClient.get(url)
        val adapter = httpClient.adapter(ZoneResponse::class.java)
        val resp = adapter.fromJson(body) ?: return DEFAULT_ZONE_CODE
        return resp.data?.countryCode ?: DEFAULT_ZONE_CODE
    }

    suspend fun getCountryList(): List<CountryInfo> {
        val url = "https://passport.bilibili.com/web/generic/country/list".toHttpUrl()
        val body = httpClient.get(url)
        val adapter = httpClient.adapter(CountryListResponse::class.java)
        val resp = adapter.fromJson(body) ?: return emptyList()
        val common = resp.data?.common.orEmpty()
        val others = resp.data?.others.orEmpty()
        return (common + others).mapNotNull { entry ->
            val id = entry.countryId?.toIntOrNull() ?: return@mapNotNull null
            val name = entry.countryName ?: "+$id"
            CountryInfo(id, name)
        }
    }

    suspend fun sendSmsCode(
        cid: Int,
        tel: String,
        captcha: CaptchaResult,
        token: String,
    ): String {
        val url = "https://passport.bilibili.com/x/passport-login/web/sms/send"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("cid", cid.toString())
            .addQueryParameter("tel", tel)
            .addQueryParameter("token", token)
            .addQueryParameter("source", "main-fe-header")
            .addQueryParameter("challenge", captcha.challenge)
            .addQueryParameter("validate", captcha.validate)
            .addQueryParameter("seccode", captcha.seccode)
            .build()
        val body = httpClient.postEmpty(url)
        val adapter = httpClient.adapter(SendSmsResponse::class.java)
        val resp = adapter.fromJson(body) ?: throw BiliHttpException("Empty SMS response", -1)
        val key = resp.data?.captchaKey
        if (resp.code != 0 || key.isNullOrBlank()) {
            throw BiliHttpException(resp.message, resp.code)
        }
        return key
    }

    suspend fun smsLogin(
        cid: Int,
        tel: String,
        code: String,
        captchaKey: String,
    ) {
        val url = "https://passport.bilibili.com/x/passport-login/web/login/sms"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("cid", cid.toString())
            .addQueryParameter("tel", tel)
            .addQueryParameter("code", code)
            .addQueryParameter("source", "main-fe-header")
            .addQueryParameter("captcha_key", captchaKey)
            .addQueryParameter("keep", "true")
            .build()
        val body = httpClient.postEmpty(url)
        val adapter = httpClient.adapter(SmsLoginResponse::class.java)
        val resp = adapter.fromJson(body) ?: throw BiliHttpException("Empty login response", -1)
        val data = resp.data ?: throw BiliHttpException("SMS login failed", resp.code)
        if (data.status != 0) {
            throw BiliHttpException(data.message, data.status)
        }
    }

    suspend fun pwdLogin(
        username: String,
        password: String,
        captcha: CaptchaResult,
        token: String,
    ) {
        val keyUrl = "https://passport.bilibili.com/x/passport-login/web/key".toHttpUrl()
        val keyBody = httpClient.get(keyUrl)
        val keyAdapter = httpClient.adapter(PwdKeyResponse::class.java)
        val keyResp = keyAdapter.fromJson(keyBody) ?: throw BiliHttpException("Empty key", -1)
        val keyData = keyResp.data ?: throw BiliHttpException(keyResp.message, keyResp.code)
        val encodedPwd = encryptPassword(keyData.hash, keyData.key, password)

        val url = "https://passport.bilibili.com/x/passport-login/web/login".toHttpUrl()
        val params = mapOf(
            "username" to username,
            "password" to encodedPwd,
            "token" to token,
            "challenge" to captcha.challenge,
            "validate" to captcha.validate,
            "seccode" to captcha.seccode,
            "go_url" to "https://www.bilibili.com/",
            "source" to "main-fe-header",
        )
        val body = httpClient.postForm(url, params)
        val adapter = httpClient.adapter(PwdLoginResponse::class.java)
        val resp = adapter.fromJson(body) ?: throw BiliHttpException("Empty login response", -1)
        val data = resp.data ?: throw BiliHttpException(resp.message, resp.code)
        if (data.status != 0) {
            throw BiliHttpException(data.message, data.status)
        }
    }

    fun isLoggedIn(): Boolean = cookieStore.isLoggedIn()

    suspend fun getUserInfo(): UserInfo? {
        val url = "https://api.bilibili.com/x/web-interface/nav".toHttpUrl()
        val body = httpClient.get(url)
        val adapter = httpClient.adapter(NavResponse::class.java)
        val resp = adapter.fromJson(body) ?: return null
        val data = resp.data ?: return null
        if (!data.isLogin) return null
        val mid = data.mid ?: return null
        val accountInfo = runCatching { fetchAccountInfo(mid) }.getOrNull()
        val stat = runCatching { fetchUserStat() }.getOrNull()
        return UserInfo(
            name = accountInfo?.name ?: data.uname.orEmpty(),
            mid = accountInfo?.mid ?: mid,
            avatarUrl = normalizeUrl(accountInfo?.face ?: data.face),
            level = accountInfo?.level ?: data.levelInfo?.currentLevel,
            isSeniorMember = (accountInfo?.isSeniorMember ?: data.isSeniorMember) == 1,
            sign = accountInfo?.sign ?: data.sign,
            vipLabel = data.vipLabel?.text ?: accountInfo?.vip?.label?.text,
            vipLabelImageUrl = normalizeImageUrl(accountInfo?.vip?.label?.imgLabelHansStatic),
            vipStatus = accountInfo?.vip?.status ?: data.vipStatus,
            vipType = accountInfo?.vip?.type ?: data.vipType,
            vipAvatarSubscript = accountInfo?.vip?.avatarSubscript ?: data.vipAvatarSubscript,
            topPhotoUrl = normalizeImageUrl(accountInfo?.topPhoto?.lImg),
            coins = accountInfo?.coins,
            following = stat?.following,
            follower = stat?.follower,
            dynamic = stat?.dynamicCount,
        )
    }

    fun logout() = cookieStore.clear()

    private fun normalizeUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("http://") -> "https://${url.removePrefix("http://")}"
            else -> url
        }
    }

    private fun normalizeImageUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return when {
            url.startsWith("http") -> url
            url.startsWith("//") -> "https:$url"
            else -> "https:$url"
        }
    }

    private suspend fun fetchAccountInfo(mid: Long): UserInfoData? {
        val url = wbiSigner.signedUrl(
            "https://api.bilibili.com/x/space/wbi/acc/info",
            mapOf("mid" to mid.toString()),
        )
        val body = httpClient.get(url)
        val adapter = httpClient.adapter(UserInfoResponse::class.java)
        val resp = adapter.fromJson(body) ?: return null
        if (resp.code != 0) return null
        return resp.data
    }

    private suspend fun fetchUserStat(): UserStatData? {
        val url = "https://api.bilibili.com/x/web-interface/nav/stat".toHttpUrl()
        val body = httpClient.get(url)
        val adapter = httpClient.adapter(UserStatResponse::class.java)
        val resp = adapter.fromJson(body) ?: return null
        if (resp.code != 0) return null
        return resp.data
    }

    private fun encryptPassword(hash: String, publicKey: String, password: String): String {
        val trimmed = publicKey
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\n", "")
            .trim()
        val decoded = Base64.decode(trimmed, Base64.DEFAULT)
        val keySpec = X509EncodedKeySpec(decoded)
        val keyFactory = KeyFactory.getInstance("RSA")
        val key = keyFactory.generatePublic(keySpec)
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal((hash + password).toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    companion object {
        private const val DEFAULT_ZONE_CODE = 86
    }
}

data class CaptchaParams(
    val token: String,
    val gt: String,
    val challenge: String,
)

data class CaptchaResult(
    val challenge: String,
    val validate: String,
    val seccode: String,
)

data class CountryInfo(
    val id: Int,
    val name: String,
)

private data class QrGenerateResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String,
    @Json(name = "data") val data: QrGenerateData?,
)

private data class QrGenerateData(
    @Json(name = "url") val url: String,
    @Json(name = "qrcode_key") val qrcodeKey: String,
)

private data class QrPollResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String,
    @Json(name = "data") val data: QrPollData?,
)

private data class QrPollData(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String,
    @Json(name = "url") val url: String?,
    @Json(name = "refresh_token") val refreshToken: String?,
)

private data class CaptchaResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String,
    @Json(name = "data") val data: CaptchaData?,
)

private data class CaptchaData(
    @Json(name = "token") val token: String,
    @Json(name = "geetest") val geetest: CaptchaGeetest?,
)

private data class CaptchaGeetest(
    @Json(name = "gt") val gt: String?,
    @Json(name = "challenge") val challenge: String?,
)

private data class SendSmsResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String,
    @Json(name = "data") val data: SendSmsData?,
)

private data class SendSmsData(
    @Json(name = "captcha_key") val captchaKey: String?,
)

private data class SmsLoginResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "data") val data: SmsLoginData?,
)

private data class SmsLoginData(
    @Json(name = "status") val status: Int,
    @Json(name = "message") val message: String,
    @Json(name = "refresh_token") val refreshToken: String?,
)

private data class PwdKeyResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String,
    @Json(name = "data") val data: PwdKeyData?,
)

private data class PwdKeyData(
    @Json(name = "hash") val hash: String,
    @Json(name = "key") val key: String,
)

private data class PwdLoginResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String,
    @Json(name = "data") val data: PwdLoginData?,
)

private data class PwdLoginData(
    @Json(name = "status") val status: Int,
    @Json(name = "message") val message: String,
    @Json(name = "refresh_token") val refreshToken: String?,
)

private data class CountryListResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String?,
    @Json(name = "data") val data: CountryListData?,
)

private data class CountryListData(
    @Json(name = "common") val common: List<CountryEntry>?,
    @Json(name = "others") val others: List<CountryEntry>?,
)

private data class CountryEntry(
    @Json(name = "country_id") val countryId: String?,
    @Json(name = "country_name") val countryName: String?,
)

private data class ZoneResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String?,
    @Json(name = "data") val data: ZoneData?,
)

private data class ZoneData(
    @Json(name = "country_code") val countryCode: Int?,
)

private data class NavResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String?,
    @Json(name = "data") val data: NavData?,
)

private data class NavData(
    @Json(name = "isLogin") val isLogin: Boolean,
    @Json(name = "uname") val uname: String?,
    @Json(name = "mid") val mid: Long?,
    @Json(name = "face") val face: String?,
    @Json(name = "sign") val sign: String?,
    @Json(name = "level_info") val levelInfo: NavLevelInfo?,
    @Json(name = "vip_label") val vipLabel: NavVipLabel?,
    @Json(name = "vipStatus") val vipStatus: Int?,
    @Json(name = "vipType") val vipType: Int?,
    @Json(name = "vip_avatar_subscript") val vipAvatarSubscript: Int?,
    @Json(name = "is_senior_member") val isSeniorMember: Int?,
)

private data class NavLevelInfo(
    @Json(name = "current_level") val currentLevel: Int?,
)

private data class NavVipLabel(
    @Json(name = "text") val text: String?,
)

private data class UserInfoResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String?,
    @Json(name = "data") val data: UserInfoData?,
)

private data class UserInfoData(
    @Json(name = "mid") val mid: Long?,
    @Json(name = "name") val name: String?,
    @Json(name = "sign") val sign: String?,
    @Json(name = "face") val face: String?,
    @Json(name = "level") val level: Int?,
    @Json(name = "is_senior_member") val isSeniorMember: Int?,
    @Json(name = "coins") val coins: Double?,
    @Json(name = "vip") val vip: UserVip?,
    @Json(name = "top_photo_v2") val topPhoto: UserTopPhoto?,
)

private data class UserVip(
    @Json(name = "status") val status: Int? = null,
    @Json(name = "type") val type: Int? = null,
    @Json(name = "avatar_subscript") val avatarSubscript: Int? = null,
    @Json(name = "label") val label: UserVipLabel?,
)

private data class UserVipLabel(
    @Json(name = "text") val text: String?,
    @Json(name = "img_label_uri_hans_static") val imgLabelHansStatic: String?,
    @Json(name = "img_label_uri_hant_static") val imgLabelHantStatic: String?,
)

private data class UserTopPhoto(
    @Json(name = "l_img") val lImg: String?,
    @Json(name = "l_200h_img") val l200Img: String?,
)

private data class UserStatResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String?,
    @Json(name = "data") val data: UserStatData?,
)

private data class UserStatData(
    @Json(name = "following") val following: Int?,
    @Json(name = "follower") val follower: Int?,
    @Json(name = "dynamic_count") val dynamicCount: Int?,
)
