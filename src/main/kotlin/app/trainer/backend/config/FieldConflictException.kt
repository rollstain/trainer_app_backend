package app.trainer.backend.config

class FieldConflictException(
    val field: String,
    val explanation: String,
) : RuntimeException(explanation)
