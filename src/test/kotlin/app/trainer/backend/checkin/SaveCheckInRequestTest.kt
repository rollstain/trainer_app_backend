package app.trainer.backend.checkin

import jakarta.validation.Validation
import jakarta.validation.Validator
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

private const val REAL_WEIGHT_GRAMS = 82_400
private const val TYPO_WEIGHT_GRAMS = 824_000
private const val REAL_WAIST_MILLIMETERS = 800
private const val TYPO_WAIST_MILLIMETERS = 8_000

class SaveCheckInRequestTest {

    private val validator: Validator = Validation.buildDefaultValidatorFactory().validator

    @Test
    fun `a plausible check-in passes`() {
        val violations = validator.validate(checkIn(weightGrams = REAL_WEIGHT_GRAMS))

        assertTrue(violations.isEmpty(), "82,4 кг и талия 80 см — обычные значения")
    }

    @Test
    fun `a weight ten times too big is rejected`() {
        val violations = validator.validate(checkIn(weightGrams = TYPO_WEIGHT_GRAMS))

        assertEquals(1, violations.size, "824 кг — опечатка, а не вес")
        assertEquals("weightGrams", violations.first().propertyPath.toString())
    }

    @Test
    fun `a girth ten times too big is rejected`() {
        val violations = validator.validate(checkIn(waistMillimeters = TYPO_WAIST_MILLIMETERS))

        assertEquals(1, violations.size, "талия 800 см — опечатка")
        assertEquals("waistMillimeters", violations.first().propertyPath.toString())
    }

    @Test
    fun `a check-in without measurements passes`() {
        val violations = validator.validate(checkIn(weightGrams = null, waistMillimeters = null))

        assertTrue(violations.isEmpty(), "замеры необязательны")
    }

    private fun checkIn(
        weightGrams: Int? = REAL_WEIGHT_GRAMS,
        waistMillimeters: Int? = REAL_WAIST_MILLIMETERS,
    ): SaveCheckInRequest = SaveCheckInRequest(
        weightGrams = weightGrams,
        waistMillimeters = waistMillimeters,
        chestMillimeters = null,
        hipsMillimeters = null,
        wellbeing = null,
        sleepQuality = null,
        adherence = null,
        notes = null,
        photoIds = emptyList(),
    )
}
