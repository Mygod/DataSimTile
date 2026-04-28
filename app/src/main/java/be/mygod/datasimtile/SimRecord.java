package be.mygod.datasimtile;

record SimRecord(int subId, int slotIndex, String name) {
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
