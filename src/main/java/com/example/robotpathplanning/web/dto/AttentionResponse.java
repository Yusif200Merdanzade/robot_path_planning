package com.example.robotpathplanning.web.dto;

import com.example.robotpathplanning.fuzzy.AttentionLevel;

/**
 * POST /api/attention cavabı:
 * {"attentionLevel": "HIGH", "robotAction": "STOP", "message": "..."}
 */
public class AttentionResponse {
    public final String attentionLevel;
    public final String robotAction;
    public final String message;

    public AttentionResponse(AttentionLevel level) {
        this.attentionLevel = level.name();
        this.robotAction = level.action.name();
        this.message = level.message;
    }
}
