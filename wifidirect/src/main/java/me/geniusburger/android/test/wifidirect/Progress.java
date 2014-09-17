package me.geniusburger.android.test.wifidirect;

public enum Progress {
    START(0, "WiFi Disabled"),
    ENABLED(1, "WiFi Enabled"),
    DISCOVER(2, "Discovering Peers"),
    FOUND_PEERS(3, "Found Peers"),
    CONNECTED(4, "Connected"),
    TALKING(5, "Talking");

    private int value;
    private String text;

    public static int max = 0;

    static {
        for( Progress p : Progress.values()) {
            if( p.value > max) {
                max = p.value;
            }
        }
    }

    private Progress(int value, String text) {
        this.value = value;
        this.text = text;
    }

    public int getValue() {
        return value;
    }

    public String getText() {
        return text;
    }

    public static int getMax() {
        return max;
    }
}
