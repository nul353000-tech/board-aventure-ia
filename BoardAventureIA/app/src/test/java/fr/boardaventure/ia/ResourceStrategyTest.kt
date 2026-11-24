package fr.boardaventure.ia

import org.junit.Assert.assertEquals
import org.junit.Test

class ResourceStrategyTest {
    @Test fun waitsInsteadOfStartingUnsupervisedAutoWhenResourcesAreHigh() {
        val plan = ResourceStrategy.plan(true, 0.99, "364267595", "120/120", "+0", "x2", "ready", "90000000,125000000,150000000")
        assertEquals(ResourceStrategy.SpinCommand.WAIT, plan.spinCommand)
        assertEquals(listOf(1, 2, 3), plan.affordableCards)
        assertEquals(1, plan.suggestedCard)
    }

    @Test fun recommendsSafeMultiplierUntilRewardEfficiencyIsMeasured() {
        val x2 = ResourceStrategy.plan(true, 0.99, "500000000", "120/120", "+0", "x2", "ready", null)
        val x5 = ResourceStrategy.plan(true, 0.99, "500000000", "120/120", "+0", "x5", "ready", null)
        assertEquals(1, x2.recommendedMultiplier)
        assertEquals(1, x5.recommendedMultiplier)
        assertEquals(2, x2.multiplier)
        assertEquals(5, x5.multiplier)
    }

    @Test fun stopsAutoAtEnergyReserve() {
        val plan = ResourceStrategy.plan(true, 0.99, "100000000", "10/120", "+0", "x1", "auto_active", "90000000")
        assertEquals(ResourceStrategy.SpinCommand.TAP_STOP, plan.spinCommand)
    }

    @Test fun blocksWhenEnergyOrRecognitionIsUncertain() {
        val noEnergy = ResourceStrategy.plan(true, 0.99, "100000000", null, "+100", "x1", "ready", "90000000")
        assertEquals(ResourceStrategy.SpinCommand.BLOCKED, noEnergy.spinCommand)
        val unrecognized = ResourceStrategy.plan(false, 0.99, "100000000", "120/120", "+100", "x1", "ready", "90000000")
        assertEquals(ResourceStrategy.SpinCommand.BLOCKED, unrecognized.spinCommand)
    }

    @Test fun neverTreatsGemsAsCoinsAndKeepsCoinReserve() {
        val plan = ResourceStrategy.plan(true, 0.99, "100000000", "120/120", "+100", "x1", "ready", "95000000,85000000")
        // 95M exceeds the 90M spendable limit; 85M remains affordable after the 10M reserve.
        // Gem balances are never included in this calculation.
        assertEquals(listOf(2), plan.affordableCards)
        assertEquals(2, plan.suggestedCard)
    }

    @Test fun unknownSpinStateBlocksGesture() {
        val plan = ResourceStrategy.plan(true, 0.99, "100000000", "120/120", "+100", "x1", "unknown", "90000000")
        assertEquals(ResourceStrategy.SpinCommand.BLOCKED, plan.spinCommand)
    }
    @Test fun choosesSingleSpinWhenBonusEnergyIsAvailable() {
        val plan = ResourceStrategy.plan(true, 0.99, "100000000", "120/120", "+3155", "x1", "ready", null)
        assertEquals(ResourceStrategy.SpinCommand.TAP_SINGLE, plan.spinCommand)
    }

    @Test fun choosesSingleSpinNearTheNormalEnergyReserve() {
        val plan = ResourceStrategy.plan(true, 0.99, "100000000", "30/120", "+0", "x1", "ready", null)
        assertEquals(ResourceStrategy.SpinCommand.TAP_SINGLE, plan.spinCommand)
    }

    @Test fun doesNotStartAutoWithoutLiveWatchdog() {
        val plan = ResourceStrategy.plan(true, 0.99, "100000000", "60/120", "+0", "x2", "ready", null)
        assertEquals(ResourceStrategy.SpinCommand.WAIT, plan.spinCommand)
    }

    @Test fun blocksWhenBonusEnergyCannotBeRead() {
        val plan = ResourceStrategy.plan(true, 0.99, "100000000", "120/120", null, "x1", "ready", null)
        assertEquals(ResourceStrategy.SpinCommand.BLOCKED, plan.spinCommand)
    }


    @Test fun startsOnlyShortWatchdogAutoAtX1WithEnergyBuffer() {
        val safe = ResourceStrategy.plan(true, 0.99, "100000000", "60/120", "+0", "x1", "ready", null)
        assertEquals(ResourceStrategy.SpinCommand.HOLD_TO_START_AUTO, safe.spinCommand)
        val highMultiplier = ResourceStrategy.plan(true, 0.99, "100000000", "120/120", "+0", "x2", "ready", null)
        assertEquals(ResourceStrategy.SpinCommand.WAIT, highMultiplier.spinCommand)
        val lowBuffer = ResourceStrategy.plan(true, 0.99, "100000000", "50/120", "+0", "x1", "ready", null)
        assertEquals(ResourceStrategy.SpinCommand.WAIT, lowBuffer.spinCommand)
    }

    @Test fun alwaysStopsAnAutoSessionDetectedByVerification() {
        val plan = ResourceStrategy.plan(true, 0.99, "100000000", "100/120", "+0", "x1", "auto_active", null)
        assertEquals(ResourceStrategy.SpinCommand.TAP_STOP, plan.spinCommand)
    }

}