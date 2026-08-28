package app.trainer.backend.media

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

private const val DIALOG_STORAGE_PREFIX = "dialogs"
private const val CHECK_IN_STORAGE_PREFIX = "check-ins"
private const val EXERCISE_STORAGE_PREFIX = "exercises"
private const val FORM_CHECK_STORAGE_PREFIX = "form-checks"

@Service
class MediaFileService(
    private val mediaFileRepository: MediaFileRepository,
    private val storage: MediaStorage,
    private val properties: MediaProperties,
    private val clock: Clock,
) {

    @Transactional
    fun prepareUpload(
        uploaderUserId: UUID,
        ownerKind: MediaOwnerKind,
        scopeId: UUID,
        request: PrepareUploadRequest,
    ): PrepareUploadResponse {
        requireAllowedContentType(request.contentType)
        requireAllowedSize(request.sizeBytes)

        val mediaFileId = UUID.randomUUID()
        val presigned = storage.presignUpload(
            storageKey = storageKeyOf(ownerKind = ownerKind, scopeId = scopeId, mediaFileId = mediaFileId),
            contentType = request.contentType,
            lifetime = Duration.ofMinutes(properties.uploadUrlLifetimeMinutes),
        )
        val downloadUrl = storage.presignDownload(
            storageKey = presigned.storageKey,
            lifetime = Duration.ofMinutes(properties.downloadUrlLifetimeMinutes),
        )
        mediaFileRepository.save(
            MediaFileEntity(
                id = mediaFileId,
                ownerKind = ownerKind,
                ownerId = null,
                scopeId = scopeId,
                uploadedByUserId = uploaderUserId,
                storageKey = presigned.storageKey,
                contentType = request.contentType,
                sizeBytes = request.sizeBytes,
                originalName = request.fileName,
                createdAt = Instant.now(clock),
                linkedAt = null,
            )
        )
        return PrepareUploadResponse(
            mediaFileId = mediaFileId,
            uploadUrl = presigned.url,
            downloadUrl = downloadUrl,
        )
    }

    @Transactional
    fun link(
        mediaFileIds: List<UUID>,
        ownerKind: MediaOwnerKind,
        ownerId: UUID,
        scopeId: UUID,
        uploaderUserId: UUID,
    ): List<MediaFileEntity> {
        if (mediaFileIds.isEmpty()) return emptyList()
        val files = mediaFileRepository.findAllById(mediaFileIds)
        if (files.size != mediaFileIds.distinct().size) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Часть файлов не найдена")
        }
        val now = Instant.now(clock)
        files.forEach { file ->
            if (file.ownerId == ownerId) return@forEach
            requireLinkable(
                file = file,
                ownerKind = ownerKind,
                scopeId = scopeId,
                uploaderUserId = uploaderUserId,
            )
            requireUploadMatchesDeclared(file)
            file.ownerId = ownerId
            file.linkedAt = now
        }
        return files
    }

    @Transactional
    fun replaceOwned(
        mediaFileIds: List<UUID>,
        ownerKind: MediaOwnerKind,
        ownerId: UUID,
        scopeId: UUID,
        uploaderUserId: UUID,
    ): List<MediaFileEntity> {
        val kept = link(
            mediaFileIds = mediaFileIds,
            ownerKind = ownerKind,
            ownerId = ownerId,
            scopeId = scopeId,
            uploaderUserId = uploaderUserId,
        )
        val keptIds = kept.map { it.id }.toSet()
        mediaFileRepository
            .findByOwnerKindAndOwnerId(ownerKind = ownerKind, ownerId = ownerId)
            .filterNot { keptIds.contains(it.id) }
            .forEach(::discard)
        return kept
    }

    @Transactional(readOnly = true)
    fun filesOf(ownerKind: MediaOwnerKind, ownerIds: Collection<UUID>): Map<UUID, List<MediaFileResponse>> {
        if (ownerIds.isEmpty()) return emptyMap()
        return mediaFileRepository
            .findByOwnerKindAndOwnerIdIn(ownerKind = ownerKind, ownerIds = ownerIds)
            .groupBy { checkNotNull(it.ownerId) { "Выборка по ownerId вернула файл без владельца" } }
            .mapValues { (_, files) -> files.map(::toResponse) }
    }

    @Transactional(readOnly = true)
    fun freshDownloadUrl(mediaFileId: UUID, ownerKind: MediaOwnerKind, scopeId: UUID): String {
        val file = requireFile(mediaFileId)
        requireSameScope(file = file, ownerKind = ownerKind, scopeId = scopeId)
        return downloadUrlOf(file)
    }

    @Transactional
    fun delete(mediaFileId: UUID, ownerKind: MediaOwnerKind, scopeId: UUID, requestedByUserId: UUID) {
        val file = requireFile(mediaFileId)
        requireSameScope(file = file, ownerKind = ownerKind, scopeId = scopeId)
        if (file.uploadedByUserId != requestedByUserId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Файл загружен другим пользователем")
        }
        runCatching { storage.delete(file.storageKey) }
        mediaFileRepository.delete(file)
    }

    @Transactional
    fun removeOrphans(olderThan: Duration): Int {
        val threshold = Instant.now(clock).minus(olderThan)
        val orphans = mediaFileRepository.findByOwnerIdIsNullAndCreatedAtBefore(threshold)
        orphans.forEach { orphan ->
            runCatching { storage.delete(orphan.storageKey) }
            mediaFileRepository.delete(orphan)
        }
        return orphans.size
    }

    @Transactional(readOnly = true)
    fun findResponse(mediaFileId: UUID): MediaFileResponse? =
        mediaFileRepository.findByIdOrNull(mediaFileId)?.let(::toResponse)

    fun toResponse(file: MediaFileEntity): MediaFileResponse = MediaFileResponse(
        id = file.id,
        contentType = file.contentType,
        sizeBytes = file.sizeBytes,
        originalName = file.originalName,
        downloadUrl = downloadUrlOf(file),
    )

    private fun downloadUrlOf(file: MediaFileEntity): String = storage.presignDownload(
        storageKey = file.storageKey,
        lifetime = Duration.ofMinutes(properties.downloadUrlLifetimeMinutes),
    )

    private fun requireFile(mediaFileId: UUID): MediaFileEntity {
        return mediaFileRepository.findByIdOrNull(mediaFileId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Файл не найден")
    }

    private fun requireSameScope(file: MediaFileEntity, ownerKind: MediaOwnerKind, scopeId: UUID) {
        if (file.ownerKind != ownerKind || file.scopeId != scopeId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Файл принадлежит другому владельцу")
        }
    }

    private fun requireLinkable(
        file: MediaFileEntity,
        ownerKind: MediaOwnerKind,
        scopeId: UUID,
        uploaderUserId: UUID,
    ) {
        if (file.uploadedByUserId != uploaderUserId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Файл загружен другим пользователем")
        }
        requireSameScope(file = file, ownerKind = ownerKind, scopeId = scopeId)
        if (file.ownerId != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Файл уже привязан")
        }
    }

    private fun requireUploadMatchesDeclared(file: MediaFileEntity) {
        val stored = storage.head(file.storageKey)
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Файл не загружен в хранилище")
        if (stored.sizeBytes != file.sizeBytes) {
            rejectUpload(
                file = file,
                status = HttpStatus.PAYLOAD_TOO_LARGE,
                reason = "Размер загруженного файла не совпадает с заявленным: ${stored.sizeBytes}",
            )
        }
        val storedContentType = stored.contentType
        if (storedContentType != null && storedContentType != file.contentType) {
            rejectUpload(
                file = file,
                status = HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                reason = "Тип загруженного файла не совпадает с заявленным: $storedContentType",
            )
        }
    }

    private fun rejectUpload(file: MediaFileEntity, status: HttpStatus, reason: String): Nothing {
        runCatching { storage.delete(file.storageKey) }
        throw ResponseStatusException(status, reason)
    }

    private fun discard(file: MediaFileEntity) {
        runCatching { storage.delete(file.storageKey) }
        mediaFileRepository.delete(file)
    }

    private fun storageKeyOf(ownerKind: MediaOwnerKind, scopeId: UUID, mediaFileId: UUID): String {
        val prefix = when (ownerKind) {
            MediaOwnerKind.DIALOG_MESSAGE -> DIALOG_STORAGE_PREFIX
            MediaOwnerKind.CHECK_IN -> CHECK_IN_STORAGE_PREFIX
            MediaOwnerKind.EXERCISE -> EXERCISE_STORAGE_PREFIX
            MediaOwnerKind.FORM_CHECK -> FORM_CHECK_STORAGE_PREFIX
        }
        return "$prefix/$scopeId/$mediaFileId"
    }

    private fun requireAllowedContentType(contentType: String) {
        if (contentType !in properties.allowedContentTypes) {
            throw ResponseStatusException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Тип файла не поддерживается: $contentType",
            )
        }
    }

    private fun requireAllowedSize(sizeBytes: Long) {
        if (sizeBytes > properties.maxFileSizeBytes) {
            throw ResponseStatusException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "Размер файла больше допустимого: $sizeBytes",
            )
        }
    }
}
