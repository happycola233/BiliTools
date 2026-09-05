# -*- coding: utf-8 -*-
"""配色视觉预览：回读出货的 XML，渲染色板与界面 mock。

改完 gen_themes.mjs 之后，verify.py 只能保证数值达标，「好不好看」得看图。
产出两张 PNG 到 .tmp/：

  palette.png  十三套配色的填充色 / 前景色 / 表面四层色板，浅深并排
  mock.png     典型界面 mock：浅色、深色与纯黑下的常用组件与模态弹窗

用法：python docs/配色系统/preview.py
"""
import io
import os
import re

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', '..', 'app', 'src', 'main', 'res')
OUT_DIR = os.path.join(HERE, '..', '..', '.tmp')

SCHEMES = [('Periwinkle', '蓝紫'), ('Iris', '鸢尾'), ('Sky', '天蓝'), ('Lagoon', '湖蓝'),
           ('Seafoam', '薄荷'), ('Mint', '青苹'), ('Matcha', '抹茶'), ('Sand', '沙金'),
           ('Apricot', '蜜杏'), ('Coral', '珊瑚'), ('Sakura', '樱粉'), ('Orchid', '藕荷'),
           ('Lilac', '丁香')]

FONT_DIR = os.path.join(os.environ.get('WINDIR', r'C:\Windows'), 'Fonts')
F = ImageFont.truetype(os.path.join(FONT_DIR, 'msyh.ttc'), 19)
FS = ImageFont.truetype(os.path.join(FONT_DIR, 'msyh.ttc'), 15)
FB = ImageFont.truetype(os.path.join(FONT_DIR, 'msyhbd.ttc'), 24)


def overlays(dark):
    path = os.path.join(RES, 'values-night' if dark else 'values', 'themes.xml')
    text = io.open(path, encoding='utf-8').read()
    out = {}
    for m in re.finditer(
            r'<style name="ThemeOverlay\.BiliTools\.Color(\w+)"[^>]*>(.*?)</style>',
            text, re.S):
        out[m.group(1)] = {
            k: '#' + v for k, v in re.findall(
                r'<item name="([\w:]+)">#FF([0-9A-Fa-f]{6})</item>', m.group(2))}
    return out


def named_overlay(style_name):
    """回读 values/themes.xml 中不属于十三套主题色的具名 overlay。"""
    path = os.path.join(RES, 'values', 'themes.xml')
    text = io.open(path, encoding='utf-8').read()
    match = re.search(
        r'<style name="%s"[^>]*>(.*?)</style>' % re.escape(style_name),
        text,
        re.S,
    )
    if not match:
        raise ValueError('找不到 overlay：%s' % style_name)
    return {
        key: '#' + value for key, value in re.findall(
            r'<item name="([\w:]+)">#FF([0-9A-Fa-f]{6})</item>', match.group(1)
        )
    }


LIGHT, DARK = overlays(False), overlays(True)
PURE_BLACK_SURFACES = named_overlay('ThemeOverlay.BiliTools.DarkPureBlack')
PURE_BLACK = {
    style: {**DARK[style], **PURE_BLACK_SURFACES} for style, _ in SCHEMES
}


def swatch(d, x, y, w, h, color, label=None, label_color='#000000'):
    d.rounded_rectangle([x, y, x + w, y + h], radius=8, fill=color)
    if label:
        d.text((x + 8, y + h / 2 - 9), label, font=FS, fill=label_color)


def draw_switch(d, x, y, track, thumb, checked, border=None):
    """按实际组件的开启/关闭滑块比例画一个无状态图标的开关。"""
    width, height = 76, 42
    d.rounded_rectangle(
        [x, y, x + width, y + height],
        radius=height // 2,
        fill=track,
        outline=border,
        width=3,
    )
    center_x = x + width - height // 2 if checked else x + height // 2
    radius = 15 if checked else 11
    center_y = y + height // 2
    d.ellipse(
        [center_x - radius, center_y - radius, center_x + radius, center_y + radius],
        fill=thumb,
    )


def draw_centered_text(d, center_x, y, text, font, fill):
    """以给定横坐标为中心绘制单行文本。"""
    bounds = d.textbbox((0, 0), text, font=font)
    width = bounds[2] - bounds[0]
    d.text((center_x - width / 2, y), text, font=font, fill=fill)


def blend_hex(foreground, background, alpha):
    """把 foreground 以 alpha 叠到 background 上，用于预览带透明度的模态边缘。"""
    fg = tuple(int(foreground[i:i + 2], 16) for i in (1, 3, 5))
    bg = tuple(int(background[i:i + 2], 16) for i in (1, 3, 5))
    channels = tuple(round(f * alpha + b * (1 - alpha)) for f, b in zip(fg, bg))
    return '#%02X%02X%02X' % channels


def apply_black_scrim(image, bounds, alpha):
    """在指定预览区域叠加与 Compose Dialog 一致的黑色遮罩。"""
    overlay = Image.new('RGBA', image.size, (0, 0, 0, 0))
    overlay_draw = ImageDraw.Draw(overlay)
    overlay_draw.rectangle(bounds, fill=(0, 0, 0, round(alpha * 255)))
    image.paste(overlay, (0, 0), overlay)


