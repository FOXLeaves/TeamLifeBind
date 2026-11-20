"""Label unretouched production GPU readbacks for four recovery events."""
from pathlib import Path
import argparse
from PIL import Image, ImageDraw, ImageFont

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--source', type=Path, default=root / '.tools/vfx-restoration-final-details/fabric')
args = parser.parse_args()
font_path = Path('C:/Windows/Fonts/msyh.ttc')


def font(size):
    return ImageFont.truetype(str(font_path), size) if font_path.exists() else ImageFont.load_default(size=size)


entries = [('heal', '治疗 / 缝合光带', '三股缝合带与上升治疗纹'),
           ('mana', '回蓝 / 内收能量球', '内收涡纹与上升能量面'),
           ('revive', '重生 / 绽开羽翼', '六片半透明羽翼从直立转向展开'),
           ('relic', '遗物 / 悬浮棱晶', '封闭棱核与三轴卫星轨道')]


def paste_frame(sheet, path, x, y, width, height):
    with Image.open(path) as raw:
        # One crop for all effects and times, without recolouring or exposure edits.
        crop = raw.convert('RGB').crop((raw.width * .28, raw.height * .04, raw.width * .72, raw.height * .86))
        crop.thumbnail((width, height), Image.Resampling.LANCZOS)
        sheet.paste(crop, (x + (width - crop.width) // 2, y + (height - crop.height) // 2))


margin, gap, tile, picture, top = 20, 12, 354, 380, 100
width, height = margin * 2 + tile * 4 + gap * 3, top + picture + 70 + 47
sheet = Image.new('RGB', (width, height), '#09111a')
draw = ImageDraw.Draw(sheet)
draw.text((margin, 16), 'TeamLifeBind / 治疗、回蓝、重生与遗物', font=font(28), fill='#e4f5ff')
draw.text((margin, 59), '四种独立结构 · 原分辨率细节与半透明能量面 · 实际触发半径与生命周期', font=font(18), fill='#91adbf')
for i, (key, title, description) in enumerate(entries):
    x, y = margin + i * (tile + gap), top
    paste_frame(sheet, args.source / f'restoration-{key}.png', x, y, tile, picture)
    draw.rectangle((x, y, x + tile - 1, y + picture + 69), outline='#293c4d')
    draw.text((x + 12, y + picture + 8), title, font=font(21), fill='#e2f1fb')
    draw.text((x + 12, y + picture + 40), description, font=font(15), fill='#91adbf')
draw.text((margin, height - 30), '真实 GPU 读回 · 固定相机与合成深度 · 未调整曝光或颜色 · 非完整游戏客户端截图', font=font(17), fill='#809cac')
output = root / 'docs/images/restoration-vfx.png'
sheet.save(output)
print(output)

row_height, cell_width, header = 240, 366, 100
timeline = Image.new('RGB', (cell_width * 3 + 230, header + row_height * 4 + 40), '#09111a')
draw = ImageDraw.Draw(timeline)
draw.text((20, 16), '恢复与成长 / 连续动画', font=font(28), fill='#e4f5ff')
for stage, label in enumerate(('前期 / 12%', '中期 / 42%', '后期 / 74%')):
    draw.text((230 + stage * cell_width + 95, 63), label, font=font(18), fill='#91adbf')
for row, (key, title, _) in enumerate(entries):
    y = header + row * row_height
    draw.text((20, y + 105), title.split(' / ')[1], font=font(23), fill='#e2f1fb')
    for stage in range(3):
        x = 230 + stage * cell_width
        paste_frame(timeline, args.source / f'restoration-{key}-stage-{stage}.png', x, y, cell_width, row_height)
        draw.rectangle((x, y, x + cell_width - 1, y + row_height - 1), outline='#293c4d')
draw.text((20, timeline.height - 29), '相同相机、裁切与曝光；按各自持续时间取样。回蓝能量面表示动画进度，不代表当前魔力比例。', font=font(16), fill='#809cac')
output = root / 'docs/images/restoration-vfx-timeline.png'
timeline.save(output)
print(output)
