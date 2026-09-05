# -*- coding: utf-8 -*-
"""配色回归校验：直接回读出货的 XML，不看生成器里的意图。

校验项与门槛的来历见同目录 README.md 第九节。改完 gen_themes.mjs 跑这个，
任何一项 FAIL 都说明改动破坏了既有约定，不要靠目视放过。

用法：python docs/配色系统/verify.py
"""
import io
import os
import re
import sys

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                   '..', '..', 'app', 'src', 'main', 'res')
LIGHT_XML = os.path.join(RES, 'values', 'themes.xml')
DARK_XML = os.path.join(RES, 'values-night', 'themes.xml')
MAIN_BOTTOM_BAR_KT = os.path.join(
    os.path.dirname(RES), 'java', 'com', 'happycola233', 'bilitools', 'ui',
    'liquidtabs', 'MainLiquidBottomBar.kt'
)
APP_DIALOGS_KT = os.path.join(
    os.path.dirname(RES), 'java', 'com', 'happycola233', 'bilitools', 'ui',
    'AppDialogs.kt'
)
APP_SURFACES_KT = os.path.join(
    os.path.dirname(RES), 'java', 'com', 'happycola233', 'bilitools', 'ui',
    'theme', 'AppSurfaces.kt'
)
APP_ACCENTS_KT = os.path.join(os.path.dirname(APP_SURFACES_KT), 'AppAccents.kt')

SCHEMES = ['Periwinkle', 'Iris', 'Sky', 'Lagoon', 'Seafoam', 'Mint', 'Matcha',
           'Sand', 'Apricot', 'Coral', 'Sakura', 'Orchid', 'Lilac']

# 填充色所在的 fixed 色组，这些角色在两个模式下必须逐字节相同
FIXED_ROLES = [
    'colorPrimaryFixed', 'colorPrimaryFixedDim',
    'colorOnPrimaryFixed', 'colorOnPrimaryFixedVariant',
    'colorSecondaryFixed', 'colorSecondaryFixedDim',
    'colorOnSecondaryFixed', 'colorOnSecondaryFixedVariant',
    'colorTertiaryFixed', 'colorTertiaryFixedDim',
    'colorOnTertiaryFixed', 'colorOnTertiaryFixedVariant',
]

# 每套 overlay 都必须齐备的角色；漏掉的会静默回落到 Compose/Material 基线色
REQUIRED_ROLES = FIXED_ROLES + [
    'colorPrimary', 'colorOnPrimary', 'colorPrimaryContainer', 'colorOnPrimaryContainer',
    'colorSecondary', 'colorOnSecondary', 'colorSecondaryContainer', 'colorOnSecondaryContainer',
    'colorTertiary', 'colorOnTertiary', 'colorTertiaryContainer', 'colorOnTertiaryContainer',
    'colorError', 'colorOnError', 'colorErrorContainer', 'colorOnErrorContainer',
    'android:colorBackground', 'colorOnBackground', 'colorSurface', 'colorOnSurface',
    'colorSurfaceVariant', 'colorOnSurfaceVariant', 'colorOutline', 'colorOutlineVariant',
    'colorSurfaceBright', 'colorSurfaceDim',
    'colorSurfaceContainerLowest', 'colorSurfaceContainerLow', 'colorSurfaceContainer',
    'colorSurfaceContainerHigh', 'colorSurfaceContainerHighest',
    'colorSurfaceInverse', 'colorOnSurfaceInverse', 'colorPrimaryInverse',
]

failures = []


def check(ok, label, detail):
    mark = 'PASS' if ok else 'FAIL'
    if not ok:
        failures.append('%s —— %s' % (label, detail))
    print('  [%s] %-34s %s' % (mark, label, detail))


