# -*- coding: utf-8 -*-
"""配色生成器：产出 values/themes.xml、values-night/themes.xml 与 values/colors.xml。

设计理念、参数来历与那几条绕不过去的硬约束，见同目录 README.md。核心一句话：
填充色（M3 fixed 色组）在深浅两个模式下取同一个值，只有前景色与各层表面底色随模式变化。

改完这里跑 verify.py 回归校验，再跑 preview.py 目视确认。
"""
import sys, types, os

# material-color-utilities-python 会 import curses，而 Windows 没有这个模块，先顶掉
sys.modules.setdefault('curses', types.ModuleType('curses'))
sys.modules['curses'].termattrs = None
from material_color_utilities_python.hct.hct import Hct

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                    '..', '..', 'app', 'src', 'main', 'res')
chans = lambda a: ((a >> 16) & 255, (a >> 8) & 255, a & 255)


def maxc(h, t):
    lo, hi = 0.0, 130.0
    for _ in range(22):
        m = (lo + hi) / 2
        if Hct.fromHct(h, m, t).chroma >= m - 0.35:
            lo = m
        else:
            hi = m
    return lo


def c(h, chroma, tone):
    """按色相取色，彩度自动收敛到该明度下的可用范围内，避免落在色域边界上被裁切。"""
    return '#FF%06X' % (Hct.fromHct(h, min(chroma, 0.92 * maxc(h, tone)), tone).toInt() & 0xFFFFFF)


WHITE = '#FFFFFFFF'
# 明度上限压到 87：绿─青色相原本会顶到 T90-91，离卡片底太近，按钮浮不起来。
# 代价是这几个色相在色板里比暖色略深一点点，换来的是全局层级立得住。
C_CAP, T_CAP, TARGET_CHANNEL = 30.0, 87.0, 242


def fill_tone_chroma(h):
    """填充色的明度/彩度：在彩度与明度上限内，取最大 sRGB 通道刚够 244 的那一档。

    不用明度（L*）而用最大通道对齐，是因为冷色相在相同明度下观感明显更深，
    只有对齐最大通道，13 个色相摆在一起才是同一种「淡」。
    """
    for tone10 in range(780, int(T_CAP * 10) + 1):
        tone = tone10 / 10
        chroma = min(C_CAP, 0.92 * maxc(h, tone))
        if max(chans(Hct.fromHct(h, chroma, tone).toInt())) >= TARGET_CHANNEL:
            return tone, chroma
    return T_CAP, min(C_CAP, 0.92 * maxc(h, T_CAP))


def fixed_roles(h):
    """深浅模式共用的填充色组。fixedDim 是填充主色，fixed 是同色系的淡填充。"""
    tone, chroma = fill_tone_chroma(h)
    t3 = h + 60
    return [
        ('colorPrimaryFixed', c(h, 14, min(94.0, tone + 6))),
        ('colorPrimaryFixedDim', c(h, chroma, tone)),
        ('colorOnPrimaryFixed', c(h, 20, 14)),
        ('colorOnPrimaryFixedVariant', c(h, 22, 32)),
        ('colorSecondaryFixed', c(h, 12, 91)),
        ('colorSecondaryFixedDim', c(h, 18, 84)),
        ('colorOnSecondaryFixed', c(h, 14, 14)),
        ('colorOnSecondaryFixedVariant', c(h, 14, 32)),
        ('colorTertiaryFixed', c(t3, 12, 91)),
        ('colorTertiaryFixedDim', c(t3, 18, 84)),
        ('colorOnTertiaryFixed', c(t3, 18, 14)),
        ('colorOnTertiaryFixedVariant', c(t3, 18, 32)),
    ]


