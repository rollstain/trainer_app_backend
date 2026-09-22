package app.trainer.backend.coach

import app.trainer.backend.clientnotes.ClientNoteKind
import app.trainer.backend.clientnotes.ClientNoteRepository
import app.trainer.backend.config.EXTRA_ROW_TO_DETECT_NEXT_PAGE
import app.trainer.backend.config.Page
import app.trainer.backend.config.PageCursor
import app.trainer.backend.config.decodeCursor
import app.trainer.backend.config.encodeCursor
import app.trainer.backend.config.pageSizeOf
import app.trainer.backend.program.ProgramService
import app.trainer.backend.push.PushChannel
import app.trainer.backend.push.PushMessage
import app.trainer.backend.push.PushSender
import app.trainer.backend.push.PushText
import app.trainer.backend.schedule.ScheduleService
import app.trainer.backend.user.UserRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.util.UUID
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

private const val PUSH_CLIENT_USER_ID_KEY = "clientUserId"

@Service
class CoachService(
    private val coachRepository: CoachRepository,
    private val coachClientRepository: CoachClientRepository,
    private val userRepository: UserRepository,
    private val clientNoteRepository: ClientNoteRepository,
    private val workingHourRepository: CoachWorkingHourRepository,
    private val scheduleService: ScheduleService,
    private val programService: ProgramService,
    private val pushSender: PushSender,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun missedSessions(coachUserId: UUID, clientUserIds: List<UUID>): List<MissedSessionsResponse> = scheduleService
        .missedSessionsByClient(coachUserId = coachUserId, clientUserIds = clientUserIds)
        .map { (clientUserId, missed) ->
            MissedSessionsResponse(clientUserId = clientUserId, missedInARow = missed)
        }

    @Transactional(readOnly = true)
    fun clientsOfCoach(
        coachUserId: UUID,
        limit: Int?,
        after: String?,
        userIds: List<UUID>?,
        query: String?,
    ): Page<CoachClientResponse> {
        val coach = requireCoach(coachUserId)
        val pageSize = if (userIds == null) pageSizeOf(limit) else null
        val fetched = if (userIds != null) {
            coachClientRepository.findActiveByUserIds(coachId = coach.id, userIds = userIds.toTypedArray())
        } else if (pageSize == null) {
            coachClientRepository.findActiveOrdered(coachId = coach.id)
        } else {
            val cursor = decodeCursor(after)
            coachClientRepository.findActivePage(
                coachId = coach.id,
                query = query?.trim()?.takeIf { it.isNotEmpty() },
                afterName = cursor?.sortKey,
                afterId = cursor?.id,
                pageSize = pageSize + EXTRA_ROW_TO_DETECT_NEXT_PAGE,
            )
        }
        val links = if (pageSize == null) fetched else fetched.take(pageSize)
        val hasMore = pageSize != null && fetched.size > pageSize
        val withMedicalNotes = clientNoteRepository
            .findClientUserIdsWithKind(coachId = coach.id, kind = ClientNoteKind.MEDICAL)
            .toSet()
        val usersById = userRepository.findAllById(links.map { it.userId }).associateBy { it.id }
        val items = links.mapNotNull { link ->
            val user = usersById[link.userId] ?: return@mapNotNull null
            CoachClientResponse(
                userId = user.id,
                displayName = user.displayName,
                status = link.status,
                hasMedicalNotes = user.id in withMedicalNotes,
                linkedAt = link.createdAt,
            )
        }
        val lastLink = links.lastOrNull()?.takeIf { hasMore }
        val lastName = lastLink?.let { usersById[it.userId]?.displayName }
        return Page(
            items = items,
            nextCursor = if (lastLink == null || lastName == null) {
                null
            } else {
                encodeCursor(PageCursor(sortKey = lastName, id = lastLink.userId))
            },
        )
    }

    @Transactional(readOnly = true)
    fun policyOf(coachUserId: UUID): CoachPolicyResponse {
        return toPolicyResponse(requireCoach(coachUserId))
    }

    @Transactional
    fun updatePolicy(coachUserId: UUID, request: UpdateCoachPolicyRequest): CoachPolicyResponse {
        val coach = requireCoach(coachUserId)
        request.cancellationWindowHours?.let { coach.cancellationWindowHours = it }
        request.reminderHour?.let { coach.reminderHour = it }
        request.sessionRemindersEnabled?.let { coach.sessionRemindersEnabled = it }
        request.diaryRemindersEnabled?.let { coach.diaryRemindersEnabled = it }
        request.checkInRemindersEnabled?.let { coach.checkInRemindersEnabled = it }
        request.workingHours?.let { replaceWorkingHours(coachId = coach.id, workingHours = it) }
        return toPolicyResponse(coach)
    }

    private fun replaceWorkingHours(coachId: UUID, workingHours: List<WorkingDayDto>) {
        val duplicatedDay = workingHours.groupBy { it.dayOfWeek }.any { it.value.size > 1 }
        if (duplicatedDay) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "День недели в графике повторяется")
        }
        if (workingHours.any { it.closesAt <= it.opensAt }) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Время «по» должно быть позже времени «с»")
        }
        workingHourRepository.deleteAllOfCoach(coachId)
        workingHourRepository.saveAll(
            workingHours.map { day ->
                CoachWorkingHourEntity(
                    id = UUID.randomUUID(),
                    coachId = coachId,
                    dayOfWeek = day.dayOfWeek.value,
                    opensAt = day.opensAt,
                    closesAt = day.closesAt,
                )
            }
        )
    }

    private fun workingHoursOf(coachId: UUID): List<WorkingDayDto> = workingHourRepository
        .findByCoachIdOrderByDayOfWeek(coachId)
        .map { hour ->
            WorkingDayDto(
                dayOfWeek = DayOfWeek.of(hour.dayOfWeek),
                opensAt = hour.opensAt,
                closesAt = hour.closesAt,
            )
        }

    private fun toPolicyResponse(coach: CoachEntity): CoachPolicyResponse = CoachPolicyResponse(
        cancellationWindowHours = coach.cancellationWindowHours,
        reminderHour = coach.reminderHour,
        sessionRemindersEnabled = coach.sessionRemindersEnabled,
        diaryRemindersEnabled = coach.diaryRemindersEnabled,
        checkInRemindersEnabled = coach.checkInRemindersEnabled,
        workingHours = workingHoursOf(coach.id),
    )

    @Transactional
    fun archiveClient(coachUserId: UUID, clientUserId: UUID) {
        val coach = requireCoach(coachUserId)
        val link = coachClientRepository.findByCoachIdAndUserId(coachId = coach.id, userId = clientUserId)
        if (link == null || link.status != CoachClientStatus.ACTIVE) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Подопечный не найден")
        }
        endLink(link = link, endedBy = LinkEndedBy.COACH)
    }

    @Transactional
    fun leaveCoach(userId: UUID, coachId: UUID) {
        val link = coachClientRepository.findByCoachIdAndUserId(coachId = coachId, userId = userId)
        if (link == null || link.status != CoachClientStatus.ACTIVE) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Этот тренер вас не ведёт")
        }
        endLink(link = link, endedBy = LinkEndedBy.CLIENT)
        tellCoachAboutLeaving(coachId = coachId, clientUserId = userId)
    }

    @Transactional(readOnly = true)
    fun pastClients(coachUserId: UUID): List<CoachClientResponse> {
        val coach = requireCoach(coachUserId)
        val links = coachClientRepository
            .findByCoachIdAndStatus(coachId = coach.id, status = CoachClientStatus.ARCHIVED)
            .sortedByDescending { it.endedAt ?: it.createdAt }
        val usersById = userRepository.findAllById(links.map { it.userId }).associateBy { it.id }
        return links.mapNotNull { link ->
            val user = usersById[link.userId] ?: return@mapNotNull null
            CoachClientResponse(
                userId = user.id,
                displayName = user.displayName,
                status = link.status,
                hasMedicalNotes = false,
                linkedAt = link.createdAt,
                endedAt = link.endedAt,
                endedBy = link.endedBy,
            )
        }
    }

    private fun endLink(link: CoachClientEntity, endedBy: LinkEndedBy) {
        link.status = CoachClientStatus.ARCHIVED
        link.endedAt = Instant.now(clock)
        link.endedBy = endedBy
        programService.endAssignmentQuietly(link.userId)
        scheduleService.releaseBookingsOf(coachId = link.coachId, clientUserId = link.userId)
    }

    private fun tellCoachAboutLeaving(coachId: UUID, clientUserId: UUID) {
        val coach = coachRepository.findByIdOrNull(coachId) ?: return
        val clientName = userRepository.findByIdOrNull(clientUserId)?.displayName ?: return
        pushSender.send(
            userIds = listOf(coach.userId),
            message = PushMessage(
                channel = PushChannel.CHAT,
                text = PushText.CLIENT_UNLINKED,
                args = listOf(clientName),
                data = mapOf(PUSH_CLIENT_USER_ID_KEY to clientUserId.toString()),
            ),
        )
    }

    private fun requireCoach(coachUserId: UUID): CoachEntity = coachRepository.findByUserId(coachUserId)
        ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Пользователь не тренер")

    @Transactional(readOnly = true)
    fun coachesOfClient(userId: UUID): List<CoachSummaryResponse> {
        return coachClientRepository
            .findByUserId(userId)
            .filter { it.status == CoachClientStatus.ACTIVE }
            .mapNotNull { link ->
                val coach = coachRepository.findByIdOrNull(link.coachId) ?: return@mapNotNull null
                val coachUser = userRepository.findByIdOrNull(coach.userId) ?: return@mapNotNull null
                CoachSummaryResponse(
                    coachId = coach.id,
                    userId = coachUser.id,
                    displayName = coachUser.displayName,
                    zoneId = coach.zoneId,
                    cancellationWindowHours = coach.cancellationWindowHours,
                    workingHours = workingHoursOf(coach.id),
                    linkedAt = link.createdAt,
                )
            }
    }
}
