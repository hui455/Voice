package voice.features.asmr

import java.io.IOException
import java.net.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync

@Serializable
internal data class AsmrWork(
  @SerialName("w") val id: Int,
  val title: String,
  @SerialName("meta_title") val displayTitle: String? = null,
  val tracks: List<AsmrTrack>,
)

@Serializable
internal data class AsmrTrack(
  @SerialName("i") val id: Int,
  val name: String,
  val title: String? = null,
  val zh: String? = null,
  val ja: String? = null,
) {
  fun has(language: SubtitleLanguage): Boolean =
    when (language) {
      SubtitleLanguage.Chinese -> !zh.isNullOrBlank()
      SubtitleLanguage.Japanese -> !ja.isNullOrBlank()
    }
}

internal enum class SubtitleLanguage(val code: String) {
  Chinese("zh"),
  Japanese("ja"),
}

internal class AsmrHttpException(val status: Int) : IOException("HTTP $status")

internal class AsmrServer(private val origin: HttpUrl, client: OkHttpClient) {
  val client =
    client
      .newBuilder()
      .followRedirects(false)
      .apply { if (origin.host.isLanHost()) proxy(Proxy.NO_PROXY) }
      .build()

  suspend fun library(): List<AsmrWork> =
    withContext(Dispatchers.IO) {
      json.decodeFromString<Library>(get(url("api", "library"))).works
    }

  suspend fun subtitles(
    work: AsmrWork,
    track: AsmrTrack,
    language: SubtitleLanguage,
  ): List<AsmrCue> =
    withContext(Dispatchers.IO) {
      val url =
        url("subs", work.id.toString(), track.id.toString())
          .newBuilder()
          .addQueryParameter("lang", language.code)
          .build()
      parseAsmrVtt(get(url))
    }

  fun audio(work: AsmrWork, track: AsmrTrack): HttpUrl =
    url("media", work.id.toString(), track.id.toString())

  private fun url(vararg segments: String): HttpUrl =
    origin.newBuilder().apply { segments.forEach { addPathSegment(it) } }.build()

  private suspend fun get(url: HttpUrl): String =
    client.newCall(Request.Builder().url(url).build()).executeAsync().use { response ->
      if (!response.isSuccessful) throw AsmrHttpException(response.code)
      response.body.string()
    }

  @Serializable private data class Library(val works: List<AsmrWork>)

  companion object {
    private val json = Json { ignoreUnknownKeys = true }

    fun address(text: String): HttpUrl? =
      text.trim().toHttpUrlOrNull()?.takeIf {
        it.username.isEmpty() &&
          it.password.isEmpty() &&
          it.encodedPath == "/" &&
          it.query == null &&
          it.fragment == null
      }
  }
}

private fun String.isLanHost(): Boolean {
  val hostname = trimEnd('.')
  if (hostname == "localhost" || hostname.endsWith(".local")) return true
  if (':' in hostname) {
    val prefix = hostname.substringBefore(':').toIntOrNull(16) ?: return hostname == "::1"
    return prefix in 0xfc00..0xfdff || prefix in 0xfe80..0xfebf
  }
  val parts = hostname.split('.')
  if (parts.size != 4 || parts.any { (it.toIntOrNull() ?: -1) !in 0..255 }) return false
  val first = parts[0].toInt()
  val second = parts[1].toInt()
  return first == 10 ||
    first == 127 ||
    (first == 172 && second in 16..31) ||
    (first == 192 && second == 168) ||
    (first == 169 && second == 254)
}
