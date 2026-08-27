package app.trainer.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableAsync
@EnableScheduling
class TrainerApplication

fun main(args: Array<String>) {
    runApplication<TrainerApplication>(*args)
}
