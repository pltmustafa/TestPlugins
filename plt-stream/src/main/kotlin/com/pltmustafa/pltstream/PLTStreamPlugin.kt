package com.pltmustafa.pltstream

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.pltmustafa.pltstream.hosts.*

@CloudstreamPlugin
class PLTStreamPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(PLTStream())
        registerExtractorAPI(RapidVid())
        registerExtractorAPI(TRsTX())
        registerExtractorAPI(VidMoxy())
        registerExtractorAPI(Sobreatsesuyp())
        registerExtractorAPI(TurboImgz())
        registerExtractorAPI(TurkeyPlayer())
        registerExtractorAPI(ContentX())
        registerExtractorAPI(YildizKisaFilm())
        registerExtractorAPI(HDPlayerSystem())
        registerExtractorAPI(HDMomPlayer())
        registerExtractorAPI(PeaceMakerst())
        registerExtractorAPI(VideoSeyred())
        registerExtractorAPI(VidRameExtractor())
        registerExtractorAPI(SetPlay())
        registerExtractorAPI(FastPlay())
        registerExtractorAPI(DizipalPlayer())
        registerExtractorAPI(DzenRu())
    }
}
