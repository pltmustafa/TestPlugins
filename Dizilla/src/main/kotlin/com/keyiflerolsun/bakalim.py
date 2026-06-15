# ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

from Kekik.cli    import konsol
# from cloudscraper import CloudScraper as Session
from httpx        import Client as Session
from parsel       import Selector

mainUrl = "https://dizilla.to"
oturum  = Session()

istek   = oturum.get(mainUrl)
secici  = Selector(istek.text)



oturum.cookies.clear()
oturum.headers.update({
    "Accept"           : "application/json, text/plain, */*",
    "X-Requested-With" : "XMLHttpRequest",
    "Referer"          : f"{mainUrl}/",
})
oturum.cookies.set("showAllDaFull", "true", "30")
istek = oturum.post(
    f"{mainUrl}/api/bg/searchContent?searchterm=",
    data = {
        "searchterm=" : "aslan"
    }
)
try:
    konsol.print(istek.json()["data"]["result"])
except KeyError:
    konsol.print(istek.json())