def draw_bottom_navigation(d, x, y, width, theme):
    """按 Material 3 Expressive 的四个导航栏颜色角色绘制普通底栏。"""
    height = 76
    d.rectangle([x, y, x + width, y + height], fill=theme['colorSurfaceContainer'])
    centers = [x + width * fraction for fraction in (1 / 6, 1 / 2, 5 / 6)]
    indicator = theme['colorSecondaryContainer']
    selected_icon = theme['colorOnSecondaryContainer']
    selected_label = theme['colorSecondary']
    inactive = theme['colorOnSurfaceVariant']

    d.rounded_rectangle(
        [centers[0] - 38, y + 7, centers[0] + 38, y + 39],
        radius=16,
        fill=indicator,
    )
    for index, center_x in enumerate(centers):
        icon_color = selected_icon if index == 0 else inactive
        d.ellipse([center_x - 7, y + 16, center_x + 7, y + 30], fill=icon_color)
        draw_centered_text(
            d,
            center_x,
            y + 46,
            ('解析', '下载', '我')[index],
            FS,
            selected_label if index == 0 else inactive,
        )


def draw_palette():
    """每套配色一行：填充色、其上内容色、前景色，以及表面各层。"""
    cols = ['colorPrimaryFixedDim', 'colorOnPrimaryFixed', 'colorPrimary',
            'colorSurfaceBright', 'colorSurfaceContainerLow', 'colorSurfaceContainer',
            'colorSurfaceContainerHigh', 'colorSurfaceContainerHighest']
    heads = ['填充面', '面上内容', '前景色', '卡片底', 'Low', '页面底', 'High', '激活/模态底']
    row_h, cell_w, gap = 52, 132, 8
    width = 2 * (170 + len(heads) * (cell_w + gap)) + 60
    img = Image.new('RGB', (width, 130 + len(SCHEMES) * row_h), '#FFFFFF')
    d = ImageDraw.Draw(img)
    d.text((28, 22), '配色色板（左浅色 / 右深色）  填充面深浅同值，前景色与表面色阶随模式变化',
           font=FB, fill='#111111')

    for side, (mode, src) in enumerate((('浅色模式', LIGHT), ('深色模式', DARK))):
        ox = 28 + side * (170 + len(heads) * (cell_w + gap) + 30)
        d.text((ox, 66), mode, font=FB, fill='#111111')
        for i, head in enumerate(heads):
            d.text((ox + 170 + i * (cell_w + gap), 98), head, font=FS, fill='#555555')
        for r, (style, zh) in enumerate(SCHEMES):
            y = 122 + r * row_h
            t = src[style]
            d.text((ox, y + 14), '%s  %s' % (zh, style), font=FS, fill='#111111')
            for i, key in enumerate(cols):
                swatch(d, ox + 170 + i * (cell_w + gap), y, cell_w, row_h - 10,
                       t[key], t[key][1:], '#FFFFFF' if i == 1 else '#000000')

    path = os.path.join(OUT_DIR, 'palette.png')
    img.save(path)
    return path


