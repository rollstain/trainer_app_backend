package app.trainer.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableScheduling
class TrainerApplication

fun main(args: Array<String>) {
    runApplication<TrainerApplication>(*args)
}
