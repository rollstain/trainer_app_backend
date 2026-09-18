package app.trainer.backend.schedule

import app.trainer.backend.coach.CoachEntity
import app.trainer.backend.coach.CoachRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

internal fun CoachRepository.requireCoach(coachUserId: UUID): CoachEntity = findByUserId(coachUserId)
    ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Пользователь не тренер")

internal fun requireSlotOwnedBy(slot: TrainingSlotEntity, coach: CoachEntity) {
    if (slot.coachId != coach.id) {
        throw ResponseStatusException(HttpStatus.FORBIDDEN, "Слот другого тренера")
    }
}

internal fun slotNotFound(): Nothing {
    throw ResponseStatusException(HttpStatus.NOT_FOUND, "Слот не найден")
}

internal fun TrainingSlotRepository.hasOverlap(
    coachId: UUID,
    startsAt: Instant,
    durationMinutes: Int,
    excludedSlotId: UUID? = null,
): Boolean {
    val endsAt = startsAt.plus(durationMinutes.toLong(), ChronoUnit.MINUTES)
    return findOverlappingSlotIds(coachId = coachId, startsAt = startsAt, endsAt = endsAt)
        .any { it != excludedSlotId }
}

internal fun isBeforeChangeDeadline(startsAt: Instant, cancellationWindowHours: Int, now: Instant): Boolean {
    val deadline = startsAt.minus(cancellationWindowHours.toLong(), ChronoUnit.HOURS)
    return now.isBefore(deadline)
}
