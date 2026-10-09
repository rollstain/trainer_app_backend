package app.trainer.backend.coach

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component

@Entity
@Table(name = "coach_quiet_hours")
class CoachQuietHoursEntity(

    @Id
    @Column(name = "coach_id")
    val coachId: UUID,

    @Column(name = "enabled")
    var enabled: Boolean,

    @Column(name = "starts_at")
    var startsAt: LocalTime,

    @Column(name = "ends_at")
    var endsAt: LocalTime,
)

interface CoachQuietHoursRepository : JpaRepository<CoachQuietHoursEntity, UUID> {

    fun findByCoachIdInAndEnabledTrue(coachIds: Collection<UUID>): List<CoachQuietHoursEntity>
}

data class QuietWindow(val startsAt: LocalTime, val endsAt: LocalTime, val zone: ZoneId) {

    fun covers(time: LocalTime): Boolean =
        if (startsAt < endsAt) time >= startsAt && time < endsAt else time >= startsAt || time < endsAt

    fun endAfter(now: Instant): Instant? {
        val local = now.atZone(zone)
        val time = local.toLocalTime()
        if (!covers(time)) return null
        val today = local.toLocalDate()
        val endsOn = if (startsAt > endsAt && time >= startsAt) today.plusDays(1) else today
        return endsOn.atTime(endsAt).atZone(zone).toInstant()
    }
}

@Component
class CoachQuietHoursLookup(
    private val coachRepository: CoachRepository,
    private val quietHoursRepository: CoachQuietHoursRepository,
) {

    fun windowsOf(userIds: Collection<UUID>): Map<UUID, QuietWindow> {
        val coaches = coachRepository.findByUserIdIn(userIds)
        if (coaches.isEmpty()) return emptyMap()
        val quietByCoach = quietHoursRepository
            .findByCoachIdInAndEnabledTrue(coaches.map { it.id })
            .associateBy { it.coachId }
        val windows = mutableMapOf<UUID, QuietWindow>()
        for (coach in coaches) {
            val quiet = quietByCoach[coach.id]
            val zone = runCatching { ZoneId.of(coach.zoneId) }.getOrNull()
            if (quiet == null || zone == null) continue
            windows[coach.userId] = QuietWindow(startsAt = quiet.startsAt, endsAt = quiet.endsAt, zone = zone)
        }
        return windows
    }

    fun windowOf(coach: CoachEntity, zone: ZoneId): QuietWindow? {
        val quiet = quietHoursRepository.findByCoachIdInAndEnabledTrue(listOf(coach.id)).firstOrNull() ?: return null
        return QuietWindow(startsAt = quiet.startsAt, endsAt = quiet.endsAt, zone = zone)
    }
}
