# -*- coding: utf-8 -*-
"""README 顶部横幅：用本机 Chrome / Edge 无头模式渲染 banner.html，输出中英文 × 浅深色四张无损 WebP。

横幅内嵌 docs/assets/screenshots 下的应用截图，更新截图或文案后重跑本脚本。
需要 Pillow，以及系统已安装 Chrome 或 Edge（中文字体使用 Noto Sans SC）。

用法：python docs/横幅/render.py
"""
import os
import shutil
import subprocess
import tempfile
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
TEMPLATE = HERE / 'banner.html'
OUT_DIR = HERE.parent / 'assets' / 'banner'

WIDTH, HEIGHT = 1280, 640
SCALE = 4  # 4 倍像素密度：放大查看时文字与内嵌截图依然清晰
# 无头模式软件光栅化偶尔漏绘视口最底部几行，窗口留出余量后再裁回画布尺寸
WINDOW_MARGIN = 120
CORNER_RADIUS = 28  # 逻辑像素；四角透明，在 GitHub 浅色与深色页面上都不露底色

LANGS = ('zh', 'en')
THEMES = ('light', 'dark')

BROWSER_CANDIDATES = [
    os.path.expandvars(r'%ProgramFiles%\Google\Chrome\Application\chrome.exe'),
    os.path.expandvars(r'%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe'),
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
    *filter(None, map(shutil.which, ('google-chrome', 'chromium', 'chromium-browser', 'msedge'))),
]


def find_browser():
    for candidate in BROWSER_CANDIDATES:
        if Path(candidate).is_file():
            return candidate
    raise SystemExit('未找到 Chrome 或 Edge，请安装后重试。')


def screenshot(browser, url, output, profile_dir):
    subprocess.run([
        browser,
        '--headless',
        '--disable-gpu',
        '--hide-scrollbars',
        '--allow-file-access-from-files',  # 允许 file:// 页面加载仓库内的字体文件
        '--force-color-profile=srgb',  # 截图带 Display P3 配置文件，统一转换到 sRGB 输出
        f'--user-data-dir={profile_dir}',
        f'--window-size={WIDTH},{HEIGHT + WINDOW_MARGIN}',
        f'--force-device-scale-factor={SCALE}',
        '--virtual-time-budget=5000',  # 等待字体与截图加载完成
        f'--screenshot={output}',
        url,
    ], check=True, capture_output=True)


def round_corners(image):
    """四角挖成透明圆角。只对角落小块做 4 倍超采样，边缘抗锯齿且不必为整张大图分配超采样蒙版。"""
    radius = CORNER_RADIUS * SCALE
    supersample = 4
    corner = Image.new('L', (radius * supersample, radius * supersample), 0)
    diameter = 2 * radius * supersample
    ImageDraw.Draw(corner).pieslice((0, 0, diameter - 1, diameter - 1), 180, 270, fill=255)
    corner = corner.resize((radius, radius), Image.LANCZOS)
    mask = Image.new('L', image.size, 255)
    mask.paste(corner, (0, 0))
    mask.paste(corner.transpose(Image.Transpose.FLIP_LEFT_RIGHT), (image.width - radius, 0))
    mask.paste(corner.transpose(Image.Transpose.FLIP_TOP_BOTTOM), (0, image.height - radius))
    mask.paste(corner.transpose(Image.Transpose.ROTATE_180), (image.width - radius, image.height - radius))
    rounded = image.convert('RGBA')
    rounded.putalpha(mask)
    return rounded


def main():
    browser = find_browser()
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as workdir:
        for lang in LANGS:
            for theme in THEMES:
                raw = Path(workdir) / f'{lang}-{theme}.png'
                url = f'{TEMPLATE.as_uri()}?lang={lang}&theme={theme}'
                screenshot(browser, url, raw, Path(workdir) / 'profile')
                with Image.open(raw) as image:
                    image = image.crop((0, 0, WIDTH * SCALE, HEIGHT * SCALE))
                    output = OUT_DIR / f'banner-{lang}-{theme}.webp'
                    # 无损编码：quality 在无损模式下只代表压缩力度，不影响像素
                    round_corners(image).save(output, 'WEBP', lossless=True, quality=100, method=6)
                print(f'{output.relative_to(HERE.parent.parent)}  {output.stat().st_size / 1024:.0f} KB')


if __name__ == '__main__':
    main()
