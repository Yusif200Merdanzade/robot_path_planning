package com.example.robotpathplanning.web.dto;

/**
 * POST /api/attention sorğusunun gövdəsi:
 * {"facialExpression": 65, "motionRate": 75}
 *
 * Sahələr public olduğu üçün Jackson getter/setter olmadan doldurur.
 */
public class AttentionRequest {
    public double facialExpression;
    public double motionRate;
}
