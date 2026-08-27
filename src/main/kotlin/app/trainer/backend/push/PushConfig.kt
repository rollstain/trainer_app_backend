package app.trainer.backend.push

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import java.io.File
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

private const val FIREBASE_APP_NAME = "trainer-push"
private const val CONFIGURED_CREDENTIALS_EXPRESSION = "'\${trainer.push.credentials-path:}'.trim().length() > 0"

@ConfigurationProperties(prefix = "trainer.push")
data class PushProperties(val credentialsPath: String)

@Configuration
@EnableConfigurationProperties(PushProperties::class)
class PushConfig {

    @Bean
    @ConditionalOnExpression(CONFIGURED_CREDENTIALS_EXPRESSION)
    fun firebaseMessaging(properties: PushProperties): FirebaseMessaging {
        val credentialsFile = File(properties.credentialsPath)
        require(credentialsFile.isFile) {
            "Файл сервисного аккаунта Firebase не найден: ${properties.credentialsPath}"
        }
        val options = FirebaseOptions.builder()
            .setCredentials(credentialsFile.inputStream().use(GoogleCredentials::fromStream))
            .build()
        val existing = FirebaseApp.getApps().firstOrNull { it.name == FIREBASE_APP_NAME }
        val app = existing ?: FirebaseApp.initializeApp(options, FIREBASE_APP_NAME)
        return FirebaseMessaging.getInstance(app)
    }

    @Bean
    @ConditionalOnExpression(CONFIGURED_CREDENTIALS_EXPRESSION)
    fun fcmPushSender(
        messaging: FirebaseMessaging,
        tokenRepository: PushTokenRepository,
        pushTexts: PushTexts,
    ): PushSender = FcmPushSender(
        messaging = messaging,
        tokenRepository = tokenRepository,
        pushTexts = pushTexts,
    )

    @Bean
    @ConditionalOnMissingBean(PushSender::class)
    fun noOpPushSender(): PushSender = NoOpPushSender()
}
