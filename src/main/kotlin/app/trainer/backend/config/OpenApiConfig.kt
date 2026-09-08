package app.trainer.backend.config

import org.springdoc.core.utils.SpringDocUtils
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    init {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(
            CurrentUserId::class.java,
            CurrentSessionId::class.java,
        )
    }
}