def overlays(path):
    """回读一个 themes.xml 里所有配色 overlay 的色表。"""
    text = io.open(path, encoding='utf-8').read()
    out = {}
    for m in re.finditer(
            r'<style name="ThemeOverlay\.BiliTools\.Color(\w+)"[^>]*>(.*?)</style>',
            text, re.S):
        out[m.group(1)] = {
            k: v for k, v in re.findall(
                r'<item name="([\w:]+)">#FF([0-9A-Fa-f]{6})</item>', m.group(2))}
    return out


def named_overlay(path, style_name):
    """回读一个具名 overlay；找不到时返回 None。"""
    text = io.open(path, encoding='utf-8').read()
    match = re.search(
        r'<style name="%s"[^>]*>(.*?)</style>' % re.escape(style_name),
        text,
        re.S,
    )
    if not match:
        return None
    return {
        key: value for key, value in re.findall(
            r'<item name="([\w:]+)">#FF([0-9A-Fa-f]{6})</item>', match.group(1)
        )
    }


def lum(hexstr):
    def channel(v):
        v /= 255
        return v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4
    r, g, b = (int(hexstr[i:i + 2], 16) for i in (0, 2, 4))
    return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)


def contrast(a, b):
    la, lb = lum(a), lum(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)


def max_channel(hexstr):
    return max(int(hexstr[i:i + 2], 16) for i in (0, 2, 4))


def midpoint(a, b):
    """两色逐通道中点；Compose 的 lerp 在 Oklab 里插值，对相邻两档中性灰差异不到 1/255。"""
    return ''.join('%02X' % round((int(a[i:i + 2], 16) + int(b[i:i + 2], 16)) / 2)
                   for i in (0, 2, 4))


def dimmed_by_black_scrim(hexstr, alpha):
    return ''.join('%02X' % round(int(hexstr[i:i + 2], 16) * (1 - alpha))
                   for i in (0, 2, 4))


LIGHT = overlays(LIGHT_XML)
DARK = overlays(DARK_XML)

print('一、配色齐备性')
check(sorted(LIGHT) == sorted(SCHEMES), '浅色 overlay 数量',
      '%d 套：%s' % (len(LIGHT), ' '.join(sorted(LIGHT))))
check(sorted(DARK) == sorted(SCHEMES), '深色 overlay 数量', '%d 套' % len(DARK))
missing = {ov: [r for r in REQUIRED_ROLES if r not in t]
           for src in (LIGHT, DARK) for ov, t in src.items()}
missing = {k: v for k, v in missing.items() if v}
check(not missing, '角色齐备',
      '每套 %d 个角色，无缺失' % len(REQUIRED_ROLES) if not missing else str(missing))

print()
print('二、fixed 色组深浅同值（设置页色块所见即所得的前提）')
mismatch = [(ov, r) for ov in SCHEMES for r in FIXED_ROLES
            if LIGHT[ov][r] != DARK[ov][r]]
check(not mismatch, 'fixed 色组两模式一致',
      '%d 个角色 × %d 套全部相同' % (len(FIXED_ROLES), len(SCHEMES))
      if not mismatch else '不一致：%s' % mismatch[:5])

print()
print('三、填充色按最大通道对齐（十三色相观感同一种「淡」）')
# 目标是 TARGET_CHANNEL = 242。允许两侧偏离，两种偏离都是生成器里写明的取舍：
#   偏高——暖色相在搜索起点 T78 就已超过目标，不再往下压；
#   偏低——绿/青色相顶到 T_CAP = 87 仍达不到目标，为了让按钮浮得起来而接受略深。
# 只要全部落在这个带内，十三个色相摆在一起就仍是同一种「淡」。
channels = {ov: max_channel(LIGHT[ov]['colorPrimaryFixedDim']) for ov in SCHEMES}
lo, hi = min(channels.values()), max(channels.values())
check(lo >= 220 and hi <= 252, '最大通道落在同一带内',
      '%d ~ %d  (220 ~ 252)' % (lo, hi))
print('         ' + '  '.join('%s %d' % (ov[:4], channels[ov]) for ov in SCHEMES))

