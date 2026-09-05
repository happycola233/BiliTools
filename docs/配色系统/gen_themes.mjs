// 配色生成器：产出 values/themes.xml、values-night/themes.xml 与 values/colors.xml。
//
// 骨架是官方 Material Color Utilities 的 Material 3 Expressive（2025 spec）TonalSpot 方案，
// 与 Android 16 系统动态取色同一套规则；应用只保留几个设计旋钮：
//   1. 主填充面（primary fixed 色组）按最大 sRGB 通道对齐，十三个色相同一种淡；
//   2. 浅色中性色板彩度按「页面底 T94 的通道差 = TINT_LIGHT」反解；
//   3. 深色中性色板彩度按「surfaceContainerHigh（T12）的通道差 = TINT_DARK」反解；
//   4. 浅色正文与次要文字明度压深；
//   5. 错误色固定为 M3 基线档位，不用 2025 spec 的饱和红容器。
// 设计理念、参数来历与硬约束见同目录 README.md。改完跑 verify.py 回归校验，再跑 preview.py 目视确认。
//
// 用法：node docs/配色系统/gen_themes.mjs   （首次先在本目录 npm ci）
import { register } from 'node:module';
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

// @material/material-color-utilities 的 ESM 内部相对导入不带 .js 后缀，Node 原生解析不到，
// 这里注册一个解析钩子补上后缀；钩子只对随后的动态 import 生效，所以库必须在下面动态导入。
register('data:text/javascript,' + encodeURIComponent(`
  export async function resolve(specifier, context, nextResolve) {
    try { return await nextResolve(specifier, context); }
    catch (error) {
      if (error.code === 'ERR_MODULE_NOT_FOUND' && /^\\.{1,2}\\//.test(specifier) && !/\\.[cm]?js$/.test(specifier)) {
        return nextResolve(specifier + '.js', context);
      }
      throw error;
    }
  }`), import.meta.url);
const { Hct, TonalPalette, DynamicScheme, SchemeTonalSpot, MaterialDynamicColors, Variant } =
  await import('@material/material-color-utilities');

const HERE = dirname(fileURLToPath(import.meta.url));
const RES = join(HERE, '..', '..', 'app', 'src', 'main', 'res');

// ---------------------------------------------------------------------------------------------
// 设计旋钮

// 浅色表面着色量，单位是 sRGB 通道差（最大通道 − 最小通道），在页面底 T94 处对齐。
// 8：大面积页面底长时间看不累，色调只在与卡片并排时才读得出来。13 色调明确，18 偏鲜。
const TINT_LIGHT = 8;
// 深色表面着色量，在 surfaceContainerHigh（T12，页面 T9 与卡片 T18 之间）处对齐。深色底的通道值本来就小，同样的通道差观感上要重两三倍；
// 官方 Monet 深色约 Δ8（相对着色 25%），暖色相会读作酱色。3 保留一点与浅色呼应的色相，不再发脏。
const TINT_DARK = 3;
// 官方 TonalSpot 的 neutralVariant 彩度是 neutral 的 8.5 / 5 倍，沿用这个比例
const NEUTRAL_VARIANT_RATIO = 8.5 / 5;
// 浅色正文与次要文字的明度。2025 spec 按对比度曲线只取「刚好够」的档位（正文约 T21、次要文字约 T40），
// 大面积阅读偏淡发灰，这里压到 M3 2021 的经典档位 T10 / T30；深色不动。
const LIGHT_ON_SURFACE_TONE = 10;
const LIGHT_ON_SURFACE_VARIANT_TONE = 30;
// 浅色 outlineVariant（输入框描边、分隔线等细线）的明度。2025 spec 给到约 T72，细线在浅色卡片上偏重，
// 回到 M3 2021 的 T80；深色两版都是 T30，不动。
const LIGHT_OUTLINE_VARIANT_TONE = 80;
// 主填充面：彩度上限、明度上限，以及最大通道的对齐目标
const FILL_CHROMA_CAP = 30;
const FILL_TONE_CAP = 87;
const FILL_TARGET_CHANNEL = 242;
// 错误色不走 2025 spec：官方 errorContainer 是 T65 的饱和红块（#FA746F），放在淡雅浅色里太跳。
// 固定为 M3 基线错误色板（H25 C84）的经典档位：主色 T40 / 容器 T90 / 容器上内容 T30，深色反转。
// 错误色板本就与种子无关，十三套共用一组值。
const ERROR_ROLES = {
  light: { error: 0xFFBA1A1A, onError: 0xFFFFFFFF, errorContainer: 0xFFFFDAD6, onErrorContainer: 0xFF93000A },
  dark: { error: 0xFFFFB4AB, onError: 0xFF690005, errorContainer: 0xFF93000A, onErrorContainer: 0xFFFFDAD6 },
};

