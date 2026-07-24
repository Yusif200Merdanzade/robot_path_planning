package com.example.robotPath.scenario;

import com.example.robotPath.model.Cell;
import com.example.robotPath.model.GridMap;
import com.example.robotPath.model.ObjectType;
import com.example.robotPath.model.WorldObject;
import com.example.robotPath.ui.WarehouseVisualizer;

public class ScenarioConfig {

    public static final Cell START = new Cell(9, 0);
    public static final Cell GOAL = new Cell(0, 11);
    public static final Cell BOX_CELL = new Cell(8, 1);
    public static final int BOX_TOTAL = 3;
    public static final int BOX_CARRY = 1;
    public static final int STEP_INTERVAL = 50;
    public static final int WEB_STEP_INTERVAL = 500;
    public static final int LIDAR_RANGE = 3;

    public static GridMap createWorld() {
        GridMap world = new GridMap(10, 12);
        // Diaqonal rəflər (V şəkilli)
        int[] cols = {3, 4, 5, 6, 5, 4, 3, 4};
        for (int r = 1; r <= 8; r++) {
            world.addObject(new WorldObject(new Cell(r, cols[r - 1]), ObjectType.SHELF));
        }

        for (int r = 3; r <= 7; r++) {
            world.addObject(new WorldObject(new Cell(r, 11), ObjectType.SHELF));
        }

        // L şəkilli rəf
        for (int r = 4; r <= 6; r++) {
            world.addObject(new WorldObject(new Cell(r, 9), ObjectType.SHELF));
        }
        world.addObject(new WorldObject(new Cell(6, 8), ObjectType.SHELF));

        world.addObject(new WorldObject(BOX_CELL, ObjectType.PACKAGE, BOX_TOTAL));
        return world;
    }

    public static Cell[] buildMovingPath(Cell origin) {
        return new Cell[]{
                new Cell(origin.row, origin.col),
                new Cell(origin.row, origin.col + 1),
                new Cell(origin.row, origin.col + 2),
                new Cell(origin.row - 1, origin.col + 2),
                new Cell(origin.row - 1, origin.col + 1),
                new Cell(origin.row - 1, origin.col),
                new Cell(origin.row, origin.col),
        };
    }

    public static void pickUpBox(GridMap world, WarehouseVisualizer panel) {
        WorldObject boxObj = world.getObjectAt(BOX_CELL);
        if (boxObj != null) boxObj.count = BOX_TOTAL - BOX_CARRY;
        panel.setCarrying(true);
        panel.setBoxCount(BOX_CARRY);
        panel.repaint();
    }

    public static void dropBox(WarehouseVisualizer panel) {
        panel.setCarrying(false);
        panel.setBoxCount(0);
    }

    public static void status(WarehouseVisualizer panel, String msg) {
        panel.setStatusMessage(msg);
        panel.repaint();
    }

    public static void delayThen(int ms, Runnable action) {
        javax.swing.Timer t = new javax.swing.Timer(ms, e -> action.run());
        t.setRepeats(false);
        t.start();
    }
}
