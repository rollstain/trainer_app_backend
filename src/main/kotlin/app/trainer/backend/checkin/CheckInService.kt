package app.trainer.backend.checkin

import app.trainer.backend.coach.CoachClientRepository
import app.trainer.backend.coach.CoachClientStatus
import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.media.MediaFileResponse
import app.trainer.backend.media.MediaFileService
import app.trainer.backend.media.MediaOwnerKind
import app.trainer.backend.media.PrepareUploadRequest
import app.trainer.backend.media.PrepareUploadResponse
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class CheckInService(
    private val checkInRepository: CheckInRepository,
    private val coachRepository: CoachRepository,
    private val coachClientRepository: CoachClientRepository,
    private val mediaFileService: MediaFileService,
    private val clock: Clock,
) {

    @Transactional
    fun save(clientUserId: UUID, checkInDate: LocalDate, request: SaveCheckInRequest): CheckInResponse {
        val now = Instant.now(clock)
        val checkIn = checkInRepository.findByClientUserIdAndCheckInDate(
            clientUserId = clientUserId,
            checkInDate = checkInDate,
        ) ?: checkInRepository.save(
            CheckInEntity(
                id = UUID.randomUUID(),
                clientUserId = clientUserId,
                checkInDate = checkInDate,
                weightGrams = null,
                waistMillimeters = null,
                chestMillimeters = null,
                hipsMillimeters = null,
                wellbeing = null,
                sleepQuality = null,
                notes = null,
                createdAt = now,
                updatedAt = now,
            )
        )
        checkIn.weightGrams = request.weightGrams
        checkIn.waistMillimeters = request.waistMillimeters
        checkIn.chestMillimeters = request.chestMillimeters
        checkIn.hipsMillimeters = request.hipsMillimeters
        checkIn.wellbeing = request.wellbeing
        checkIn.sleepQuality = request.sleepQuality
        checkIn.notes = request.notes?.trim()?.ifEmpty { null }
        checkIn.updatedAt = now

        val photos = mediaFileService.replaceOwned(
            mediaFileIds = request.photoIds,
            ownerKind = MediaOwnerKind.CHECK_IN,
            ownerId = checkIn.id,
            scopeId = clientUserId,
            uploaderUserId = clientUserId,
        )
        return toResponse(checkIn = checkIn, photos = photos.map(mediaFileService::toResponse))
    }

    @Transactional
    fun preparePhotoUpload(clientUserId: UUID, request: PrepareUploadRequest): PrepareUploadResponse {
        return mediaFileService.prepareUpload(
            uploaderUserId = clientUserId,
            ownerKind = MediaOwnerKind.CHECK_IN,
            scopeId = clientUserId,
            request = request,
        )
    }

    @Transactional
    fun deletePhoto(clientUserId: UUID, photoId: UUID) {
        mediaFileService.delete(
            mediaFileId = photoId,
            ownerKind = MediaOwnerKind.CHECK_IN,
            scopeId = clientUserId,
            requestedByUserId = clientUserId,
        )
    }

    @Transactional(readOnly = true)
    fun ownCheckIns(clientUserId: UUID, from: LocalDate, to: LocalDate): List<CheckInResponse> {
        return checkInsOf(clientUserId = clientUserId, from = from, to = to)
    }

    @Transactional(readOnly = true)
    fun clientCheckIns(
        coachUserId: UUID,
        clientUserId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<CheckInResponse> {
        requireOwnClient(coachUserId = coachUserId, clientUserId = clientUserId)
        return checkInsOf(clientUserId = clientUserId, from = from, to = to)
    }

    private fun checkInsOf(clientUserId: UUID, from: LocalDate, to: LocalDate): List<CheckInResponse> {
        val checkIns = checkInRepository.findByClientUserIdAndCheckInDateBetweenOrderByCheckInDateDesc(
            clientUserId = clientUserId,
            from = from,
            to = to,
        )
        if (checkIns.isEmpty()) return emptyList()
        val photosByCheckIn = mediaFileService.filesOf(
            ownerKind = MediaOwnerKind.CHECK_IN,
            ownerIds = checkIns.map { it.id },
        )
        return checkIns.map { checkIn ->
            toResponse(checkIn = checkIn, photos = photosByCheckIn[checkIn.id].orEmpty())
        }
    }

    private fun requireOwnClient(coachUserId: UUID, clientUserId: UUID) {
        val coach = coachRepository.findByUserId(coachUserId)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Пользователь не тренер")
        val link = coachClientRepository.findByCoachIdAndUserId(coachId = coach.id, userId = clientUserId)
        if (link == null || link.status != CoachClientStatus.ACTIVE) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Это не ваш подопечный")
        }
    }

    private fun toResponse(
        checkIn: CheckInEntity,
        photos: List<MediaFileResponse>,
    ): CheckInResponse = CheckInResponse(
        id = checkIn.id,
        clientUserId = checkIn.clientUserId,
        checkInDate = checkIn.checkInDate,
        weightGrams = checkIn.weightGrams,
        waistMillimeters = checkIn.waistMillimeters,
        chestMillimeters = checkIn.chestMillimeters,
        hipsMillimeters = checkIn.hipsMillimeters,
        wellbeing = checkIn.wellbeing,
        sleepQuality = checkIn.sleepQuality,
        notes = checkIn.notes,
        photos = photos,
    )
}
