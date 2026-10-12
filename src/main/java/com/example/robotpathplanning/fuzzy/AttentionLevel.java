package com.example.robotpathplanning.fuzzy;

import static com.example.robotpathplanning.fuzzy.AttentionLevel.Action.*;

/**
 * Çıxış dilçi dəyişəni: operatora lazım olan diqqət səviyyəsi.
 *
 * Hər səviyyə defuzzifikasiyada istifadə olunan mərkəz nöqtəsini (centroid),
 * robotun edəcəyi hərəkəti və istifadəçiyə göstəriləcək mesajı saxlayır.
 */
public enum AttentionLevel {
    NONE(0, CONTINUE, "Diqqət tələb olunmur — robot davam edir"),
    VERY_LOW(15, CONTINUE, "Çox aşağı diqqət — robot davam edir"),
    LOW(30, SLOW_DOWN, "Aşağı diqqət — robot sürəti azaldır"),
    MEDIUM(50, SLOW_DOWN, "Orta diqqət — robot ehtiyatla davam edir"),
    HIGH(70, STOP, "Yüksək diqqət tələb olunur — robot dayanır"),
    VERY_HIGH(90, STOP, "Çox yüksək diqqət tələb olunur — robot dərhal dayanır");

    /** Robotun cavabı: davam et, yavaşla, dayan. */
    public enum Action { CONTINUE, SLOW_DOWN, STOP }

    public final double centroid;
    public final Action action;
    public final String message;

    AttentionLevel(double centroid, Action action, String message) {
        this.centroid = centroid;
        this.action = action;
        this.message = message;
    }
}
