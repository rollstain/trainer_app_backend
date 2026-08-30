package app.trainer.backend.formcheck

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

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

    @Query(
        value = """
            select f.* from form_checks f
            where f.client_user_id = :clientUserId
              and (
                cast(:afterCreatedAt as text) is null
                or (f.created_at, f.id) < (cast(:afterCreatedAt as timestamptz), cast(:afterId as uuid))
              )
            order by f.created_at desc, f.id desc
            limit :pageSize
        """,
        nativeQuery = true,
    )
    fun findClientPage(
        @Param("clientUserId") clientUserId: UUID,
        @Param("afterCreatedAt") afterCreatedAt: String?,
        @Param("afterId") afterId: UUID?,
        @Param("pageSize") pageSize: Int,
    ): List<FormCheckEntity>

    @Query(
        value = """
            select f.* from form_checks f
            where f.coach_id = :coachId
              and f.reviewed_at is null
              and (
                cast(:afterCreatedAt as text) is null
                or (f.created_at, f.id) < (cast(:afterCreatedAt as timestamptz), cast(:afterId as uuid))
              )
            order by f.created_at desc, f.id desc
            limit :pageSize
        """,
        nativeQuery = true,
    )
    fun findAwaitingPage(
        @Param("coachId") coachId: UUID,
        @Param("afterCreatedAt") afterCreatedAt: String?,
        @Param("afterId") afterId: UUID?,
        @Param("pageSize") pageSize: Int,
    ): List<FormCheckEntity>
}
