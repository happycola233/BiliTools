package com.happycola233.bilitools.data

import android.os.LocaleList
import com.happycola233.bilitools.core.AppLanguage
import org.json.JSONArray
import org.json.JSONObject

/**
 * 彩蛋文案所用的语言。B 站梗基本没法翻译，只有简中、繁中提供完整词库，英文提供精简的通用版；
 * 其他语言没有文案，文字类彩蛋不出现，只保留小电视的动画。
 *
 * 彩蛋文案放在 `assets/easter_eggs/` 而不是 strings.xml：后者要求 13 种语言键集合一致，
 * 而这里刻意只覆盖三种语言，条目数量也各不相同。
 */
enum class EggCopyLanguage(val assetName: String) {
    SimplifiedChinese("zh-Hans"),
    TraditionalChinese("zh-Hant"),
    English("en"),
    ;

    companion object {
        private val supportedTags = AppLanguage.entries
            .filter { it != AppLanguage.System }
            .map { it.languageTag }
            .toTypedArray()

        /**
         * 与系统挑选界面资源的方式一致：取语言列表里第一个应用支持的语言；一个都不支持时
         * 界面回退到 values/ 的简体中文，彩蛋文案也跟着用简体中文。
         */
        fun resolve(locales: LocaleList): EggCopyLanguage? {
            val matched = locales.getFirstMatch(supportedTags)
            return when (AppLanguage.fromLocale(matched)) {
                AppLanguage.SimplifiedChinese, AppLanguage.System -> SimplifiedChinese
                AppLanguage.TraditionalChinese -> TraditionalChinese
                AppLanguage.English -> English
                else -> null
            }
        }
    }
}

/** 一份语言的全部彩蛋文案，结构见 `assets/easter_eggs/zh-Hans.json`。 */
data class EasterEggCopy(
    val tips: List<String>,
    /** 点了会直接解析 [RICKROLL_TARGET] 的那条 */
    val rickrollTip: String,
    val lateNightTips: List<String>,
    val aprilFoolsTips: List<String>,
    /** 键为 MM-dd */
    val dateTips: Map<String, String>,
    /** BiliTools 生日当天的祝福，`%1$d` 是第几岁 */
    val appBirthday: String,
    val launchMilestones: Map<Int, String>,
    val downloadMilestones: Map<Int, String>,
    val downloadTerabyte: String,
    val cheers: String,
    val parseAir: String,
    val result: ResultEggCopy,
    val mascot: MascotEggCopy,
    val profile: ProfileEggCopy,
    val loginWait: String,
) {
    fun milestone(milestone: EggMilestone): String? = when (milestone) {
        is EggMilestone.Launch -> launchMilestones[milestone.count]
        is EggMilestone.Downloads -> downloadMilestones[milestone.count]
        EggMilestone.Terabyte -> downloadTerabyte
    }

    companion object {
        /** 索尼音乐中国在 B 站上传的《Never Gonna Give You Up》官方 MV，用来 Rickroll */
        const val RICKROLL_TARGET = "BV1GJ411x7h7"

        fun parse(json: String): EasterEggCopy {
            val root = JSONObject(json)
            val result = root.getJSONObject("result")
            val mascot = root.getJSONObject("mascot")
            val profile = root.getJSONObject("profile")
            return EasterEggCopy(
                tips = root.getJSONArray("tips").strings(),
                rickrollTip = root.getString("rickrollTip"),
                lateNightTips = root.getJSONArray("lateNightTips").strings(),
                aprilFoolsTips = root.getJSONArray("aprilFoolsTips").strings(),
                dateTips = root.getJSONObject("dateTips").stringMap { it },
                appBirthday = root.getString("appBirthday"),
                launchMilestones = root.getJSONObject("launchMilestones").stringMap(String::toInt),
                downloadMilestones = root.getJSONObject("downloadMilestones").stringMap(String::toInt),
                downloadTerabyte = root.getString("downloadTerabyte"),
                cheers = root.getString("cheers"),
                parseAir = root.getString("parseAir"),
                result = ResultEggCopy(
                    developer = result.getString("developer"),
                    founder = result.getString("founder"),
                    firstUpload = result.getString("firstUpload"),
                    treasure = result.getString("treasure"),
                    vintage = result.getString("vintage"),
                    fresh = result.getString("fresh"),
                ),
                mascot = MascotEggCopy(
                    pokes = mascot.getJSONObject("pokes").stringMap(String::toInt),
                    signal = mascot.getString("signal"),
                    drag = mascot.getString("drag"),
                ),
                profile = ProfileEggCopy(
                    levelMax = profile.getString("levelMax"),
                    levelSenior = profile.getString("levelSenior"),
                    levelZero = profile.getString("levelZero"),
                    levelOther = profile.getString("levelOther"),
                    coins = profile.getString("coins"),
                    followers = profile.getJSONObject("followers").stringMap(String::toLong),
                ),
                loginWait = root.getString("loginWait"),
            )
        }

        private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }

        private fun <K> JSONObject.stringMap(key: (String) -> K): Map<K, String> =
            keys().asSequence().associate { key(it) to getString(it) }
    }
}

/** 解析结果封面上的小贴纸。 */
data class ResultEggCopy(
    val developer: String,
    val founder: String,
    val firstUpload: String,
    val treasure: String,
    /** `%1$d` 是稿件的年龄 */
    val vintage: String,
    val fresh: String,
)

/** 关于页小电视的台词。 */
data class MascotEggCopy(
    /** 键为连续戳的次数 */
    val pokes: Map<Int, String>,
    val signal: String,
    val drag: String,
)

/** 「我」页资料卡上点等级与硬币的回应。 */
data class ProfileEggCopy(
    val levelMax: String,
    val levelSenior: String,
    val levelZero: String,
    /** `%1$d` 是距离六级还差几级；英文版不带数字 */
    val levelOther: String,
    val coins: String,
    /** 键为粉丝数门槛，取不超过实际粉丝数的最高一档 */
    val followers: Map<Long, String>,
) {
    fun followerLine(count: Long): String? =
        followers.filterKeys { it <= count }.maxByOrNull { it.key }?.value
}

/** 一次性的里程碑祝贺，在下次打开首页时显示。 */
sealed interface EggMilestone {
    data class Launch(val count: Int) : EggMilestone
    data class Downloads(val count: Int) : EggMilestone
    data object Terabyte : EggMilestone
}
