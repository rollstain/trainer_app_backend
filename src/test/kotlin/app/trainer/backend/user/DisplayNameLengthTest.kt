package app.trainer.backend.user

import app.trainer.backend.admin.CreateCoachRequest
import app.trainer.backend.auth.RedeemInviteRequest
import app.trainer.backend.auth.password.PasswordSignUpRequest
import jakarta.validation.Validation
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

private const val DEVICE_INFO = "iPhone · Safari"

class DisplayNameLengthTest {

    private val validator = Validation.buildDefaultValidatorFactory().validator

    private val longest = "а".repeat(DISPLAY_NAME_MAX_LENGTH)
    private val tooLong = "а".repeat(DISPLAY_NAME_MAX_LENGTH + 1)

    @Test
    fun `a name of the longest allowed length passes everywhere a name is typed`() {
        assertTrue(validator.validate(signUp(longest)).isEmpty())
        assertTrue(validator.validate(redeem(longest)).isEmpty())
        assertTrue(validator.validate(becomeCoach(longest)).isEmpty())
        assertTrue(validator.validate(onboardCoach(longest)).isEmpty())
        assertTrue(validator.validate(RenameMeRequest(displayName = longest)).isEmpty())
    }

    @Test
    fun `a longer name is refused on the display name field`() {
        assertEquals(setOf("displayName"), refusedFields(signUp(tooLong)))
        assertEquals(setOf("displayName"), refusedFields(redeem(tooLong)))
        assertEquals(setOf("displayName"), refusedFields(becomeCoach(tooLong)))
        assertEquals(setOf("displayName"), refusedFields(onboardCoach(tooLong)))
        assertEquals(setOf("displayName"), refusedFields(RenameMeRequest(displayName = tooLong)))
    }

    @Test
    fun `a blank name is not a new name`() {
        assertEquals(setOf("displayName"), refusedFields(RenameMeRequest(displayName = "   ")))
    }

    private fun refusedFields(request: Any): Set<String> =
        validator.validate(request).map { it.propertyPath.toString() }.toSet()

    private fun signUp(displayName: String) = PasswordSignUpRequest(
        displayName = displayName,
        email = "anna@example.com",
        login = null,
        password = "достаточно-длинный",
        deviceInfo = DEVICE_INFO,
    )

    private fun redeem(displayName: String) = RedeemInviteRequest(
        code = "K7M3Q9",
        displayName = displayName,
        deviceInfo = DEVICE_INFO,
    )

    private fun becomeCoach(displayName: String) = BecomeCoachRequest(
        displayName = displayName,
        zoneId = "Europe/Moscow",
    )

    private fun onboardCoach(displayName: String) = CreateCoachRequest(
        displayName = displayName,
        zoneId = "Europe/Moscow",
        phone = null,
        email = null,
    )
}
