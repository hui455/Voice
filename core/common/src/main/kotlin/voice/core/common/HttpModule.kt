package voice.core.common

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import okhttp3.OkHttpClient

@BindingContainer
@ContributesTo(AppScope::class)
object HttpModule {

  // Clients that need their own settings derive from this one with newBuilder(), so all of them
  // share one connection pool and its threads
  @Provides
  @SingleIn(AppScope::class)
  fun okHttpClient(appInfoProvider: AppInfoProvider): OkHttpClient {
    // Wikimedia blocks the default user agent of OkHttp and asks for a version and a contact
    val userAgent = "Voice/${appInfoProvider.versionName} (https://github.com/PaulWoitaschek/Voice)"
    return OkHttpClient.Builder()
      .proxySelector(VoiceProxySelector)
      .addInterceptor { chain ->
        chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
      }
      .build()
  }
}

private object VoiceProxySelector : ProxySelector() {
  override fun select(uri: URI): List<Proxy> {
    if (uri.path.orEmpty().startsWith("/api/voice/") && uri.host.orEmpty().isLocalHost()) {
      return listOf(Proxy.NO_PROXY)
    }
    return ProxySelector.getDefault()?.select(uri) ?: listOf(Proxy.NO_PROXY)
  }

  override fun connectFailed(uri: URI, address: SocketAddress, error: IOException) {
    ProxySelector.getDefault()?.connectFailed(uri, address, error)
  }
}

private fun String.isLocalHost(): Boolean {
  val host = trimEnd('.').removeSurrounding("[", "]")
  if (host == "localhost" || host.endsWith(".local")) return true
  if (':' in host) {
    val prefix = host.substringBefore(':').toIntOrNull(16) ?: return host == "::1"
    return prefix in 0xfc00..0xfdff || prefix in 0xfe80..0xfebf
  }
  val parts = host.split('.').mapNotNull(String::toIntOrNull)
  if (parts.size != 4 || parts.any { it !in 0..255 }) return false
  val (first, second) = parts
  return first == 10 ||
    first == 127 ||
    (first == 172 && second in 16..31) ||
    (first == 192 && second == 168) ||
    (first == 169 && second == 254)
}
