// ! https://codeberg.org/cloudstream/cloudstream-extensions-multilingual/src/branch/master/FreeTVProvider/src/main/kotlin/com/lagradost/FreeTVProvider.kt

package com.keyiflerolsun

import CanliTvResult
import ChannelResult
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.api.Log
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.ResponseBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

class CanliTV : MainAPI() {
    override var mainUrl = "https://core-api.kablowebtv.com/api/channels"
    override var name = "CanliTV"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Live)
    private var kanallar = mutableListOf<ChannelResult>()

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {


        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36",
            "Referer" to "https://tvheryerde.com",
            "Origin" to "https://tvheryerde.com",
            "Cache-Control" to "max-age=0",
            "Connection" to "keep-alive",
            "Accept-Encoding" to "gzip",
            "Authorization" to "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJlbnYiOiJMSVZFIiwiaXBiIjoiMCIsImNnZCI6IjA5M2Q3MjBhLTUwMmMtNDFlZC1hODBmLTJiODE2OTg0ZmI5NSIsImNzaCI6IlRSS1NUIiwiZGN0IjoiM0VGNzUiLCJkaSI6IjNkY2I2NmJiLTZhNjctNDIwYi1iN2MyLTg3ZGQ2MGFjNDNjZCIsInNnZCI6Ijk1N2U3NjliLWJiYjgtNGFiMC05NzYwLTgyM2UyMGE1OWFlMyIsInNwZ2QiOiIxNTY0ODUxZC1hY2ViLTQyZWUtYjkwZi04MGFlNTczOGEyM2EiLCJpY2giOiIwIiwiaWRtIjoiMCIsImlhIjoiOjpmZmZmOjEwLjAuMC42IiwiYXB2IjoiMS4wLjAiLCJhYm4iOiIxMDAwIiwibmJmIjoxNzQwOTY1ODI4LCJleHAiOjE3NDA5NjU4ODgsImlhdCI6MTc0MDk2NTgyOH0.8SgjsXtcwvmCYpV0W2T-rwwUiiFKpluz8crfpRhDv9A"
        )

        val response = app.get(mainUrl, headers = headers)
        val decompressedBody = decompressGzip(response.body)
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val result: CanliTvResult = objectMapper.readValue(decompressedBody)
        val liste = mutableListOf<HomePageList>()
        kanallar.addAll(result.dataResult.allChannels!!)
        val map = kanallar.groupBy { it.categories!![0].name }["Ulusal"]?.map { kanal ->
            val streamurl = kanal.streamData!!.hlsStreamUrl.toString()
            val channelname = kanal.name.toString()
            val posterurl = kanal.primaryLogo.toString()
            val chGroup = kanal.categories!![0].name.toString()
            val nation = "tr"

            newLiveSearchResponse(
                channelname,
                streamurl,
                type = TvType.Live
            ) {
                this.posterUrl = posterurl
                this.lang = nation
            }
        }
        val newHomePageResponse = newHomePageResponse(
            kanallar.groupBy{ it.categories!![0].name }.filter{ it.key != "Bilgilendirme" }.map { group ->
                val title = group.key ?: ""
                val show = group.value.map { kanal ->
                    val streamurl = kanal.streamData!!.hlsStreamUrl.toString()
                    val channelname = kanal.name.toString()
                    val posterurl = kanal.primaryLogo.toString()
                    val chGroup = kanal.categories!![0].name.toString()
                    val nation = "tr"

                    newLiveSearchResponse(
                        channelname,
                        streamurl,
                        type = TvType.Live
                    ) {
                        this.posterUrl = posterurl
                        this.lang = nation
                    }
                }
                HomePageList(title, show, isHorizontalImages = true)
            },
            hasNext = false
        )
        return newHomePageResponse
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return kanallar.filter { it.name.toString().lowercase().contains(query.lowercase()) }
            .map { kanal ->
                val streamurl = kanal.streamData!!.hlsStreamUrl.toString()
                val channelname = kanal.name.toString()
                val posterurl = kanal.primaryLogo.toString()
                val chGroup = kanal.categories!![0].name.toString()
                val nation = "tr"

                newLiveSearchResponse(
                    channelname,
                    streamurl,
                    //LoadData(streamurl, channelname, posterurl, chGroup, nation).toJson(),
                    type = TvType.Live
                ) {
                    this.posterUrl = posterurl
                    this.lang = nation
                }

            }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        Log.d("CTV", "Kanallar -> ${kanallar.size}")
        Log.d("CTV", "URL -> $url")
        Log.d("CTV", "*********************************")
        var loadData: LoadData
        kanallar.forEach { it ->
            Log.d("CTV", "Each -> ${it.streamData?.hlsStreamUrl.toString()}")
            if (url == it.streamData?.hlsStreamUrl.toString()) {
                Log.d("CTV", "Eşitmi -> Eşit")
                loadData = LoadData(
                    it.streamData!!.hlsStreamUrl.toString(),
                    it.name.toString(),
                    it.primaryLogo.toString(),
                    it.categories!![0].name.toString(),
                    "tr"
                )
                Log.d("CTV", "loadData -> $loadData")
                return newLiveStreamLoadResponse(
                    it.name!!,
                    it.streamData.hlsStreamUrl.toString(),
                    url
                ) {
                    this.posterUrl = loadData.poster
                    this.plot = "tr"
                    this.tags = listOf(loadData.group, loadData.nation)
                }
            }
        }
        kanallar.filter { it.streamData?.hlsStreamUrl.toString() == url }.forEach {
            Log.d("CTV", "Filter")
            loadData = LoadData(
                it.streamData!!.hlsStreamUrl.toString(),
                it.name.toString(),
                it.primaryLogo.toString(),
                it.categories!![0].name.toString(),
                "tr"
            )
            Log.d("CTV", "loadData -> $loadData")
            return newLiveStreamLoadResponse(
                it.name!!,
                it.streamData.hlsStreamUrl.toString(),
                url
            ) {
                this.posterUrl = loadData.poster
                this.plot = "tr"
                this.tags = listOf(loadData.group, loadData.nation)
            }
        }
        Log.d("CTV", "Eşitmi -> Eşit değil")

        return newLiveStreamLoadResponse("", "", url) {
            this.posterUrl = ""
            this.plot = "tr"
            this.tags = listOf("loadData.group", "loadData.nation")
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("CTV", "data -> $data")
        kanallar.forEach { it ->
            if (data == it.streamData!!.hlsStreamUrl.toString()) {
                callback.invoke(
                    newExtractorLink(
                        source = it.name.toString() + " - HLS",
                        name = it.name.toString() + " - HLS",
                        url = it.streamData.hlsStreamUrl.toString(),
                        ExtractorLinkType.M3U8
                    ) {
                        this.quality = Qualities.Unknown.value
                    })
                callback.invoke(
                    newExtractorLink(
                        source = it.name.toString() + " - DASH",
                        name = it.name.toString() + " - DASH",
                        url = it.streamData.dashStreamUrl.toString(),
                        ExtractorLinkType.DASH
                    ) {
                        this.quality = Qualities.Unknown.value
                    })
            }
        }
        return true
    }

    data class LoadData(
        val url: String,
        val title: String,
        val poster: String,
        val group: String,
        val nation: String
    )

    private fun decompressGzip(body: ResponseBody): String {
        GZIPInputStream(body.byteStream()).use { gzipStream ->
            InputStreamReader(gzipStream).use { reader ->
                BufferedReader(reader).use { bufferedReader ->
                    return bufferedReader.readText()
                }
            }
        }
    }

}

