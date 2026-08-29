package app.trainer.backend.owner

import app.trainer.backend.coach.CoachEntity
import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CoachAccountRow {

    fun getCoachId(): UUID

    fun getDisplayName(): String

    fun getCreatedAt(): Instant

    fun getActiveClients(): Long

    fun getOwner(): Boolean
}

interface CoachAccountCardRow {

    fun getCoachId(): UUID

    fun getUserId(): UUID

    fun getDisplayName(): String

    fun getEmail(): String?

    fun getPhone(): String?

    fun getLogin(): String?

    fun getZoneId(): String

    fun getCreatedAt(): Instant

    fun getActiveClients(): Long

    fun getArchivedClients(): Long

    fun getLastSeenAt(): Instant?

    fun getOwner(): Boolean
}

interface OwnerCoachRepository : JpaRepository<CoachEntity, UUID> {

    @Query(
        value = """
            select c.id as coachId,
                   u.display_name as displayName,
                   c.created_at as createdAt,
                   u.is_owner as owner,
                   (
                     select count(*) from coach_clients cc
                     where cc.coach_id = c.id and cc.status = 'ACTIVE'
                   ) as activeClients
            from coaches c
            join users u on u.id = c.user_id
            where (
                cast(:afterCreatedAt as text) is null
                or (c.created_at, c.id) < (cast(:afterCreatedAt as timestamptz), cast(:afterId as uuid))
            )
            order by c.created_at desc, c.id desc
            limit :pageSize
        """,
        nativeQuery = true,
    )
    fun findPage(
        @Param("afterCreatedAt") afterCreatedAt: String?,
        @Param("afterId") afterId: UUID?,
        @Param("pageSize") pageSize: Int,
    ): List<CoachAccountRow>

    @Query(
        value = """
            select c.id as coachId,
                   c.user_id as userId,
                   u.display_name as displayName,
                   u.email as email,
                   u.phone as phone,
                   u.login as login,
                   c.zone_id as zoneId,
                   c.created_at as createdAt,
                   u.is_owner as owner,
                   (
                     select count(*) from coach_clients cc
                     where cc.coach_id = c.id and cc.status = 'ACTIVE'
                   ) as activeClients,
                   (
                     select count(*) from coach_clients cc
                     where cc.coach_id = c.id and cc.status <> 'ACTIVE'
                   ) as archivedClients,
                   (
                     select max(s.last_seen_at) from device_sessions s
                     where s.user_id = c.user_id and s.revoked_at is null
                   ) as lastSeenAt
            from coaches c
            join users u on u.id = c.user_id
            where c.id = cast(:coachId as uuid)
        """,
        nativeQuery = true,
    )
    fun findCard(@Param("coachId") coachId: UUID): CoachAccountCardRow?
}
