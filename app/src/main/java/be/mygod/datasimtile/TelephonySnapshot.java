package be.mygod.datasimtile;

record TelephonySnapshot(int currentSubId, int currentSlotIndex, String currentName,
                         int targetSubId, int targetSlotIndex, String targetName, int simCount) {
    boolean canSwitch() {
        return currentSubId >= 0 && targetSubId >= 0;
    }
}
