package com.example.robotpathplanning.sensor;

import com.example.robotpathplanning.model.Cell;
import com.example.robotpathplanning.model.GridMap;

import java.util.HashSet;
import java.util.Set;

/**
 * Sadələşdirilmiş LiDAR sensoru.
 *
 * Hər skanda robotun ətrafında {@code range} addımlıq (Manhattan, yəni romb formalı)
 * sahəni "görülmüş" kimi işarələyir. Görülmüş xanalar yadda qalır, robotun
 * xəritə haqqında bildikləri tədricən artır.
 */
public class LidarSensor {
    private final int range;
    private final Set<Cell> discoveredCells = new HashSet<>();
    private final Set<Cell> visibleNow = new HashSet<>();

    public LidarSensor(int range) {
        this.range = range;
    }

    /**
     * Robotun ətrafını skan edir və bu skanda İLK DƏFƏ görülən xanaları qaytarır (D* Lite yalnız onları yeniləyir).
     * Kəşf olunmuş xanalar yadda qalır (statik xəritə), hazırda görünən xanalar isə hər skanda yenilənir
     * (hərəkət edən maneə yalnız indi sensor zonasındadırsa bilinir).
     */
    public Set<Cell> scan(Cell robot, GridMap world) {
        Set<Cell> newlyDiscovered = new HashSet<>();
        visibleNow.clear();
        for (int dr = -range; dr <= range; dr++) {
            for (int dc = -range; dc <= range; dc++) {
                Cell c = new Cell(robot.row + dr, robot.col + dc);
                if (Math.abs(dr) + Math.abs(dc) > range || !world.inBounds(c)) continue;
                visibleNow.add(c);
                if (discoveredCells.add(c)) newlyDiscovered.add(c);
            }
        }
        return newlyDiscovered;
    }

    /** Xana son skanın zonasındadırmı (sensor onu indi görürmü). */
    public boolean isVisibleNow(Cell c) {
        return visibleNow.contains(c);
    }

    public int getRange() {
        return range;
    }

    public boolean isDiscovered(Cell c) {
        return discoveredCells.contains(c);
    }
    public Set<Cell> getDiscoveredCells() {
        return discoveredCells;
    }
}
