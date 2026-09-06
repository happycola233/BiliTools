# -*- coding: utf-8 -*-
"""README 配色色卡：回读出货的 XML，生成浅色与深色 SVG。

桌面两行、手机三列，依次展示主题填充色、三层界面底色、配色名称与强调色。
改完 gen_themes.mjs 重新产出 XML 后，重跑本脚本同步 docs/assets/palette-*.svg。

用法：python docs/配色系统/readme_palette.py
"""
from html import escape
from pathlib import Path
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
RES = HERE.parent.parent / 'app' / 'src' / 'main' / 'res'
OUT_DIR = HERE.parent / 'assets'

# 顺序与中文名同 gen_themes.mjs 的 SCHEMES，默认方案居首。
SCHEMES = [
    ('Periwinkle', '蓝紫'), ('Iris', '鸢尾'), ('Sky', '天蓝'),
    ('Lagoon', '湖蓝'), ('Seafoam', '薄荷'), ('Mint', '青苹'),
    ('Matcha', '抹茶'), ('Sand', '沙金'), ('Apricot', '蜜杏'),
    ('Coral', '珊瑚'), ('Sakura', '樱粉'), ('Orchid', '藕荷'), ('Lilac', '丁香'),
]
DEFAULT_SCHEME = 'Periwinkle'

FONT_SANS = ("-apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', "
             "'Microsoft YaHei', 'Noto Sans CJK SC', 'Noto Sans SC', Helvetica, Arial, sans-serif")
FONT_MONO = "ui-monospace, SFMono-Regular, Menlo, Consolas, 'Liberation Mono', monospace"

# 画布保持透明，文字色匹配 README 自动选取的 GitHub 浅色或深色模式。
CAPTION_INK = {False: ('#1F2328', '#59636E'), True: ('#F0F6FC', '#9198A1')}

SWATCH_WIDTH, COLUMN_GAP = 120, 20
MARGIN = 24
GRID_TOP, ROW_HEIGHT = 52, 200


def load_overlays(dark):
    theme_path = RES / ('values-night' if dark else 'values') / 'themes.xml'
    prefix = 'ThemeOverlay.BiliTools.Color'
    return {
        style.attrib['name'].removeprefix(prefix): {
            item.attrib['name']: '#' + item.text[3:]
            for item in style.findall('item') if item.text.startswith('#FF')
        }
        for style in ET.parse(theme_path).getroot().findall('style')
        if style.attrib['name'].startswith(prefix)
    }


def blend_hex(first, second, amount):
    first_channels = [int(first[i:i + 2], 16) for i in (1, 3, 5)]
    second_channels = [int(second[i:i + 2], 16) for i in (1, 3, 5)]
    return '#%02X%02X%02X' % tuple(
        round(start + (end - start) * amount)
        for start, end in zip(first_channels, second_channels)
    )


def surface_colors(colors, dark):
    page = colors['colorSurfaceContainer']
    # 与 AppSurfaces.insetContainerColor 一致：浅色取 Low/Container 中点，深色取 High。
    inset = colors['colorSurfaceContainerHigh'] if dark else blend_hex(
        colors['colorSurfaceContainerLow'], page, 0.5
    )
    return page, colors['colorSurfaceBright'], inset


def rect(x, y, width, height, radius, fill):
    return (f'<rect x="{x:g}" y="{y:g}" width="{width:g}" height="{height:g}" '
            f'rx="{radius:g}" fill="{fill}"/>')


def circle(x, y, radius, fill):
    return f'<circle cx="{x:g}" cy="{y:g}" r="{radius:g}" fill="{fill}"/>'


def text(x, y, content, size, fill, weight=400, family=FONT_SANS, anchor='start'):
    return (f'<text x="{x:g}" y="{y:g}" font-family="{family}" font-size="{size:g}" '
            f'font-weight="{weight}" fill="{fill}" text-anchor="{anchor}">'
            f'{escape(content)}</text>')


