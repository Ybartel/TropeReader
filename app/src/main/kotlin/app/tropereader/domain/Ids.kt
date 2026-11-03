package app.tropereader.domain

import java.util.UUID

/**
 * Deterministic ids, derived from natural keys: two devices that add the same work or read the
 * same card produce the same row ids, so a future sync merges them instead of duplicating.
 */
object Ids {
    /** A work is identified by the wiki path it was first added with; later renames become aliases (F11.3). */
    fun work(firstPath: String): String = uuid("work:${firstPath.lowercase()}")

    /** A logical page of a work, e.g. `Main`, `Tropes`, `YMMV`, `Recap/S1E1`. */
    fun page(workId: String, role: String): String = uuid("page:$workId:$role")

    fun readState(pageId: String, tropeKey: String): String = uuid("read:$pageId:$tropeKey")

    fun position(pageId: String): String = uuid("position:$pageId")

    private fun uuid(name: String): String = UUID.nameUUIDFromBytes(name.toByteArray()).toString()
}
