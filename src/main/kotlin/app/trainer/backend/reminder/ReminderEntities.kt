package app.trainer.backend.reminder

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

enum class ReminderKind { SESSION, DIARY_IDLE, CHECK_IN_IDLE }

@Entity
@Table(name = "reminder_log")
class ReminderLogEntity(

    @Id
    @Column(name = "id")
    val id: UUID,

    @Column(name = "user_id")
    val userId: UUID,

    @Column(name = "kind")
    val kind: String,

    @Column(name = "subject")
    val subject: String,

    @Column(name = "sent_at")
    val sentAt: Instant,
)

interface ReminderLogRepository : JpaRepository<ReminderLogEntity, UUID> {

    fun existsByUserIdAndKindAndSubject(userId: UUID, kind: String, subject: String): Boolean
}