def scheme(h, dark):
    t3 = h + 60
    if dark:
        roles = [
            ('colorPrimary', c(h, 28, 84)), ('colorOnPrimary', c(h, 24, 22)),
            ('colorPrimaryContainer', c(h, 18, 32)), ('colorOnPrimaryContainer', c(h, 14, 92)),
            ('colorSecondary', c(h, 12, 82)), ('colorOnSecondary', c(h, 10, 24)),
            # secondaryContainer 是次级按钮（复制、浮动按钮）的底色，
            # 要比卡片底亮一档且带得出色相，才能既像按钮又不抢主按钮
            ('colorSecondaryContainer', c(h, 17, 34)), ('colorOnSecondaryContainer', c(h, 12, 92)),
            ('colorTertiary', c(t3, 22, 82)), ('colorOnTertiary', c(t3, 18, 24)),
            ('colorTertiaryContainer', c(t3, 16, 32)), ('colorOnTertiaryContainer', c(t3, 12, 92)),
        ] + fixed_roles(h) + [
            ('android:colorBackground', c(h, 6, 7)), ('colorOnBackground', c(h, 4, 92)),
            ('colorSurface', c(h, 6, 7)), ('colorOnSurface', c(h, 4, 92)),
            ('colorSurfaceVariant', c(h, 7, 30)), ('colorOnSurfaceVariant', c(h, 7, 80)),
            ('colorOutline', c(h, 6, 62)), ('colorOutlineVariant', c(h, 7, 32)),
            ('colorSurfaceBright', c(h, 7, 24)), ('colorSurfaceDim', c(h, 6, 7)),
            ('colorSurfaceContainerLowest', c(h, 6, 4)), ('colorSurfaceContainerLow', c(h, 6, 10)),
            ('colorSurfaceContainer', c(h, 6, 12)), ('colorSurfaceContainerHigh', c(h, 7, 17)),
            ('colorSurfaceContainerHighest', c(h, 7, 21)),
            # 反色组：Snackbar、富提示这类要盖住页面的浮层会用到，深色下反过来取浅底深字
            ('colorSurfaceInverse', c(h, 5, 92)), ('colorOnSurfaceInverse', c(h, 5, 20)),
            ('colorPrimaryInverse', c(h, 38, 36)),
        ]
    else:
        roles = [
            ('colorPrimary', c(h, 38, 36)), ('colorOnPrimary', WHITE),
            ('colorPrimaryContainer', c(h, 16, 90)), ('colorOnPrimaryContainer', c(h, 24, 28)),
            ('colorSecondary', c(h, 14, 38)), ('colorOnSecondary', WHITE),
            # secondaryContainer 是次级按钮（复制、浮动按钮）的底色，
            # 彩度介于主填充与中性容器之间，读得出是同一色族的低一档
            ('colorSecondaryContainer', c(h, 22, 90)), ('colorOnSecondaryContainer', c(h, 18, 28)),
            ('colorTertiary', c(t3, 22, 38)), ('colorOnTertiary', WHITE),
            ('colorTertiaryContainer', c(t3, 16, 90)), ('colorOnTertiaryContainer', c(t3, 20, 28)),
        ] + fixed_roles(h) + [
            ('android:colorBackground', c(h, 5, 98)), ('colorOnBackground', c(h, 5, 13)),
            ('colorSurface', c(h, 5, 98)), ('colorOnSurface', c(h, 5, 13)),
            ('colorSurfaceVariant', c(h, 8, 92)), ('colorOnSurfaceVariant', c(h, 7, 32)),
            ('colorOutline', c(h, 6, 48)), ('colorOutlineVariant', c(h, 7, 80)),
            ('colorSurfaceBright', c(h, 5, 98)), ('colorSurfaceDim', c(h, 7, 85)),
            # 内嵌层沉到页面底之下：卡片底为了保住色相只能停在 T98，可用亮度实际封顶在 247，
            # 页面底一抬亮，卡片就浮不起来了。把腾不出来的余量从内嵌层这边借，
            # 内嵌与页面隔着卡片、从不相邻，同不同深浅都看不出来
            ('colorSurfaceContainerLowest', WHITE), ('colorSurfaceContainerLow', c(h, 6, 93)),
            # 页面底：明度抬高、彩度压低，色调只作为一层极淡的底噪，
            # 与近白的卡片底仍差得出层次
            ('colorSurfaceContainer', c(h, 4, 94)), ('colorSurfaceContainerHigh', c(h, 7, 88)),
            ('colorSurfaceContainerHighest', c(h, 7, 85)),
            # 反色组：Snackbar、富提示这类要盖住页面的浮层会用到，浅色下反过来取深底浅字
            ('colorSurfaceInverse', c(h, 5, 20)), ('colorOnSurfaceInverse', c(h, 5, 95)),
            ('colorPrimaryInverse', c(h, 38, 84)),
        ]
    return roles