// 顺序与 AppThemeColor 一致：默认方案居首，之后沿色相环由冷到暖排到紫色收尾
const SCHEMES = [
  // 蓝紫锚定在 Android 16 系统默认 Monet 的种子色相上，关掉动态取色也能拿到那套蓝灰
  ['Periwinkle', '蓝紫', 269],
  ['Iris', '鸢尾', 252], ['Sky', '天蓝', 228], ['Lagoon', '湖蓝', 202], ['Seafoam', '薄荷', 178],
  ['Mint', '青苹', 152], ['Matcha', '抹茶', 125], ['Sand', '沙金', 90], ['Apricot', '蜜杏', 62],
  ['Coral', '珊瑚', 32], ['Sakura', '樱粉', 5], ['Orchid', '藕荷', 332], ['Lilac', '丁香', 302],
];
const DEFAULT_HUE = 269;

// ---------------------------------------------------------------------------------------------
// HCT 工具

const hex = (argb) => '#FF' + (argb & 0xFFFFFF).toString(16).toUpperCase().padStart(6, '0');
const channels = (argb) => [(argb >> 16) & 255, (argb >> 8) & 255, argb & 255];
const spread = (argb) => Math.max(...channels(argb)) - Math.min(...channels(argb));

/** 该色相、该明度下色域内能达到的最大彩度 */
function maxChroma(hue, tone) {
  let lo = 0, hi = 130;
  for (let i = 0; i < 22; i++) {
    const mid = (lo + hi) / 2;
    if (Hct.from(hue, mid, tone).chroma >= mid - 0.35) lo = mid; else hi = mid;
  }
  return lo;
}

/** 按色相取色，彩度自动收敛到该明度下最大彩度的 92%，避免落在色域边界上被裁切 */
function color(hue, chroma, tone) {
  return Hct.from(hue, Math.min(chroma, 0.92 * maxChroma(hue, tone)), tone).toInt();
}

/**
 * 主填充面的明度/彩度：在上限内取最大 sRGB 通道刚够 FILL_TARGET_CHANNEL 的那一档。
 * 不用明度而用最大通道对齐，是因为冷色相在相同明度下观感明显更深。
 */
function fillToneChroma(hue) {
  for (let tone10 = 780; tone10 <= FILL_TONE_CAP * 10; tone10++) {
    const tone = tone10 / 10;
    const chroma = Math.min(FILL_CHROMA_CAP, 0.92 * maxChroma(hue, tone));
    if (Math.max(...channels(Hct.from(hue, chroma, tone).toInt())) >= FILL_TARGET_CHANNEL) return [tone, chroma];
  }
  return [FILL_TONE_CAP, Math.min(FILL_CHROMA_CAP, 0.92 * maxChroma(hue, FILL_TONE_CAP))];
}

/** 主填充面色组：fixedDim 是填充主色，fixed 是同色系更淡的一档。深浅模式共用。 */
function primaryFixedRoles(hue) {
  const [tone, chroma] = fillToneChroma(hue);
  return {
    primaryFixed: color(hue, 14, Math.min(94, tone + 6)),
    primaryFixedDim: color(hue, chroma, tone),
    onPrimaryFixed: color(hue, 20, 14),
    onPrimaryFixedVariant: color(hue, 22, 32),
  };
}

// ---------------------------------------------------------------------------------------------
// 方案构建

