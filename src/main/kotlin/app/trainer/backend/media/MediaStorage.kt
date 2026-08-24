package app.trainer.backend.media

import java.time.Duration

data class PresignedUpload(
    val url: String,
    val storageKey: String,
)

data class StoredObject(
    val sizeBytes: Long,
    val contentType: String?,
)

interface MediaStorage {

    fun presignUpload(storageKey: String, contentType: String, lifetime: Duration): PresignedUpload

    fun presignDownload(storageKey: String, lifetime: Duration): String

    fun head(storageKey: String): StoredObject?

    fun delete(storageKey: String)
}
