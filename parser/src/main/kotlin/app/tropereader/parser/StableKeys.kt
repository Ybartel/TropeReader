package app.tropereader.parser

import java.security.MessageDigest
import java.text.Normalizer

/**
 * Stable card keys (F3.6).
 *
 * - Trope with a link: its wiki path, e.g. `Main/AbortedArc` (several links joined with `+`).
 * - Trope without a link: `n:` + normalized name.
 * - Entry with no detectable name: `e:` + hash of its normalized text.
 * - Sub-bullet: parent key + `#` + hash of its own normalized text (deeper bullets excluded).
 * - Free text between lists: `x:` + hash.
 * - Repeated key on the same page: `~2`, `~3`… suffix, in page order.
 *
 * Normalization ignores case, accents, punctuation and spacing, so cosmetic edits keep the key.
 */
object StableKeys {
    private const val HASH_LENGTH = 12

    fun forLinks(paths: List<String>): String = paths.joinToString("+")

    fun forName(name: String): String = "n:" + normalizeName(name)

    fun forText(text: String): String = "e:" + hash(text)

    fun forExample(parentKey: String, text: String): String = parentKey + "#" + hash(text)

    /** Free text between lists. */
    fun forBlock(text: String): String = "x:" + hash(text)

    fun hash(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(normalizeText(text).toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(HASH_LENGTH)
    }

    fun normalizeText(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    fun normalizeName(name: String): String = normalizeText(name).replace(" ", "")
}
