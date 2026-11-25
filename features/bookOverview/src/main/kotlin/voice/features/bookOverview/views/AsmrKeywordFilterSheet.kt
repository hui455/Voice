package voice.features.bookOverview.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import voice.features.bookOverview.R

@Composable
internal fun AsmrKeywordFilterSheet(
  activeKeywords: List<String>,
  suggestions: List<String>,
  onApply: (List<String>) -> Unit,
  onDismiss: () -> Unit,
) {
  var selected by rememberSaveable { mutableStateOf(ArrayList(activeKeywords)) }
  var input by rememberSaveable { mutableStateOf("") }
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .imePadding()
          .verticalScroll(rememberScrollState())
          .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        stringResource(R.string.asmr_filter_keywords),
        style = MaterialTheme.typography.titleLarge,
      )
      OutlinedTextField(
        value = input,
        onValueChange = { input = it },
        label = { Text(stringResource(R.string.asmr_search_hint)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
      )
      val available = (selected + suggestions).distinct()
      if (available.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          available.forEach { keyword ->
            FilterChip(
              selected = keyword in selected,
              onClick = {
                selected =
                  ArrayList(if (keyword in selected) selected - keyword else selected + keyword)
              },
              label = { Text(keyword, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
          }
        }
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(
          onClick = { onApply(emptyList()) },
          enabled = selected.isNotEmpty() || input.isNotBlank() || activeKeywords.isNotEmpty(),
        ) {
          Text(stringResource(R.string.asmr_filter_clear))
        }
        Button(
          onClick = {
            val typed = input.trim().takeIf(String::isNotEmpty)
            onApply((selected + listOfNotNull(typed)).distinctBy(String::lowercase))
          },
        ) {
          Text(stringResource(R.string.asmr_filter_apply))
        }
      }
    }
  }
}
