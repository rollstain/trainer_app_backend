package app.trainer.backend.config

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

private const val UNEXPECTED_FAILURE_MESSAGE = "Что-то пошло не так, попробуйте позже"
private const val VALIDATION_FAILURE_MESSAGE = "Проверьте заполненные поля"

data class ApiErrorResponse(
    val status: Int,
    val message: String,
    val fieldErrors: Map<String, String>,
)

@RestControllerAdvice
class ApiErrorHandler {

    private val logger = LoggerFactory.getLogger(ApiErrorHandler::class.java)

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatus(failure: ResponseStatusException): ResponseEntity<ApiErrorResponse> {
        val status = HttpStatus.valueOf(failure.statusCode.value())
        val message = failure.reason ?: status.reasonPhrase
        return ResponseEntity.status(status).body(
            ApiErrorResponse(status = status.value(), message = message, fieldErrors = emptyMap())
        )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(failure: MethodArgumentNotValidException): ResponseEntity<ApiErrorResponse> {
        val fieldErrors = failure.bindingResult.fieldErrors.associate { fieldError ->
            fieldError.field to (fieldError.defaultMessage ?: VALIDATION_FAILURE_MESSAGE)
        }
        return ResponseEntity.badRequest().body(
            ApiErrorResponse(
                status = HttpStatus.BAD_REQUEST.value(),
                message = VALIDATION_FAILURE_MESSAGE,
                fieldErrors = fieldErrors,
            )
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(failure: Exception): ResponseEntity<ApiErrorResponse> {
        logger.error("Необработанная ошибка", failure)
        return ResponseEntity.internalServerError().body(
            ApiErrorResponse(
                status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
                message = UNEXPECTED_FAILURE_MESSAGE,
                fieldErrors = emptyMap(),
            )
        )
    }
}
