package app.trainer.backend.notification

import app.trainer.backend.push.NotificationReason
import app.trainer.backend.push.PushText
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

@Entity
@Table(name = "notifications")
class NotificationEntity(

    @Id
    @Column(name = "id")
    val id: UUID,

    @Column(name = "user_id")
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "kind")
    val kind: PushText,

    @Column(name = "args")
    val args: String,

    @Column(name = "data")
    val data: String,

    @Column(name = "created_at")
    val createdAt: Instant,

    @Column(name = "read_at")
    var readAt: Instant?,
)

@Entity
@Table(name = "notification_settings")
class NotificationSettingEntity(

    @Id
    @Column(name = "id")
    val id: UUID,

    @Column(name = "user_id")
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "reason")
    val reason: NotificationReason,

    @Column(name = "push_enabled")
    var pushEnabled: Boolean,
)

interface NotificationRepository : JpaRepository<NotificationEntity, UUID> {

    @Query(
        value = """
            select n.* from notifications n
            where n.user_id = :userId
              and (
                cast(:beforeCreatedAt as text) is null
                or (n.created_at, n.id) < (cast(:beforeCreatedAt as timestamptz), cast(:beforeId as uuid))
              )
            order by n.created_at desc, n.id desc
            limit :pageSize
        """,
        nativeQuery = true,
    )
    fun findPage(
        @Param("userId") userId: UUID,
        @Param("beforeCreatedAt") beforeCreatedAt: String?,
        @Param("beforeId") beforeId: UUID?,
        @Param("pageSize") pageSize: Int,
    ): List<NotificationEntity>

    fun countByUserIdAndReadAtIsNull(userId: UUID): Long

    @Modifying
    @Query("update NotificationEntity n set n.readAt = :readAt where n.userId = :userId and n.readAt is null")
    fun markAllRead(@Param("userId") userId: UUID, @Param("readAt") readAt: Instant): Int
}

interface NotificationSettingRepository : JpaRepository<NotificationSettingEntity, UUID> {

    fun findByUserId(userId: UUID): List<NotificationSettingEntity>

    fun findByUserIdAndReason(userId: UUID, reason: NotificationReason): NotificationSettingEntity?

    fun findByUserIdInAndReasonAndPushEnabledFalse(
        userIds: Collection<UUID>,
        reason: NotificationReason,
    ): List<NotificationSettingEntity>
}
