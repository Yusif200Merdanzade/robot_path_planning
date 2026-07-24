package com.example.robotPath.fuzzy;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/attention
 * request:  {"facialExpression": 65, "motionRate": 75}
 * response: {"attentionLevel": "HIGH", "robotAction": "STOP", "message": "Yüksək diqqət tələb olunur — robot dayanır"}
 */
@RestController
public class AttentionController {

    private final MamdaniFuzzyInferenceSystem fis = new MamdaniFuzzyInferenceSystem();

    @PostMapping("/api/attention")
    public AttentionResponse getAttention(@RequestBody AttentionRequest request) {
        FuzzyResult result = fis.infer(request.getFacialExpression(), request.getMotionRate());
        return AttentionResponse.from(result);
    }
}