def swatch(x, y, scheme, name, colors, dark):
    """独立色块搭配轻量底色条，名称留在画布上，减少卡片套卡片的视觉重量。"""
    ink, muted = CAPTION_INK[dark]
    fill = colors['colorPrimaryFixedDim']
    parts = [
        f'<g id="swatch-{scheme}">',
        f'<title>{name} · {scheme} · {fill}</title>',
        rect(x, y, SWATCH_WIDTH, 88, 22, fill),
    ]
    if scheme == DEFAULT_SCHEME:
        parts.extend([
            rect(x + 10, y + 10, 42, 22, 11, colors['colorPrimaryFixed']),
            text(x + 31, y + 25, '默认', 12, colors['colorOnPrimaryFixed'], 500, anchor='middle'),
        ])

    # 三条等宽底色从左到右对应页面、卡片、内嵌；末尾圆点展示当前模式的强调色。
    for index, surface in enumerate(surface_colors(colors, dark)):
        parts.append(rect(x + index * 42, y + 96, 36, 8, 4, surface))
    parts.extend([
        text(x, y + 134, name, 19, ink, 500),
        circle(x + SWATCH_WIDTH - 5, y + 127, 4, colors['colorPrimary']),
        text(x, y + 154, scheme, 12, muted),
        text(x, y + 173, fill, 11, muted, family=FONT_MONO),
        '</g>',
    ])
    return '\n'.join(parts)


def legend(dark, colors, width, baseline):
    _, muted = CAPTION_INK[dark]
    # 只保留图形的阅读指引，详细的角色约定由 README 正文解释。
    start = (width - 400) / 2
    parts = [
        rect(start, baseline - 10, 14, 14, 5, colors['colorPrimaryFixedDim']),
        text(start + 22, baseline + 1, '主题色', 13, muted),
    ]
    for index, surface in enumerate(surface_colors(colors, dark)):
        parts.append(rect(start + 94 + index * 12, baseline - 7, 9, 9, 3, surface))
    parts.extend([
        text(start + 138, baseline + 1, '页面 · 卡片 · 内嵌', 13, muted),
        circle(start + 314, baseline - 3, 4, colors['colorPrimary']),
        text(start + 328, baseline + 1, '链接与图标', 13, muted),
    ])
    return '\n'.join(parts)


def render(dark, columns=7):
    width = 2 * MARGIN + columns * SWATCH_WIDTH + (columns - 1) * COLUMN_GAP
    rows = (len(SCHEMES) + columns - 1) // columns
    legend_y = GRID_TOP + rows * ROW_HEIGHT + 8
    height = legend_y + 24
    overlays = load_overlays(dark)
    _, muted = CAPTION_INK[dark]
    mode_name = '深色' if dark else '浅色'
    parts = [
        text(MARGIN, 24, f'{len(SCHEMES)} 种配色 / {mode_name}', 14, muted),
    ]
    for row in range(rows):
        row_schemes = SCHEMES[row * columns:(row + 1) * columns]
        # 最后一行不足整行时居中，保持两侧留白均衡。
        row_width = len(row_schemes) * SWATCH_WIDTH + (len(row_schemes) - 1) * COLUMN_GAP
        start_x = (width - row_width) / 2
        for column, (scheme, name) in enumerate(row_schemes):
            parts.append(swatch(
                start_x + column * (SWATCH_WIDTH + COLUMN_GAP), GRID_TOP + row * ROW_HEIGHT,
                scheme, name, overlays[scheme], dark,
            ))
    parts.append(legend(dark, overlays[DEFAULT_SCHEME], width, legend_y))
    title = f'BiliTools 十三套配色（{mode_name}模式）'
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {height}" '
            f'width="{width}" height="{height}" role="img" aria-labelledby="palette-title palette-desc">\n'
            f'<title id="palette-title">{title}</title>\n'
            '<desc id="palette-desc">蓝紫为默认配色。每枚色卡展示主题色、页面与卡片底色、'
            '配色名称、色号，以及链接与图标的强调色。</desc>\n'
            + '\n'.join(parts) + '\n</svg>\n')


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for mode, dark in (('light', False), ('dark', True)):
        for suffix, columns in (('', 7), ('-mobile', 3)):
            output_path = OUT_DIR / f'palette-{mode}{suffix}.svg'
            output_path.write_text(render(dark, columns), encoding='utf-8', newline='\n')
            print(output_path)


if __name__ == '__main__':
    main()
