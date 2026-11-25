package voice.core.asmr

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Test
import voice.core.data.BookId
import voice.core.data.ChapterId

class AsmrCaptionsTest {
  @Test
  fun `loads Chinese captions by the Voice chapter id and keeps a long cue intact`() = runTest {
    MockWebServer().use { server ->
      server.start()
      val workPath = "/api/voice/work/test-work"
      val trackPath = "$workPath/track/test-track"
      server.enqueue(
        MockResponse(
          body =
            """
            WEBVTT

            00:00:01.000 --> 00:00:30.000
            下雨了。

            00:00:34.000 --> 00:00:35.000
            晚安。
            """
              .trimIndent(),
        ),
      )
      val book = BookId("asmr:${server.url(workPath)}")
      val chapter = ChapterId(server.url("$trackPath/media").toString())
      val captions = AsmrCaptions(OkHttpClient()).forChapter(book, chapter).first()
      assertEquals("$trackPath/subtitles", server.takeRequest().target)
      assertNull(captions.at(999))
      assertEquals("下雨了。", captions.at(20_000)?.text)
      assertEquals("下雨了。", captions.at(33_999)?.text)
      assertEquals("晚安。", captions.at(34_000)?.text)
      assertNull(captions.at(45_000))
    }
  }
}