// 主题里以 @color/md_theme_* 引用的角色，与 XML 属性名一一对应；顺序即 XML 输出顺序
const COLOR_REF = [
  ['colorPrimary', 'primary'], ['colorOnPrimary', 'onPrimary'],
  ['colorPrimaryContainer', 'primaryContainer'], ['colorOnPrimaryContainer', 'onPrimaryContainer'],
  ['colorSecondary', 'secondary'], ['colorOnSecondary', 'onSecondary'],
  ['colorSecondaryContainer', 'secondaryContainer'], ['colorOnSecondaryContainer', 'onSecondaryContainer'],
  ['colorTertiary', 'tertiary'], ['colorOnTertiary', 'onTertiary'],
  ['colorTertiaryContainer', 'tertiaryContainer'], ['colorOnTertiaryContainer', 'onTertiaryContainer'],
  ['colorPrimaryFixed', 'primaryFixed'], ['colorPrimaryFixedDim', 'primaryFixedDim'],
  ['colorOnPrimaryFixed', 'onPrimaryFixed'], ['colorOnPrimaryFixedVariant', 'onPrimaryFixedVariant'],
  ['colorSecondaryFixed', 'secondaryFixed'], ['colorSecondaryFixedDim', 'secondaryFixedDim'],
  ['colorOnSecondaryFixed', 'onSecondaryFixed'], ['colorOnSecondaryFixedVariant', 'onSecondaryFixedVariant'],
  ['colorTertiaryFixed', 'tertiaryFixed'], ['colorTertiaryFixedDim', 'tertiaryFixedDim'],
  ['colorOnTertiaryFixed', 'onTertiaryFixed'], ['colorOnTertiaryFixedVariant', 'onTertiaryFixedVariant'],
  ['colorError', 'error'], ['colorOnError', 'onError'],
  ['colorErrorContainer', 'errorContainer'], ['colorOnErrorContainer', 'onErrorContainer'],
  ['android:colorBackground', 'background'], ['colorOnBackground', 'onBackground'],
  ['colorSurface', 'surface'], ['colorOnSurface', 'onSurface'],
  ['colorSurfaceVariant', 'surfaceVariant'], ['colorOnSurfaceVariant', 'onSurfaceVariant'],
  ['colorOutline', 'outline'], ['colorOutlineVariant', 'outlineVariant'],
  ['colorSurfaceBright', 'surfaceBright'], ['colorSurfaceDim', 'surfaceDim'],
  ['colorSurfaceContainerLowest', 'surfaceContainerLowest'], ['colorSurfaceContainerLow', 'surfaceContainerLow'],
  ['colorSurfaceContainer', 'surfaceContainer'], ['colorSurfaceContainerHigh', 'surfaceContainerHigh'],
  ['colorSurfaceContainerHighest', 'surfaceContainerHighest'],
  ['colorSurfaceInverse', 'inverseSurface'], ['colorOnSurfaceInverse', 'inverseOnSurface'],
  ['colorPrimaryInverse', 'inversePrimary'],
];
const ROLE_NAMES = COLOR_REF.map(([, name]) => name);
// 深浅同值的色组。官方 2025 spec 深色方案的主色板彩度是 26 而不是 32，单独算会得到另一组数；
// Android 系统把浅色算出来的 fixed 值同时用于两个模式，这里照做。
const FIXED_ROLE_NAMES = ROLE_NAMES.filter((name) => /Fixed/.test(name));

/** 官方 2025 spec TonalSpot 方案（Android 16 系统动态取色即此规则），只取色相 */
function officialScheme(seed, isDark) {
  return new SchemeTonalSpot(seed, isDark, 0, '2025', 'phone');
}

function readRoles(scheme) {
  return Object.fromEntries(ROLE_NAMES.map((name) => [name, MaterialDynamicColors[name].getArgb(scheme)]));
}

/** 官方方案换掉中性色板彩度，其余色板与全部角色规则不动 */
function schemeWithNeutralChroma(official, hue, neutralChroma) {
  return new DynamicScheme({
    sourceColorHct: official.sourceColorHct,
    variant: Variant.TONAL_SPOT,
    contrastLevel: 0,
    isDark: official.isDark,
    platform: 'phone',
    specVersion: '2025',
    primaryPalette: official.primaryPalette,
    secondaryPalette: official.secondaryPalette,
    tertiaryPalette: official.tertiaryPalette,
    errorPalette: official.errorPalette,
    neutralPalette: TonalPalette.fromHueAndChroma(hue, neutralChroma),
    neutralVariantPalette: TonalPalette.fromHueAndChroma(hue, neutralChroma * NEUTRAL_VARIANT_RATIO),
  });
}

