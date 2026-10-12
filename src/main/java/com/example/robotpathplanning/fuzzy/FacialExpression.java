package com.example.robotpathplanning.fuzzy;

import static java.lang.Double.NEGATIVE_INFINITY;
import static java.lang.Double.POSITIVE_INFINITY;

/**
 * Giriş dilçi dəyişəni: operatorun üz ifadəsi, [0, 100] aralığında.
 * Hər termin öz trapesiya üzvlük funksiyasının 4 nöqtəsini (a, b, c, d) saxlayır.
 */
public enum FacialExpression {
    HAPPY(NEGATIVE_INFINITY, NEGATIVE_INFINITY, 20, 25),
    SURPRISE(20, 25, 40, 50),
    FEAR(40, 50, 60, 70),
    ANGER(60, 70, 80, 85),
    DISGUST(80, 85, POSITIVE_INFINITY, POSITIVE_INFINITY);

    private final double a, b, c, d;

    FacialExpression(double a, double b, double c, double d) {
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
    }

    public double membership(double x) {
        return MamdaniFuzzyInferenceSystem.trapezoid(x, a, b, c, d);
    }
}
