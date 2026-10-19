package com.example.robotpathplanning.web;

import com.example.robotpathplanning.scenario.ScenarioRunner;
import com.example.robotpathplanning.websocket.SimulationWebSocketHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

/**
 * POST /api/start - simulyasiyanı (4 ssenarini) başladır. Brauzerdəki "Başlat" düyməsi bunu çağırır.
 * POST-dur, çünki vəziyyəti dəyişir: GET olsa brauzerin qabaqcadan yükləməsi və ya botlar onu təsadüfən çağırardı.
 * Əvvəlki simulyasiya hələ gedirsə dayandırılır ki, iki simulyasiya eyni anda işləməsin.
 */
@RestController
public class SimulationController {

    private final SimulationWebSocketHandler ws;
    private ScenarioRunner runner;

    public SimulationController(SimulationWebSocketHandler ws) {
        this.ws = ws;
    }

    @PostMapping("/api/start")
    public synchronized Map<String, String> start() {
        if (runner != null) runner.stop();
        runner = new ScenarioRunner(ws::broadcast);
        runner.start();
        return Collections.singletonMap("status", "started");
    }
}
