package com.example.robotpathplanning.websocket;

import com.example.robotpathplanning.scenario.ScenarioRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brauzerlə WebSocket əlaqəsi (/ws/robot).
 *
 * Hər qoşulmanın (brauzer səhifəsinin) öz simulyasiyası var: biri başlatdığı simulyasiyanı yalnız özü görür,
 * başqalarının ekranına qarışmır. Qoşulma bağlananda (çıxış, səhifə bağlandı/yeniləndi) onun simulyasiyası
 * dayandırılır — yenidən girəndə köhnəsinin davamı yox, "Başlat" gözləyən təmiz ekran açılır.
 *
 * Qoşulan kimi brauzerə təsadüfi simulyasiya id-si göndərilir: {"type":"hello","simulationId":"..."}.
 * Brauzer "Başlat"-da onu POST /api/start-a verir (bax: SimulationController). Id təxmin edilə bilməz (UUID),
 * ona görə başqasının simulyasiyasını kənardan idarə etmək olmur.
 */
@Component
public class SimulationWebSocketHandler extends TextWebSocketHandler {

    /** Eyni anda işləyən simulyasiyaların ən çox sayı: hər biri ayrıca thread-dir, server (pulsuz plan) tükənməsin. */
    static final int MAX_SIMULATIONS = 20;
    private static final String ID_ATTRIBUTE = "simulationId";
    /** Yavaş brauzer simulyasiya thread-ini bloklamasın: göndərmə 5 san-dən uzun çəkərsə və ya növbə dolarsa qoşulma bağlanır. */
    private static final int SEND_TIME_LIMIT_MS = 5000;
    private static final int BUFFER_SIZE_LIMIT = 512 * 1024;

    private final Map<String, Viewer> viewers = new ConcurrentHashMap<>();
    private final JsonMapper json;

    public SimulationWebSocketHandler(JsonMapper json) {
        this.json = json;
    }

    /** Bir brauzer qoşulması və onun simulyasiyası (null = hələ başladılmayıb). */
    private static final class Viewer {
        final WebSocketSession session;
        ScenarioRunner runner;

        Viewer(WebSocketSession session) {
            this.session = session;
        }
    }

    /** Nəticə: brauzerə nə cavab veriləcəyini SimulationController müəyyən edir. */
    public enum StartResult { STARTED, UNKNOWN_SIMULATION, BUSY }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // Simulyasiya thread-i ilə server thread-i eyni anda göndərə bilər — dekorator göndərişləri növbəyə düzür
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT);
        String id = UUID.randomUUID().toString();
        session.getAttributes().put(ID_ATTRIBUTE, id);
        viewers.put(id, new Viewer(safe));
        send(id, Map.of("type", "hello", "simulationId", id));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Viewer viewer = viewers.remove((String) session.getAttributes().get(ID_ATTRIBUTE));
        if (viewer != null) {
            synchronized (viewer) {
                if (viewer.runner != null) viewer.runner.stop();
            }
        }
    }

    /** Bu qoşulmanın simulyasiyasını sıfırdan başladır (gedirsə əvvəlcə dayandırılır). */
    public StartResult start(String simulationId) {
        Viewer viewer = simulationId == null ? null : viewers.get(simulationId);
        if (viewer == null) return StartResult.UNKNOWN_SIMULATION;
        synchronized (viewer) {
            if (viewer.runner != null) {
                viewer.runner.stop();
                viewer.runner = null;
            } else if (runningCount() >= MAX_SIMULATIONS) {
                return StartResult.BUSY;
            }
            if (!viewers.containsKey(simulationId)) return StartResult.UNKNOWN_SIMULATION; // bu arada bağlandı
            viewer.runner = new ScenarioRunner(state -> send(simulationId, state));
            viewer.runner.start();
            return StartResult.STARTED;
        }
    }

    private long runningCount() {
        return viewers.values().stream().filter(v -> v.runner != null).count();
    }

    private void send(String simulationId, Object message) {
        Viewer viewer = viewers.get(simulationId);
        if (viewer == null) return;
        try {
            if (viewer.session.isOpen()) viewer.session.sendMessage(new TextMessage(json.writeValueAsString(message)));
        } catch (IOException | IllegalStateException e) {
            // Qırılmış qoşulma: afterConnectionClosed simulyasiyanı dayandıracaq, burada sadəcə ötürülür
        }
    }
}
