# -*- coding: utf-8 -*-
"""配色回归校验：直接回读出货的 XML，不看生成器里的意图。

校验项与门槛的来历见同目录 README.md 第九节。改完 gen_themes.py 跑这个，
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

SCHEMES = ['Sakura', 'Coral', 'Apricot', 'Sand', 'Matcha', 'Mint', 'Seafoam',
           'Lagoon', 'Sky', 'Iris', 'Periwinkle', 'Lilac', 'Orchid']

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
    check(body >= 4.5, '%s 正文 / 卡片底' % mode, '%.2f:1  (>= 4.5)' % body)
    check(sub >= 4.5, '%s 次要文字 / 卡片底' % mode, '%.2f:1  (>= 4.5)' % sub)
    check(outline >= 3.0, '%s 描边 / 卡片底' % mode, '%.2f:1  (>= 3)' % outline)
    check(on_fill >= 4.5, '%s 填充面上的内容' % mode, '%.2f:1  (>= 4.5)' % on_fill)
    check(on_secondary >= 4.5, '%s 次级容器上的内容' % mode,
          '%.2f:1  (>= 4.5)' % on_secondary)

# 浅色开关使用 M3 的 primary / onPrimary 组合，深色开关使用 fixed 填充组合；
# 两种模式下开启轨道都要能从卡片底中明确浮出，滑块也要与轨道保持足够的非文字元素对比度。
light_switch_track = min(contrast(LIGHT[ov]['colorPrimary'],
                                  LIGHT[ov]['colorSurfaceBright']) for ov in SCHEMES)
light_switch_thumb = min(contrast(LIGHT[ov]['colorOnPrimary'],
                                  LIGHT[ov]['colorPrimary']) for ov in SCHEMES)
dark_switch_track = min(contrast(DARK[ov]['colorPrimaryFixedDim'],
                                 DARK[ov]['colorSurfaceBright']) for ov in SCHEMES)
light_switch_states = min(contrast(LIGHT[ov]['colorPrimary'],
                                   LIGHT[ov]['colorSurfaceContainerHigh']) for ov in SCHEMES)
dark_switch_states = min(contrast(DARK[ov]['colorPrimaryFixedDim'],
                                  DARK[ov]['colorSurfaceContainerHigh']) for ov in SCHEMES)
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

print()
print('五、悬浮元素按模式取色并浮在页面底之上')
# 浅色模式的 secondaryContainer 对页面底达不到 3:1 是淡雅风格的设计取舍，由投影补足；
# 深色模式改用 fixed 填充面，容器本身必须达到 3:1。
dark_float = min(contrast(DARK[ov]['colorPrimaryFixedDim'],
                          DARK[ov]['colorSurfaceContainer']) for ov in SCHEMES)
light_float = min(contrast(LIGHT[ov]['colorSecondaryContainer'],
                           LIGHT[ov]['colorSurfaceContainer']) for ov in SCHEMES)
check(dark_float >= 3.0, '深色 fixed 填充面 / 页面底', '%.2f:1  (>= 3)' % dark_float)
print('  [ -- ] %-34s %.2f:1  （淡雅风格的取舍，靠投影补足）'
      % ('浅色 secondaryContainer / 页面底', light_float))

print()
print('六、表面三层的相邻分离度')
for mode, src, card, page, inset in (
        ('浅色', LIGHT, 'colorSurfaceBright', 'colorSurfaceContainer', 'colorSurfaceContainerLow'),
        ('深色', DARK, 'colorSurfaceBright', 'colorSurfaceContainer', 'colorSurfaceContainerHigh')):
    cp = min(contrast(src[ov][card], src[ov][page]) for ov in SCHEMES)
    ci = min(contrast(src[ov][card], src[ov][inset]) for ov in SCHEMES)
    check(min(cp, ci) >= 1.09, '%s 卡片对页面 / 对内嵌' % mode,
          '%.3f / %.3f  (>= 1.09)' % (cp, ci))
    # 卡片必须是最亮的一层（浅色）/ 最亮的一层（深色色阶方向相反但卡片仍最亮）
    top = all(lum(src[ov][card]) > lum(src[ov][page])
              and lum(src[ov][card]) > lum(src[ov][inset]) for ov in SCHEMES)
    check(top, '%s 卡片是最亮的一层' % mode, '十三套均成立' if top else '存在反序')

print()
print('七、纯黑深色模式 overlay')
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

print()
print('八、开屏页色号（平台限制：静态、零彩度、明度对齐页面底）')
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
