package voice.core.asmr

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.BookMetadata
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.isAsmr
import voice.core.data.repo.BookContentRepo
import voice.core.data.repo.BookRepository
import voice.core.data.repo.ChapterRepo

@SingleIn(AppScope::class)
@Inject
public class AsmrLibrary(
  context: Context,
  http: OkHttpClient,
  private val contents: BookContentRepo,
  private val chapters: ChapterRepo,
  private val books: BookRepository,
  private val clock: Clock,
) {
  private val preferences = context.getSharedPreferences("asmr", Context.MODE_PRIVATE)
  private val client =
    http.newBuilder().followRedirects(false).readTimeout(30, TimeUnit.SECONDS).build()
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val sync = Mutex()
  private val _state = MutableStateFlow(AsmrSyncState.Loading)
  public val state: StateFlow<AsmrSyncState> = _state.asStateFlow()

  public fun savedAddress(): String = preferences.getString("address", null) ?: DEFAULT_ADDRESS

  public fun refreshInBackground() {
    scope.launch {
      sync.withLock {
        val origin = address(savedAddress()) ?: return@withLock
        try {
          val otherIds =
            contents.all().filter { it.isActive && !it.id.isAsmr }.map { it.id }.toSet()
          books.updateBooks(otherIds) { it.copy(isActive = false) }
          update(origin)
        } catch (_: IOException) {} catch (_: SerializationException) {}
      }
    }
  }

  public suspend fun connect(origin: HttpUrl): Unit =
    withContext(Dispatchers.IO) {
      sync.withLock {
        update(origin)
        preferences.edit().putString("address", origin.toString()).apply()
      }
    }

  private suspend fun update(origin: HttpUrl) {
    _state.value = AsmrSyncState.Loading
    try {
      importBooks(origin)
      _state.value = AsmrSyncState.Ready
    } finally {
      if (_state.value == AsmrSyncState.Loading) _state.value = AsmrSyncState.Unavailable
    }
  }

  private suspend fun importBooks(origin: HttpUrl) {
    val api = origin.newBuilder().addPathSegments("api/voice/").build()
    val libraryUrl = api.newBuilder().addPathSegment("library").build()
    val snapshot =
      client.newCall(Request.Builder().url(libraryUrl).build()).executeAsync().use {
        if (!it.isSuccessful) throw AsmrHttpException(it.code)
        json.decodeFromString<AsmrCatalog>(it.body.string())
      }
    val imported = snapshot.works.mapNotNull { it.toVoiceBook(api) }.associateBy { it.content.id }
    val existing = contents.all().filter { it.id.isAsmr }.associateBy { it.id }
    val nextChapters = imported.values.flatMap { it.chapters }
    val previousChapters =
      nextChapters.map { it.id }.chunked(500).flatMap { chapters.get(it) }.associateBy { it.id }
    withContext(NonCancellable) {
      chapters.put(nextChapters.filter { previousChapters[it.id] != it })
      contents.put(imported.filterKeys { it !in existing }.values.map { it.content })
      books.updateBooks(existing.keys) { current ->
        imported[current.id]?.merge(current) ?: current.copy(isActive = false)
      }
    }
  }

  private fun AsmrWork.toVoiceBook(api: HttpUrl): ImportedBook? {
    if (!stableKey.matches(id)) return null
    val workUrl = api.newBuilder().addPathSegment("work").addPathSegment(id).build()
    val bookId = BookId("asmr:$workUrl")
    val nativeChapters =
      tracks
        .filter { it.durationMs > 0 && stableKey.matches(it.id) }
        .map {
          val media =
            workUrl
              .newBuilder()
              .addPathSegment("track")
              .addPathSegment(it.id)
              .addPathSegment("media")
              .build()
          Chapter(
            id = ChapterId(media.toString()),
            name = it.title,
            duration = it.durationMs,
            fileLastModified = Instant.fromEpochMilliseconds(it.modifiedMs),
            fileSize = it.size,
            markData = emptyList(),
          )
        }
        .distinctBy { it.id }
    if (nativeChapters.isEmpty()) return null
    val content =
      BookContent(
        id = bookId,
        playbackSpeed = 1F,
        skipSilence = false,
        isActive = true,
        lastPlayedAt = Instant.fromEpochMilliseconds(0),
        addedAt = clock.now(),
        chapters = nativeChapters.map { it.id },
        currentChapter = nativeChapters.first().id,
        positionInChapter = 0,
        gain = 0F,
        metadata = BookMetadata(title, author, null, null, "ASMR", null, null, null, null, null),
      )
    return ImportedBook(content, nativeChapters)
  }

  public companion object {
    public const val DEFAULT_ADDRESS: String = "http://192.168.5.103:8899"
    private val stableKey = Regex("[0-9a-f]{64}")
    private val json = Json { ignoreUnknownKeys = true }

    public fun address(raw: String): HttpUrl? =
      raw.trim().toHttpUrlOrNull()?.takeIf {
        it.username.isEmpty() &&
          it.password.isEmpty() &&
          it.encodedPath == "/" &&
          it.query == null &&
          it.fragment == null
      }
  }
}

public enum class AsmrSyncState {
  Loading,
  Ready,
  Unavailable,
}

private data class ImportedBook(val content: BookContent, val chapters: List<Chapter>) {
  fun merge(stored: BookContent): BookContent {
    val existingChapter = chapters.firstOrNull { it.id == stored.currentChapter }
    return stored.copy(
      isActive = true,
      metadata = content.metadata,
      chapters = content.chapters,
      currentChapter = existingChapter?.id ?: content.currentChapter,
      positionInChapter =
        existingChapter?.let { stored.positionInChapter.coerceAtMost(it.duration) } ?: 0L,
    )
  }
}

public class AsmrHttpException(public val status: Int) : IOException("HTTP $status")

@Serializable private data class AsmrCatalog(val works: List<AsmrWork>)

@Serializable
private data class AsmrWork(
  val id: String,
  val title: String,
  val author: String? = null,
  val tracks: List<AsmrTrack>,
)

@Serializable
private data class AsmrTrack(
  val id: String,
  val title: String,
  val durationMs: Long,
  val modifiedMs: Long,
  val size: Long,
)
