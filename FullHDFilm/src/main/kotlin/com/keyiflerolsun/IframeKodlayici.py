# ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

from Kekik.cli    import konsol
from cloudscraper import CloudScraper
import re, base64

class IframeKodlayici:
    @staticmethod
    def ters_cevir(metin: str) -> str:
        return metin[::-1]

    @staticmethod
    def base64_coz(encoded_string: str) -> str:
        return base64.b64decode(encoded_string).decode("utf-8")

    @staticmethod
    def iframe_parse(html_icerik: str) -> list:
        iframe_pattern = r'<iframe[^>]+src=["\']([^"\']+)["\'][^>]*>'
        return re.search(iframe_pattern, html_icerik).group(1)

    def iframe_coz(self, veri: str) -> str:
        if not veri.startswith("PGltZyB3aWR0aD0iMTAwJSIgaGVpZ2"):
            veri = self.ters_cevir("BSZtFmcmlGP") + veri

        iframe = self.base64_coz(veri)
        return self.iframe_parse(iframe)


oturum    = CloudScraper()
istek     = oturum.get("https://fullhdfilm.pro/american-sports-story-izle/")
partlar   = re.findall(r"pdata\[\'(.*?)'\] = \'(.*?)\';", istek.text)
kodlayici = IframeKodlayici()

for parca_id, parca_veri in partlar:
    iframe = kodlayici.iframe_coz(parca_veri)
    konsol.log(f"{parca_id:<6} » {iframe}")