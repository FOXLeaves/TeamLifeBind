"""Arrange actual production GPU readbacks; this is not a gameplay screenshot."""
from pathlib import Path
import argparse
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source', type=Path, default=ROOT / '.tools/world-vfx-release-gpu/fabric')
args = parser.parse_args()
labels = [
    ('muzzle', '枪口 · 星芒'), ('tracer', '弹道 · 能量核心'),
    ('impact', '命中 · 金属裂光'), ('explosion', '爆炸 · 火焰冲击环'),
    ('beacon', '空投 · 螺旋光标'), ('supply_open', '开启 · 展开星环'),
    ('heal', '治疗 · 翡翠螺旋'), ('mana', '回蓝 · 蓝色星仪'),
    ('revive', '重生 · 守护光笼'), ('boss', '首领 · 赤色领域'),
    ('victory', '通关 · 金色冠冕'), ('relic', '遗物 · 紫色轨道'),
]
def font(size):
    path = Path('C:/Windows/Fonts/msyh.ttc')
    return ImageFont.truetype(str(path), size) if path.exists() else ImageFont.load_default(size=size)

canvas = Image.new('RGB', (1648, 986), '#0b1018')
draw = ImageDraw.Draw(canvas)
draw.text((24, 18), 'TeamLifeBind / 世界特效', font=font(30), fill='#e4f5ff')
draw.text((24, 62), '生产渲染器 GPU 读回 · 合成相机与深度场景 · 非完整游戏截图', font=font(18), fill='#8ea9bd')
for i, (key, title) in enumerate(labels):
    x, y = 24 + (i % 4) * 400, 104 + (i // 4) * 278
    source = args.source / f'world-{key}.png'
    with Image.open(source) as image:
        image = image.convert('RGB')
        image.thumbnail((388, 218), Image.Resampling.LANCZOS)
        canvas.paste(image, (x + (388 - image.width) // 2, y + (218 - image.height) // 2))
    draw.rectangle((x, y, x + 388, y + 256), outline='#26394b', width=1)
    draw.text((x + 12, y + 224), title, font=font(19), fill='#d7eafb')
draw.text((24, 950), 'Fabric / Forge / NeoForge · Java 26 · 显示版本 0.1.2 · 无需额外光影包', font=font(18), fill='#7d9ba7')
output = ROOT / 'docs/images/world-vfx-preview.png'
output.parent.mkdir(parents=True, exist_ok=True)
canvas.save(output)
print(output)
