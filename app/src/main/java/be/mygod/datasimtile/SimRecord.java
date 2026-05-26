package be.mygod.datasimtile;

record SimRecord(int subId, int slotIndex, String name) {
    static String chooseName(CharSequence displayName, CharSequence carrierName) {
        String display = clean(displayName);
        if (display != null) return display;
        return clean(carrierName);
    }

    private static String clean(CharSequence value) {
        if (value == null) return null;
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }
}
