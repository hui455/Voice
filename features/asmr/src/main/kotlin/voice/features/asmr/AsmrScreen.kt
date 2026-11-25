package voice.features.asmr

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import okhttp3.OkHttpClient
import voice.core.common.rootGraphAs
import voice.core.playback.PlayerController
import voice.core.strings.R as StringsR
import voice.core.ui.CenteredContent
import voice.core.ui.R as UiR
import voice.core.ui.bookText
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import voice.navigation.Navigator

@ContributesTo(AppScope::class)
interface AsmrGraph {
  val navigator: Navigator
  val okHttpClient: OkHttpClient
  val playerController: PlayerController
}

@BindingContainer
@ContributesTo(AppScope::class)
object AsmrProvider {
  @Provides
  @IntoSet
  fun asmrNavEntryProvider(): NavEntryProvider<*> =
    NavEntryProvider<Destination.Asmr> { key ->
      NavEntry(key) {
        val graph = rootGraphAs<AsmrGraph>()
        AsmrScreen(graph.okHttpClient, graph.playerController::pause, graph.navigator::goBack)
      }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AsmrScreen(client: OkHttpClient, pauseBook: () -> Unit, onBack: () -> Unit) {
  val context = LocalContext.current
  val focusManager = LocalFocusManager.current
  val preferences = remember(context) { context.getSharedPreferences("asmr", Context.MODE_PRIVATE) }
  var connectedAddress by rememberSaveable {
    mutableStateOf(preferences.getString("address", null))
  }
  var address by rememberSaveable { mutableStateOf(connectedAddress.orEmpty()) }
  var loadAttempt by remember { mutableStateOf(0) }
  var works by remember { mutableStateOf<List<AsmrWork>?>(null) }
  var work by remember { mutableStateOf<AsmrWork?>(null) }
  var track by remember { mutableStateOf<AsmrTrack?>(null) }
  var language by rememberSaveable { mutableStateOf(SubtitleLanguage.Chinese) }
  var connectionError by remember { mutableStateOf<ConnectionFailure?>(null) }
  val listState = rememberLazyListState()
  val parsedAddress = AsmrServer.address(address)
  val server =
    remember(connectedAddress, client) {
      connectedAddress?.let(AsmrServer::address)?.let { AsmrServer(it, client) }
    }

  LaunchedEffect(server, loadAttempt) {
    if (server != null) {
      works =
        try {
          server.library().also {
            preferences.edit().putString("address", connectedAddress).apply()
          }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          connectionError = ConnectionFailure.from(e)
          null
        }
    }
  }
  LaunchedEffect(work) { listState.scrollToItem(0) }

  val currentWork = work
  val currentTrack = track
  val goUp = {
    when {
      currentTrack != null -> track = null
      currentWork != null -> work = null
      else -> onBack()
    }
  }
  BackHandler(enabled = currentWork != null, onBack = goUp)
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("ASMR") },
        navigationIcon = {
          IconButton(onClick = goUp) {
            Icon(
              painterResource(UiR.drawable.ic_arrow_back),
              stringResource(StringsR.string.common_action_back),
            )
          }
        },
      )
    },
  ) { insets ->
    CenteredContent(Modifier.padding(insets)) { horizontalPadding ->
      if (currentWork != null && currentTrack != null && server != null) {
        Column(
          Modifier.verticalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding, vertical = 16.dp),
        ) {
          AsmrPlayback(
            server,
            currentWork,
            currentTrack,
            language,
            { language = it },
            pauseBook,
          )
        }
        return@CenteredContent
      }
      LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (currentWork == null) {
          if (works == null) {
            item {
              Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                  value = address,
                  onValueChange = { address = it },
                  label = { Text(stringResource(R.string.asmr_server)) },
                  placeholder = { Text("http://192.168.1.10:8899") },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                  modifier = Modifier.fillMaxWidth(),
                )
                Button(
                  enabled = parsedAddress != null && (server == null || connectionError != null),
                  onClick = {
                    focusManager.clearFocus()
                    connectionError = null
                    connectedAddress = parsedAddress?.toString()
                    loadAttempt++
                  },
                ) {
                  Text(stringResource(R.string.asmr_connect))
                }
                val failure = connectionError
                if (failure != null) {
                  Text(
                    if (failure.status != null) stringResource(failure.label, failure.status)
                    else stringResource(failure.label),
                    color = MaterialTheme.colorScheme.error,
                  )
                } else if (server != null) {
                  Text(stringResource(R.string.asmr_loading))
                }
              }
            }
          } else {
            item {
              TextButton(
                onClick = {
                  works = null
                  connectedAddress = null
                  preferences.edit().remove("address").apply()
                },
              ) {
                Text(stringResource(R.string.asmr_change_server))
              }
            }
            if (works.orEmpty().isEmpty()) {
              item { Text(stringResource(R.string.asmr_empty)) }
            }
            items(works.orEmpty(), key = { it.id }) { entry ->
              AsmrRow(entry.displayTitle?.takeIf { it.isNotBlank() } ?: entry.title) {
                work = entry
              }
            }
          }
        } else {
          item {
            Text(
              currentWork.displayTitle?.takeIf { it.isNotBlank() } ?: currentWork.title,
              style = MaterialTheme.typography.titleLarge.bookText(),
            )
          }
          items(currentWork.tracks, key = { it.id }) { entry ->
            AsmrRow(entry.title?.takeIf { it.isNotBlank() } ?: entry.name) {
              track = entry
            }
          }
        }
      }
    }
  }
}

private data class ConnectionFailure(@StringRes val label: Int, val status: Int? = null) {
  companion object {
    fun from(error: Exception): ConnectionFailure =
      when (error) {
        is AsmrHttpException -> ConnectionFailure(R.string.asmr_http_error, error.status)
        is SerializationException -> ConnectionFailure(R.string.asmr_format_error)
        is SocketTimeoutException -> ConnectionFailure(R.string.asmr_timeout)
        is UnknownHostException -> ConnectionFailure(R.string.asmr_unknown_host)
        is SSLException -> ConnectionFailure(R.string.asmr_tls_error)
        is SocketException,
        is SecurityException -> ConnectionFailure(R.string.asmr_network_error)
        is IOException -> ConnectionFailure(R.string.asmr_read_error)
        else -> ConnectionFailure(R.string.asmr_connect_error)
      }
  }
}

@Composable
private fun AsmrRow(title: String, onClick: () -> Unit) {
  ListItem(modifier = Modifier.clickable(onClick = onClick)) {
    Text(title, style = MaterialTheme.typography.bodyLarge.bookText())
  }
}
