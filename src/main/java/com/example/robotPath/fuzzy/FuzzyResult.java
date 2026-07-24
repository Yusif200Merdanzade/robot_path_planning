package com.example.robotPath.fuzzy;

/**
 * Output of the inference: the defuzzified crisp score (0-100) plus the
 * linguistic level whose centroid is closest to that score.
 */
public class FuzzyResult {

    private final double attentionScore;
    private final AttentionLevel attentionLevel;

    public FuzzyResult(double attentionScore, AttentionLevel attentionLevel) {
        this.attentionScore = attentionScore;
        this.attentionLevel = attentionLevel;
    }

    public double getAttentionScore() {
        return attentionScore;
    }

    public AttentionLevel getAttentionLevel() {
        return attentionLevel;
    }

    @Override
    public String toString() {
        return String.format("FuzzyResult{score=%.2f, level=%s}", attentionScore, attentionLevel);
    }
}
