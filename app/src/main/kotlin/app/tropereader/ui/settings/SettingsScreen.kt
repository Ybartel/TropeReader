package app.tropereader.ui.settings

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import app.tropereader.domain.Settings
import app.tropereader.domain.SettingsRepository
import app.tropereader.domain.SpoilerMode
import app.tropereader.domain.ThemeMode
import app.tropereader.ui.common.BackButton
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repository: SettingsRepository) : ViewModel() {
    val settings: StateFlow<Settings?> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { BackButton(onBack) }) }) { padding ->
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
