"""Six world-event previews from unmodified production RenderPearl GPU readbacks."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

root = Path(__file__).resolve().parents[1]
source = root / '.tools/vfx-journey-final-world/fabric'
font_path = Path('C:/Windows/Fonts/msyh.ttc')


def font(size):
    return ImageFont.truetype(str(font_path), size) if font_path.exists() else ImageFont.load_default(size=size)


entries = [('spell_learn', '学习魔法 · 展开的书页', '成功学习后出现，重复学习不播放'),
           ('mana_growth', '魔力成长 · 升阶阶梯', '苹果实际增加上限后出现'),
           ('encounter_spawn', '敌人入场 · 裂隙门', 'PVE / RG 每波最多三个普通怪'),
           ('wave_clear', '清波奖励 · 胜利月桂', '第 1–9 波奖励发放时出现'),
           ('route_lock', '路线锁定 · 汇聚星盘', 'RG 决定路线后，仅参战队员可见'),
           ('border_warning', '缩圈预警 · 警戒箭纹', '缩圈前一分钟，仅玩家本人可见')]
margin, gap, tile, picture, top = 20, 14, 442, 340, 102
width, height = margin * 2 + tile * 3 + gap * 2, top + (picture + 68) * 2 + gap + 55
sheet = Image.new('RGB', (width, height), '#09111a')
draw = ImageDraw.Draw(sheet)
draw.text((margin, 18), 'TeamLifeBind / 成长与远征特效', font=font(29), fill='#e4f5ff')
draw.text((margin, 62), '连续几何与体积能量 + 原分辨率刻线 · 六种不同结构与触发条件', font=font(18), fill='#91adbf')
for i, (key, label, description) in enumerate(entries):
    x = margin + (i % 3) * (tile + gap)
    y = top + (i // 3) * (picture + 68 + gap)
    with Image.open(source / ('world-' + key + '.png')) as raw:
        # Identical crop and scale; no exposure, color, or effect retouching.
        crop = raw.convert('RGB').crop((raw.width * .23, raw.height * .06, raw.width * .77, raw.height * .84))
        crop.thumbnail((tile, picture), Image.Resampling.LANCZOS)
        sheet.paste(crop, (x + (tile - crop.width) // 2, y + (picture - crop.height) // 2))
    draw.rectangle((x, y, x + tile - 1, y + picture + 67), outline='#293c4d')
    draw.text((x + 12, y + picture + 8), label, font=font(21), fill='#e2f1fb')
    draw.text((x + 12, y + picture + 39), description, font=font(16), fill='#91adbf')
draw.text((margin, height - 34), '真实 GPU 读回 · 固定相机与合成深度 · 非完整游戏客户端截图', font=font(17), fill='#809cac')
output = root / 'docs/images/journey-vfx.png'
sheet.save(output)
print(output)