/**
 * 反解中性色板彩度，使基准表面的通道差刚够 target。
 *
 * 不能直接给彩度：同一个彩度在十三个色相上的实际浓度差着三倍（樱粉在 T94 被色域裁到 C8 封顶，
 * 抹茶却能到 C69），照数字给必然一半发灰、一半发腻。通道差才是「上了多少色」的直观量。
 * 2025 spec 的表面梯度还会在色板彩度上再乘一个随层级递增的倍率（TonalSpot：Low ×1.25、
 * Container ×1.4、High ×1.5、Highest ×1.7），所以必须对着角色的最终输出解，不能对着色板解。
 * 浅色以页面底 surfaceContainer（T94）为基准，深色以 surfaceContainerHigh（T12）为基准。
 */
function solveNeutralChroma(official, hue, target) {
  const role = official.isDark ? MaterialDynamicColors.surfaceContainerHigh : MaterialDynamicColors.surfaceContainer;
  let lo = 0, hi = 40;
  for (let i = 0; i < 24; i++) {
    const mid = (lo + hi) / 2;
    if (spread(role.getArgb(schemeWithNeutralChroma(official, hue, mid))) < target) lo = mid; else hi = mid;
  }
  return hi;
}

/** 应用方案：官方主/次/三级/错误色板与全部角色规则，只替换中性色板彩度（主填充面在 buildPair 里覆盖） */
function appScheme(hue, isDark) {
  // 2025 spec 对色相落在 [105, 125) 的「黄色」中性色板另给一套更亮的表面档位（页面 T96、卡片 T99），
  // 卡片与页面会贴到 1.08:1 以下，语义层的四层深度立不住。十三套色相都避开这个区间，
  // 中性色板直接用整数色相而不是种子反算出来的 124.98 之类的值，避免在边界上抖动。
  if (Hct.isYellow(hue)) throw new Error(`色相 ${hue} 落在 2025 spec 的黄色分支里，表面档位会变，请换一个色相`);
  const official = officialScheme(Hct.from(hue, 32, 50), isDark);
  const neutralChroma = solveNeutralChroma(official, hue, isDark ? TINT_DARK : TINT_LIGHT);
  const roles = readRoles(schemeWithNeutralChroma(official, hue, neutralChroma));
  if (!isDark) {
    // 2025 spec 的 onSurface 系列对中性色板彩度乘 1.7，这里沿用同一倍率只改明度
    const ink = (tone) => Hct.from(hue, neutralChroma * 1.7, tone).toInt();
    roles.onSurface = roles.onBackground = ink(LIGHT_ON_SURFACE_TONE);
    roles.onSurfaceVariant = ink(LIGHT_ON_SURFACE_VARIANT_TONE);
    // outlineVariant 走 neutralVariant 色板（彩度 ×NEUTRAL_VARIANT_RATIO），2025 spec 未再乘倍率
    roles.outlineVariant = Hct.from(hue, neutralChroma * NEUTRAL_VARIANT_RATIO, LIGHT_OUTLINE_VARIANT_TONE).toInt();
  }
  return { ...roles, ...(isDark ? ERROR_ROLES.dark : ERROR_ROLES.light) };
}

/** 一套配色的浅色 + 深色角色表，fixed 色组两模式取同一份 */
function buildPair(hue) {
  const light = appScheme(hue, false);
  const dark = appScheme(hue, true);
  const fixed = { ...Object.fromEntries(FIXED_ROLE_NAMES.map((name) => [name, light[name]])), ...primaryFixedRoles(hue) };
  return { light: { ...light, ...fixed }, dark: { ...dark, ...fixed } };
}

// ---------------------------------------------------------------------------------------------
// 官方库自检：用平台实测色表确认依赖的库版本与 Android 16 的实现一致

function checkAgainstAndroid16() {
  const golden = JSON.parse(readFileSync(join(HERE, 'android16_default_monet.json'), 'utf-8'));
  const seed = Hct.fromInt(parseInt('FF' + golden.seed.slice(1), 16));
  // 三级色由种子色相旋转得出，对种子的一位小数敏感；深色 onSurfaceVariant 平台实现取了不同档位。
  // 这些不影响我们要复现的主色、次色与表面梯度，跳过。
  const skip = new Set(['tertiary', 'onTertiary', 'tertiaryContainer', 'onTertiaryContainer', 'dark:onSurfaceVariant']);
  const mismatches = [];
  for (const mode of ['light', 'dark']) {
    const roles = readRoles(officialScheme(seed, mode === 'dark'));
    for (const [name, expected] of Object.entries(golden[mode])) {
      if (!(name in roles) || skip.has(name) || skip.has(`${mode}:${name}`)) continue;
      const got = channels(roles[name]);
      const want = channels(parseInt(expected.slice(1), 16));
      if (Math.max(...got.map((v, i) => Math.abs(v - want[i]))) > 2) {
        mismatches.push(`${mode}.${name} 期望 ${expected} 实得 ${hex(roles[name]).slice(3)}`);
      }
    }
  }
  if (mismatches.length) {
    console.error('官方库输出与 Android 16 系统色表不一致，先核对 @material/material-color-utilities 版本：\n  ' +
      mismatches.join('\n  '));
    process.exit(1);
  }
  console.log('官方库自检通过：SchemeTonalSpot(2025, phone) 复现 Android 16 系统默认色表（容差 ±2/通道）');
}

