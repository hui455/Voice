package voice.features.asmr

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import voice.core.asmr.AsmrHttpException
import voice.core.asmr.AsmrLibrary
import voice.core.common.rootGraphAs
import voice.core.strings.R as StringsR
import voice.core.ui.CenteredContent
import voice.core.ui.R as UiR
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import voice.navigation.Navigator

@ContributesTo(AppScope::class)
interface AsmrGraph {
  val navigator: Navigator
  val asmrLibrary: AsmrLibrary
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
        AsmrSettings(
          library = graph.asmrLibrary,
          onBack = graph.navigator::goBack,
          onConnected = { graph.navigator.setRoot(Destination.BookOverview) },
        )
      }
    }
}

@Composable
private fun AsmrSettings(library: AsmrLibrary, onBack: () -> Unit, onConnected: () -> Unit) {
  val focus = LocalFocusManager.current
  val scope = rememberCoroutineScope()
  var address by rememberSaveable { mutableStateOf(library.savedAddress()) }
  var syncing by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<ConnectionFailure?>(null) }
  val origin = AsmrLibrary.address(address)
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("ASMR") },
        navigationIcon = {
          IconButton(onClick = onBack) {
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
      Column(
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = horizontalPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedTextField(
          value = address,
          onValueChange = {
            address = it
            error = null
          },
          label = { Text(stringResource(R.string.asmr_server)) },
          placeholder = { Text(AsmrLibrary.DEFAULT_ADDRESS) },
          singleLine = true,
          enabled = !syncing,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
          modifier = Modifier.fillMaxWidth(),
        )
        Button(
          enabled = origin != null && !syncing,
          onClick = {
            if (origin != null) {
              focus.clearFocus()
              syncing = true
              error = null
              scope.launch {
                try {
                  library.connect(origin)
                  onConnected()
                } catch (e: CancellationException) {
                  throw e
                } catch (e: Exception) {
                  error = ConnectionFailure.from(e)
                } finally {
                  syncing = false
                }
              }
            }
          },
        ) {
          Text(stringResource(R.string.asmr_connect))
        }
        if (syncing) Text(stringResource(R.string.asmr_loading))
        error?.let { failure ->
          Text(
            text =
              if (failure.status != null) stringResource(failure.label, failure.status)
              else stringResource(failure.label),
            color = MaterialTheme.colorScheme.error,
          )
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