print()
print('四、对比度门槛')
for mode, src in (('浅色', LIGHT), ('深色', DARK)):
    card_key = 'colorSurfaceBright'
    body = min(contrast(src[ov]['colorOnSurface'], src[ov][card_key]) for ov in SCHEMES)
    sub = min(contrast(src[ov]['colorOnSurfaceVariant'], src[ov][card_key]) for ov in SCHEMES)
    outline = min(contrast(src[ov]['colorOutline'], src[ov][card_key]) for ov in SCHEMES)
    on_fill = min(contrast(src[ov]['colorOnPrimaryFixed'],
                           src[ov]['colorPrimaryFixedDim']) for ov in SCHEMES)
    on_secondary = min(contrast(src[ov]['colorOnSecondaryContainer'],
                                src[ov]['colorSecondaryContainer']) for ov in SCHEMES)
    nav_selected_label = min(contrast(src[ov]['colorSecondary'],
                                      src[ov]['colorSurfaceContainer']) for ov in SCHEMES)
    nav_inactive_content = min(contrast(src[ov]['colorOnSurfaceVariant'],
                                        src[ov]['colorSurfaceContainer']) for ov in SCHEMES)
    check(body >= 4.5, '%s 正文 / 卡片底' % mode, '%.2f:1  (>= 4.5)' % body)
    check(sub >= 4.5, '%s 次要文字 / 卡片底' % mode, '%.2f:1  (>= 4.5)' % sub)
    check(outline >= 3.0, '%s 描边 / 卡片底' % mode, '%.2f:1  (>= 3)' % outline)
    check(on_fill >= 4.5, '%s 填充面上的内容' % mode, '%.2f:1  (>= 4.5)' % on_fill)
    check(on_secondary >= 4.5, '%s 次级容器上的内容' % mode,
          '%.2f:1  (>= 4.5)' % on_secondary)
    check(nav_selected_label >= 4.5, '%s 底栏选中文字 / 底栏底' % mode,
          '%.2f:1  (>= 4.5)' % nav_selected_label)
    check(nav_inactive_content >= 4.5, '%s 底栏未选中内容 / 底栏底' % mode,
          '%.2f:1  (>= 4.5)' % nav_inactive_content)

# 浅色开关使用 M3 的 primary / onPrimary 组合，深色开关使用 fixed 填充组合；
# 两种模式下开启轨道都要能从卡片底中明确浮出，滑块也要与轨道保持足够的非文字元素对比度。
# 关闭轨道（同时也是滑条未选段）取离卡片底最远的容器档位：浅色 Highest、深色 High，
# 关闭滑块统一用 outline，须与 AppAccents.inactiveTrackColor / switchColors 保持一致。
INACTIVE_TRACK = {'浅色': 'colorSurfaceContainerHighest', '深色': 'colorSurfaceContainerHigh'}
app_accents = io.open(APP_ACCENTS_KT, encoding='utf-8').read()
check("if (usesDarkSurfaces() && !usesPureBlackSurfaces()) surfaceContainerHigh "
      "else surfaceContainerHighest" in app_accents
      and 'uncheckedThumbColor = colorScheme.outline' in app_accents,
      'AppAccents 关闭态取色', '轨道浅色 Highest / 深色 High，滑块 outline')
light_switch_track = min(contrast(LIGHT[ov]['colorPrimary'],
                                  LIGHT[ov]['colorSurfaceBright']) for ov in SCHEMES)
light_switch_thumb = min(contrast(LIGHT[ov]['colorOnPrimary'],
                                  LIGHT[ov]['colorPrimary']) for ov in SCHEMES)
dark_switch_track = min(contrast(DARK[ov]['colorPrimaryFixedDim'],
                                 DARK[ov]['colorSurfaceBright']) for ov in SCHEMES)
light_switch_states = min(contrast(LIGHT[ov]['colorPrimary'],
                                   LIGHT[ov][INACTIVE_TRACK['浅色']]) for ov in SCHEMES)