def draw_mock():
    """典型界面：并排检查浅色、深色与纯黑模式下的常用组件和模态层。"""
    shown = ['Periwinkle', 'Sakura', 'Matcha', 'Lagoon']
    modes = (
        ('浅色', LIGHT, False, False),
        ('深色', DARK, True, False),
        ('纯黑', PURE_BLACK, True, True),
    )
    panel_w, normal_h, panel_h = 520, 356, 610
    image_width = 56 + len(modes) * panel_w + (len(modes) - 1) * 34
    img = Image.new('RGB', (image_width, 80 + len(shown) * (panel_h + 24)), '#FFFFFF')
    d = ImageDraw.Draw(img)
    d.text((28, 22), '界面 mock（浅色 / 深色 / 纯黑）', font=FB, fill='#111111')

    for r, style in enumerate(shown):
        for side, (mode_name, src, dark, pure_black) in enumerate(modes):
            t = src[style]
            ox = 28 + side * (panel_w + 34)
            oy = 68 + r * (panel_h + 24)
            # 内嵌层与 AppSurfaces.insetContainerColor 一致：浅色取 Low/Container 中点，
            # 深色取 High，纯黑取 Container
            if pure_black:
                page, card, inset = (t['colorSurface'], t['colorSurfaceContainerHigh'],
                                     t['colorSurfaceContainer'])
            elif dark:
                page, card, inset = (t['colorSurfaceContainer'], t['colorSurfaceBright'],
                                     t['colorSurfaceContainerHigh'])
            else:
                page, card = t['colorSurfaceContainer'], t['colorSurfaceBright']
                inset = blend_hex(t['colorSurfaceContainerLow'], page, 0.5)
            fill, on_fill = t['colorPrimaryFixedDim'], t['colorOnPrimaryFixed']
            ink, sub = t['colorOnSurface'], t['colorOnSurfaceVariant']
            error, error_container = t['colorError'], t['colorErrorContainer']

            d.rounded_rectangle([ox, oy, ox + panel_w, oy + panel_h], radius=20, fill=page)
            d.text((ox + 20, oy + 12), '%s  %s' % (style, mode_name),
                   font=FS, fill=sub)
            # 卡片
            d.rounded_rectangle([ox + 18, oy + 40, ox + panel_w - 18, oy + 212],
                                radius=18, fill=card)
            d.text((ox + 38, oy + 54), '视频标题占位', font=F, fill=ink)
            # 卡片内嵌区域
            d.rounded_rectangle([ox + 38, oy + 88, ox + 288, oy + 128], radius=12, fill=inset)
            d.text((ox + 54, oy + 99), '内嵌下拉框', font=FS, fill=sub)
            # 主按钮（填充面）
            d.rounded_rectangle([ox + 38, oy + 142, ox + 168, oy + 180], radius=14, fill=fill)
            d.text((ox + 84, oy + 152), '解析', font=FS, fill=on_fill)
            # 次级按钮
            d.rounded_rectangle([ox + 180, oy + 142, ox + 320, oy + 180], radius=14,
                                fill=t['colorSecondaryContainer'])
            d.text((ox + 214, oy + 152), '复制', font=FS, fill=t['colorOnSecondaryContainer'])
            # 开关：同时画关闭与开启态；关闭轨道取离卡片最远的容器档位
            # （浅色与纯黑 Highest、深色 High），纯黑另加描边；关闭滑块统一 outline
            sx, sy = ox + 326, oy + 146
            unchecked_track = t[
                'colorSurfaceContainerHigh' if dark and not pure_black
                else 'colorSurfaceContainerHighest'
            ]
            draw_switch(
                d,
                sx,
                sy,
                unchecked_track,
                t['colorOutline'],
                checked=False,
                border=t['colorOutline'] if pure_black else None,
            )
            draw_switch(
                d,
                sx + 84,
                sy,
                fill if dark else t['colorPrimary'],
                on_fill if dark else t['colorOnPrimary'],
                checked=True,
            )
            # 悬浮按钮：浅色与次级按钮同色，深色改用固定填充色保证能浮出页面底
            fab_fill = fill if dark else t['colorSecondaryContainer']
            on_fab_fill = on_fill if dark else t['colorOnSecondaryContainer']
            bx, by, bs = ox + panel_w - 96, oy + 220, 40
            d.rounded_rectangle([bx, by, bx + bs, by + bs], radius=13, fill=fab_fill)
            for k in (-6, 0, 6):
                d.line([(bx + 11, by + bs // 2 + k), (bx + bs - 11, by + bs // 2 + k)],
                       fill=on_fab_fill, width=2)
            # 普通底栏：选中指示器、图标与文字是三个独立角色，不能复用同一内容色
            draw_bottom_navigation(d, ox, oy + normal_h - 76, panel_w, t)

            # 下半区单独展示模态状态，避免覆盖上方常用组件预览。
            modal_top = oy + normal_h + 20
            d.text((ox + 20, modal_top + 4), '模态状态', font=FS, fill=sub)
            d.rounded_rectangle(
                [ox + 18, modal_top + 34, ox + panel_w - 18, oy + panel_h - 18],
                radius=18,
                fill=card,
            )
            d.text((ox + 38, modal_top + 48), '底层内容', font=F, fill=ink)
            d.rounded_rectangle(
                [ox + 330, modal_top + 80, ox + panel_w - 34, modal_top + 122],
                radius=12,
                fill=error_container,
            )

            scrim_alpha = 0.48 if dark else 0.32
            apply_black_scrim(
                img,
                [ox, modal_top, ox + panel_w, oy + panel_h],
                scrim_alpha,
            )
            d = ImageDraw.Draw(img)

            modal_fill = t['colorSurfaceContainerHighest']
            outline_alpha = 0.50 if pure_black else 0.30 if dark else 0
            modal_outline = (
                blend_hex(t['colorOutlineVariant'], modal_fill, outline_alpha)
                if outline_alpha else None
            )
            dialog_bounds = [
                ox + 86,
                modal_top + 32,
                ox + panel_w - 86,
                oy + panel_h - 34,
            ]
            d.rounded_rectangle(
                dialog_bounds,
                radius=24,
                fill=modal_fill,
                outline=modal_outline,
                width=2,
            )
            d.text((ox + 114, modal_top + 52), '删除该组', font=F, fill=ink)
            d.text((ox + 114, modal_top + 86), '此操作将删除组内所有任务。', font=FS, fill=sub)
            d.text((ox + 312, modal_top + 126), '取消', font=FS, fill=t['colorPrimary'])
            d.text((ox + 376, modal_top + 126), '删除', font=FS, fill=error)

    path = os.path.join(OUT_DIR, 'mock.png')
    img.save(path)
    return path


os.makedirs(OUT_DIR, exist_ok=True)
print(os.path.abspath(draw_palette()))
print(os.path.abspath(draw_mock()))
