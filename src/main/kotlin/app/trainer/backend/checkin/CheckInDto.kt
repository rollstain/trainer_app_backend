package app.trainer.backend.checkin

import app.trainer.backend.media.MediaFileResponse
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.util.UUID

private const val MIN_RATING = 1L
private const val MAX_RATING = 5L
private const val NOTES_MAX_LENGTH = 2000
private const val PHOTOS_MAX_COUNT = 6

data class SaveCheckInRequest(
    @field:Positive
    val weightGrams: Int?,
    @field:Positive
    val waistMillimeters: Int?,
    @field:Positive
    val chestMillimeters: Int?,
    @field:Positive
    val hipsMillimeters: Int?,
    @field:Min(MIN_RATING)
    @field:Max(MAX_RATING)
    val wellbeing: Int?,
    @field:Min(MIN_RATING)
    @field:Max(MAX_RATING)
    val sleepQuality: Int?,
    @field:Min(MIN_RATING)
    @field:Max(MAX_RATING)
    val adherence: Int?,
    @field:Size(max = NOTES_MAX_LENGTH)
    val notes: String?,
    @field:Size(max = PHOTOS_MAX_COUNT)
    val photoIds: List<UUID>,
)

data class AwaitingCheckInResponse(
    val checkInId: UUID,
    val clientUserId: UUID,
    val clientDisplayName: String,
    val checkInDate: LocalDate,
)

data class ReviewCheckInRequest(
    @field:Size(max = NOTES_MAX_LENGTH)
    val comment: String?,
)

data class CheckInResponse(
    val id: UUID,
    val clientUserId: UUID,
    val checkInDate: LocalDate,
    val weightGrams: Int?,
    val waistMillimeters: Int?,
    val chestMillimeters: Int?,
    val hipsMillimeters: Int?,
    val wellbeing: Int?,
    val sleepQuality: Int?,
    val adherence: Int?,
    val notes: String?,
    val coachComment: String?,
    val isReviewed: Boolean,
    val photos: List<MediaFileResponse>,
)