dark_switch_states = min(contrast(DARK[ov]['colorPrimaryFixedDim'],
                                  DARK[ov][INACTIVE_TRACK['深色']]) for ov in SCHEMES)
check(light_switch_track >= 3.0, '浅色开关轨道 / 卡片底',
      '%.2f:1  (>= 3)' % light_switch_track)
check(light_switch_thumb >= 3.0, '浅色开关滑块 / 轨道',
      '%.2f:1  (>= 3)' % light_switch_thumb)
check(dark_switch_track >= 3.0, '深色开关轨道 / 卡片底',
      '%.2f:1  (>= 3)' % dark_switch_track)
check(light_switch_states >= 3.0, '浅色开关开启 / 关闭轨道',
      '%.2f:1  (>= 3)' % light_switch_states)
check(dark_switch_states >= 3.0, '深色开关开启 / 关闭轨道',
      '%.2f:1  (>= 3)' % dark_switch_states)
for mode, src in (('浅色', LIGHT), ('深色', DARK)):
    track_key = INACTIVE_TRACK[mode]
    off_track = min(contrast(src[ov][track_key], src[ov]['colorSurfaceBright'])
                    for ov in SCHEMES)
    off_thumb = min(contrast(src[ov]['colorOutline'], src[ov][track_key]) for ov in SCHEMES)
    check(off_track >= 1.15, '%s关闭轨道 / 卡片底' % mode, '%.3f:1  (>= 1.15)' % off_track)
    check(off_thumb >= 3.0, '%s关闭滑块 / 轨道' % mode, '%.2f:1  (>= 3)' % off_thumb)

print()
print('五、普通底栏沿用 Material 3 Expressive 颜色契约')
main_bottom_bar = io.open(MAIN_BOTTOM_BAR_KT, encoding='utf-8').read()
material_bottom_bar = main_bottom_bar.split('fun MainMaterialBottomBar', 1)
material_bottom_bar = material_bottom_bar[1] if len(material_bottom_bar) == 2 else ''
check(bool(material_bottom_bar) and 'NavigationBar(' in material_bottom_bar,
      'Compose 普通底栏存在', '已找到 MainMaterialBottomBar')
check('containerColor = AppSurfaces.pageContainerColor' in material_bottom_bar and
      'tonalElevation = 0.dp' in material_bottom_bar,
      '底栏底色使用页面表面 token', 'pageContainerColor，且不叠加 tonal elevation')
check('NavigationBarItem(' in material_bottom_bar and
      'NavigationBarItemDefaults' not in material_bottom_bar,
      '底栏内容色继承 M3 token', '未覆盖图标、文字或指示器颜色')
check('alwaysShowLabel = true' in material_bottom_bar,
      '普通底栏保持 labeled', '三个入口始终显示标签')

print()
print('六、悬浮元素按模式取色并浮在页面底之上')
# 浅色模式的 secondaryContainer 对页面底达不到 3:1 是淡雅风格的设计取舍，由投影补足，
# 其上的文案与图标必须维持正文对比度；深色模式改用 fixedDim 填充面，容器本身必须达到 3:1。
dark_float = min(contrast(DARK[ov]['colorPrimaryFixedDim'],
                          DARK[ov]['colorSurfaceContainer']) for ov in SCHEMES)
light_float = min(contrast(LIGHT[ov]['colorSecondaryContainer'],
                           LIGHT[ov]['colorSurfaceContainer']) for ov in SCHEMES)
light_float_content = min(contrast(LIGHT[ov]['colorOnSecondaryContainer'],
                                   LIGHT[ov]['colorSecondaryContainer']) for ov in SCHEMES)
check(dark_float >= 3.0, '深色 fixed 填充面 / 页面底', '%.2f:1  (>= 3)' % dark_float)
check(light_float_content >= 4.5, '浅色悬浮按钮内容 / 容器',
      '%.2f:1  (>= 4.5)' % light_float_content)
