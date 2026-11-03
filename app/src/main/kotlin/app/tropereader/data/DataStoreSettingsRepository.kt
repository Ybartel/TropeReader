package app.tropereader.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.tropereader.domain.Settings
import app.tropereader.domain.SettingsRepository
import app.tropereader.domain.SpoilerMode
import app.tropereader.domain.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class DataStoreSettingsRepository @Inject constructor(@ApplicationContext private val context: Context) : SettingsRepository {
    override val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            spoilerMode = prefs.enum(SPOILER_MODE, SpoilerMode.HIDE),
            textScale = (prefs[TEXT_SCALE] ?: 1f).coerceIn(Settings.MIN_TEXT_SCALE, Settings.MAX_TEXT_SCALE),
            themeMode = prefs.enum(THEME_MODE, ThemeMode.SYSTEM),
        )
    }.distinctUntilChanged()

    override suspend fun setSpoilerMode(mode: SpoilerMode) {
        context.dataStore.edit { it[SPOILER_MODE] = mode.name }
    }

    override suspend fun setTextScale(scale: Float) {
        context.dataStore.edit { it[TEXT_SCALE] = scale.coerceIn(Settings.MIN_TEXT_SCALE, Settings.MAX_TEXT_SCALE) }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[THEME_MODE] = mode.name }
    }

    /** Unknown stored values (e.g. from a newer version) fall back to the default. */
    private inline fun <reified E : Enum<E>> Preferences.enum(key: Preferences.Key<String>, default: E): E =
        this[key]?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

    private companion object {
        val SPOILER_MODE = stringPreferencesKey("spoiler_mode")
        val TEXT_SCALE = floatPreferencesKey("text_scale")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
