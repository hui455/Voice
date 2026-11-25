package voice.features.asmr

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Test

class AsmrServerTest {
  @Test
  fun `loads a work and reads both subtitle languages at the same position`() = runTest {
    MockWebServer().use { server ->
      server.start()
      server.enqueue(
        MockResponse(
          body =
            """
            {"works":[{"w":7,"title":"Demo","meta_title":"Rain","tracks":[
              {"i":2,"name":"rain.wav","zh":"rain.wav.vtt","ja":"rain.wav.ja.vtt","size":128},
              {"i":3,"name":"wind.wav","zh":null,"ja":"wind.wav.ja.vtt"}
            ]}]}
            """
              .trimIndent(),
        ),
      )
      server.enqueue(MockResponse(body = "WEBVTT\n\n00:00.500 --> 00:03.000\n下雨了。"))
      server.enqueue(MockResponse(body = "WEBVTT\n\n00:00.000 --> 00:03.000\n雨が降っています。"))
      val asmr = AsmrServer(server.url("/"), OkHttpClient())
      val work = asmr.library().single()
      val (rain, wind) = work.tracks
      assertEquals("Rain", work.displayTitle)
      assertFalse(wind.has(SubtitleLanguage.Chinese))
      assertTrue(wind.has(SubtitleLanguage.Japanese))
      assertEquals("/media/7/2", asmr.audio(work, rain).encodedPath)
      assertEquals("下雨了。", asmr.subtitles(work, rain, SubtitleLanguage.Chinese).at(1_000)?.text)
      assertEquals(
        "雨が降っています。",
        asmr.subtitles(work, rain, SubtitleLanguage.Japanese).at(1_000)?.text,
      )
      assertEquals("/api/library", server.takeRequest().target)
      assertEquals("/subs/7/2?lang=zh", server.takeRequest().target)
      assertEquals("/subs/7/2?lang=ja", server.takeRequest().target)
    }
  }

  @Test
  fun `keeps long Japanese subtitles intact and holds the last cue during a short gap`() {
    val cues =
      parseAsmrVtt(
        """
        WEBVTT

        cue-long
        00:00:03.000 --> 00:00:30.000 align:start
        <v Demo>雨の音を聞いています。

        00:00:34.000 --> 00:00:35.000
        おやすみなさい。
        """
          .trimIndent(),
      )
    assertNull(cues.at(2_999))
    assertEquals("雨の音を聞いています。", cues.at(20_000)?.text)
    assertEquals("雨の音を聞いています。", cues.at(33_999)?.text)
    assertEquals("おやすみなさい。", cues.at(34_000)?.text)
    assertEquals("おやすみなさい。", cues.at(44_999)?.text)
    assertNull(cues.at(45_000))
  }
}
