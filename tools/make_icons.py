# -*- coding: utf-8 -*-
"""从源图生成 Android 图标资源。

- res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher.png   legacy 方形图标
- res/drawable-nodpi/ic_launcher_foreground.png      adaptive 前景（66% 安全区居中）
- res/drawable-nodpi/ic_stat_shadow.png              通知栏剪影图标（纯 alpha）
"""
import os
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "tools", "logo_source.png")


src = Image.open(SRC).convert("RGBA")
print("source:", src.size, src.mode)

# ---- legacy mipmap ----
for dpi, px in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    d = os.path.join(ROOT, "res", "mipmap-%s" % dpi)
    os.makedirs(d, exist_ok=True)
    src.resize((px, px), Image.LANCZOS).save(os.path.join(d, "ic_launcher.png"))
    print("wrote mipmap-%s/ic_launcher.png %dx%d" % (dpi, px, px))

# ---- adaptive 前景：432px 画布，内容 66% 居中（108dp 图层 / 66dp 安全区） ----
fg = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
inner = src.resize((288, 288), Image.LANCZOS)
fg.paste(inner, ((432 - 288) // 2, (432 - 288) // 2))
d = os.path.join(ROOT, "res", "drawable-nodpi")
os.makedirs(d, exist_ok=True)
fg.save(os.path.join(d, "ic_launcher_foreground.png"))
print("wrote drawable-nodpi/ic_launcher_foreground.png 432x432")

# ---- 通知小图标：白描边卡片 + 半透明影子，纯 alpha ----
S = 96
stat = Image.new("L", (S, S), 0)       # 亮度 = alpha
draw = ImageDraw.Draw(stat)
r = 10

def card(x0, y0, x1, y1, width, fill):
    draw.rounded_rectangle([x0, y0, x1, y1], radius=r, outline=255, width=width, fill=fill)

# 影子卡片（左后，半透明）
card(14, 26, 58, 74, 6, 96)
# 前景卡片（右前，白描边）
card(34, 20, 82, 70, 6, 0)
# 前景内小方块
draw.rounded_rectangle([56, 46, 74, 62], radius=3, fill=255)

stat = stat.filter(ImageFilter.GaussianBlur(0.6))
out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
out.putalpha(stat)
out.save(os.path.join(d, "ic_stat_shadow.png"))
print("wrote drawable-nodpi/ic_stat_shadow.png %dx%d" % (S, S))
