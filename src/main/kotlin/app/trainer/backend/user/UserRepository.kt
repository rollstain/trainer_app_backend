package app.trainer.backend.user

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<UserEntity, UUID> {

    fun findByPhone(phone: String): UserEntity?

    fun findByEmail(email: String): UserEntity?

    fun findByLogin(login: String): UserEntity?
}
