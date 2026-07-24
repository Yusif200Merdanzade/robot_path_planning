package com.example.robotPath.fuzzy;

/**
 * POST /api/attention cavabı:
 * {"attentionLevel": "HIGH", "robotAction": "STOP", "message": "..."}
 */
public class AttentionResponse {

    private final String attentionLevel;
    private final String robotAction;
    private final String message;

    public AttentionResponse(String attentionLevel, String robotAction, String message) {
        this.attentionLevel = attentionLevel;
        this.robotAction = robotAction;
        this.message = message;
    }

    public static AttentionResponse from(FuzzyResult result) {
        AttentionLevel level = result.getAttentionLevel();
        return new AttentionResponse(level.name(), level.getRobotAction(), level.getMessage());
    }

    public String getAttentionLevel() {
        return attentionLevel;
    }

    public String getRobotAction() {
        return robotAction;
    }

    public String getMessage() {
        return message;
    }
}
