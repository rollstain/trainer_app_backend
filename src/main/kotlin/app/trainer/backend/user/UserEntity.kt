package app.trainer.backend.user

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
class UserEntity(

    @Id
    @Column(name = "id")
    val id: UUID,

    @Column(name = "display_name")
    var displayName: String,

    @Column(name = "phone")
    var phone: String?,

    @Column(name = "email")
    var email: String?,

    @Column(name = "created_at")
    val createdAt: Instant,
)
