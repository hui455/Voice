package voice.core.asmr

public data class AsmrCue(val startMs: Long, val endMs: Long, val text: String)

private val time = Regex("""(?:(\d+):)?(\d{2}):(\d{2})[.,](\d{3})""")
private val timing = Regex("""^(\S+)\s+-->\s+(\S+)(?:\s.*)?$""")
private val blankLine = Regex("""\n[ \t]*\n""")
private val styleTag = Regex("""</?(?:b|i|u|ruby|rt|c|v|lang)(?:[ .][^>]*)?>""")

internal fun parseAsmrVtt(vtt: String): List<AsmrCue> =
  vtt
    .removePrefix("\uFEFF")
    .replace("\r\n", "\n")
    .replace('\r', '\n')
    .split(blankLine)
    .mapNotNull { block ->
      val lines = block.trim().lines()
      if (
        lines.first() == "STYLE" ||
          lines.first() == "REGION" ||
          lines.first() == "NOTE" ||
          lines.first().startsWith("NOTE ")
      )
        return@mapNotNull null
      val index = lines.indexOfFirst { timing.matches(it.trim()) }
      if (index < 0) return@mapNotNull null
      val match = timing.matchEntire(lines[index].trim()) ?: return@mapNotNull null
      val start = timestamp(match.groupValues[1]) ?: return@mapNotNull null
      val end = timestamp(match.groupValues[2]) ?: return@mapNotNull null
      val text = lines.drop(index + 1).joinToString("\n").replace(styleTag, "").trim()
      if (end <= start || text.isEmpty()) null else AsmrCue(start, end, text)
    }
    .sortedBy { it.startMs }

public fun List<AsmrCue>.at(positionMs: Long): AsmrCue? {
  val found = binarySearchBy(positionMs) { it.startMs }
  val index = if (found < 0) -found - 2 else found
  return getOrNull(index)?.takeIf { positionMs - it.endMs < 10_000 }
}

private fun timestamp(value: String): Long? {
  val match = time.matchEntire(value) ?: return null
  val hours = match.groupValues[1].ifEmpty { "0" }.toIntOrNull() ?: return null
  val minutes = match.groupValues[2].toInt()
  val seconds = match.groupValues[3].toInt()
  if (minutes >= 60 || seconds >= 60) return null
  return hours * 3_600_000L + minutes * 60_000L + seconds * 1000L + match.groupValues[4].toLong()
}
