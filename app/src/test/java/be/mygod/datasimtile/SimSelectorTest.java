package be.mygod.datasimtile;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public final class SimSelectorTest {
    @Test
    public void chooseNameUsesDisplayThenCarrierThenSlot() {
        assertEquals("Display", SimRecord.chooseName(" Display ", "Carrier", 0));
        assertEquals("Carrier", SimRecord.chooseName(" ", " Carrier ", 0));
        assertEquals("SIM 2", SimRecord.chooseName(null, "", 1));
    }

    @Test
    public void sortedActiveFiltersInactiveAndSortsBySlotThenSubId() {
        SimRecord sim30 = new SimRecord(30, 1, "B");
        SimRecord inactive = new SimRecord(10, -1, "Inactive");
        SimRecord sim20 = new SimRecord(20, 0, "A");
        SimRecord sim15 = new SimRecord(15, 0, "A2");
        List<SimRecord> result = SimSelector.sortedActive(Arrays.asList(
                sim30, inactive, sim20, sim15));
        assertSame(sim15, result.get(0));
        assertSame(sim20, result.get(1));
        assertSame(sim30, result.get(2));
    }

    @Test
    public void nextAfterWrapsToFirstSim() {
        SimRecord first = new SimRecord(14, 0, "A");
        SimRecord second = new SimRecord(15, 1, "B");
        List<SimRecord> sims = SimSelector.sortedActive(Arrays.asList(
                first, second));
        assertSame(second, SimSelector.nextAfter(sims, 14));
        assertSame(first, SimSelector.nextAfter(sims, 15));
    }

    @Test
    public void nextAfterUnknownCurrentUsesFirstSim() {
        SimRecord first = new SimRecord(14, 0, "A");
        List<SimRecord> sims = SimSelector.sortedActive(Arrays.asList(
                first,
                new SimRecord(15, 1, "B")));
        assertSame(first, SimSelector.nextAfter(sims, 99));
    }

    @Test
    public void nextAfterSingleOrEmptySimHasNoTarget() {
        assertNull(SimSelector.nextAfter(SimSelector.sortedActive(Arrays.asList(
                new SimRecord(14, 0, "A"))), 14));
        assertNull(SimSelector.nextAfter(SimSelector.sortedActive(Arrays.asList()), 14));
    }
}
