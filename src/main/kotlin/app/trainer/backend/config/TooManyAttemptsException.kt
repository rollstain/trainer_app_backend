package app.trainer.backend.config

class TooManyAttemptsException(
    val retryAfterSeconds: Long,
    val explanation: String,
) : RuntimeException(explanation)
