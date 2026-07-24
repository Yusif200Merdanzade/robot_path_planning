package com.example.robotPath.fuzzy;

/** POST /api/attention sorğu gövdəsi: {"facialExpression": 65, "motionRate": 75} */
public class AttentionRequest {

    private double facialExpression;
    private double motionRate;

    public AttentionRequest() {
    }

    public AttentionRequest(double facialExpression, double motionRate) {
        this.facialExpression = facialExpression;
        this.motionRate = motionRate;
    }

    public double getFacialExpression() {
        return facialExpression;
    }

    public void setFacialExpression(double facialExpression) {
        this.facialExpression = facialExpression;
    }

    public double getMotionRate() {
        return motionRate;
    }

    public void setMotionRate(double motionRate) {
        this.motionRate = motionRate;
    }
}
