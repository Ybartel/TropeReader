package app.tropereader.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.tropereader.domain.HistoryItem
import app.tropereader.domain.HistoryRepository
import app.tropereader.domain.WorkPlanner
import app.tropereader.domain.groupByDay
import app.tropereader.ui.common.BackButton
import app.tropereader.ui.common.relativeDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(private val history: HistoryRepository) : ViewModel() {
    val items: StateFlow<List<HistoryItem>?> = history.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun clear() {
        viewModelScope.launch { history.clear() }
    }
}

/** F8.2: card views grouped by day; tapping one reopens the reader on that card. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit, onOpenCard: (pageId: String, cardKey: String) -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (!items.isNullOrEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Clear") }
                },
            )
        },
    ) { padding ->
        val list = items ?: return@Scaffold
        if (list.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Cards you read will appear here.", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            list.groupByDay().forEach { (day, entries) ->
                item(key = day.toString()) {
                    Text(
                        relativeDay(entries.first().viewedAt).replaceFirstChar { it.uppercase() },
                        Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                items(entries, key = { it.id }) { entry ->
                    ListItem(
                        modifier = Modifier.clickable { onOpenCard(entry.pageId, entry.tropeKey) },
                        headlineContent = { Text(entry.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(context(entry), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingContent = { Text(time(entry.viewedAt), style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = { Text("Reading progress is not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clear()
                    confirmClear = false
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

private fun context(entry: HistoryItem): String =
    if (entry.pageRole == WorkPlanner.MAIN_ROLE) entry.workTitle else "${entry.workTitle} · ${entry.pageRole}"

private fun time(epochMillis: Long): String =
    LocalTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
