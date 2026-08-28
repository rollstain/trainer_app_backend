package app.trainer.backend.traininglog

import app.trainer.backend.coach.CoachClientRepository
import app.trainer.backend.coach.CoachClientStatus
import app.trainer.backend.coach.CoachEntity
import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.config.EXTRA_ROW_TO_DETECT_NEXT_PAGE
import app.trainer.backend.config.Page
import app.trainer.backend.config.PageCursor
import app.trainer.backend.config.decodeCursor
import app.trainer.backend.config.encodeCursor
import app.trainer.backend.config.pageSizeOf
import app.trainer.backend.media.MediaFileResponse
import app.trainer.backend.media.MediaFileService
import app.trainer.backend.media.MediaOwnerKind
import app.trainer.backend.media.PrepareUploadRequest
import app.trainer.backend.media.PrepareUploadResponse
import app.trainer.backend.user.UserRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class TrainingLogService(
    private val exerciseRepository: ExerciseRepository,
    private val entryRepository: TrainingLogEntryRepository,
    private val setRepository: TrainingLogSetRepository,
    private val coachRepository: CoachRepository,
    private val coachClientRepository: CoachClientRepository,
    private val userRepository: UserRepository,
    private val mediaFileService: MediaFileService,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun availableExercises(userId: UUID, limit: Int?, after: String?): Page<ExerciseResponse> {
        val ownerIds = ownerIdsVisibleTo(userId).toTypedArray()
        val pageSize = pageSizeOf(limit)
        val fetched = if (pageSize == null) {
            exerciseRepository.findAvailable(ownerIds)
        } else {
            val cursor = decodeCursor(after)
            exerciseRepository.findAvailablePage(
                ownerIds = ownerIds,
                afterName = cursor?.sortKey,
                afterId = cursor?.id,
                pageSize = pageSize + EXTRA_ROW_TO_DETECT_NEXT_PAGE,
            )
        }
        val exercises = if (pageSize == null) fetched else fetched.take(pageSize)
        val hasMore = pageSize != null && fetched.size > pageSize
        val latestByExercise = setRepository.findLatestPerExercise(userId).associateBy { it.exerciseId }
        return Page(
            items = exercises.map { exercise ->
                toResponse(exercise = exercise, latest = latestByExercise[exercise.id])
            },
            nextCursor = exercises.lastOrNull()
                ?.takeIf { hasMore }
                ?.let { encodeCursor(PageCursor(sortKey = it.name, id = it.id)) },
        )
    }

    @Transactional
    fun prepareVideoUpload(coachUserId: UUID, request: PrepareUploadRequest): PrepareUploadResponse {
        val coach = requireCoach(coachUserId)
        return mediaFileService.prepareUpload(
            uploaderUserId = coachUserId,
            ownerKind = MediaOwnerKind.EXERCISE,
            scopeId = coach.id,
            request = request,
        )
    }

    @Transactional
    fun attachVideo(coachUserId: UUID, exerciseId: UUID, mediaFileId: UUID): ExerciseResponse {
        val coach = requireCoach(coachUserId)
        val exercise = requireOwnExercise(coachId = coach.id, exerciseId = exerciseId)
        val linked = mediaFileService.link(
            mediaFileIds = listOf(mediaFileId),
            ownerKind = MediaOwnerKind.EXERCISE,
            ownerId = exercise.id,
            scopeId = coach.id,
            uploaderUserId = coachUserId,
        )
        exercise.videoMediaFileId = mediaFileId
        return toResponse(exercise = exercise, video = linked.firstOrNull()?.let(mediaFileService::toResponse))
    }

    @Transactional
    fun detachVideo(coachUserId: UUID, exerciseId: UUID) {
        val coach = requireCoach(coachUserId)
        val exercise = requireOwnExercise(coachId = coach.id, exerciseId = exerciseId)
        exercise.videoMediaFileId = null
    }

    private fun requireCoach(coachUserId: UUID): CoachEntity = coachRepository.findByUserId(coachUserId)
        ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Пользователь не тренер")

    private fun requireOwnExercise(coachId: UUID, exerciseId: UUID): ExerciseEntity {
        val exercise = exerciseRepository.findByIdOrNull(exerciseId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Упражнение не найдено")
        if (exercise.ownerId != coachId || exercise.ownerKind != ExerciseOwnerKind.COACH) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Упражнение не найдено")
        }
        return exercise
    }

    private fun videoOf(exercise: ExerciseEntity): MediaFileResponse? {
        val mediaFileId = exercise.videoMediaFileId ?: return null
        return mediaFileService.findResponse(mediaFileId)
    }

    @Transactional(readOnly = true)
    fun diarySummary(coachUserId: UUID, from: LocalDate, to: LocalDate): List<ClientDiarySummaryResponse> {
        val coach = coachRepository.findByUserId(coachUserId)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Пользователь не тренер")
        val links = coachClientRepository.findActiveOrdered(coachId = coach.id)
        if (links.isEmpty()) return emptyList()

        val clientIds = links.map { it.userId }.toTypedArray()
        val usersById = userRepository.findAllById(clientIds.toList()).associateBy { it.id }
        val daysByClient = entryRepository
            .findDiaryDays(clientIds = clientIds, from = from, to = to)
            .groupBy { it.getClientUserId() }
        val lastEntryByClient = entryRepository
            .findLastEntryDates(clientIds = clientIds)
            .associate { it.getClientUserId() to it.getLastEntryDate() }

        return links.mapNotNull { link ->
            val user = usersById[link.userId] ?: return@mapNotNull null
            ClientDiarySummaryResponse(
                clientUserId = link.userId,
                displayName = user.displayName,
                linkedAt = link.createdAt,
                lastEntryDate = lastEntryByClient[link.userId],
                days = daysByClient[link.userId].orEmpty().map { day ->
                    DiaryDayResponse(entryDate = day.getEntryDate(), volumeGrams = day.getVolumeGrams())
                },
            )
        }
    }

    @Transactional
    fun createExercise(userId: UUID, request: CreateExerciseRequest): ExerciseResponse {
        val coach = coachRepository.findByUserId(userId)
        val exercise = exerciseRepository.save(
            ExerciseEntity(
                id = UUID.randomUUID(),
                ownerKind = if (coach == null) ExerciseOwnerKind.CLIENT else ExerciseOwnerKind.COACH,
                ownerId = coach?.id ?: userId,
                name = request.name.trim(),
                primaryMuscle = request.primaryMuscle,
                equipment = request.equipment,
                kind = request.kind,
                description = request.description?.trim()?.ifEmpty { null },
                videoUrl = request.videoUrl?.trim()?.ifEmpty { null },
                videoMediaFileId = null,
                createdAt = Instant.now(clock),
                archivedAt = null,
            )
        )
        return toResponse(exercise)
    }

    @Transactional
    fun archiveExercise(userId: UUID, exerciseId: UUID) {
        val exercise = exerciseRepository.findByIdOrNull(exerciseId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Упражнение не найдено")
        val coach = coachRepository.findByUserId(userId)
        val ownerId = coach?.id ?: userId
        if (exercise.ownerKind == ExerciseOwnerKind.SHARED || exercise.ownerId != ownerId) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Упражнение не найдено")
        }
        exercise.archivedAt = Instant.now(clock)
    }

    @Transactional
    fun saveEntry(
        clientUserId: UUID,
        entryDate: LocalDate,
        request: SaveTrainingLogRequest,
    ): TrainingLogEntryResponse {
        val exercisesById = requireKnownExercises(userId = clientUserId, request = request)
        request.sets.forEach { set ->
            val exercise = exercisesById.getValue(set.exerciseId)
            requireConsistentSet(set = set, kind = exercise.kind)
        }

        val now = Instant.now(clock)
        val entry = entryRepository.findByClientUserIdAndEntryDate(
            clientUserId = clientUserId,
            entryDate = entryDate,
        ) ?: entryRepository.save(
            TrainingLogEntryEntity(
                id = UUID.randomUUID(),
                clientUserId = clientUserId,
                entryDate = entryDate,
                slotId = request.slotId,
                notes = null,
                createdAt = now,
                updatedAt = now,
            )
        )
        entry.slotId = request.slotId
        entry.notes = request.notes?.trim()?.ifEmpty { null }
        entry.updatedAt = now

        setRepository.deleteByEntryId(entry.id)
        val saved = request.sets.mapIndexed { index, set ->
            TrainingLogSetEntity(
                id = UUID.randomUUID(),
                entryId = entry.id,
                exerciseId = set.exerciseId,
                position = index,
                repetitions = set.repetitions,
                weightGrams = set.weightGrams,
                durationSeconds = set.durationSeconds,
                distanceMeters = set.distanceMeters,
            )
        }
        setRepository.saveAll(saved)
        return toResponse(
            entry = entry,
            sets = saved,
            exercisesById = exercisesById,
            recordSetIds = recordSetIds(clientUserId = clientUserId, entriesWithSets = listOf(entry to saved)),
        )
    }

    @Transactional(readOnly = true)
    fun ownEntries(clientUserId: UUID, from: LocalDate, to: LocalDate): List<TrainingLogEntryResponse> {
        return entriesOf(clientUserId = clientUserId, from = from, to = to)
    }

    @Transactional(readOnly = true)
    fun clientEntries(
        coachUserId: UUID,
        clientUserId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<TrainingLogEntryResponse> {
        val coach = coachRepository.findByUserId(coachUserId)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Пользователь не тренер")
        val link = coachClientRepository.findByCoachIdAndUserId(coachId = coach.id, userId = clientUserId)
        if (link == null || link.status != CoachClientStatus.ACTIVE) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Это не ваш подопечный")
        }
        return entriesOf(clientUserId = clientUserId, from = from, to = to)
    }

    private fun entriesOf(clientUserId: UUID, from: LocalDate, to: LocalDate): List<TrainingLogEntryResponse> {
        val entries = entryRepository.findByClientUserIdAndEntryDateBetweenOrderByEntryDateDesc(
            clientUserId = clientUserId,
            from = from,
            to = to,
        )
        if (entries.isEmpty()) return emptyList()

        val sets = setRepository.findByEntryIdInOrderByPositionAsc(entries.map { it.id })
        val exercisesById = exerciseRepository
            .findAllById(sets.map { it.exerciseId }.distinct())
            .associateBy { it.id }
        val setsByEntry = sets.groupBy { it.entryId }
        val recordSetIds = recordSetIds(
            clientUserId = clientUserId,
            entriesWithSets = entries
                .sortedBy { it.entryDate }
                .map { entry -> entry to setsByEntry[entry.id].orEmpty() },
        )
        return entries.map { entry ->
            toResponse(
                entry = entry,
                sets = setsByEntry[entry.id].orEmpty(),
                exercisesById = exercisesById,
                recordSetIds = recordSetIds,
            )
        }
    }

    private fun recordSetIds(
        clientUserId: UUID,
        entriesWithSets: List<Pair<TrainingLogEntryEntity, List<TrainingLogSetEntity>>>,
    ): Set<UUID> {
        val earliestDate = entriesWithSets.minOfOrNull { (entry, _) -> entry.entryDate } ?: return emptySet()
        val bestVolumeByExercise = setRepository
            .bestVolumePerExerciseBefore(clientUserId = clientUserId, beforeDate = earliestDate)
            .associate { it.getExerciseId() to it.getBestVolume() }
            .toMutableMap()

        val records = mutableSetOf<UUID>()
        entriesWithSets.forEach { (_, sets) ->
            sets.sortedBy { it.position }.forEach { set ->
                val volume = volumeOf(set) ?: return@forEach
                val best = bestVolumeByExercise[set.exerciseId] ?: 0
                if (volume > best) {
                    records.add(set.id)
                    bestVolumeByExercise[set.exerciseId] = volume
                }
            }
        }
        return records
    }

    private fun volumeOf(set: TrainingLogSetEntity): Long? {
        val repetitions = set.repetitions ?: return null
        val weightGrams = set.weightGrams ?: return null
        val volume = repetitions.toLong() * weightGrams
        return if (volume == 0L) null else volume
    }

    private fun ownerNameOf(exercise: ExerciseEntity): String? = when (exercise.ownerKind) {
        ExerciseOwnerKind.SHARED, ExerciseOwnerKind.COACH -> null
        ExerciseOwnerKind.CLIENT -> exercise.ownerId?.let { userRepository.findByIdOrNull(it)?.displayName }
    }

    private fun ownerIdsVisibleTo(userId: UUID): List<UUID> {
        val ownCoach = coachRepository.findByUserId(userId)
        if (ownCoach != null) {
            val clientIds = coachClientRepository
                .findByCoachIdAndStatus(coachId = ownCoach.id, status = CoachClientStatus.ACTIVE)
                .map { it.userId }
            return listOf(ownCoach.id) + clientIds
        }
        val coachIds = coachClientRepository.findByUserId(userId)
            .filter { it.status == CoachClientStatus.ACTIVE }
            .map { it.coachId }
        return listOf(userId) + coachIds
    }

    private fun requireKnownExercises(
        userId: UUID,
        request: SaveTrainingLogRequest,
    ): Map<UUID, ExerciseEntity> {
        val requestedIds = request.sets.map { it.exerciseId }.distinct()
        if (requestedIds.isEmpty()) return emptyMap()
        val available = exerciseRepository
            .findAvailable(ownerIdsVisibleTo(userId).toTypedArray())
            .associateBy { it.id }
        val unknown = requestedIds.filterNot(available::containsKey)
        if (unknown.isNotEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Упражнение недоступно: ${unknown.first()}")
        }
        return available
    }

    private fun requireConsistentSet(set: TrainingSetRequest, kind: ExerciseKind) {
        val isConsistent = when (kind) {
            ExerciseKind.STRENGTH -> set.repetitions != null && set.weightGrams != null
            ExerciseKind.BODYWEIGHT -> set.repetitions != null
            ExerciseKind.CARDIO -> set.durationSeconds != null || set.distanceMeters != null
        }
        if (!isConsistent) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Подход не заполнен для типа упражнения $kind",
            )
        }
    }

    private fun toResponse(
        exercise: ExerciseEntity,
        latest: TrainingLogSetEntity? = null,
        video: MediaFileResponse? = videoOf(exercise),
    ): ExerciseResponse = ExerciseResponse(
        id = exercise.id,
        name = exercise.name,
        primaryMuscle = exercise.primaryMuscle,
        equipment = exercise.equipment,
        kind = exercise.kind,
        ownerKind = exercise.ownerKind,
        ownerDisplayName = ownerNameOf(exercise),
        description = exercise.description,
        videoUrl = exercise.videoUrl,
        video = video,
        lastRepetitions = latest?.repetitions,
        lastWeightGrams = latest?.weightGrams,
        lastDurationSeconds = latest?.durationSeconds,
        lastDistanceMeters = latest?.distanceMeters,
    )

    private fun toResponse(
        entry: TrainingLogEntryEntity,
        sets: List<TrainingLogSetEntity>,
        exercisesById: Map<UUID, ExerciseEntity>,
        recordSetIds: Set<UUID>,
    ): TrainingLogEntryResponse {
        val setResponses = sets.mapNotNull { set ->
            val exercise = exercisesById[set.exerciseId] ?: return@mapNotNull null
            TrainingSetResponse(
                id = set.id,
                exerciseId = set.exerciseId,
                exerciseName = exercise.name,
                kind = exercise.kind,
                position = set.position,
                repetitions = set.repetitions,
                weightGrams = set.weightGrams,
                durationSeconds = set.durationSeconds,
                distanceMeters = set.distanceMeters,
                isPersonalRecord = recordSetIds.contains(set.id),
            )
        }
        return TrainingLogEntryResponse(
            id = entry.id,
            clientUserId = entry.clientUserId,
            entryDate = entry.entryDate,
            slotId = entry.slotId,
            notes = entry.notes,
            sets = setResponses,
            totalVolumeGrams = totalVolumeOf(setResponses),
        )
    }

    private fun totalVolumeOf(sets: List<TrainingSetResponse>): Long {
        return sets.sumOf { set ->
            val repetitions = set.repetitions
            val weightGrams = set.weightGrams
            if (repetitions == null || weightGrams == null) 0L else repetitions.toLong() * weightGrams
        }
    }
}
