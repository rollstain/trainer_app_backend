package app.trainer.backend.habit

import app.trainer.backend.coach.CoachClientRepository
import app.trainer.backend.coach.CoachClientStatus
import app.trainer.backend.coach.CoachEntity
import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.user.UserRepository
import java.time.Clock
import java.time.Instant
import java.util.UUID
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class HabitSetService(
    private val setRepository: HabitSetRepository,
    private val itemRepository: HabitSetItemRepository,
    private val habitRepository: HabitRepository,
    private val coachRepository: CoachRepository,
    private val coachClientRepository: CoachClientRepository,
    private val userRepository: UserRepository,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun setsOfCoach(coachUserId: UUID): List<HabitSetResponse> =
        responsesOf(setRepository.findByCoachIdOrderByCreatedAtAsc(requireCoach(coachUserId).id))

    @Transactional(readOnly = true)
    fun setsCount(coachUserId: UUID): Long = setRepository.countByCoachId(requireCoach(coachUserId).id)

    @Transactional
    fun create(coachUserId: UUID, request: HabitSetRequest): HabitSetResponse {
        val coach = requireCoach(coachUserId)
        val habits = habitTitlesOf(request.habits)
        val set = setRepository.save(
            HabitSetEntity(
                id = UUID.randomUUID(),
                coachId = coach.id,
                title = request.title.trim(),
                createdAt = Instant.now(clock),
                updatedAt = null,
            )
        )
        saveItems(setId = set.id, habits = habits)
        return responsesOf(listOf(set)).single()
    }

    @Transactional
    fun update(coachUserId: UUID, setId: UUID, request: HabitSetRequest): HabitSetResponse {
        val set = requireOwnSet(coach = requireCoach(coachUserId), setId = setId)
        val habits = habitTitlesOf(request.habits)
        set.title = request.title.trim()
        set.updatedAt = Instant.now(clock)
        itemRepository.deleteBySetId(set.id)
        saveItems(setId = set.id, habits = habits)
        return responsesOf(listOf(set)).single()
    }

    @Transactional
    fun delete(coachUserId: UUID, setId: UUID) {
        setRepository.delete(requireOwnSet(coach = requireCoach(coachUserId), setId = setId))
    }

    @Transactional
    fun assign(
        coachUserId: UUID,
        clientUserId: UUID,
        setId: UUID,
        request: AssignHabitSetRequest?,
    ): List<HabitResponse> {
        val coach = requireCoach(coachUserId)
        requireOwnClient(coach = coach, clientUserId = clientUserId)
        val set = requireOwnSet(coach = coach, setId = setId)
        val setHabits = itemRepository.findBySetIdInOrderByPositionAsc(listOf(set.id)).map { it.title }
        val chosen = if (request == null) setHabits else chosenFrom(setHabits = setHabits, requested = request.habits)
        val alreadyHas = habitRepository
            .findByClientUserIdAndArchivedAtIsNullOrderByCreatedAtAsc(clientUserId)
            .map { it.title.lowercase() }
            .toSet()
        val now = Instant.now(clock)
        val created = mutableListOf<HabitResponse>()
        for (title in chosen) {
            if (title.lowercase() in alreadyHas) continue
            val habit = habitRepository.save(
                HabitEntity(
                    id = UUID.randomUUID(),
                    coachId = coach.id,
                    clientUserId = clientUserId,
                    title = title,
                    createdAt = now,
                    archivedAt = null,
                    habitSetId = set.id,
                )
            )
            created.add(
                HabitResponse(
                    id = habit.id,
                    clientUserId = habit.clientUserId,
                    title = habit.title,
                    isSetByCoach = true,
                    doneDates = emptyList(),
                )
            )
        }
        return created
    }

    private fun chosenFrom(setHabits: List<String>, requested: List<String>): List<String> {
        val wanted = requested.map { it.trim().lowercase() }.toSet()
        val inSet = setHabits.map { it.lowercase() }.toSet()
        if (!inSet.containsAll(wanted)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Таких привычек в наборе нет")
        }
        return setHabits.filter { it.lowercase() in wanted }
    }

    private fun habitTitlesOf(raw: List<String>): List<String> {
        val titles = raw.map { it.trim() }
        if (titles.any { it.isEmpty() || it.length > HABIT_TITLE_MAX_LENGTH }) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "У каждой привычки нужно название до $HABIT_TITLE_MAX_LENGTH знаков",
            )
        }
        if (titles.map { it.lowercase() }.toSet().size != titles.size) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Привычки в наборе повторяются")
        }
        return titles
    }

    private fun saveItems(setId: UUID, habits: List<String>) {
        itemRepository.saveAll(
            habits.mapIndexed { position, title ->
                HabitSetItemEntity(id = UUID.randomUUID(), setId = setId, position = position, title = title)
            }
        )
    }

    private fun responsesOf(sets: List<HabitSetEntity>): List<HabitSetResponse> {
        if (sets.isEmpty()) return emptyList()
        val setIds = sets.map { it.id }
        val habitsBySet = itemRepository
            .findBySetIdInOrderByPositionAsc(setIds)
            .groupBy(keySelector = { it.setId }, valueTransform = { it.title })
        val clientsBySet = habitRepository
            .clientsOfSets(setIds)
            .groupBy(keySelector = { it.setId }, valueTransform = { it.clientUserId })
        val clientIds = clientsBySet.values.flatten().distinct()
        val namesById = userRepository.findAllById(clientIds).associate { it.id to it.displayName }
        return sets.map { set ->
            val clients = clientsBySet[set.id].orEmpty()
            HabitSetResponse(
                id = set.id,
                title = set.title,
                habits = habitsBySet[set.id].orEmpty(),
                assignedClientsCount = clients.size,
                assignedClientNames = clients.mapNotNull { namesById[it] }.sorted(),
                createdAt = set.createdAt,
                updatedAt = set.updatedAt,
            )
        }
    }

    private fun requireOwnSet(coach: CoachEntity, setId: UUID): HabitSetEntity {
        val set = setRepository.findByIdOrNull(setId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Набор не найден")
        if (set.coachId != coach.id) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Набор другого тренера")
        }
        return set
    }

    private fun requireCoach(coachUserId: UUID): CoachEntity = coachRepository.findByUserId(coachUserId)
        ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Пользователь не тренер")

    private fun requireOwnClient(coach: CoachEntity, clientUserId: UUID) {
        val link = coachClientRepository.findByCoachIdAndUserId(coachId = coach.id, userId = clientUserId)
        if (link == null || link.status != CoachClientStatus.ACTIVE) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Это не ваш подопечный")
        }
    }
}
