package be.mygod.datasimtile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class SimSelector {
    private static final Comparator<SimRecord> SIM_ORDER = Comparator.comparingInt((SimRecord left) -> left.slotIndex())
            .thenComparingInt(left -> left.subId());

    private SimSelector() {
    }

    static List<SimRecord> sortedActive(List<SimRecord> sims) {
        ArrayList<SimRecord> result = new ArrayList<>();
        for (SimRecord sim : sims) {
            if (sim.slotIndex() >= 0) result.add(sim);
        }
        result.sort(SIM_ORDER);
        return result;
    }

    static SimRecord findCurrent(List<SimRecord> sims, int currentSubId) {
        for (SimRecord sim : sims) {
            if (sim.subId() == currentSubId) return sim;
        }
        return null;
    }

    static SimRecord nextAfter(List<SimRecord> sims, int currentSubId) {
        if (sims.size() < 2) return null;
        for (int i = 0; i < sims.size(); ++i) {
            if (sims.get(i).subId() == currentSubId) return sims.get((i + 1) % sims.size());
        }
        return sims.get(0);
    }
}
