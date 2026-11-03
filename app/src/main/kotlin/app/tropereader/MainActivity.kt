package app.tropereader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tropereader.domain.Settings
import app.tropereader.domain.SettingsRepository
import app.tropereader.ui.common.LocalSpoilerMode
import app.tropereader.ui.AppNavHost
import app.tropereader.ui.theme.TropeReaderTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var settingsRepository: SettingsRepository

    /** Text shared from another app ("Share" from the browser, F1.1), until the library has used it. */
    private val sharedText = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) sharedText.value = intent.sharedText()
        enableEdgeToEdge()
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = Settings())
            TropeReaderTheme(settings.themeMode) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    // Text size setting on top of the system font scale (accessibility).
                    LocalDensity provides Density(density.density, density.fontScale * settings.textScale),
                    LocalSpoilerMode provides settings.spoilerMode,
                ) {
                    AppNavHost(sharedText = sharedText.value, onSharedTextConsumed = { sharedText.value = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.sharedText()?.let { sharedText.value = it }
    }

    private fun Intent.sharedText(): String? =
        if (action == Intent.ACTION_SEND && type == "text/plain") getStringExtra(Intent.EXTRA_TEXT) else null
}
