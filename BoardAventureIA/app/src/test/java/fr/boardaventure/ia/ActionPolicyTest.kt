package fr.boardaventure.ia

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionPolicyTest {
    private fun validate(foreground: String? = ActionPolicy.TARGET_PACKAGE, recognized: Boolean = true,
        confidence: Double = 0.99, action: String = "spin", x: Int? = 400, y: Int? = 800,
        turns: Long? = 6L, width: Int = 1080, height: Int = 2400) =
        ActionPolicy.validate(foreground, recognized, confidence, action, x, y, width, height, turns)

    @Test fun wrongPackageIsRejected() {
        assertFalse(validate(foreground = "com.other.app").allowed)
        assertFalse(validate(foreground = null).allowed)
    }
    @Test fun invalidConfidenceIsRejected() {
        assertFalse(validate(recognized = false).allowed)
        assertFalse(validate(confidence = 0.92).allowed)
        assertFalse(validate(confidence = Double.NaN).allowed)
        assertFalse(validate(confidence = Double.POSITIVE_INFINITY).allowed)
        assertFalse(validate(confidence = 1.01).allowed)
        assertFalse(validate(confidence = -0.1).allowed)
    }
    @Test fun unknownActionsAreRejected() {
        assertFalse(validate(action = "none").allowed)
        assertFalse(validate(action = "purchase").allowed)
        assertFalse(validate(action = "").allowed)
    }
    @Test fun invalidCoordinatesAndDimensionsAreRejected() {
        assertFalse(validate(x = null).allowed)
        assertFalse(validate(y = null).allowed)
        assertFalse(validate(x = 0).allowed)
        assertFalse(validate(y = 2400).allowed)
        assertFalse(validate(x = 1080).allowed)
        assertFalse(validate(width = 1).allowed)
        assertFalse(validate(height = 0).allowed)
    }
    @Test fun spinRequiresMoreThanFiveKnownTurns() {
        assertFalse(validate(turns = null).allowed)
        assertFalse(validate(turns = 5L).allowed)
        assertFalse(validate(turns = 4L).allowed)
    }
    @Test fun validLookingActionStillRequiresHumanValidation() {
        val decision = validate(turns = 6L)
        assertFalse(decision.allowed)
        assertTrue(decision.explanation.contains("validation humaine"))
    }
    @Test fun freeActionsRemainLockedUntilCalibration() {
        assertFalse(validate(action = "collect_free_reward").allowed)
        assertFalse(validate(action = "upgrade_village").allowed)
    }
}