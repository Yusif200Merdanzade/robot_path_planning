package com.example.robotpathplanning.fuzzy;

import static com.example.robotpathplanning.fuzzy.AttentionLevel.*;

/**
 * Sıfırdan yazılmış Mamdani tipli qeyri-səlis (fuzzy) nəticə çıxarma sistemi.
 *
 * Girişlər: üz ifadəsi (0-100) və hərəkət sürəti (0-100).
 * Çıxış: operatora nə qədər diqqət lazım olduğu ({@link AttentionLevel}).
 *
 * Addımlar:
 *   1. Fuzzifikasiya - hər giriş üçün 5 dilçi terminin üzvlük dərəcəsi hesablanır;
 *   2. Qaydalar - "ƏGƏR ifadə=X VƏ hərəkət=Y ONDA diqqət=Z", VƏ = min;
 *   3. Aqreqasiya - eyni nəticəyə gələn qaydalardan ən güclüsü götürülür (max);
 *   4. Defuzzifikasiya - səviyyələrin mərkəzlərinin çəkili ortası (centroid),
 *      sonra bu balın ən yaxın olduğu səviyyə seçilir.
 */
public class MamdaniFuzzyInferenceSystem {

    /** Qayda cədvəli: sətir = {@link FacialExpression}, sütun = {@link MotionRate}. 25 qayda. */
    private static final AttentionLevel[][] RULES = {
            //              VERY_SLOW  SLOW    NORMAL  FAST       VERY_FAST
            /* HAPPY    */ {NONE,      LOW,    LOW,    MEDIUM,    HIGH},
            /* SURPRISE */ {LOW,       LOW,    MEDIUM, HIGH,      HIGH},
            /* FEAR     */ {LOW,       MEDIUM, HIGH,   HIGH,      VERY_HIGH},
            /* ANGER    */ {MEDIUM,    MEDIUM, HIGH,   VERY_HIGH, VERY_HIGH},
            /* DISGUST  */ {VERY_LOW,  LOW,    HIGH,   VERY_HIGH, VERY_HIGH},
    };

    /** Nəticə səviyyəsi (API və ssenarilər bunu istifadə edir). */
    public AttentionLevel infer(double facialExpression, double motionRate) {
        return closestLevel(score(facialExpression, motionRate));
    }

    /** Defuzzifikasiya olunmuş dəqiq bal (0-100). */
    public double score(double facialExpression, double motionRate) {
        double[] strength = new double[AttentionLevel.values().length];
        for (FacialExpression e : FacialExpression.values()) {
            for (MotionRate m : MotionRate.values()) {
                double fire = Math.min(e.membership(facialExpression), m.membership(motionRate));
                int out = RULES[e.ordinal()][m.ordinal()].ordinal();
                strength[out] = Math.max(strength[out], fire);
            }
        }

        double num = 0, den = 0;
        for (AttentionLevel level : AttentionLevel.values()) {
            num += strength[level.ordinal()] * level.centroid;
            den += strength[level.ordinal()];
        }
        return den == 0 ? 0 : num / den;
    }

    private static AttentionLevel closestLevel(double score) {
        AttentionLevel best = NONE;
        for (AttentionLevel level : AttentionLevel.values()) {
            if (Math.abs(level.centroid - score) < Math.abs(best.centroid - score)) best = level;
        }
        return best;
    }

    /**
     * Trapesiya üzvlük funksiyası: a-dan b-yə qalxır, b..c arası 1-dir, c-dən d-yə enir.
     * Kənar terminlər üçün a=b=-∞ (sol tərəf açıq) və ya c=d=+∞ (sağ tərəf açıq) verilir.
     */
    static double trapezoid(double x, double a, double b, double c, double d) {
        if (x <= a) return 0;
        if (x <= b) return (x - a) / (b - a);
        if (x <= c) return 1;
        if (x <= d) return (d - x) / (d - c);
        return 0;
    }
}
