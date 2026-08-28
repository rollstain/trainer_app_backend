package app.trainer.backend.coach

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CoachRepository : JpaRepository<CoachEntity, UUID> {

    fun findByUserId(userId: UUID): CoachEntity?
}

interface CoachClientRepository : JpaRepository<CoachClientEntity, UUID> {

    fun findByCoachIdAndStatus(coachId: UUID, status: CoachClientStatus): List<CoachClientEntity>

    @Query(
        value = """
            select cc.* from coach_clients cc
            join users u on u.id = cc.user_id
            where cc.coach_id = cast(:coachId as uuid)
              and cc.status = 'ACTIVE'
              and (
                cast(:afterName as text) is null
                or (u.display_name, cc.user_id) > (cast(:afterName as text), cast(:afterId as uuid))
              )
            order by u.display_name, cc.user_id
            limit :pageSize
        """,
        nativeQuery = true,
    )
    fun findActivePage(
        @Param("coachId") coachId: UUID,
        @Param("afterName") afterName: String?,
        @Param("afterId") afterId: UUID?,
        @Param("pageSize") pageSize: Int,
    ): List<CoachClientEntity>

    @Query(
        value = """
            select cc.* from coach_clients cc
            join users u on u.id = cc.user_id
            where cc.coach_id = cast(:coachId as uuid) and cc.status = 'ACTIVE'
            order by u.display_name, cc.user_id
        """,
        nativeQuery = true,
    )
    fun findActiveOrdered(@Param("coachId") coachId: UUID): List<CoachClientEntity>

    @Query(
        value = """
            select cc.* from coach_clients cc
            join users u on u.id = cc.user_id
            where cc.coach_id = cast(:coachId as uuid)
              and cc.status = 'ACTIVE'
              and cc.user_id = any (cast(:userIds as uuid[]))
            order by u.display_name, cc.user_id
        """,
        nativeQuery = true,
    )
    fun findActiveByUserIds(
        @Param("coachId") coachId: UUID,
        @Param("userIds") userIds: Array<UUID>,
    ): List<CoachClientEntity>

    fun findByCoachIdAndUserId(coachId: UUID, userId: UUID): CoachClientEntity?

    fun findByUserId(userId: UUID): List<CoachClientEntity>
}
