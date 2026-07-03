package com.vivicast.tv.iptv.m3u

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultM3uContentClassifierTest {
    private val classifier = DefaultM3uContentClassifier()

    // 1. Live .ts
    @Test
    fun liveTsStaysLive() {
        val result = classifier.classify(
            channel("Das Erste HD", "http://example.test/live/user/pass/100.ts"),
        )
        assertEquals(M3uContentClassification.LiveChannel, result)
    }

    // 2. Movie via /movie/ + .mkv
    @Test
    fun movieWithMoviePathAndMkv() {
        val result = classifier.classify(
            channel("DE - Example Movie (2025)", "http://example.test/movie/user/pass/4242.mkv"),
        )
        assertEquals(M3uContentClassification.Movie, result)
    }

    // 3. Movie without /movie/, .mp4 + query string
    @Test
    fun movieWithoutMoviePathAndQueryString() {
        val result = classifier.classify(
            channel("DE - Example Movie (2025)", "http://example.test/vod/4242.mp4?token=abc"),
        )
        assertEquals(M3uContentClassification.Movie, result)
    }

    // 4. Series S01 E02 + .mkv → season/episode/title/episodeTitle
    @Test
    fun seriesSpaceSeparatedWithEpisodeTitle() {
        val result = classifier.classify(
            channel("DE - Example Show (2020) S01 E02 Pilot", "http://example.test/vod/example-show-s01e02.mkv"),
        )
        val info = (result as M3uContentClassification.SeriesEpisode).info
        assertEquals(1, info.seasonNumber)
        assertEquals(2, info.episodeNumber)
        assertEquals("DE - Example Show (2020)", info.seriesTitleRaw)
        assertEquals("Pilot", info.episodeTitle)
    }

    // 5. Series S01E02 + .mp4 without /series/
    @Test
    fun seriesCompactWithoutSeriesPath() {
        val result = classifier.classify(
            channel("DE - Example Show S01E02", "http://example.test/vod/example-show.mp4"),
        )
        val info = (result as M3uContentClassification.SeriesEpisode).info
        assertEquals(1, info.seasonNumber)
        assertEquals(2, info.episodeNumber)
        assertNull(info.episodeTitle)
    }

    // 6. Series S01.E02
    @Test
    fun seriesDotSeparated() {
        val result = classifier.classify(
            channel("DE - Example Show S01.E02", "http://example.test/vod/example-show.mkv"),
        )
        assertTrue(result is M3uContentClassification.SeriesEpisode)
    }

    // 7. Series S01-E02
    @Test
    fun seriesDashSeparated() {
        val result = classifier.classify(
            channel("DE - Example Show S01-E02", "http://example.test/vod/example-show.mkv"),
        )
        assertTrue(result is M3uContentClassification.SeriesEpisode)
    }

    // 8. Optional 1x01 short form
    @Test
    fun seriesShortFormOneXZeroOne() {
        val result = classifier.classify(
            channel("DE - Example Show 1x01", "http://example.test/vod/example-show.mkv"),
        )
        val info = (result as M3uContentClassification.SeriesEpisode).info
        assertEquals(1, info.seasonNumber)
        assertEquals(1, info.episodeNumber)
    }

    // 9. 24/7 VIKINGS + .ts stays Live
    @Test
    fun twentyFourSevenVikingsStaysLive() {
        val result = classifier.classify(
            channel("24/7 VIKINGS", "http://example.test/live/9001.ts"),
        )
        assertEquals(M3uContentClassification.LiveChannel, result)
    }

    // 10. 24/7 FILME 2022 1 + .ts stays Live
    @Test
    fun twentyFourSevenFilmeStaysLive() {
        val result = classifier.classify(
            channel("24/7 FILME 2022 1", "http://example.test/live/9002.ts"),
        )
        assertEquals(M3uContentClassification.LiveChannel, result)
    }

    // 11. Sky Cinema Action HD, group Cinema, .ts stays Live
    @Test
    fun cinemaLiveChannelStaysLive() {
        val result = classifier.classify(
            channel("Sky Cinema Action HD", "http://example.test/live/9003.ts", group = "Cinema"),
        )
        assertEquals(M3uContentClassification.LiveChannel, result)
    }

    // 12. Warner TV Serie HD + .ts stays Live (word "Serie" must not trip series detection)
    @Test
    fun serieWordInLiveTitleStaysLive() {
        val result = classifier.classify(
            channel("Warner TV Serie HD", "http://example.test/live/9004.ts"),
        )
        assertEquals(M3uContentClassification.LiveChannel, result)
    }

    // 13. Cinema group + .mp4 → Movie (VOD extension wins; cinema is neutral)
    @Test
    fun cinemaGroupWithMp4IsMovie() {
        val result = classifier.classify(
            channel("Example Feature", "http://example.test/vod/1234.mp4", group = "Cinema"),
        )
        assertEquals(M3uContentClassification.Movie, result)
    }

    // 14. group-title="Filme" but no VOD extension and no path hint → Live
    @Test
    fun weakMovieGroupHintOnlyStaysLive() {
        val result = classifier.classify(
            channel("Some Channel HD", "http://example.test/stream/5678", group = "Filme"),
        )
        assertEquals(M3uContentClassification.LiveChannel, result)
    }

    // 15. Unknown format, no signals → Live
    @Test
    fun unknownFormatStaysLive() {
        val result = classifier.classify(
            channel("Mystery Channel", "http://example.test/stream/abcdef"),
        )
        assertEquals(M3uContentClassification.LiveChannel, result)
    }

    // 16. Missing/broken URL → Live, no crash
    @Test
    fun brokenOrMissingUrlStaysLiveWithoutCrash() {
        assertEquals(M3uContentClassification.LiveChannel, classifier.classify(channel("Broken", "")))
        assertEquals(M3uContentClassification.LiveChannel, classifier.classify(channel("Weird", "not a url ::: ///")))
    }

    // tvg-name takes precedence over display name as the title source
    @Test
    fun tvgNamePreferredForSeriesTitle() {
        val result = classifier.classify(
            channel(
                name = "Display Fallback",
                streamUrl = "http://example.test/vod/x.mkv",
                tvgName = "Real Show S03E04",
            ),
        )
        val info = (result as M3uContentClassification.SeriesEpisode).info
        assertEquals(3, info.seasonNumber)
        assertEquals(4, info.episodeNumber)
        assertEquals("Real Show", info.seriesTitleRaw)
    }

    private fun channel(
        name: String,
        streamUrl: String,
        group: String? = null,
        tvgName: String? = null,
    ): M3uChannel =
        M3uChannel(
            remoteId = "test-id",
            name = name,
            streamUrl = streamUrl,
            categoryName = group,
            logoUrl = null,
            channelNumber = null,
            tvgId = null,
            tvgName = tvgName,
            isCatchupAvailable = false,
            catchupDays = 0,
            catchupMode = null,
            catchupSource = null,
            rawAttributes = emptyMap(),
        )
}