SCHEMES = [
    ('Sakura', '樱粉', 5), ('Coral', '珊瑚', 32), ('Apricot', '蜜杏', 62), ('Sand', '沙金', 90),
    ('Matcha', '抹茶', 125), ('Mint', '青苹', 152), ('Seafoam', '薄荷', 178), ('Lagoon', '湖蓝', 202),
    ('Sky', '天蓝', 228), ('Iris', '鸢尾', 252), ('Periwinkle', '蓝紫', 277),
    ('Lilac', '丁香', 302), ('Orchid', '藕荷', 332),
]
DEFAULT_HUE = 5

# 错误色与配色方案无关，沿用原值
ERROR_LIGHT = [('error', '#BA1A1A'), ('onError', '#FFFFFF'),
               ('errorContainer', '#FFDAD6'), ('onErrorContainer', '#93000A')]
ERROR_DARK = [('error', '#FFB4AB'), ('onError', '#690005'),
              ('errorContainer', '#93000A'), ('onErrorContainer', '#FFDAD6')]

# 主题里以 @color/md_theme_* 引用的角色，与 scheme() 的属性名一一对应
COLOR_REF = [
    ('colorPrimary', 'primary'), ('colorOnPrimary', 'onPrimary'),
    ('colorPrimaryContainer', 'primaryContainer'), ('colorOnPrimaryContainer', 'onPrimaryContainer'),
    ('colorSecondary', 'secondary'), ('colorOnSecondary', 'onSecondary'),
    ('colorSecondaryContainer', 'secondaryContainer'),
    ('colorOnSecondaryContainer', 'onSecondaryContainer'),
    ('colorTertiary', 'tertiary'), ('colorOnTertiary', 'onTertiary'),
    ('colorTertiaryContainer', 'tertiaryContainer'),
    ('colorOnTertiaryContainer', 'onTertiaryContainer'),
    ('colorPrimaryFixed', 'primaryFixed'), ('colorPrimaryFixedDim', 'primaryFixedDim'),
    ('colorOnPrimaryFixed', 'onPrimaryFixed'),
    ('colorOnPrimaryFixedVariant', 'onPrimaryFixedVariant'),
    ('colorSecondaryFixed', 'secondaryFixed'), ('colorSecondaryFixedDim', 'secondaryFixedDim'),
    ('colorOnSecondaryFixed', 'onSecondaryFixed'),
    ('colorOnSecondaryFixedVariant', 'onSecondaryFixedVariant'),
    ('colorTertiaryFixed', 'tertiaryFixed'), ('colorTertiaryFixedDim', 'tertiaryFixedDim'),
    ('colorOnTertiaryFixed', 'onTertiaryFixed'),
    ('colorOnTertiaryFixedVariant', 'onTertiaryFixedVariant'),
    ('colorError', 'error'), ('colorOnError', 'onError'),
    ('colorErrorContainer', 'errorContainer'), ('colorOnErrorContainer', 'onErrorContainer'),
    ('android:colorBackground', 'background'), ('colorOnBackground', 'onBackground'),
    ('colorSurface', 'surface'), ('colorOnSurface', 'onSurface'),
    ('colorSurfaceVariant', 'surfaceVariant'), ('colorOnSurfaceVariant', 'onSurfaceVariant'),
    ('colorOutline', 'outline'), ('colorOutlineVariant', 'outlineVariant'),
    ('colorSurfaceBright', 'surfaceBright'), ('colorSurfaceDim', 'surfaceDim'),
    ('colorSurfaceContainerLowest', 'surfaceContainerLowest'),
    ('colorSurfaceContainerLow', 'surfaceContainerLow'),
    ('colorSurfaceContainer', 'surfaceContainer'),
    ('colorSurfaceContainerHigh', 'surfaceContainerHigh'),
    ('colorSurfaceContainerHighest', 'surfaceContainerHighest'),
    ('colorSurfaceInverse', 'inverseSurface'), ('colorOnSurfaceInverse', 'inverseOnSurface'),
    ('colorPrimaryInverse', 'inversePrimary'),
]