// ---------------------------------------------------------------------------------------------
// XML 输出

const PURE_BLACK = `
    <style name="ThemeOverlay.BiliTools.DarkPureBlack" parent="ThemeOverlay.Material3Expressive">
        <item name="android:colorBackground">#FF000000</item>
        <item name="colorSurface">#FF000000</item>
        <item name="colorSurfaceDim">#FF000000</item>
        <item name="colorSurfaceBright">#FF1F1F1F</item>
        <item name="colorSurfaceContainerLowest">#FF000000</item>
        <item name="colorSurfaceContainerLow">#FF0A0A0A</item>
        <item name="colorSurfaceContainer">#FF101010</item>
        <item name="colorSurfaceContainerHigh">#FF171717</item>
        <item name="colorSurfaceContainerHighest">#FF1F1F1F</item>
        <!-- 纯黑模式下页面底色回落到 colorSurface，窗口背景需同步为纯黑 -->
        <item name="android:windowBackground">#FF000000</item>
    </style>
`;

const BASE_TAIL_LIGHT = `        <!-- 与各页面底色（AppSurfaces.pageContainerColor）一致，避免 Activity 切换时闪出异色 -->
        <item name="android:windowBackground">?attr/colorSurfaceContainer</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
        <item name="android:windowLightStatusBar">true</item>
        <item name="android:windowLightNavigationBar">true</item>`;
const BASE_TAIL_DARK = BASE_TAIL_LIGHT.replaceAll('>true<', '>false<');

// 开屏页由系统在应用进程启动前绘制，拿不到用户选的配色方案，只能取一个静态色号。
// 因此刻意用零彩度的中性灰：明度对齐页面底色（浅色 T94 / 深色 T9），十三套配色下都读作干净的加载底。
const SPLASH_LIGHT = hex(Hct.from(0, 0, 94).toInt());
const SPLASH_DARK = hex(Hct.from(0, 0, 9).toInt());

function buildColorsXml(pairs) {
  const out = ['<?xml version="1.0" encoding="utf-8"?>',
    '<!-- 本文件由 docs/配色系统/gen_themes.mjs 产出，请勿手改。',
           '     基线为默认配色「蓝紫」；设计说明见同目录 README.md -->',
    '<resources>'];
  const base = pairs.find(([, , hue]) => hue === DEFAULT_HUE)[3];
  for (const mode of ['light', 'dark']) {
    const roles = base[mode];
    for (const name of ROLE_NAMES) out.push(`    <color name="md_theme_${mode}_${name}">${hex(roles[name])}</color>`);
    out.push(`    <color name="md_theme_${mode}_surfaceTint">${hex(roles.primary)}</color>`);
    out.push(`    <color name="md_theme_${mode}_shadow">#FF000000</color>`);
    out.push(`    <color name="md_theme_${mode}_scrim">#FF000000</color>`);
    out.push('');
  }
  out.push(`    <color name="splash_background_light">${SPLASH_LIGHT}</color>`);
  out.push(`    <color name="splash_background_dark">${SPLASH_DARK}</color>`);
  out.push('</resources>');
  return out.join('\n') + '\n';
}

