package app.trainer.backend.habit

import app.trainer.backend.coach.CoachClientRepository
import app.trainer.backend.coach.CoachClientStatus
import app.trainer.backend.coach.CoachEntity
import app.trainer.backend.coach.CoachRepository
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
class HabitService(
    private val habitRepository: HabitRepository,
    private val markRepository: HabitMarkRepository,
    private val coachRepository: CoachRepository,
    private val coachClientRepository: CoachClientRepository,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun ownHabits(clientUserId: UUID, from: LocalDate, to: LocalDate): List<HabitResponse> {
        return habitsOf(clientUserId = clientUserId, from = from, to = to)
    }

    @Transactional(readOnly = true)
    fun clientHabits(
        coachUserId: UUID,
        clientUserId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<HabitResponse> {
        requireOwnClient(coach = requireCoach(coachUserId), clientUserId = clientUserId)
        return habitsOf(clientUserId = clientUserId, from = from, to = to)
    }

    @Transactional
    fun createForClient(
        coachUserId: UUID,
        clientUserId: UUID,
        request: CreateHabitRequest,
    ): HabitResponse {
        val coach = requireCoach(coachUserId)
        requireOwnClient(coach = coach, clientUserId = clientUserId)
        return toResponse(habit = saveHabit(coachId = coach.id, clientUserId = clientUserId, request = request))
    }

    @Transactional
    fun createOwn(clientUserId: UUID, request: CreateHabitRequest): HabitResponse {
        return toResponse(habit = saveHabit(coachId = null, clientUserId = clientUserId, request = request))
    }

    @Transactional
    fun archive(userId: UUID, habitId: UUID) {
        val habit = requireOwnHabit(userId = userId, habitId = habitId)
        habit.archivedAt = Instant.now(clock)
    }

    @Transactional
    fun mark(userId: UUID, habitId: UUID, markDate: LocalDate, isDone: Boolean) {
        val habit = requireOwnHabit(userId = userId, habitId = habitId)
        val existing = markRepository.findByHabitIdAndMarkDate(habitId = habit.id, markDate = markDate)
        when {
            isDone && existing == null -> markRepository.save(
                HabitMarkEntity(id = UUID.randomUUID(), habitId = habit.id, markDate = markDate)
            )
            !isDone && existing != null -> markRepository.delete(existing)
        }
    }

    private fun saveHabit(coachId: UUID?, clientUserId: UUID, request: CreateHabitRequest): HabitEntity {
        return habitRepository.save(
            HabitEntity(
                id = UUID.randomUUID(),
                coachId = coachId,
                clientUserId = clientUserId,
                title = request.title.trim(),
                createdAt = Instant.now(clock),
                archivedAt = null,
            )
        )
    }

    private fun habitsOf(clientUserId: UUID, from: LocalDate, to: LocalDate): List<HabitResponse> {
        val habits = habitRepository.findByClientUserIdAndArchivedAtIsNullOrderByCreatedAtAsc(clientUserId)
        if (habits.isEmpty()) return emptyList()
        val marksByHabit = markRepository
            .findByHabitIdInAndMarkDateBetween(habitIds = habits.map { it.id }, from = from, to = to)
            .groupBy { it.habitId }
        return habits.map { habit ->
            toResponse(
                habit = habit,
                doneDates = marksByHabit[habit.id].orEmpty().map { it.markDate }.sorted(),
            )
        }
    }

    private fun requireOwnHabit(userId: UUID, habitId: UUID): HabitEntity {
        val habit = habitRepository.findByIdOrNull(habitId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Привычка не найдена")
        if (habit.clientUserId != userId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Это не ваша привычка")
        }
        return habit
    }

    private fun requireCoach(coachUserId: UUID): CoachEntity = coachRepository.findByUserId(coachUserId)
        ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Пользователь не тренер")

    private fun requireOwnClient(coach: CoachEntity, clientUserId: UUID) {
        val link = coachClientRepository.findByCoachIdAndUserId(coachId = coach.id, userId = clientUserId)
        if (link == null || link.status != CoachClientStatus.ACTIVE) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Это не ваш подопечный")
        }
    }

    private fun toResponse(habit: HabitEntity, doneDates: List<LocalDate> = emptyList()): HabitResponse =
        HabitResponse(
            id = habit.id,
            clientUserId = habit.clientUserId,
            title = habit.title,
            isSetByCoach = habit.coachId != null,
            doneDates = doneDates,
        )
}
