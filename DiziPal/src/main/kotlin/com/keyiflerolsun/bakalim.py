# ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

from Kekik.cli    import konsol
from cloudscraper import CloudScraper
from parsel       import Selector
from re           import findall

oturum  = CloudScraper()

mainUrl = "https://dizipal951.com"
pageUrl = f"{mainUrl}/diziler?kelime=&durum=&tur=1&type=&siralama="
istek   = oturum.get(pageUrl)
secici  = Selector(istek.text)

def icerik_ver(secici: Selector):
    son_date = ""

    for icerik in secici.css("article.type2 ul li"):
        konsol.print(icerik.css("span.title::text").get())
        konsol.print(icerik.css("a::attr(href)").get())
        konsol.print(icerik.css("img::attr(src)").get())
        konsol.print(icerik.css("a::attr(data-date)").get())
        konsol.print("\n")
        son_date = icerik.css("a::attr(data-date)").get()

    return son_date

son_date = icerik_ver(secici)

def devam_ver(son_date) -> str:
    istek = oturum.post(
        url  = f"{mainUrl}/api/load-series",
        data = {
            "date"     : son_date,
            "tur"      : findall(r"tur=([\d]+)&", pageUrl)[0],
            "durum"    : "",
            "kelime"   : "",
            "type"     : "",
            "siralama" : ""
        }
    )
    veri = istek.json()
    if not veri.get("html"):
        return ""

    devam_html = "<article class='type2'><ul>" + veri["html"] + "</ul></article>"

    return icerik_ver(Selector(devam_html))

while son_date:
    son_date = devam_ver(son_date)
