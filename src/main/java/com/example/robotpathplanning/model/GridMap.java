package com.example.robotpathplanning.model;

import com.example.robotpathplanning.sensor.LidarSensor;

import java.util.HashMap;
import java.util.Map;

/**
 * Anbarın grid xəritəsi: ölçülər və hər xanada duran obyekt (rəf, forklift, hərəkət edən robot, qutu).
 *
 * Boş xanalar saxlanmır, yalnız obyekt olan xanalar Map-dədir. Planlaşdırıcılar
 * xananın keçilə bilən olub-olmadığını buradan soruşur.
 */
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

    /** Qutu (PACKAGE) keçidi bağlamır, qalan bütün obyektlər maneədir. */
    public boolean isBlocked(Cell c) {
        WorldObject obj = objects.get(c);
        return obj != null && obj.type != ObjectType.PACKAGE;
    }

    /**
     * LiDAR rejimi: robotun hələ görmədiyi xana boş sayılır (bilinməyən = sərbəst fərziyyəsi).
     * Statik maneə bir dəfə görüldüsə yadda qalır. Hərəkət edən maneənin (MOVING_ROBOT) yeri isə yalnız
     * o hazırda sensor zonasındadırsa bilinir: robot onu görmədən onun ətrafından yol çəkə bilməz.
     */
    public boolean isBlockedForLidar(Cell c, LidarSensor lidar) {
        if (!isBlocked(c)) return false;
        return objects.get(c).type == ObjectType.MOVING_ROBOT ? lidar.isVisibleNow(c) : lidar.isDiscovered(c);
    }

    public boolean inBounds(Cell c) {
        return c.row >= 0 && c.row < rows && c.col >= 0 && c.col < cols;
    }
}
