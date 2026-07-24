package com.example.robotPath.websocket;

import com.example.robotPath.algorithm.LidarSensor;
import com.example.robotPath.model.Cell;
import com.example.robotPath.model.GridMap;
import com.example.robotPath.model.WorldObject;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class RobotWebSocketHandler extends TextWebSocketHandler {

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        System.out.println("WebSocket bağlantı: " + session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    public void sendState(GridMap map, Cell robotPos, Cell goal, String status,
                          boolean carrying, int boxCount, List<Cell> trail,
                          LidarSensor lidar, String fuzzyInfo, boolean fuzzyAlert) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"rows\":").append(map.getRows()).append(",");
        json.append("\"cols\":").append(map.getCols()).append(",");
        json.append("\"robot\":{\"row\":").append(robotPos.row).append(",\"col\":").append(robotPos.col).append("},");
        json.append("\"goal\":{\"row\":").append(goal.row).append(",\"col\":").append(goal.col).append("},");
        json.append("\"status\":\"").append(escapeJson(status)).append("\",");
        json.append("\"carrying\":").append(carrying).append(",");
        json.append("\"boxCount\":").append(boxCount).append(",");
        json.append("\"hasLidar\":").append(lidar != null).append(",");
        json.append("\"fuzzyInfo\":\"").append(escapeJson(fuzzyInfo == null ? "" : fuzzyInfo)).append("\",");
        json.append("\"fuzzyAlert\":").append(fuzzyAlert).append(",");

        if (lidar != null) {
            json.append("\"discovered\":[");
            boolean firstD = true;
            for (Cell c : lidar.getDiscoveredCells()) {
                if (!firstD) json.append(",");
                json.append("{\"row\":").append(c.row).append(",\"col\":").append(c.col).append("}");
                firstD = false;
            }
            json.append("],");
        }

        json.append("\"objects\":[");
        boolean first = true;
        for (int r = 0; r < map.getRows(); r++) {
            for (int c = 0; c < map.getCols(); c++) {
                Cell cell = new Cell(r, c);
                if (lidar != null && !lidar.isDiscovered(cell)) continue;
                WorldObject obj = map.getObjectAt(cell);
                if (obj != null) {
                    if (!first) json.append(",");
                    json.append("{\"row\":").append(r)
                        .append(",\"col\":").append(c)
                        .append(",\"type\":\"").append(obj.type.name()).append("\"")
                        .append(",\"count\":").append(obj.count).append("}");
                    first = false;
                }
            }
        }
        json.append("],");

        json.append("\"trail\":[");
        for (int i = 0; i < trail.size(); i++) {
            if (i > 0) json.append(",");
            json.append("{\"row\":").append(trail.get(i).row)
                .append(",\"col\":").append(trail.get(i).col).append("}");
        }
        json.append("]");

        json.append("}");

        String message = json.toString();
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                }
            } catch (IOException e) {
                sessions.remove(session);
            }
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public boolean hasConnections() {
        return !sessions.isEmpty();
    }
}
