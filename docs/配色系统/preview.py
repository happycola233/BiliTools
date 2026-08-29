# -*- coding: utf-8 -*-
"""配色视觉预览：回读出货的 XML，渲染色板与界面 mock。

改完 gen_themes.py 之后，verify.py 只能保证数值达标，「好不好看」得看图。
产出两张 PNG 到 .tmp/：

  palette.png  十三套配色的填充色 / 前景色 / 表面三层色板，浅深并排
  mock.png     典型界面 mock：卡片、内嵌区域、三档按钮、开关、悬浮按钮

用法：python docs/配色系统/preview.py
"""
import io
import os
import re

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', '..', 'app', 'src', 'main', 'res')
OUT_DIR = os.path.join(HERE, '..', '..', '.tmp')

SCHEMES = [('Sakura', '樱粉'), ('Coral', '珊瑚'), ('Apricot', '蜜杏'), ('Sand', '沙金'),
           ('Matcha', '抹茶'), ('Mint', '青苹'), ('Seafoam', '薄荷'), ('Lagoon', '湖蓝'),
           ('Sky', '天蓝'), ('Iris', '鸢尾'), ('Periwinkle', '蓝紫'), ('Lilac', '丁香'),
           ('Orchid', '藕荷')]

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


LIGHT, DARK = overlays(False), overlays(True)


def swatch(d, x, y, w, h, color, label=None, label_color='#000000'):
    d.rounded_rectangle([x, y, x + w, y + h], radius=8, fill=color)
    if label:
        d.text((x + 8, y + h / 2 - 9), label, font=FS, fill=label_color)


def draw_palette():
    """每套配色一行：填充色、其上内容色、前景色，以及表面三层。"""
    cols = ['colorPrimaryFixedDim', 'colorOnPrimaryFixed', 'colorPrimary',
            'colorSurfaceBright', 'colorSurfaceContainer', 'colorSurfaceContainerLow']
    heads = ['填充面', '面上内容', '前景色', '卡片底', '页面底', '内嵌底']
    row_h, cell_w, gap = 52, 132, 8
    width = 2 * (170 + len(cols) * (cell_w + gap)) + 60
    img = Image.new('RGB', (width, 130 + len(SCHEMES) * row_h), '#FFFFFF')
    d = ImageDraw.Draw(img)
    d.text((28, 22), '配色色板（左浅色 / 右深色）  填充面深浅同值，前景色与表面色阶随模式变化',
           font=FB, fill='#111111')

    for side, (mode, src) in enumerate((('浅色模式', LIGHT), ('深色模式', DARK))):
        ox = 28 + side * (170 + len(cols) * (cell_w + gap) + 30)
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
    """典型界面：卡片浮在页面底上，卡片里有内嵌区域、三档按钮、开关和悬浮按钮。"""
    shown = ['Sakura', 'Matcha', 'Lagoon', 'Lilac']
    panel_w, panel_h = 520, 268
    img = Image.new('RGB', (2 * panel_w + 90, 80 + len(shown) * (panel_h + 24)), '#FFFFFF')
    d = ImageDraw.Draw(img)
    d.text((28, 22), '界面 mock（左浅色 / 右深色）', font=FB, fill='#111111')

    for r, style in enumerate(shown):
        for side, (src, dark) in enumerate(((LIGHT, False), (DARK, True))):
            t = src[style]
            ox = 28 + side * (panel_w + 34)
            oy = 68 + r * (panel_h + 24)
            page, card, inset = (t['colorSurfaceContainer'], t['colorSurfaceBright'],
                                 t['colorSurfaceContainerLow' if not dark
                                   else 'colorSurfaceContainerHigh'])
            fill, on_fill = t['colorPrimaryFixedDim'], t['colorOnPrimaryFixed']
            ink, sub = t['colorOnSurface'], t['colorOnSurfaceVariant']

            d.rounded_rectangle([ox, oy, ox + panel_w, oy + panel_h], radius=20, fill=page)
            d.text((ox + 20, oy + 12), '%s  %s' % (style, '深色' if dark else '浅色'),
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
            # 开关：浅色白滑块 / 深色深滑块
            sx, sy, sw, sh = ox + 340, oy + 146, 76, 42
            d.rounded_rectangle([sx, sy, sx + sw, sy + sh], radius=sh // 2, fill=fill)
            cx, cy, rr = sx + sw - sh // 2, sy + sh // 2, 15
            thumb = on_fill if dark else '#FFFFFF'
            tick = fill if dark else on_fill
            d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=thumb)
            d.line([(cx - 6, cy + 1), (cx - 2, cy + 5), (cx + 6, cy - 4)], fill=tick, width=3)
            # 悬浮按钮：浮在页面底上，两个模式都用填充面
            bx, by, bs = ox + panel_w - 96, oy + 220, 40
            d.rounded_rectangle([bx, by, bx + bs, by + bs], radius=13, fill=fill)
            for k in (-6, 0, 6):
                d.line([(bx + 11, by + bs // 2 + k), (bx + bs - 11, by + bs // 2 + k)],
                       fill=on_fill, width=2)

    path = os.path.join(OUT_DIR, 'mock.png')
    img.save(path)
    return path


os.makedirs(OUT_DIR, exist_ok=True)
print(os.path.abspath(draw_palette()))
print(os.path.abspath(draw_mock()))