print('  [ -- ] %-34s %.2f:1  （淡雅风格的取舍，靠投影补足）'
      % ('浅色 secondaryContainer / 页面底', light_float))

print()
print('七、基础表面的相邻分离度')
# 卡片 surfaceBright、页面 surfaceContainer、激活 surfaceContainerHighest；内嵌浅色取
# surfaceContainerLow 与 surfaceContainer 的中点（T95），深色取 surfaceContainerHigh（T12），
# 须与 AppSurfaces 一致。浅色内嵌是刻意的轻凹陷、输入框另有描边，门槛低于卡片对页面。
app_surfaces = io.open(APP_SURFACES_KT, encoding='utf-8').read()
check('usesDarkSurfaces() -> surfaceContainerHigh' in app_surfaces
      and 'else -> lerp(surfaceContainerLow, surfaceContainer, 0.5f)' in app_surfaces
      and 'if (usesPureBlackSurfaces()) surfaceContainerHigh else surfaceContainerHighest'
      in app_surfaces,
      'AppSurfaces 内嵌取色', '浅色 Low/Container 中点、深色 High，激活 Highest')


def inset_of(mode, t):
    if mode == '深色':
        return t['colorSurfaceContainerHigh']
    return midpoint(t['colorSurfaceContainerLow'], t['colorSurfaceContainer'])


for mode, src in (('浅色', LIGHT), ('深色', DARK)):
    card, page, active = 'colorSurfaceBright', 'colorSurfaceContainer', 'colorSurfaceContainerHighest'
    # 浅色内嵌是刻意的轻凹陷；深色激活态 Highest 对 High 是 2025 spec 自己的相邻档距，
    # 而且激活态总伴随展开菜单或聚焦描边，都放低门槛
    inset_floor = 1.06 if mode == '浅色' else 1.09
    active_floor = 1.09 if mode == '浅色' else 1.07
    cp = min(contrast(src[ov][card], src[ov][page]) for ov in SCHEMES)
    ci = min(contrast(src[ov][card], inset_of(mode, src[ov])) for ov in SCHEMES)
    ia = min(contrast(inset_of(mode, src[ov]), src[ov][active]) for ov in SCHEMES)
    check(cp >= 1.09, '%s 卡片对页面' % mode, '%.3f  (>= 1.09)' % cp)
    check(ci >= inset_floor, '%s 卡片对内嵌' % mode, '%.3f  (>= %.2f)' % (ci, inset_floor))
    check(ia >= active_floor, '%s 内嵌激活对内嵌' % mode, '%.3f  (>= %.2f)' % (ia, active_floor))
    # 卡片必须是最亮的一层（深色色阶方向相反但卡片仍最亮）
    top = all(lum(src[ov][card]) > lum(src[ov][page])
              and lum(src[ov][card]) > lum(inset_of(mode, src[ov])) for ov in SCHEMES)
    check(top, '%s 卡片是最亮的一层' % mode, '十三套均成立' if top else '存在反序')

print()
print('八、模态容器层级')
app_dialogs = io.open(APP_DIALOGS_KT, encoding='utf-8').read()
check('val modalContainerColor: Color' in app_surfaces and
      'get() = MaterialTheme.colorScheme.surfaceContainerHighest' in app_surfaces,
      '模态容器取最高容器色阶', '浅色、深色、纯黑均为 surfaceContainerHighest')
check(all(name in app_dialogs for name in (
        'fun AppAlertDialog(', 'fun AppDatePickerDialog(', 'fun AppDialog(')),
      '模态组件统一入口', '标准、日期与自定义对话框均已封装')
scrim_match = re.search(
    r'val scrimAlpha: Float.*?if \(MaterialTheme\.colorScheme\.usesDarkSurfaces\(\)\) ([0-9.]+)f else ([0-9.]+)f',
    app_dialogs,
    re.S,
)
scrims = tuple(float(value) for value in scrim_match.groups()) if scrim_match else None
check(scrims == (0.48, 0.32), '模态遮罩按模式分档',
      '深色（含纯黑）48% / 浅色 32%' if scrims else '未识别到完整配置')
