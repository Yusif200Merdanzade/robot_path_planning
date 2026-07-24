package com.example.robotPath.algorithm;

import com.example.robotPath.model.Cell;
import com.example.robotPath.model.GridMap;

import java.util.HashSet;
import java.util.Set;

public class LidarSensor {
    private final int range;
    private final Set<Cell> discoveredCells = new HashSet<>();

    public LidarSensor(int range) {
        this.range = range;
    }

    public void scan(Cell robotPosition, GridMap realWorld) {
        for (int dr = -range; dr <= range; dr++) {
            for (int dc = -range; dc <= range; dc++) {
                if (Math.abs(dr) + Math.abs(dc) > range) continue;
                Cell target = new Cell(robotPosition.row + dr,
                        robotPosition.col + dc);
                if (realWorld.inBounds(target)) {
                    discoveredCells.add(target);
                }   }   }   }
    public boolean isDiscovered(Cell c) {
        return discoveredCells.contains(c);
    }
    public Set<Cell> getDiscoveredCells() {
        return discoveredCells;
    }   }