def build_colors_xml():
    """基线配色（樱粉）。Theme.BiliTools 与开屏页引用这里的色号。"""
    h = DEFAULT_HUE
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           '<!-- 本文件由 docs/配色系统/gen_themes.py 产出，请勿手改。',
           '     基线为默认配色「樱粉」；设计说明见同目录 README.md -->',
           '<resources>']
    for mode, dark, errors in (('light', False, ERROR_LIGHT), ('dark', True, ERROR_DARK)):
        roles = dict(scheme(h, dark))
        roles['android:colorBackground'] = roles['android:colorBackground']
        named = {name: roles[attr] for attr, name in COLOR_REF if attr in roles}
        named['background'] = roles['android:colorBackground']
        for k, v in errors:
            named[k] = '#FF' + v.lstrip('#')
        named['surfaceTint'] = named['primary']
        named['shadow'] = '#FF000000'
        named['scrim'] = '#FF000000'
        for _, name in COLOR_REF:
            out.append(f'    <color name="md_theme_{mode}_{name}">{named[name]}</color>')
        for name in ('surfaceTint', 'shadow', 'scrim'):
            out.append(f'    <color name="md_theme_{mode}_{name}">{named[name]}</color>')
        out.append('')
    # 开屏页由系统在应用进程启动前绘制，拿不到用户选的配色方案，只能取一个静态色号。
    # 因此这里刻意用零彩度的中性灰：明度对齐页面底色（浅色 T94 / 深色 T12），
    # 十三套配色下都读作干净的加载底，不会像带色相的基线色那样和当前配色打架。
    out.append('    <color name="splash_background_light">#FFEEEEEE</color>')
    out.append('    <color name="splash_background_dark">#FF1F1F1F</color>')
    out.append('</resources>')
    return '\n'.join(out) + '\n'


BASE_TAIL_LIGHT = """        <!-- 与各页面底色（AppSurfaces.pageContainerColor）一致，避免 Activity 切换时闪出异色 -->
        <item name="android:windowBackground">?attr/colorSurfaceContainer</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
        <item name="android:windowLightStatusBar">true</item>
        <item name="android:windowLightNavigationBar">true</item>"""
BASE_TAIL_DARK = BASE_TAIL_LIGHT.replace('>true<', '>false<')

PURE_BLACK = """
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
"""


def build_themes_xml(dark):
    mode = 'dark' if dark else 'light'
    out = ['<!-- 配色部分由 docs/配色系统/gen_themes.py 产出，请勿手改。',
           '     十三套配色的参数取值与设计理念见同目录 README.md -->',
           '<resources>',
           '    <style name="Theme.BiliTools" parent="Theme.Material3Expressive.DayNight.NoActionBar">']
    for attr, name in COLOR_REF:
        out.append(f'        <item name="{attr}">@color/md_theme_{mode}_{name}</item>')
    out.append(BASE_TAIL_DARK if dark else BASE_TAIL_LIGHT)
    out.append('    </style>')
    out.append(f"""
    <style name="Theme.BiliTools.Splash" parent="Theme.SplashScreen">
        <item name="windowSplashScreenBackground">@color/splash_background_{mode}</item>
        <item name="windowSplashScreenAnimatedIcon">@drawable/splash_bilitools_icon</item>
        <item name="postSplashScreenTheme">@style/Theme.BiliTools</item>
    </style>

    <style name="Theme.BiliTools.ExternalDialog" parent="Theme.BiliTools">
        <item name="android:windowNoTitle">true</item>
        <item name="android:windowIsTranslucent">true</item>
        <item name="android:windowBackground">@android:color/transparent</item>
        <item name="android:windowContentOverlay">@null</item>
        <item name="android:backgroundDimEnabled">false</item>
    </style>""")
    for style, zh, h in SCHEMES:
        out.append('')
        out.append(f'    <!-- {zh} -->')
        out.append(f'    <style name="ThemeOverlay.BiliTools.Color{style}" '
                   f'parent="ThemeOverlay.Material3Expressive">')
        for attr, value in scheme(h, dark):
            out.append(f'        <item name="{attr}">{value}</item>')
        out.append('    </style>')
    if not dark:
        out.append(PURE_BLACK)
    out.append('</resources>')
    return '\n'.join(out) + '\n'


def write(path, text):
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        f.write(text)
    print(f'{path}  ({len(text.splitlines())} 行)')


write(os.path.join(ROOT, 'values', 'colors.xml'), build_colors_xml())
write(os.path.join(ROOT, 'values', 'themes.xml'), build_themes_xml(False))
write(os.path.join(ROOT, 'values-night', 'themes.xml'), build_themes_xml(True))

print('\n各配色的填充色（深浅共用）：')
for style, zh, h in SCHEMES:
    fixed = dict(fixed_roles(h))
    print(f'  {zh}  {style:<11}H{h:>4}  {fixed["colorPrimaryFixedDim"][3:]}')
