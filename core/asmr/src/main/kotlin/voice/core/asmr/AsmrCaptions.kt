package voice.core.asmr

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.data.asmrWorkUrl

@SingleIn(AppScope::class)
@Inject
public class AsmrCaptions(http: OkHttpClient) {
  private val client = http.newBuilder().followRedirects(false).build()

  public fun forChapter(book: BookId, chapter: ChapterId): Flow<List<AsmrCue>> = flow {
    val work = book.asmrWorkUrl ?: return@flow
    if (!chapter.value.startsWith("$work/track/") || !chapter.value.endsWith("/media")) return@flow
    val url = (chapter.value.removeSuffix("/media") + "/subtitles").toHttpUrlOrNull() ?: return@flow
    val cues =
      try {
        client.newCall(Request.Builder().url(url).build()).executeAsync().use {
          if (it.isSuccessful) parseAsmrVtt(it.body.string()) else emptyList()
        }
      } catch (_: IOException) {
        emptyList()
      }
    emit(cues)
  }
    .flowOn(Dispatchers.IO)
}
