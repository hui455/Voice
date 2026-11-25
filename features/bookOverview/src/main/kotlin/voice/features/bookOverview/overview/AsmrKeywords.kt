package voice.features.bookOverview.overview

import voice.core.data.Book
import voice.core.data.isAsmr

private val ActorSeparators = Regex("[、・,，/／;；&＆\n]+")

internal fun suggestedAsmrKeywords(books: List<Book>): List<String> =
  books
    .asSequence()
    .filter { it.id.isAsmr }
    .flatMap { book ->
      book.author
        .orEmpty()
        .split(ActorSeparators)
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
    }
    .groupingBy { it }
    .eachCount()
    .entries
    .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
    .take(6)
    .map { it.key }

internal fun Book.matchesAsmrKeywords(keywords: List<String>): Boolean = keywords.all { keyword ->
  name.contains(keyword, ignoreCase = true) || author?.contains(keyword, ignoreCase = true) == true
}
