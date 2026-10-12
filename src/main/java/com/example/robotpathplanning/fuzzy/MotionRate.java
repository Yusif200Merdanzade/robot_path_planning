package com.example.robotpathplanning.fuzzy;

import static java.lang.Double.NEGATIVE_INFINITY;
import static java.lang.Double.POSITIVE_INFINITY;

/**
 * Giriş dilçi dəyişəni: operatorun hərəkət sürəti, [0, 100] aralığında.
 * Hər termin öz trapesiya üzvlük funksiyasının 4 nöqtəsini (a, b, c, d) saxlayır.
 */
public enum MotionRate {
    VERY_SLOW(NEGATIVE_INFINITY, NEGATIVE_INFINITY, 20, 25),
    SLOW(20, 25, 40, 50),
    NORMAL(40, 50, 60, 70),
    FAST(60, 70, 80, 85),
    VERY_FAST(80, 85, POSITIVE_INFINITY, POSITIVE_INFINITY);

    private final double a, b, c, d;

    MotionRate(double a, double b, double c, double d) {
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
    }

    public double membership(double x) {
        return MamdaniFuzzyInferenceSystem.trapezoid(x, a, b, c, d);
    }
}
