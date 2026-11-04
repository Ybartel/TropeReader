package app.tropereader.data.backup

import app.tropereader.domain.BackupFormatException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The backup file. Soft-deleted records are included so that deletions travel too.
 * Fields may be added later with defaults; [format] changes only for incompatible changes.
 */
@Serializable
data class BackupFile(
    val format: Int,
    val exportedAt: Long,
    val appVersion: String,
    val works: List<WorkRecord> = emptyList(),
    val pages: List<PageRecord> = emptyList(),
    val readStates: List<ReadStateRecord> = emptyList(),
    val positions: List<PositionRecord> = emptyList(),
    val history: List<HistoryRecord> = emptyList(),
) {
    companion object {
        const val FORMAT = 1

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }

        fun encode(file: BackupFile): String = json.encodeToString(serializer(), file)

        fun decode(text: String): BackupFile {
            val file = try {
                json.decodeFromString(serializer(), text)
            } catch (e: SerializationException) {
                throw BackupFormatException("This file is not a TropeReader backup.", e)
            } catch (e: IllegalArgumentException) {
                throw BackupFormatException("This file is not a TropeReader backup.", e)
            }
            if (file.format > FORMAT) throw BackupFormatException("This backup comes from a newer version of TropeReader. Update the app first.")
            return file
        }
    }
}

@Serializable
data class WorkRecord(
    val id: String,
    val canonicalUrl: String,
    val aliasUrls: List<String> = emptyList(),
    val kind: String,
    val title: String,
    val mediaType: String,
    val addedAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class PageRecord(
    val id: String,
    val workId: String,
    val role: String,
    val sourceUrls: List<String>,
    val pageType: String,
    val displayOrder: Int,
    val isHidden: Boolean,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class ReadStateRecord(
    val id: String,
    val pageId: String,
    val tropeKey: String,
    val isRead: Boolean,
    val readAt: Long? = null,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class PositionRecord(
    val id: String,
    val pageId: String,
    val tropeKey: String,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class HistoryRecord(
    val id: String,
    val pageId: String,
    val tropeKey: String,
    val label: String,
    val viewedAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/**
 * Last write wins (data model rules): keeps the [incoming] records that are new here or more recent
 * than the local copy. On a tie the local copy stays.
 */
internal fun <T> newerThanLocal(local: List<T>, incoming: List<T>, id: (T) -> String, updatedAt: (T) -> Long): List<T> {
    val localById = local.associateBy(id)
    return incoming.filter { record -> localById[id(record)]?.let { updatedAt(record) > updatedAt(it) } ?: true }
}
