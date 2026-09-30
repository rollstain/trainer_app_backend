package app.trainer.backend.traininglog

import app.trainer.backend.coach.CoachEntity
import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.config.EXTRA_ROW_TO_DETECT_NEXT_PAGE
import app.trainer.backend.config.Page
import app.trainer.backend.config.PageCursor
import app.trainer.backend.config.decodeCursor
import app.trainer.backend.config.encodeCursor
import app.trainer.backend.config.pageSizeOf
import app.trainer.backend.user.UserRepository
import java.time.Clock
import java.time.Instant
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

private const val TRAINING_FEED_PER_PAGE = 20

@Service
class TrainingFeedService(
    private val coachRepository: CoachRepository,
    private val entryRepository: TrainingLogEntryRepository,
    private val setRepository: TrainingLogSetRepository,
    private val exerciseRepository: ExerciseRepository,
    private val userRepository: UserRepository,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun feed(coachUserId: UUID, unseenOnly: Boolean, limit: Int?, after: String?): Page<TrainingFeedItemResponse> {
        val coach = requireCoach(coachUserId)
        val pageSize = pageSizeOf(limit) ?: TRAINING_FEED_PER_PAGE
        val cursor = decodeCursor(after)
        val fetched = entryRepository.findFeedPage(
            coachId = coach.id,
            unseenOnly = unseenOnly,
            afterUpdatedAt = cursor?.sortKey,
            afterId = cursor?.id,
            pageSize = pageSize + EXTRA_ROW_TO_DETECT_NEXT_PAGE,
        )
        val entries = fetched.take(pageSize)
        if (entries.isEmpty()) return Page(items = emptyList(), nextCursor = null)
        val last = entries.last().takeIf { fetched.size > pageSize }

        val setsByEntry = setRepository.findByEntryIdInOrderByPositionAsc(entries.map { it.id }).groupBy { it.entryId }
        val exerciseNames = exerciseRepository
            .findAllById(setsByEntry.values.flatten().map { it.exerciseId }.distinct())
            .associate { it.id to it.name }
        val clientNames = userRepository
            .findAllById(entries.map { it.clientUserId }.distinct())
            .associate { it.id to it.displayName }
        val seen = entryRepository.findSeenEntryIds(coachId = coach.id, entryIds = entries.map { it.id }).toSet()

        return Page(
            items = entries.mapNotNull { entry ->
                val clientName = clientNames[entry.clientUserId] ?: return@mapNotNull null
                val sets = setsByEntry[entry.id].orEmpty()
                val records = recordsOf(entry = entry, sets = sets)
                TrainingFeedItemResponse(
                    entryId = entry.id,
                    clientUserId = entry.clientUserId,
                    clientDisplayName = clientName,
                    entryDate = entry.entryDate,
                    updatedAt = entry.updatedAt,
                    exercisesCount = sets.map { it.exerciseId }.distinct().size,
                    setsCount = sets.size,
                    totalVolumeGrams = sets.sumOf { setVolumeOf(it) ?: 0L },
                    durationSeconds = durationSecondsOf(startedAt = entry.startedAt, finishedAt = entry.finishedAt),
                    personalRecords = sets
                        .filter { it.id in records }
                        .mapNotNull { set ->
                            TrainingFeedRecordResponse(
                                exerciseName = exerciseNames[set.exerciseId] ?: return@mapNotNull null,
                                weightGrams = set.weightGrams,
                                repetitions = set.repetitions,
                                durationSeconds = set.durationSeconds,
                                distanceMeters = set.distanceMeters,
                            )
                        },
                    isSeen = entry.id in seen,
                )
            },
            nextCursor = last?.let { encodeCursor(PageCursor(sortKey = it.updatedAt.toString(), id = it.id)) },
        )
    }

    @Transactional(readOnly = true)
    fun unseenCount(coachUserId: UUID): Int = entryRepository.countUnseen(requireCoach(coachUserId).id).toInt()

    @Transactional
    fun markSeen(coachUserId: UUID, entryId: UUID) {
        val coach = requireCoach(coachUserId)
        if (!entryRepository.isEntryOfActiveClient(coachId = coach.id, entryId = entryId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Тренировка не найдена")
        }
        entryRepository.markSeen(coachId = coach.id, entryId = entryId, seenAt = Instant.now(clock))
    }

    private fun recordsOf(entry: TrainingLogEntryEntity, sets: List<TrainingLogSetEntity>): Set<UUID> {
        if (sets.isEmpty()) return emptySet()
        val bestVolumeBefore = setRepository
            .bestVolumePerExerciseBefore(clientUserId = entry.clientUserId, beforeDate = entry.entryDate)
            .associate { it.getExerciseId() to it.getBestVolume() }
        return recordSetIdsOf(bestVolumeBefore = bestVolumeBefore, setsInDateOrder = listOf(sets))
    }

    private fun requireCoach(coachUserId: UUID): CoachEntity =
        coachRepository.findByUserId(coachUserId)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Пользователь не тренер")
}
