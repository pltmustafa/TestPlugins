# ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

from Kekik.cli       import konsol
from cloudscraper    import CloudScraper
from parsel          import Selector
from re              import search
from Kekik.Sifreleme import AESManager
from json            import loads

oturum = CloudScraper()
oturum.headers.update({"User-Agent": "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"})
# oturum.cookies.update({"wordpress_logged_in_7e0a80686bffd7035218d41e8240d65f": "keyiflerolsun|1704461004|TJh8nQRYrqZ9xlAyO7rO5QgiqTQiw7op8I6LKkSvytX|0dc654883fad5f0301df32e6465aa676a3f235288dd9c8a73260d9c8a20b19ae"})

oturum.post(
    url  = "https://www.dizimom.im/wp-login.php",
    data = {
        "log"         : "keyiflerolsun",
        "pwd"         : "12345",
        "rememberme"  : "forever",
        "redirect_to" : "https://www.dizimom.im",
    }
)

istek  = oturum.get("https://www.dizimom.im/modern-dogu-masallari-1-sezon-7-bolum-izle/")
konsol.print(istek.url)

secici = Selector(istek.text)
iframe = secici.css("div.video p iframe::attr(src)").get()
konsol.print(iframe)

oturum.headers.update({"Referer": "https://www.dizimom.im/"})
i_source = oturum.get(iframe).text

be_player = search(r"bePlayer\('([^']+)',\s*'(\{[^\}]+\})'\);", i_source).groups()
konsol.print(be_player)

be_player_pass = be_player[0]
be_player_data = be_player[1]

encrypted = AESManager.decrypt(be_player_data, be_player_pass).replace("\\", "")
konsol.print(loads(encrypted))