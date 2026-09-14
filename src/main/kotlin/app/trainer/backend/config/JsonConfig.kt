package app.trainer.backend.config

import com.fasterxml.jackson.annotation.JsonInclude
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JsonConfig {

    @Bean
    fun absentValuesOmitted(): Jackson2ObjectMapperBuilderCustomizer =
        Jackson2ObjectMapperBuilderCustomizer { builder ->
            builder.serializationInclusion(JsonInclude.Include.NON_NULL)
        }
}