function buildThemesXml(pairs, dark) {
  const mode = dark ? 'dark' : 'light';
  const out = ['<!-- 配色部分由 docs/配色系统/gen_themes.mjs 产出，请勿手改。',
    '     十三套配色的参数取值与设计理念见同目录 README.md -->',
    '<resources>',
    '    <style name="Theme.BiliTools" parent="Theme.Material3Expressive.DayNight.NoActionBar">'];
  for (const [attr, name] of COLOR_REF) out.push(`        <item name="${attr}">@color/md_theme_${mode}_${name}</item>`);
  out.push(dark ? BASE_TAIL_DARK : BASE_TAIL_LIGHT);
  out.push('    </style>');
  out.push(`
    <style name="Theme.BiliTools.Splash" parent="Theme.SplashScreen">
        <item name="windowSplashScreenBackground">@color/splash_background_${mode}</item>
        <item name="windowSplashScreenAnimatedIcon">@drawable/splash_bilitools_icon</item>
        <item name="postSplashScreenTheme">@style/Theme.BiliTools</item>
    </style>

    <style name="Theme.BiliTools.ExternalDialog" parent="Theme.BiliTools">
        <item name="android:windowNoTitle">true</item>
        <item name="android:windowIsTranslucent">true</item>
        <item name="android:windowBackground">@android:color/transparent</item>
        <item name="android:windowContentOverlay">@null</item>
        <item name="android:backgroundDimEnabled">false</item>
    </style>`);
  for (const [style, zh, , pair] of pairs) {
    out.push('');
    out.push(`    <!-- ${zh} -->`);
    out.push(`    <style name="ThemeOverlay.BiliTools.Color${style}" parent="ThemeOverlay.Material3Expressive">`);
    for (const [attr, name] of COLOR_REF) out.push(`        <item name="${attr}">${hex(pair[mode][name])}</item>`);
    out.push('    </style>');
  }
  if (!dark) out.push(PURE_BLACK);
  out.push('</resources>');
  return out.join('\n') + '\n';
}

function write(path, text) {
  writeFileSync(path, text, { encoding: 'utf-8' });
  console.log(`${path}  (${text.split('\n').length - 1} 行)`);
}

// ---------------------------------------------------------------------------------------------
// 与官方方案的偏差表：按角色列出十三套里最大的明度偏差，说明每处偏差来自哪个旋钮

function printDeviation(pairs) {
  const tone = (argb) => Hct.fromInt(argb).tone;
  const rows = [];
  for (const name of ROLE_NAMES) {
    let worst = 0;
    for (const [, , hue, pair] of pairs) {
      for (const mode of ['light', 'dark']) {
        const official = readRoles(officialScheme(Hct.from(hue, 32, 50), mode === 'dark'));
        worst = Math.max(worst, Math.abs(tone(pair[mode][name]) - tone(official[name])));
      }
    }
    const reason = /^(primaryFixed|primaryFixedDim|onPrimaryFixed|onPrimaryFixedVariant)$/.test(name)
      ? '主填充面按最大通道对齐'
      : /Fixed/.test(name) ? '深色沿用浅色值（同系统）'
        : /^(error|onError|errorContainer|onErrorContainer)$/.test(name) ? '错误色固定 M3 基线档位'
        : /^on(Surface|Background)(Variant)?$/.test(name) ? '浅色正文明度压深'
        : name === 'outlineVariant' ? '浅色细线明度调淡'
          : /surface|Surface|outline|Outline|background|Background/i.test(name) ? '中性色板彩度按通道差反解'
            : '官方规则（对比度曲线随表面微调）';
    rows.push([name, worst, reason]);
  }
  console.log('\n与官方 2025 spec 的最大明度偏差（十三套 × 两模式）：');
  for (const [name, worst, reason] of rows) {
    console.log(`  ${name.padEnd(26)} ΔT ${worst.toFixed(1).padStart(5)}  ${reason}`);
  }
}

// ---------------------------------------------------------------------------------------------

checkAgainstAndroid16();
const pairs = SCHEMES.map(([style, zh, hue]) => [style, zh, hue, buildPair(hue)]);
write(join(RES, 'values', 'colors.xml'), buildColorsXml(pairs));
write(join(RES, 'values', 'themes.xml'), buildThemesXml(pairs, false));
write(join(RES, 'values-night', 'themes.xml'), buildThemesXml(pairs, true));

console.log('\n各配色的填充色（深浅共用）与页面底：');
for (const [style, zh, hue, pair] of pairs) {
  console.log(`  ${zh}  ${style.padEnd(11)}H${String(hue).padStart(4)}  填充 ${hex(pair.light.primaryFixedDim).slice(3)}` +
    `  浅色页面 ${hex(pair.light.surfaceContainer).slice(3)}  深色页面 ${hex(pair.dark.surfaceContainer).slice(3)}` +
    `  深色卡片 ${hex(pair.dark.surfaceBright).slice(3)}`);
}
printDeviation(pairs);
