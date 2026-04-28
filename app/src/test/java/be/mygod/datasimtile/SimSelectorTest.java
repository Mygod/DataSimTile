package be.mygod.datasimtile;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class SimSelectorTest {
    @Test
    public void chooseNameUsesDisplayThenCarrierThenSlot() {
        assertEquals("Display", SimRecord.chooseName(" Display ", "Carrier", 0));
        assertEquals("Carrier", SimRecord.chooseName(" ", " Carrier ", 0));
        assertEquals("SIM 2", SimRecord.chooseName(null, "", 1));
    }

    @Test
    public void sortedActiveFiltersInactiveAndSortsBySlotThenSubId() {
        List<SimRecord> result = SimSelector.sortedActive(Arrays.asList(
                new SimRecord(30, 1, "B"),
                new SimRecord(10, -1, "Inactive"),
                new SimRecord(20, 0, "A"),
                new SimRecord(15, 0, "A2")));
        assertEquals(15, result.get(0).subId);
        assertEquals(20, result.get(1).subId);
        assertEquals(30, result.get(2).subId);
    }

    @Test
    public void nextAfterWrapsToFirstSim() {
        List<SimRecord> sims = SimSelector.sortedActive(Arrays.asList(
                new SimRecord(14, 0, "A"),
                new SimRecord(15, 1, "B")));
        assertEquals(15, SimSelector.nextAfter(sims, 14).subId);
        assertEquals(14, SimSelector.nextAfter(sims, 15).subId);
    }

    @Test
    public void nextAfterUnknownCurrentUsesFirstSim() {
        List<SimRecord> sims = SimSelector.sortedActive(Arrays.asList(
                new SimRecord(14, 0, "A"),
                new SimRecord(15, 1, "B")));
        assertEquals(14, SimSelector.nextAfter(sims, 99).subId);
    }

    @Test
    public void nextAfterSingleOrEmptySimHasNoTarget() {
        assertNull(SimSelector.nextAfter(SimSelector.sortedActive(Arrays.asList(
                new SimRecord(14, 0, "A"))), 14));
        assertNull(SimSelector.nextAfter(SimSelector.sortedActive(Arrays.asList()), 14));
    }
}
