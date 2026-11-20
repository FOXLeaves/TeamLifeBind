"""Contact sheets of real production GPU readbacks, using a fixed camera crop."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parents[1]
OUTPUT=ROOT/'docs/images'
FONT=Path('C:/Windows/Fonts/msyh.ttc')
def font(size):
    return ImageFont.truetype(str(FONT),size) if FONT.exists() else ImageFont.load_default(size=size)

def sheet(entries,source,columns,tile_width,title,subtitle,name):
    gap=12; margin=20; top=100; image_height=int(tile_width*.70); tile_height=image_height+42
    rows=(len(entries)+columns-1)//columns
    width=margin*2+columns*tile_width+(columns-1)*gap
    height=top+rows*tile_height+(rows-1)*gap+58
    result=Image.new('RGB',(width,height),'#0a1018');draw=ImageDraw.Draw(result)
    draw.text((margin,18),title,font=font(28),fill='#e4f5ff')
    draw.text((margin,61),subtitle,font=font(17),fill='#8ca8bd')
    for i,(key,label) in enumerate(entries):
        x=margin+(i%columns)*(tile_width+gap);y=top+(i//columns)*(tile_height+gap)
        with Image.open(source/(key+'.png')) as raw:
            # Same crop for every sample, no brightness/color edits or fabricated effects.
            crop=raw.convert('RGB').crop((raw.width*.23,raw.height*.06,raw.width*.77,raw.height*.84))
            crop.thumbnail((tile_width,image_height),Image.Resampling.LANCZOS)
            result.paste(crop,(x+(tile_width-crop.width)//2,y+(image_height-crop.height)//2))
        draw.rectangle((x,y,x+tile_width-1,y+tile_height-1),outline='#25384b')
        draw.text((x+10,y+image_height+9),label,font=font(18),fill='#d8ecfa')
    draw.text((margin,height-37),'真实 GPU 输出 · 固定相机裁切 · 合成深度场景 · 非完整客户端截图',font=font(17),fill='#809cac')
    OUTPUT.mkdir(parents=True,exist_ok=True);result.save(OUTPUT/name);print(OUTPUT/name)

schools=[('fire','炎 · 卷曲冠纹'),('water','潮 · 交叠涟漪'),('ice','霜 · 分叉冰晶'),('wind','岚 · 双臂旋流'),
    ('earth','岩 · 断层岩板'),('lightning','雷 · 分叉电弧'),('light','辉 · 星芒光环'),('shadow','影 · 双重月牙'),
    ('arcane','奥 · 星形刻线'),('nature','森 · 叶片脉络'),('gravity','引 · 偏心轨道'),('time','时 · 旋转刻盘'),
    ('blood','血 · 脉动心印'),('spirit','灵 · 交织魂迹')]
sheet([('school-'+key+'-impact',label) for key,label in schools],ROOT/'.tools/vfx-plus-release-school/fabric',7,252,
    'TeamLifeBind / 14 学派命中刻纹','原分辨率线条与原有体积能量共同渲染 · 同一相机与生命期','vfx-plus-schools.png')
sheet([('world-team_signal','共鸣哨 · 定位脉冲'),('world-team_anchor','队伍床 · 双拱落点'),
    ('world-totem_rescue','图腾 · 守护翼'),('world-scout_pulse','侦察 · 扫描半球'),
    ('world-control_point','据点 · 范围刻盘'),('world-control_capture','占领 · 升起箭纹')],ROOT/'.tools/vfx-plus-release-world/fabric',3,432,
    'TeamLifeBind / 队伍与据点特效','共鸣、队伍床、侦察仅同步给队友 · 据点光环与实际占领范围对应','vfx-plus-team.png')
