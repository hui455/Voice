package voice.features.asmr

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import voice.core.strings.R as StringsR
import voice.core.ui.bookText

@OptIn(UnstableApi::class)
@Composable
internal fun AsmrPlayback(
  server: AsmrServer,
  client: OkHttpClient,
  work: AsmrWork,
  track: AsmrTrack,
  preferredLanguage: SubtitleLanguage,
  onLanguage: (SubtitleLanguage) -> Unit,
  pauseBook: () -> Unit,
) {
  val context = LocalContext.current.applicationContext
  val player =
    remember(client, context, work.id, track.id) {
      ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(client)))
        .setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build(),
          true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
    }
  var position by remember(player) { mutableStateOf(0L) }
  var duration by remember(player) { mutableStateOf(0L) }
  var dragging by remember(player) { mutableStateOf<Float?>(null) }
  var playing by remember(player) { mutableStateOf(false) }
  var buffering by remember(player) { mutableStateOf(true) }
  var audioError by remember(player) { mutableStateOf(false) }

  DisposableEffect(player) {
    val listener =
      object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
          playing =
            player.playWhenReady &&
              player.playbackState != Player.STATE_IDLE &&
              player.playbackState != Player.STATE_ENDED
          buffering = player.playbackState == Player.STATE_BUFFERING
          audioError = player.playerError != null
          duration = player.duration.coerceAtLeast(0L)
          position = player.currentPosition
        }
      }
    player.addListener(listener)
    pauseBook()
    player.setMediaItem(MediaItem.fromUri(Uri.parse(server.audio(work, track).toString())))
    player.prepare()
    player.play()
    onDispose {
      player.removeListener(listener)
      player.release()
    }
  }
  LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.pause() }
  LaunchedEffect(player, playing) {
    while (playing) {
      position = player.currentPosition
      delay(100)
    }
  }

  val languages = SubtitleLanguage.entries.filter(track::has)
  val activeLanguage = preferredLanguage.takeIf { it in languages } ?: languages.firstOrNull()
  var cues by
    remember(server, work.id, track.id, activeLanguage) { mutableStateOf<List<AsmrCue>?>(null) }
  var subtitleError by remember(server, work.id, track.id, activeLanguage) { mutableStateOf(false) }
  var subtitleAttempt by remember { mutableStateOf(0) }
  LaunchedEffect(server, work.id, track.id, activeLanguage, subtitleAttempt) {
    if (activeLanguage != null) {
      cues =
        try {
          server.subtitles(work, track, activeLanguage)
        } catch (e: CancellationException) {
          throw e
        } catch (_: Exception) {
          subtitleError = true
          null
        }
    }
  }
  val cue = cues?.at(position)

  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      track.title?.takeIf { it.isNotBlank() } ?: track.name,
      style = MaterialTheme.typography.titleLarge.bookText(),
    )
    val slider = rememberSliderState()
    SideEffect {
      slider.value =
        dragging ?: position.coerceIn(0L, duration).toFloat() / duration.coerceAtLeast(1L)
    }
    Slider(
      state = slider,
      onValueChange = { dragging = it },
      onValueChangeFinished = {
        dragging?.let {
          position = (it * duration).toLong()
          player.seekTo(position)
        }
        dragging = null
      },
      enabled = duration > 0L && !audioError,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(formatTime(position), style = MaterialTheme.typography.labelMedium)
      Text(formatTime(duration), style = MaterialTheme.typography.labelMedium)
    }
    Button(
      onClick = {
        if (playing) {
          player.pause()
        } else {
          pauseBook()
          if (audioError) player.prepare()
          if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
          player.play()
        }
      },
    ) {
      Text(
        stringResource(
          when {
            audioError -> R.string.asmr_retry
            playing -> StringsR.string.playback_action_pause
            else -> StringsR.string.playback_action_play
          },
        ),
      )
    }
    if (audioError) {
      Text(stringResource(R.string.asmr_audio_error), color = MaterialTheme.colorScheme.error)
    } else if (buffering) {
      Text(stringResource(R.string.asmr_loading))
    }
    if (languages.isNotEmpty()) {
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        languages.forEach { language ->
          FilterChip(
            selected = language == activeLanguage,
            onClick = { onLanguage(language) },
            label = {
              Text(
                stringResource(
                  when (language) {
                    SubtitleLanguage.Chinese -> R.string.asmr_chinese
                    SubtitleLanguage.Japanese -> R.string.asmr_japanese
                  },
                ),
              )
            },
          )
        }
      }
    }
    val subtitle =
      when {
        activeLanguage == null || cues?.isEmpty() == true ->
          stringResource(R.string.asmr_no_subtitles)
        subtitleError -> stringResource(R.string.asmr_subtitle_error)
        cues == null -> stringResource(R.string.asmr_loading)
        else -> cue?.text
      }
    val subtitleStyle = MaterialTheme.typography.bodyLarge
    val subtitleLocale =
      when (activeLanguage) {
        SubtitleLanguage.Chinese -> "zh-CN"
        SubtitleLanguage.Japanese -> "ja"
        null -> null
      }
    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = MaterialTheme.shapes.extraLarge,
      color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
      Column(Modifier.padding(16.dp)) {
        Text(
          subtitle.orEmpty(),
          minLines = 2,
          style =
            if (cue != null && subtitleLocale != null) {
              subtitleStyle.copy(localeList = LocaleList(subtitleLocale))
            } else {
              subtitleStyle
            },
        )
        if (subtitleError) {
          TextButton(
            onClick = {
              subtitleError = false
              subtitleAttempt++
            },
          ) {
            Text(stringResource(R.string.asmr_retry))
          }
        }
      }
    }
  }
}

private fun formatTime(position: Long): String {
  val seconds = position / 1000
  return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
