package com.example.robotPath.model;

import com.example.robotPath.algorithm.LidarSensor;

import java.util.HashMap;
import java.util.Map;

public class GridMap {
    private final int rows;
    private final int cols;
    private final Map<Cell, WorldObject> objects = new HashMap<>();

    public GridMap(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
    }

    public int getRows() { return rows; }
    public int getCols() { return cols; }

    public void addObject(WorldObject obj) {
        objects.put(obj.cell, obj);
    }

    public WorldObject getObjectAt(Cell c) {
        return objects.get(c);
    }

    public void removeObject(Cell c) {
        objects.remove(c);
    }

    public boolean isBlocked(Cell c) {
        WorldObject obj = objects.get(c);
        if (obj == null) return false;
        return obj.type == ObjectType.SHELF || obj.type == ObjectType.FORKLIFT || obj.type == ObjectType.HUMAN;
    }

    public boolean isBlockedForLidar(Cell c, LidarSensor lidar) {
        if (!lidar.isDiscovered(c)) return false;
        return isBlocked(c);
    }

    public boolean inBounds(Cell c) {
        return c.row >= 0 && c.row < rows && c.col >= 0 && c.col < cols;
    }
}
