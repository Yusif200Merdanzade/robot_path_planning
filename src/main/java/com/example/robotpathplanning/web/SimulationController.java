package com.example.robotpathplanning.web;

import com.example.robotpathplanning.websocket.SimulationWebSocketHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * POST /api/start {"simulationId": "..."} - bu brauzerin simulyasiyasını (4 ssenarini) sıfırdan başladır.
 * Brauzerdəki "Başlat" düyməsi bunu çağırır; simulyasiyaId-ni brauzer WebSocket-ə qoşulanda alır.
 * POST-dur, çünki vəziyyəti dəyişir: GET olsa brauzerin qabaqcadan yükləməsi və ya botlar onu təsadüfən çağırardı.
 *
 * Cavablar: 200 started | 404 - belə qoşulma yoxdur (bağlanıb, brauzer yenidən qoşulmalıdır)
 *           | 503 - eyni anda işləyən simulyasiya limiti dolub
 */
@RestController
public class SimulationController {

    private final SimulationWebSocketHandler ws;

    public SimulationController(SimulationWebSocketHandler ws) {
        this.ws = ws;
    }

    public record StartRequest(String simulationId) {
    }

    @PostMapping("/api/start")
    public ResponseEntity<Map<String, String>> start(@RequestBody(required = false) StartRequest request) {
        return switch (ws.start(request == null ? null : request.simulationId())) {
            case STARTED -> ResponseEntity.ok(Map.of("status", "started"));
            case UNKNOWN_SIMULATION -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "unknown_simulation"));
            case BUSY -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "busy"));
        };
    }
}