if scrims:
    dark_scrim, _ = scrims
    dark_modal_separation = min(
        contrast(
            DARK[ov]['colorSurfaceContainerHighest'],
            dimmed_by_black_scrim(DARK[ov]['colorSurfaceBright'], dark_scrim),
        ) for ov in SCHEMES
    )
    check(dark_modal_separation >= 1.15, '深色模态底 / 遮罩后卡片',
          '%.3f:1  (>= 1.15，另有边缘)' % dark_modal_separation)

print()
print('九、纯黑深色模式 overlay')
pure = named_overlay(LIGHT_XML, 'ThemeOverlay.BiliTools.DarkPureBlack')
check(pure is not None, '纯黑 overlay 存在', '在 values/themes.xml 中')
if pure is not None:
    need = ['colorSurface', 'colorSurfaceDim', 'colorSurfaceBright',
            'colorSurfaceContainerLowest', 'colorSurfaceContainerLow', 'colorSurfaceContainer',
            'colorSurfaceContainerHigh', 'colorSurfaceContainerHighest',
            'android:windowBackground']
    lack = [name for name in need if name not in pure]
    check(not lack, '纯黑 overlay 色阶齐备', '无缺失' if not lack else '缺 %s' % lack)
    if not lack:
        # 纯黑卡片使用 surfaceContainerHigh，关闭轨道必须升到 Highest 并由 outline 勾边；
        # 否则二者同色时轨道会完全消失。outline 同时也是关闭态滑块色。
        pure_card = pure['colorSurfaceContainerHigh']
        pure_unchecked_track = pure['colorSurfaceContainerHighest']
        pure_track_separation = contrast(pure_unchecked_track, pure_card)
        pure_outline_card = min(contrast(DARK[ov]['colorOutline'], pure_card)
                                for ov in SCHEMES)
        pure_thumb_track = min(contrast(DARK[ov]['colorOutline'], pure_unchecked_track)
                               for ov in SCHEMES)
        check(pure_track_separation >= 1.08, '纯黑关闭轨道 / 卡片底',
              '%.3f:1  (>= 1.08，另有描边)' % pure_track_separation)
        check(pure_outline_card >= 3.0, '纯黑关闭描边 / 卡片底',
              '%.2f:1  (>= 3)' % pure_outline_card)
        check(pure_thumb_track >= 3.0, '纯黑关闭滑块 / 轨道',
              '%.2f:1  (>= 3)' % pure_thumb_track)
        if scrims:
            pure_scrim, _ = scrims
            pure_modal_separation = contrast(
                pure['colorSurfaceContainerHighest'],
                dimmed_by_black_scrim(pure_card, pure_scrim),
            )
            check(pure_modal_separation >= 1.17, '纯黑模态底 / 遮罩后卡片',
                  '%.3f:1  (>= 1.17，另有边缘)' % pure_modal_separation)

print()
print('十、开屏页色号（平台限制：静态、零彩度、明度对齐页面底）')
colors_text = io.open(os.path.join(RES, 'values', 'colors.xml'), encoding='utf-8').read()
for name in ('splash_background_light', 'splash_background_dark'):
    m = re.search(r'<color name="%s">#FF([0-9A-Fa-f]{6})</color>' % name, colors_text)
    if not m:
        check(False, name, '未定义')
        continue
    hexstr = m.group(1)
    neutral = len({int(hexstr[i:i + 2], 16) for i in (0, 2, 4)}) == 1
    check(neutral, name, '#%s，零彩度：%s' % (hexstr, neutral))

print()
if failures:
    print('校验未通过，共 %d 项：' % len(failures))
    for f in failures:
        print('  - ' + f)
    sys.exit(1)
print('全部通过。')
