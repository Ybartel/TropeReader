package app.tropereader.domain

import app.tropereader.parser.TextSegment

/**
 * Text-to-speech, defined now so read-aloud can be added without reworking the reader (F10.5).
 * No implementation in v1; the target is Android `TextToSpeech` behind a Media3 session.
 */
interface SpeechEngine {
    /** Speaks one card's segments; spoilers are skipped or announced per the spoiler setting (F6.2). */
    fun speak(segments: List<TextSegment>, utteranceId: String)

    fun pause()

    fun resume()

    fun stop()

    /** Called when an utterance finishes, so the player can advance the [ReadingCursor]. */
    fun setOnUtteranceDone(listener: (utteranceId: String) -> Unit)
}
