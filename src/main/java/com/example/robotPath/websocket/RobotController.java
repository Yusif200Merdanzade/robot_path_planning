package com.example.robotPath.websocket;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RobotController {

    private final RobotWebSocketHandler wsHandler;
    private WebScenarioRunner runner;

    public RobotController(RobotWebSocketHandler wsHandler) {
        this.wsHandler = wsHandler;
    }

    @GetMapping("/api/start")
    public String start() {
        runner = new WebScenarioRunner(wsHandler);
        runner.startAllScenarios();
        return "{\"status\":\"started\"}";
    }
}
