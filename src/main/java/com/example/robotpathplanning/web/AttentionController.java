package com.example.robotpathplanning.web;

import com.example.robotpathplanning.fuzzy.MamdaniFuzzyInferenceSystem;
import com.example.robotpathplanning.web.dto.AttentionRequest;
import com.example.robotpathplanning.web.dto.AttentionResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fuzzy sistemi REST API kimi açır. Simulyasiyadan asılı deyil, ayrıca test üçündür.
 *
 * POST /api/attention
 * sorğu: {"facialExpression": 65, "motionRate": 75}
 * cavab: {"attentionLevel": "HIGH", "robotAction": "STOP", "message": "..."}
 */
@RestController
public class AttentionController {

    private final MamdaniFuzzyInferenceSystem fis = new MamdaniFuzzyInferenceSystem();

    @PostMapping("/api/attention")
    public AttentionResponse getAttention(@RequestBody AttentionRequest request) {
        return new AttentionResponse(fis.infer(request.facialExpression, request.motionRate));
    }
}
