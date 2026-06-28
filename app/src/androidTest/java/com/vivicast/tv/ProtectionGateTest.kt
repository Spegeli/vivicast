package com.vivicast.tv

import com.vivicast.tv.core.security.PinSecurityState
import com.vivicast.tv.domain.model.Episode
import com.vivicast.tv.domain.model.Movie
import com.vivicast.tv.domain.model.Series
import com.vivicast.tv.feature.settings.ParentalProtectionArea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProtectionGateTest {
    @Test
    fun routeProtectionUsesOnlyEnabledAreas() {
        val state = PinSecurityState(protectMovies = true, protectSeries = true)

        assertEquals(ParentalProtectionArea.Movies, state.protectionAreaForRoute("movies"))
        assertEquals(ParentalProtectionArea.Series, state.protectionAreaForRoute("series"))
        assertNull(state.protectionAreaForRoute("settings"))
        assertNull(state.protectionAreaForRoute("live-tv"))
    }

    @Test
    fun adultProtectionUsesAdultFlagsWithoutGuessing() {
        val state = PinSecurityState(protectAdultContent = true)

        assertEquals(ParentalProtectionArea.AdultContent, state.protectionAreaForMovie(movie(isAdult = true)))
        assertEquals(ParentalProtectionArea.AdultContent, state.protectionAreaForSeries(series(isAdult = true)))
        assertEquals(ParentalProtectionArea.AdultContent, state.protectionAreaForEpisode(episode(isAdult = true)))
        assertNull(state.protectionAreaForMovie(movie(isAdult = false)))
        assertNull(state.protectionAreaForSeries(series(isAdult = false)))
        assertNull(state.protectionAreaForEpisode(episode(isAdult = false)))
    }

    private fun movie(isAdult: Boolean) = Movie(
        id = "movie-1",
        providerId = "provider-1",
        categoryId = null,
        remoteId = "remote-movie-1",
        name = "Movie",
        originalName = null,
        containerExtension = "mp4",
        posterUrl = null,
        backdropUrl = null,
        rating = null,
        year = null,
        genre = null,
        duration = null,
        director = null,
        cast = null,
        plot = null,
        trailerUrl = null,
        addedAt = null,
        isAdult = isAdult,
    )

    private fun series(isAdult: Boolean) = Series(
        id = "series-1",
        providerId = "provider-1",
        categoryId = null,
        remoteId = "remote-series-1",
        name = "Series",
        originalName = null,
        posterUrl = null,
        backdropUrl = null,
        rating = null,
        year = null,
        genre = null,
        director = null,
        cast = null,
        plot = null,
        addedAt = null,
        isAdult = isAdult,
    )

    private fun episode(isAdult: Boolean) = Episode(
        id = "episode-1",
        providerId = "provider-1",
        seriesId = "series-1",
        seasonId = "season-1",
        remoteId = "remote-episode-1",
        episodeNumber = 1,
        seasonNumber = 1,
        name = "Episode",
        plot = null,
        thumbnailUrl = null,
        containerExtension = "mp4",
        duration = null,
        airDate = null,
        isAdult = isAdult,
    )
}
