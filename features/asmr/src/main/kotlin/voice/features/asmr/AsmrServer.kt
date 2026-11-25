package voice.features.asmr

import java.io.IOException
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

internal class AsmrServer(private val origin: HttpUrl, private val client: OkHttpClient) {
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
      if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
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
