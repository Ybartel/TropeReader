package app.tropereader.domain

import kotlinx.coroutines.flow.Flow

/** Spoilers: hidden until tapped, or always shown (F6.2). */
enum class SpoilerMode { HIDE, SHOW }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val spoilerMode: SpoilerMode = SpoilerMode.HIDE,
    /** Multiplies the system font scale (accessibility requirement). */
    val textScale: Float = 1f,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
) {
    companion object {
        const val MIN_TEXT_SCALE = 0.85f
        const val MAX_TEXT_SCALE = 1.5f
    }
}

/** Device preferences. Not part of the synced user state. */
interface SettingsRepository {
    val settings: Flow<Settings>

    suspend fun setSpoilerMode(mode: SpoilerMode)

    suspend fun setTextScale(scale: Float)

    suspend fun setThemeMode(mode: ThemeMode)
}
