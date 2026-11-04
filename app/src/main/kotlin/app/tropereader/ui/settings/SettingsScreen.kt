package app.tropereader.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.tropereader.BuildConfig
import app.tropereader.domain.BackupFormatException
import app.tropereader.domain.BackupRepository
import app.tropereader.domain.Settings
import app.tropereader.domain.SettingsRepository
import app.tropereader.domain.SpoilerMode
import app.tropereader.domain.ThemeMode
import app.tropereader.ui.common.BackButton
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SettingsRepository,
    private val backup: BackupRepository,
) : ViewModel() {
    val settings: StateFlow<Settings?> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    /** Writes the backup to the file the user picked. */
    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            val message = try {
                val json = backup.exportJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) }
                        ?: throw IOException("Cannot open the file")
                }
                "Reading progress exported"
            } catch (e: IOException) {
                "Export failed: ${e.message}"
            }
            _messages.send(message)
        }
    }

    /** Merges the picked backup into this phone's state; newest change wins. */
    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val message = try {
                val json = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                        ?: throw IOException("Cannot open the file")
                }
                val summary = backup.importJson(json)
                if (summary.total == 0) {
                    "Nothing to import: this phone is already up to date"
                } else {
                    "Imported " + listOf(
                        count(summary.works, "work"),
                        count(summary.readStates, "read card"),
                        count(summary.positions, "position"),
                        count(summary.history, "history entry", "history entries"),
                    ).joinToString(", ")
                }
            } catch (e: BackupFormatException) {
                e.message ?: "Not a TropeReader backup"
            } catch (e: IOException) {
                "Import failed: ${e.message}"
            }
            _messages.send(message)
        }
    }

    fun setSpoilerMode(mode: SpoilerMode) {
        viewModelScope.launch { repository.setSpoilerMode(mode) }
    }

    fun setTextScale(scale: Float) {
        viewModelScope.launch { repository.setTextScale(scale) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }
}

private fun count(n: Int, one: String, many: String = one + "s") = "$n ${if (n == 1) one else many}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::exportTo)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importFrom)
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { BackButton(onBack) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val current = settings ?: return@Scaffold
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Section("Spoilers") {
                Choices(
                    options = listOf(SpoilerMode.HIDE to "Hide until tapped", SpoilerMode.SHOW to "Always show"),
                    selected = current.spoilerMode,
                    onSelect = viewModel::setSpoilerMode,
                )
            }
            Section("Text size") { TextSizeSetting(current.textScale, viewModel::setTextScale) }
            Section("Theme") {
                Choices(
                    options = listOf(ThemeMode.SYSTEM to "Follow system", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark"),
                    selected = current.themeMode,
                    onSelect = viewModel::setThemeMode,
                )
            }
            // The only backup while Android auto-backup is off; also the future sync format.
            Section("Backup") {
                Action("Export reading progress", "Works, read cards, positions and history, as a JSON file") {
                    exportLauncher.launch("tropereader-backup-${LocalDate.now()}.json")
                }
                Action("Import a backup", "Merged with this phone: the most recent change wins") {
                    importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
                }
            }
            // F11.1: the version identifies which build is installed, since updates are sideloaded.
            Section("About") {
                Text(
                    "TropeReader ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    content()
    Spacer(Modifier.height(8.dp))
    HorizontalDivider()
}

@Composable
private fun Action(title: String, description: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
    )
}

@Composable
private fun <T> Choices(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.selectableGroup()) {
        options.forEach { (value, label) ->
            Row(
                Modifier.fillMaxWidth()
                    .selectable(selected = value == selected, onClick = { onSelect(value) }, role = Role.RadioButton)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = value == selected, onClick = null)
                Text(label, Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** The slider applies on release; the sample text below shows the size while dragging. */
@Composable
private fun TextSizeSetting(scale: Float, onChange: (Float) -> Unit) {
    var dragged by remember(scale) { mutableFloatStateOf(scale) }
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text("${(dragged * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = dragged,
            onValueChange = { dragged = it },
            onValueChangeFinished = { onChange(dragged) },
            valueRange = Settings.MIN_TEXT_SCALE..Settings.MAX_TEXT_SCALE,
            steps = 12,
        )
        Text(
            "Absurdly Sharp Blade: the axe falls and cuts into the asphalt far enough to stay upright.",
            style = MaterialTheme.typography.bodyLarge.let { it.copy(fontSize = it.fontSize * (dragged / scale)) },
        )
    }
}
