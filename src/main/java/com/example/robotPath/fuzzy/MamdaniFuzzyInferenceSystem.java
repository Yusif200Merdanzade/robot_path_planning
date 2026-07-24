package com.example.robotPath.fuzzy;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Sıfırdan yazılmış Mamdani tipli fuzzy inference system.
 * Girişlər: facialExpression (0-100), motionRate (0-100).
 * Çıxış: attentionLevel (NONE..VERY_HIGH), centroid defuzzifikasiyası ilə.
 */
public class MamdaniFuzzyInferenceSystem {

    private final List<FuzzyRule> rules;

    public MamdaniFuzzyInferenceSystem() {
        this.rules = buildRuleBase();
    }

    private static List<FuzzyRule> buildRuleBase() {
        List<FuzzyRule> r = new ArrayList<>();

        r.add(new FuzzyRule(Expression.HAPPY, Motion.VERY_SLOW, AttentionLevel.NONE));
        r.add(new FuzzyRule(Expression.HAPPY, Motion.SLOW, AttentionLevel.LOW));
        r.add(new FuzzyRule(Expression.HAPPY, Motion.NORMAL, AttentionLevel.LOW));
        r.add(new FuzzyRule(Expression.HAPPY, Motion.FAST, AttentionLevel.MEDIUM));
        r.add(new FuzzyRule(Expression.HAPPY, Motion.VERY_FAST, AttentionLevel.HIGH));

        r.add(new FuzzyRule(Expression.SURPRISE, Motion.VERY_SLOW, AttentionLevel.LOW));
        r.add(new FuzzyRule(Expression.SURPRISE, Motion.SLOW, AttentionLevel.LOW));
        r.add(new FuzzyRule(Expression.SURPRISE, Motion.NORMAL, AttentionLevel.MEDIUM));
        r.add(new FuzzyRule(Expression.SURPRISE, Motion.FAST, AttentionLevel.HIGH));
        r.add(new FuzzyRule(Expression.SURPRISE, Motion.VERY_FAST, AttentionLevel.HIGH));

        r.add(new FuzzyRule(Expression.FEAR, Motion.VERY_SLOW, AttentionLevel.LOW));
        r.add(new FuzzyRule(Expression.FEAR, Motion.SLOW, AttentionLevel.MEDIUM));
        r.add(new FuzzyRule(Expression.FEAR, Motion.NORMAL, AttentionLevel.HIGH));
        r.add(new FuzzyRule(Expression.FEAR, Motion.FAST, AttentionLevel.HIGH));
        r.add(new FuzzyRule(Expression.FEAR, Motion.VERY_FAST, AttentionLevel.VERY_HIGH));

        r.add(new FuzzyRule(Expression.ANGER, Motion.VERY_SLOW, AttentionLevel.MEDIUM));
        r.add(new FuzzyRule(Expression.ANGER, Motion.SLOW, AttentionLevel.MEDIUM));
        r.add(new FuzzyRule(Expression.ANGER, Motion.NORMAL, AttentionLevel.HIGH));
        r.add(new FuzzyRule(Expression.ANGER, Motion.FAST, AttentionLevel.VERY_HIGH));
        r.add(new FuzzyRule(Expression.ANGER, Motion.VERY_FAST, AttentionLevel.VERY_HIGH));

        r.add(new FuzzyRule(Expression.DISGUST, Motion.VERY_SLOW, AttentionLevel.VERY_LOW));
        r.add(new FuzzyRule(Expression.DISGUST, Motion.SLOW, AttentionLevel.LOW));
        r.add(new FuzzyRule(Expression.DISGUST, Motion.NORMAL, AttentionLevel.HIGH));
        r.add(new FuzzyRule(Expression.DISGUST, Motion.FAST, AttentionLevel.VERY_HIGH));
        r.add(new FuzzyRule(Expression.DISGUST, Motion.VERY_FAST, AttentionLevel.VERY_HIGH));

        return r;
    }

    /**
     * Fuzzification + rule evaluation (min t-norm) + aggregation (max) +
     * centroid (weighted-average of singleton output sets) defuzzification.
     */
    public FuzzyResult infer(double facialExpression, double motionRate) {
        Map<AttentionLevel, Double> aggregatedStrength = new EnumMap<>(AttentionLevel.class);
        for (AttentionLevel level : AttentionLevel.values()) {
            aggregatedStrength.put(level, 0.0);
        }

        for (FuzzyRule rule : rules) {
            double strength = rule.firingStrength(facialExpression, motionRate);
            if (strength <= 0) {
                continue;
            }
            AttentionLevel level = rule.getLevel();
            double current = aggregatedStrength.get(level);
            aggregatedStrength.put(level, Math.max(current, strength));
        }

        double numerator = 0;
        double denominator = 0;
        for (Map.Entry<AttentionLevel, Double> entry : aggregatedStrength.entrySet()) {
            double strength = entry.getValue();
            if (strength <= 0) {
                continue;
            }
            numerator += strength * entry.getKey().getCentroid();
            denominator += strength;
        }

        double score = denominator == 0 ? 0 : numerator / denominator;
        AttentionLevel resultLevel = closestLevel(score);
        return new FuzzyResult(score, resultLevel);
    }

    private static AttentionLevel closestLevel(double score) {
        AttentionLevel closest = AttentionLevel.NONE;
        double bestDistance = Double.MAX_VALUE;
        for (AttentionLevel level : AttentionLevel.values()) {
            double distance = Math.abs(level.getCentroid() - score);
            if (distance < bestDistance) {
                bestDistance = distance;
                closest = level;
            }
        }
        return closest;
    }

    public List<FuzzyRule> getRules() {
        return rules;
    }
}
