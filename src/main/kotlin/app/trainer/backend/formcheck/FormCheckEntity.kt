package app.trainer.backend.formcheck

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository

@Entity
@Table(name = "form_checks")
class FormCheckEntity(

    @Id
    @Column(name = "id")
    val id: UUID,

    @Column(name = "client_user_id")
    val clientUserId: UUID,

    @Column(name = "coach_id")
    val coachId: UUID,

    @Column(name = "exercise_id")
    val exerciseId: UUID?,

    @Column(name = "media_file_id")
    val mediaFileId: UUID,

    @Column(name = "note")
    var note: String?,

    @Column(name = "coach_comment")
    var coachComment: String?,

    @Column(name = "reviewed_at")
    var reviewedAt: Instant?,

    @Column(name = "reviewed_by_coach_id")
    var reviewedByCoachId: UUID?,

    @Column(name = "created_at")
    val createdAt: Instant,
)

interface FormCheckRepository : JpaRepository<FormCheckEntity, UUID> {

    fun findByClientUserIdOrderByCreatedAtDesc(clientUserId: UUID, limit: Limit): List<FormCheckEntity>

    fun findByCoachIdAndReviewedAtIsNullOrderByCreatedAtDesc(
        coachId: UUID,
        limit: Limit,
    ): List<FormCheckEntity>
}
