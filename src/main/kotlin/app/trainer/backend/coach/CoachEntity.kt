package app.trainer.backend.coach

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "coaches")
class CoachEntity(

    @Id
    @Column(name = "id")
    val id: UUID,

    @Column(name = "user_id")
    val userId: UUID,

    @Column(name = "zone_id")
    var zoneId: String,

    @Column(name = "cancellation_window_hours")
    var cancellationWindowHours: Int,

    @Column(name = "reminder_hour")
    var reminderHour: Int,

    @Column(name = "session_reminders_enabled")
    var sessionRemindersEnabled: Boolean,

    @Column(name = "diary_reminders_enabled")
    var diaryRemindersEnabled: Boolean,

    @Column(name = "check_in_reminders_enabled")
    var checkInRemindersEnabled: Boolean,

    @Column(name = "is_owner")
    var isOwner: Boolean,

    @Column(name = "created_at")
    val createdAt: Instant,
)
