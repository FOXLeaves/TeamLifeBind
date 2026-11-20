"""Contact sheets of unmodified production RenderPearl readbacks, with labelled crops."""
from pathlib import Path
import argparse
from PIL import Image, ImageDraw, ImageFont

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--source', type=Path, default=root / '.tools/vfx-combat-final-details/fabric')
args = parser.parse_args()
font_path = Path('C:/Windows/Fonts/msyh.ttc')


def font(size):
    return ImageFont.truetype(str(font_path), size) if font_path.exists() else ImageFont.load_default(size=size)


entries = [('muzzle', '枪口焰瓣', '沿枪口方向展开、收束'),
           ('tracer', '弹道压缩锋', '沿有限弹道推进的菱形冲击锋'),
           ('impact', '命中折光', '向外展开、向射击来向弯折'),
           ('explosion', '爆炸冲击壳', '球形压力纹与双层扩散环'),
           ('beacon', '空投定位', '上升扫描圈与顶端菱晶'),
           ('supply_open', '补给开启', '箱盖翻起后，奖励晶体升起'),
           ('boss', '首领降临', '破裂尖塔、蚀环与中央裂隙'),
           ('victory', '通关加冕', '八角桂冠与飘动绶带')]


def paste_frame(sheet, path, x, y, width, height, key):
    with Image.open(path) as raw:
        # Small weapon effects get a closer labelled crop; all pixels retain their
        # production exposure and colour. Timeline rows use the same crop per effect.
        bounds = (.29, .25, .71, .68) if key in ('muzzle', 'tracer', 'impact') else (.23, .02, .77, .86)
        crop = raw.convert('RGB').crop(tuple(value * (raw.width if i % 2 == 0 else raw.height)
                                            for i, value in enumerate(bounds)))
        crop.thumbnail((width, height), Image.Resampling.LANCZOS)
        sheet.paste(crop, (x + (width - crop.width) // 2, y + (height - crop.height) // 2))


margin, gap, tile, picture, top = 20, 12, 346, 310, 100
width, height = margin * 2 + tile * 4 + gap * 3, top + (picture + 66) * 2 + gap + 55
sheet = Image.new('RGB', (width, height), '#09111a')
draw = ImageDraw.Draw(sheet)
draw.text((margin, 16), 'TeamLifeBind / 武器、补给与首领特效', font=font(28), fill='#e4f5ff')
draw.text((margin, 59), '八类独立结构 · 原分辨率着色器细节 · 前三格为近景裁切，非统一物理比例', font=font(18), fill='#91adbf')
for i, (key, title, description) in enumerate(entries):
    x, y = margin + (i % 4) * (tile + gap), top + (i // 4) * (picture + 66 + gap)
    paste_frame(sheet, args.source / f'combat-{key}.png', x, y, tile, picture, key)
    draw.rectangle((x, y, x + tile - 1, y + picture + 65), outline='#293c4d')
    draw.text((x + 12, y + picture + 8), title, font=font(21), fill='#e2f1fb')
    draw.text((x + 12, y + picture + 39), description, font=font(15), fill='#91adbf')
draw.text((margin, height - 34), '真实 GPU 读回 · 固定相机、合成深度 · 未调整曝光或颜色 · 非完整游戏客户端截图', font=font(17), fill='#809cac')
output = root / 'docs/images/combat-vfx.png'
sheet.save(output)
print(output)

row_height, cell_width, header = 235, 370, 100
timeline = Image.new('RGB', (cell_width * 3 + 220, header + row_height * 4 + 40), '#09111a')
draw = ImageDraw.Draw(timeline)
draw.text((20, 16), '同一特效的三个时刻', font=font(28), fill='#e4f5ff')
for stage, label in enumerate(('前期 / 10%', '中期 / 40%', '后期 / 72%')):
    draw.text((220 + stage * cell_width + 100, 63), label, font=font(18), fill='#91adbf')
for row, key in enumerate(('muzzle', 'explosion', 'supply_open', 'boss')):
    title = next(title for entry, title, _ in entries if entry == key)
    y = header + row * row_height
    draw.text((20, y + 95), title, font=font(21), fill='#e2f1fb')
    for stage in range(3):
        x = 220 + stage * cell_width
        paste_frame(timeline, args.source / f'combat-{key}-stage-{stage}.png', x, y, cell_width, row_height, key)
        draw.rectangle((x, y, x + cell_width - 1, y + row_height - 1), outline='#293c4d')
draw.text((20, timeline.height - 29), '相同相机与裁切；按各自持续时间取样。实际枪口焰只有 4 tick，不会延长遮挡视野。', font=font(16), fill='#809cac')
output = root / 'docs/images/combat-vfx-timeline.png'
timeline.save(output)
print(output)
