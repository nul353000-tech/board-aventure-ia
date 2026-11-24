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
        width: Int = 1080,
        height: Int = 2400
    ) = ActionPolicy.validate(foreground, recognized, confidence, action, x, y, width, height)

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
        assertFalse(validate(x = -1).allowed)
        assertFalse(validate(y = 2400).allowed)
        assertFalse(validate(x = 1080).allowed)
        assertFalse(validate(width = 1).allowed)
        assertFalse(validate(height = 0).allowed)
    }

    @Test fun confirmedScreenshotZonesAreAuthorizedButUnmappedRewardIsBlocked() {
        val spin = ActionPolicy.controlPreview("spin", 1080, 2400)!!
        assertTrue(validate(x = spin.first, y = spin.second).allowed)
        val village = ActionPolicy.villageCardPreview(3, 1080, 2400)!!
        assertTrue(validate(action = "upgrade_village", x = village.first, y = village.second).allowed)
        assertFalse(validate(action = "collect_free_reward").allowed)
        assertTrue(ActionPolicy.calibrationStatus().contains("FREE_REWARD"))
    }

    @Test fun multiplierZoneIsValidatedAndWrongAspectRatioIsRejected() {
        val point = ActionPolicy.controlPreview("multiplier", 684, 1536)!!
        assertTrue(validate(action = "set_multiplier", x = point.first, y = point.second, width = 684, height = 1536).allowed)
        assertFalse(validate(width = 1080, height = 1920).allowed)
    }

    @Test fun villageCardTargetsArePreviewOnlyAndNumberedLeftToRight() {
        val width = 684
        val height = 1536
        val centers = (1..5).mapNotNull { ActionPolicy.villageCardPreview(it, width, height) }
        assertTrue(centers.size == 5)
        assertTrue(centers.zipWithNext().all { (a, b) -> a.first < b.first })
        assertTrue(centers.all { it.second in 1478..1525 })
        assertTrue(ActionPolicy.villageCardPreview(6, width, height) == null)
        assertTrue(ActionPolicy.villageCardPreview(1, 1080, 2400) != null)
        assertTrue(ActionPolicy.villageCardCalibrationStatus().contains("simulation uniquement"))
    }

    @Test fun malformedZoneGeometryIsRejected() {
        val invalid = ActionPolicy.Hitbox(ActionPolicy.ZoneId.SPIN, 0.8f, 0.8f, 0.2f, 0.2f, true)
        assertFalse(invalid.isValid())
        val outOfBounds = ActionPolicy.Hitbox(ActionPolicy.ZoneId.SPIN, -0.1f, 0f, 0.5f, 0.5f, true)
        assertFalse(outOfBounds.isValid())
    }
    @Test fun spinAndMultiplierTargetsArePreviewOnlyOnReferenceAspectRatio() {
        val spin = ActionPolicy.controlPreview("spin", 684, 1536)
        val multiplier = ActionPolicy.controlPreview("multiplier", 684, 1536)
        assertTrue(spin != null)
        assertTrue(multiplier != null)
        assertTrue(spin!!.first < multiplier!!.first)
        assertTrue(ActionPolicy.controlPreview("spin", 1080, 2400) != null)
        assertTrue(ActionPolicy.controlPreview("unknown", 684, 1536) == null)
    }

}
