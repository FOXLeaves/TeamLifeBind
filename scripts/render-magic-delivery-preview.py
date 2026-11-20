"""Arrange unmodified production GPU readbacks into labelled comparison sheets."""
from pathlib import Path
import argparse
import json
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--source', type=Path, default=ROOT / '.tools/vfx-delivery-final-details/fabric')
args = parser.parse_args()
FONT = Path('C:/Windows/Fonts/msyh.ttc')


def font(size):
    return ImageFont.truetype(str(FONT), size) if FONT.exists() else ImageFont.load_default(size=size)


def sheet(entries, columns, tile_width, title, subtitle, name):
    margin, gap, top = 20, 12, 100
    picture_height = int(tile_width * .78)
    tile_height = picture_height + 60
    rows = (len(entries) + columns - 1) // columns
    width = margin * 2 + columns * tile_width + (columns - 1) * gap
    height = top + rows * tile_height + (rows - 1) * gap + 55
    result = Image.new('RGB', (width, height), '#09111a')
    draw = ImageDraw.Draw(result)
    draw.text((margin, 16), title, font=font(27), fill='#e4f5ff')
    draw.text((margin, 60), subtitle, font=font(16), fill='#91adbf')
    for i, (filename, label, note) in enumerate(entries):
        x = margin + (i % columns) * (tile_width + gap)
        y = top + (i // columns) * (tile_height + gap)
        with Image.open(args.source / (filename + '.png')) as raw:
            # Fixed camera crop for every case; no exposure, color or geometry edits.
            crop = raw.convert('RGB').crop((raw.width * .20, raw.height * .07, raw.width * .80, raw.height * .95))
            crop.thumbnail((tile_width, picture_height), Image.Resampling.LANCZOS)
            result.paste(crop, (x + (tile_width - crop.width) // 2, y + (picture_height - crop.height) // 2))
        draw.rectangle((x, y, x + tile_width - 1, y + tile_height - 1), outline='#26394b')
        draw.text((x + 10, y + picture_height + 5), label, font=font(18), fill='#e2f1fb')
        draw.text((x + 10, y + picture_height + 32), note, font=font(14), fill='#91adbf')
    draw.text((margin, height - 34), '生产 GPU 读回 · 固定相机与合成深度 · 非完整客户端截图', font=font(16), fill='#809cac')
    destination = ROOT / 'docs/images' / name
    destination.parent.mkdir(parents=True, exist_ok=True)
    result.save(destination)
    print(destination)


labels = [('BOLT', '飞矢 · 尖锥能量鞘'), ('BEAM', '射线 · 推进加速环'), ('CONE', '吐息 · 扇面骨架'),
          ('NOVA', '震爆 · 扩张冲击面'), ('ORBIT', '环绕 · 倾斜卫星轨道'), ('CHAIN', '连锁 · 连续折线'),
          ('WAVE', '波浪 · 双层弧形波前'), ('VORTEX', '旋涡 · 螺旋上升轨迹'), ('METEOR', '陨击 · 尾焰棱片'),
          ('MINE', '地雷 · 低位警戒齿'), ('DASH', '突进 · 交叉尾迹'), ('BLINK', '闪现 · 双重椭圆门'),
          ('WALL', '墙体 · 网格扫描带'), ('SHIELD', '护盾 · 球面晶格'), ('SUMMON', '召唤 · 升起三角框架'),
          ('MARK', '印记 · 内收锁定框'), ('DRAIN', '吸取 · 反向脉冲'), ('CHANNEL', '引导 · 聚焦环与光轨'),
          ('ECHO', '回响 · 交错扩散环'), ('DOMAIN', '领域 · 展开穹顶')]
coverage = {entry['mechanic']: entry for entry in json.loads((args.source / 'delivery-coverage.json').read_text(encoding='utf-8'))}
sheet([('delivery-' + key.lower(), label, coverage[key]['previewName']) for key, label in labels], 5, 300,
      'TeamLifeBind / 20 类魔法攻击结构', '原分辨率几何刻线 + 体积能量 · 采用目录内对应学派法术配色与造型', 'magic-delivery-vfx.png')
stages = ['吟唱 · 向外展开', '持续 · 旋转维持', '命中 · 扩散退场', '结束 · 向内收束']
sheet([('tier-' + str(tier) + '-phase-' + str(phase), ('五阶' if tier == 5 else '超位') + ' / ' + stages[phase],
        '固定领域场景，比较阶位与阶段结构') for tier in (5, 8) for phase in range(4)], 4, 352,
      'TeamLifeBind / 高阶与超位阶段', '五阶起增加双环轨 · 超位增加第三环轨与六柱结构 · 四阶段按生命期展开和收束', 'magic-exalted-vfx.png')
