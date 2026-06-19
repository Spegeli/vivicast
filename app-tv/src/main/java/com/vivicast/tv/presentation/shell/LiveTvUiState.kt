package com.vivicast.tv

import android.app.Application
import android.content.pm.ApplicationInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.vivicast.core.domain.EpgNowNext
import com.vivicast.core.model.Channel
import com.vivicast.core.model.Series
import com.vivicast.core.model.Season
import com.vivicast.core.model.RecentChannel
import com.vivicast.core.model.PlaybackState
import com.vivicast.core.model.Movie
import com.vivicast.core.model.FavoriteChannel
import com.vivicast.core.model.EpgSource
import com.vivicast.core.model.Episode
import com.vivicast.core.model.ChannelCategory
import com.vivicast.core.model.EpgProgram
import com.vivicast.core.model.EpisodePlaybackProgress
import com.vivicast.core.model.MoviePlaybackProgress
import com.vivicast.core.model.PlaybackContentType
import com.vivicast.core.model.PlaybackStatus
import com.vivicast.core.model.Playlist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
class LiveTvUiState {
    val selectedChannel = mutableStateOf<Channel?>(null)
    val focusedChannel = mutableStateOf<Channel?>(null)
    val selectedPlaylistId = mutableStateOf<String?>(null)
    val selectedCategoryId = mutableStateOf<String?>(null)
    val selectedSection = mutableStateOf<TvSection>(TvSection.LiveTv)
    val selectedMovieId = mutableStateOf<String?>(null)
    val selectedSeriesId = mutableStateOf<String?>(null)
    val selectedSeasonId = mutableStateOf<String?>(null)
    val selectedEpisodeId = mutableStateOf<String?>(null)
    val selectedSettingsSection = mutableStateOf<SettingsSection>(SettingsSection.Providers)
    val importStatus = mutableStateOf<String>("No playlist imported yet")
    val autoImportAttempted = mutableStateOf<Boolean>(false)
    val navigationFocusRestoreKey = mutableStateOf<Int>(0)
    val navigationVisible = mutableStateOf<Boolean>(false)
    val liveTvFiltersVisible = mutableStateOf<Boolean>(true)
    val showM3uUrlDialog = mutableStateOf<Boolean>(false)
    val showXtreamDialog = mutableStateOf<Boolean>(false)
    val showGlobalEpgDialog = mutableStateOf<Boolean>(false)
    val providerToEdit = mutableStateOf<Playlist?>(null)
    val selectedEpgDetailSourceId = mutableStateOf<String?>(null)
    val settingsFocusRestoreKey = mutableStateOf<Int>(0)
    val importingM3uUrl = mutableStateOf<Boolean>(false)
    val importingXtream = mutableStateOf<Boolean>(false)
    val importingGlobalEpg = mutableStateOf<Boolean>(false)
    val importingEpg = mutableStateOf<Boolean>(false)
    val refreshingProviderId = mutableStateOf<String?>(null)
    val refreshingAllProviders = mutableStateOf<Boolean>(false)
    val epgClockMillis = mutableStateOf<Long>(System.currentTimeMillis())
    val providerEditStatus = mutableStateOf<String?>(null)
    val epgRefreshStatus = mutableStateOf<String?>(null)
    val lastEpgSweepAtMillis = mutableStateOf<Long?>(null)
    val lastEpgSweepReason = mutableStateOf<String?>(null)
    val lastEpgSweepAttempted = mutableStateOf<Int>(0)
    val lastEpgSweepSucceeded = mutableStateOf<Int>(0)
    val lastEpgSweepFailed = mutableStateOf<Int>(0)
    val lastEpgSweepSkipped = mutableStateOf<Int>(0)
    val lastEpgSweepUnconfigured = mutableStateOf<Int>(0)
    val lastEpgSweepNotDue = mutableStateOf<Int>(0)
    val epgSweepCount = mutableStateOf<Int>(0)
    val selectedGuideProgram = mutableStateOf<EpgProgram?>(null)
    val guideWindowOffset = mutableStateOf<Int>(0)
    val favoriteFeedback = mutableStateOf<String?>(null)
    val lastAutoRetriedPlaybackToken = mutableStateOf<String?>(null)
    val lastCountedPlaybackErrorToken = mutableStateOf<String?>(null)
    val lastCountedPlaybackBufferingToken = mutableStateOf<String?>(null)
    val lastCountedPlaybackStartToken = mutableStateOf<String?>(null)
    val lastPlaybackErrorDebug = mutableStateOf<String?>(null)
    val lastPlaybackEventAtMillis = mutableStateOf<Long?>(null)
    val lastPlaybackStartedAtMillis = mutableStateOf<Long?>(null)
    val lastPlaybackPausedAtMillis = mutableStateOf<Long?>(null)
    val lastPlaybackStoppedAtMillis = mutableStateOf<Long?>(null)
    val lastPlaybackAutoRetryAtMillis = mutableStateOf<Long?>(null)
    val lastRecoverablePlaybackErrorAtMillis = mutableStateOf<Long?>(null)
    val lastFatalPlaybackErrorAtMillis = mutableStateOf<Long?>(null)
    val playbackStartCount = mutableStateOf<Int>(0)
    val playbackBufferingCount = mutableStateOf<Int>(0)
    val playbackPauseCount = mutableStateOf<Int>(0)
    val playbackStopCount = mutableStateOf<Int>(0)
    val playbackManualRetryCount = mutableStateOf<Int>(0)
    val playbackAutoRetryCount = mutableStateOf<Int>(0)
    val playbackRecoverableErrorCount = mutableStateOf<Int>(0)
    val playbackFatalErrorCount = mutableStateOf<Int>(0)
    val currentPlaybackErrorStreak = mutableStateOf<Int>(0)
    val worstPlaybackErrorStreak = mutableStateOf<Int>(0)
    val previousSelectedSection = mutableStateOf<TvSection>(TvSection.LiveTv)
    val startupSurfaceHandled = mutableStateOf<Boolean>(false)
    val startupBehaviorHandled = mutableStateOf<Boolean>(false)
    val startupRefreshHandled = mutableStateOf<Boolean>(false)
    val startupEpgRefreshHandled = mutableStateOf<Boolean>(false)
    val refreshingAllEpg = mutableStateOf<Boolean>(false)
    val lastBackPressAtMillis = mutableStateOf<Long>(0L)
}

