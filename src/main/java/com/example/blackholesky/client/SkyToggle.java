package com.example.blackholesky.client;

/** Interruptor del cielo del agujero negro (/shader cielo on|off). */
public final class SkyToggle {
    private static volatile boolean enabled = false;

    private SkyToggle() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }
}
