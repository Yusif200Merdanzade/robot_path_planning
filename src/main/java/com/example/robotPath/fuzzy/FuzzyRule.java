package com.example.robotPath.fuzzy;

/**
 * A single Mamdani IF-THEN rule: IF expression IS e AND motion IS m THEN attention IS level.
 */
public class FuzzyRule {

    private final Expression expression;
    private final Motion motion;
    private final AttentionLevel level;

    public FuzzyRule(Expression expression, Motion motion, AttentionLevel level) {
        this.expression = expression;
        this.motion = motion;
        this.level = level;
    }

    /** AND is implemented via the minimum t-norm. */
    public double firingStrength(double expressionValue, double motionValue) {
        return Math.min(expression.membership(expressionValue), motion.membership(motionValue));
    }

    public Expression getExpression() {
        return expression;
    }

    public Motion getMotion() {
        return motion;
    }

    public AttentionLevel getLevel() {
        return level;
    }
}
