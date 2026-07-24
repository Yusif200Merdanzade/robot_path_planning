package com.example.robotPath.fuzzy;

/**
 * Output linguistic variable of the Mamdani FIS.
 * Each level carries the singleton centroid used during defuzzification.
 */
public enum AttentionLevel {
    NONE(0, "CONTINUE", "Diqqət tələb olunmur — robot davam edir"),
    VERY_LOW(15, "CONTINUE", "Çox aşağı diqqət — robot davam edir"),
    LOW(30, "SLOW_DOWN", "Aşağı diqqət — robot sürəti azaldır"),
    MEDIUM(50, "SLOW_DOWN", "Orta diqqət — robot ehtiyatla davam edir"),
    HIGH(70, "STOP", "Yüksək diqqət tələb olunur — robot dayanır"),
    VERY_HIGH(90, "STOP", "Çox yüksək diqqət tələb olunur — robot dərhal dayanır");

    private final double centroid;
    private final String robotAction;
    private final String message;

    AttentionLevel(double centroid, String robotAction, String message) {
        this.centroid = centroid;
        this.robotAction = robotAction;
        this.message = message;
    }

    public double getCentroid() {
        return centroid;
    }

    public String getRobotAction() {
        return robotAction;
    }

    public String getMessage() {
        return message;
    }
}
