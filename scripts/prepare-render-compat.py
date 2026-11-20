"""Prepare pinned optional render mods in an isolated test profile, with publisher SHA-512 verification."""
from pathlib import Path
import argparse
import hashlib
import json
import shutil
import urllib.request
import zipfile

ROOT=Path(__file__).resolve().parents[1]
LOCK=ROOT/'scripts/fixtures/render-compat-lock.json'

def prepare(platform,mode,game):
    game=Path(game).resolve()
    if not game.is_relative_to((ROOT/'.tools').resolve()):
        raise ValueError('Compatibility fixtures must stay inside this workspace .tools directory')
    if platform not in ('fabric','neoforge') or mode not in ('sodium','iris'):
        raise ValueError('Choose Fabric/NeoForge and sodium/iris')
    lock=json.loads(LOCK.read_text(encoding='utf-8'))
    cache=ROOT/'.tools/render-compat/artifacts';cache.mkdir(parents=True,exist_ok=True)
    mods=game/'mods';mods.mkdir(parents=True,exist_ok=True)
    for item in lock['artifacts']:
        if item['platform']!=platform or mode in item['modes']:continue
        previous=mods/item['filename']
        if previous.is_file() and hashlib.sha512(previous.read_bytes()).hexdigest()==item['sha512']:previous.unlink()
    selected=[]
    for item in lock['artifacts']:
        if item['platform']!=platform or mode not in item['modes']:continue
        target=cache/item['filename']
        if not target.is_file() or hashlib.sha512(target.read_bytes()).hexdigest()!=item['sha512']:
            req=urllib.request.Request(item['url'],headers={'User-Agent':'TeamLifeBind-compat-validation/0.1.2'})
            with urllib.request.urlopen(req,timeout=60) as response:data=response.read()
            if hashlib.sha512(data).hexdigest()!=item['sha512']:raise ValueError('Publisher checksum mismatch: '+item['filename'])
            target.write_bytes(data)
        shutil.copyfile(target,mods/item['filename']);selected.append(item)
    (game/'render-compat-mods.json').write_text(json.dumps(selected,indent=2)+'\n',encoding='utf-8')
    return selected

def shader_fixture(game):
    game=Path(game).resolve()
    if not game.is_relative_to((ROOT/'.tools').resolve()):raise ValueError('Shader fixture must stay in .tools')
    shaders=ROOT/'scripts/fixtures/render-compat-shaders'
    output=game/'shaderpacks/TLB-Compatibility.zip';output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as pack:
        for path in shaders.glob('*'):pack.write(path,'shaders/'+path.name)
        for alias in ('gbuffers_terrain','gbuffers_entities','gbuffers_textured_lit','gbuffers_water','gbuffers_skytextured','gbuffers_weather','gbuffers_hand'):
            for suffix in ('vsh','fsh'):pack.write(shaders/('gbuffers_textured.'+suffix),'shaders/'+alias+'.'+suffix)
    return output

def shader_pack(game,kind):
    game=Path(game).resolve()
    if not game.is_relative_to((ROOT/'.tools').resolve()):raise ValueError('Shader fixture must stay in .tools')
    if kind=='fixture':
        output=shader_fixture(game)
        return {'project':'original-fixture','filename':output.name,'sha256':hashlib.sha256(output.read_bytes()).hexdigest()}
    if kind!='complementary':raise ValueError('Unknown shader pack fixture')
    item=json.loads(LOCK.read_text(encoding='utf-8'))['shaderPack']
    cache=ROOT/'.tools/render-compat/artifacts'/item['filename'];cache.parent.mkdir(parents=True,exist_ok=True)
    if not cache.is_file() or hashlib.sha512(cache.read_bytes()).hexdigest()!=item['sha512']:
        request=urllib.request.Request(item['url'],headers={'User-Agent':'TeamLifeBind-compat-validation/0.1.2'})
        with urllib.request.urlopen(request,timeout=60) as response:data=response.read()
        if hashlib.sha512(data).hexdigest()!=item['sha512']:raise ValueError('Shader pack publisher checksum mismatch')
        cache.write_bytes(data)
    output=game/'shaderpacks'/item['filename'];output.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(cache,output)
    return item

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--platform',choices=('fabric','neoforge'),required=True)
    parser.add_argument('--mode',choices=('sodium','iris'),required=True)
    parser.add_argument('--game-dir',type=Path,required=True)
    args=parser.parse_args()
    for item in prepare(args.platform,args.mode,args.game_dir):print(item['filename']+' SHA512 verified')
