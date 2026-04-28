package be.mygod.datasimtile;

final class SimRecord {
    final int subId;
    final int slotIndex;
    final String name;

    SimRecord(int subId, int slotIndex, String name) {
        this.subId = subId;
        this.slotIndex = slotIndex;
        this.name = name;
    }

    static String chooseName(CharSequence displayName, CharSequence carrierName, int slotIndex) {
        String display = clean(displayName);
        if (display != null) return display;
        String carrier = clean(carrierName);
        return carrier != null ? carrier : "SIM " + (slotIndex + 1);
    }

    private static String clean(CharSequence value) {
        if (value == null) return null;
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }
}
