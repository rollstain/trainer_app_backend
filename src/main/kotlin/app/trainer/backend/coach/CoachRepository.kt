package app.trainer.backend.coach

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface CoachRepository : JpaRepository<CoachEntity, UUID> {

    fun findByUserId(userId: UUID): CoachEntity?
}

interface CoachClientRepository : JpaRepository<CoachClientEntity, UUID> {

    fun findByCoachIdAndStatus(coachId: UUID, status: CoachClientStatus): List<CoachClientEntity>

    fun findByCoachIdAndUserId(coachId: UUID, userId: UUID): CoachClientEntity?

    fun findByUserId(userId: UUID): List<CoachClientEntity>
}
