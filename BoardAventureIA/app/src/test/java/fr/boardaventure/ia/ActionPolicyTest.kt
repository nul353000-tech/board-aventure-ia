package fr.boardaventure.ia

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionPolicyTest {
    private fun validate(
        foreground: String? = ActionPolicy.TARGET_PACKAGE,
        recognized: Boolean = true,
        confidence: Double = 0.99,
        action: String = "spin",
        x: Int? = 400,
        y: Int? = 800,
        turns: Long? = 6L
    ) = ActionPolicy.validate(
        foreground, recognized, confidence, action, x, y,
        screenWidth = 1080, screenHeight = 2400, turns = turns
    )

    @Test fun wrongPackageIsAlwaysRejected() {
        assertFalse(validate(foreground = "com.other.app").allowed)
    }

    @Test fun unrecognizedOrLowConfidenceScreenIsRejected() {
        assertFalse(validate(recognized = false).allowed)
        assertFalse(validate(confidence = 0.92).allowed)
    }

    @Test fun unknownAndDisallowedActionsAreRejected() {
        assertFalse(validate(action = "none").allowed)
        assertFalse(validate(action = "purchase").allowed)
    }

    @Test fun missingOrOutOfBoundsCoordinatesAreRejected() {
        assertFalse(validate(x = null).allowed)
        assertFalse(validate(y = null).allowed)
        assertFalse(validate(x = 0).allowed)
        assertFalse(validate(y = 2400).allowed)
    }

    @Test fun spinRequiresMoreThanFiveKnownTurns() {
        assertFalse(validate(turns = null).allowed)
        assertFalse(validate(turns = 5L).allowed)
        assertFalse(validate(turns = 4L).allowed)
    }

    @Test fun calibratedActionStillRequiresHumanValidation() {
        val decision = validate(turns = 6L)
        assertFalse(decision.allowed)
        assertTrue(decision.explanation.contains("validation humaine"))
    }
}
