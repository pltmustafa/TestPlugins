# ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

from Kekik.cli       import konsol
from httpx           import Client as Session
from parsel          import Selector
from Kekik.Sifreleme import StringCodec, Packer, HexCodec
import re, json

def rapid2m3u8(url:str) -> str:
    oturum = Session()
    oturum.headers.update({"User-Agent":"Mozilla/5.0"})

    istek = oturum.get(url)
    try:
        escaped_hex = re.findall(r'file": "(.*)",', istek.text)[0]
    except Exception:
        eval_jwsetup = re.compile(r'\};\s*(eval\(function[\s\S]*?)var played = \d+;').findall(istek.text)[0]
        jwsetup      = Packer.unpack(Packer.unpack(eval_jwsetup))
        escaped_hex  = re.findall(r'file":"(.*)","label', jwsetup)[0]

    return HexCodec.decode(escaped_hex)

def trstx2m3u8(url:str) -> list[dict]:
    oturum = Session()
    oturum.headers.update({"User-Agent":"Mozilla/5.0", "Referer":"https://www.fullhdfilmizlesene.de/"})

    istek     = oturum.get(url)
    file      = re.findall(r"file\":\"([^\"]+)", istek.text)[0]
    post_link = file.replace("\\", "")

    post_istek = oturum.post(f"https://trstx.org/{post_link}").json()

    veriler = []
    for bak in post_istek[1:]:
        vid_url = "https://trstx.org/playlist/" + bak.get("file")[1:] + ".txt"
        veriler.append({bak.get("title") : oturum.post(vid_url).text})

    return veriler

def sobreatsesuyp2m3u8(url:str) -> list[dict]:
    oturum = Session()
    oturum.headers.update({"User-Agent":"Mozilla/5.0", "Referer":"https://www.fullhdfilmizlesene.de/"})

    istek     = oturum.get(url)
    file      = re.findall(r"file\":\"([^\"]+)", istek.text)[0]
    post_link = file.replace("\\", "")

    post_istek = oturum.post(f"https://sobreatsesuyp.com/{post_link}").json()

    veriler = []
    for bak in post_istek[1:]:
        vid_url = "https://sobreatsesuyp.com/playlist/" + bak.get("file")[1:] + ".txt"
        veriler.append({bak.get("title") : oturum.post(vid_url).text})

    return veriler

def turboimgz2m3u8(url:str) -> str:
    oturum = Session()
    oturum.headers.update({"User-Agent":"Mozilla/5.0"})

    istek     = oturum.get(url)
    video_url = re.findall(r'file: "(.*)",', istek.text)[0]

    return video_url

def fullhdfilmizlesene(url:str) -> list:
    oturum = Session()
    oturum.headers.update({"User-Agent":"Mozilla/5.0"})

    konsol.print(f"\n\n{url}")
    istek  = oturum.get(url, follow_redirects=True)
    secici = Selector(istek.text)

    script   = secici.xpath("(//script)[1]").get()
    scx_data = json.loads(re.findall(r'scx = (.*?);', script)[0])
    scx_keys = list(scx_data.keys())

    link_list = []
    for key in scx_keys:
        t = scx_data[key]["sx"]["t"]
        if isinstance(t, list):
            link_list.append({key: StringCodec.decode(elem) for elem in t})
        if isinstance(t, dict):
            link_list.append({k: StringCodec.decode(v) for k, v in t.items()})

    vid_links = []
    for elem in link_list:
        for key, value in elem.items():
            if "rapidvid" in value:
                vid_links.append({key: rapid2m3u8(value)})
                continue

            if "trstx.org" in value:
                vid_links.append({key: trstx2m3u8(value)})
                continue

            if "sobreatsesuyp.com" in value:
                vid_links.append({key: sobreatsesuyp2m3u8(value)})
                continue

            if "turbo.imgz.me" in value:
                vid_links.append({key: turboimgz2m3u8(value)})
                continue

            if "vidmoxy.com" in value:
                vid_links.append({key: rapid2m3u8(value)})
                continue

            vid_links.extend(
                {key: value}
                for bidi in ("proton", "fast", "tr", "en")
                    if bidi in key
            )


    return vid_links

# konsol.print(fullhdfilmizlesene("https://www.fullhdfilmizlesene.de/film/makine-2/"))
konsol.print(fullhdfilmizlesene("https://www.fullhdfilmizlesene.de/film/iskence-okulu/"))
# konsol.print(fullhdfilmizlesene("https://www.fullhdfilmizlesene.de/film/yedi-yasam/"))
# konsol.print(fullhdfilmizlesene("https://www.fullhdfilmizlesene.de/film/cilgin-cocuklar-oyun-bitti-izle-1/"))
# konsol.print(fullhdfilmizlesene("https://www.fullhdfilmizlesene.de/film/suclu-den-skyldige/"))
# konsol.print(fullhdfilmizlesene("https://www.fullhdfilmizlesene.de/film/vahsiler-hostiles/"))
# konsol.print(fullhdfilmizlesene("https://www.fullhdfilmizlesene.de/film/satranc-oyuncusu/"))