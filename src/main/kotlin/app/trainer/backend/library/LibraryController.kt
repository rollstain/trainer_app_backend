package app.trainer.backend.library

import app.trainer.backend.config.CurrentUserId
import app.trainer.backend.habit.HabitSetService
import app.trainer.backend.program.ProgramService
import app.trainer.backend.traininglog.TrainingLogService
import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

data class LibraryCountsResponse(
    val programs: Long,
    val exercises: Long,
    val habitSets: Long,
)

@RestController
class LibraryController(
    private val programService: ProgramService,
    private val trainingLogService: TrainingLogService,
    private val habitSetService: HabitSetService,
) {

    @GetMapping("/coach/library/counts")
    fun counts(@CurrentUserId coachUserId: UUID): LibraryCountsResponse {
        return LibraryCountsResponse(
            programs = programService.programsCount(coachUserId = coachUserId),
            exercises = trainingLogService.availableExercisesCount(userId = coachUserId),
            habitSets = habitSetService.setsCount(coachUserId = coachUserId),
        )
    }
}
