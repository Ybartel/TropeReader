package app.tropereader.domain

/**
 * Backup of the user state as JSON (data model rules): the only backup while Android auto-backup is
 * off, and the format a future Google Drive sync will exchange. Page content is not included.
 */
interface BackupRepository {
    suspend fun exportJson(): String

    /**
     * Merges a backup into the local state, entity by entity: the most recent `updatedAt` wins.
     * @throws BackupFormatException when the file is not a backup this version can read.
     */
    suspend fun importJson(json: String): ImportSummary
}

/** How many records the import changed locally, per kind. */
data class ImportSummary(val works: Int, val pages: Int, val readStates: Int, val positions: Int, val history: Int) {
    val total: Int get() = works + pages + readStates + positions + history
}

class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)
