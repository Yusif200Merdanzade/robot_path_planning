package com.example.robotpathplanning.scenario;

import com.example.robotpathplanning.model.Cell;
import com.example.robotpathplanning.model.GridMap;
import com.example.robotpathplanning.model.ObjectType;
import com.example.robotpathplanning.model.WorldObject;

import java.util.Arrays;
import java.util.List;

/**
 * Bütün ssenarilər üçün ortaq sabitlər və başlanğıc xəritə.
 * Xəritəni, başlanğıc/hədəf nöqtələrini və ya sürəti dəyişmək üçün yalnız bu fayla baxmaq kifayətdir.
 */
public final class ScenarioConfig {

    public static final Cell START = new Cell(9, 0);
    public static final Cell GOAL = new Cell(0, 11);
    public static final Cell BOX_CELL = new Cell(8, 1);
    public static final int BOX_TOTAL = 3;           // qutu yerində əvvəlcə neçə qutu var
    public static final int BOX_CARRY = 1;           // robot bir dəfəyə neçə qutu götürür
    public static final int STEP_INTERVAL_MS = 500;  // robotun bir addımı (bir "tick") neçə ms çəkir
    public static final int LIDAR_RANGE = 3;
    public static final Cell MOVING_ROBOT_START = new Cell(0, 8); // gedib-gələn robot bu xanadan çıxır
    public static final int MOVING_ROBOT_MAX_COL = 10;           // və MOVING_ROBOT_START.col..MOVING_ROBOT_MAX_COL arasında gəzir
    public static final int MOVING_ROBOT_SPAWN_DISTANCE = 5;     // ssenari 2: robot bu qədər yaxınlaşanda hərəkət edən robot çıxır

    private ScenarioConfig() {
    }

    /** 10x12 anbar: V şəkilli rəf sırası, sağ divar boyu rəflər, L şəkilli rəf və qutu yeri. */
    public static GridMap createWorld() {
        GridMap world = new GridMap(10, 12);
        int[] vCols = {3, 4, 5, 6, 5, 4, 3, 4};
        for (int r = 1; r <= 8; r++) shelf(world, r, vCols[r - 1]);
        for (int r = 3; r <= 7; r++) shelf(world, r, 11);
        for (int r = 4; r <= 6; r++) shelf(world, r, 9);
        shelf(world, 6, 8);
        world.addObject(new WorldObject(BOX_CELL, ObjectType.PACKAGE, BOX_TOTAL));
        return world;
    }

    /**
     * Ssenari 2 və 3-də qəfildən çıxan 3 statik maneənin yerləri. Maneəsiz ən qısa
     * yola ({@code cleanPath}) görə hesablanır ki, maneə robotun yolunun üstünə düşsün.
     */
    public static List<Cell> surpriseObstacles(List<Cell> cleanPath) {
        Cell first = cleanPath.get(2);
        Cell nearEnd = cleanPath.get(cleanPath.size() - 4);
        return Arrays.asList(
                first,
                new Cell(nearEnd.row - 2, nearEnd.col - 2),
                new Cell(first.row - 2, first.col + 1));
    }

    private static void shelf(GridMap world, int row, int col) {
        world.addObject(new WorldObject(new Cell(row, col), ObjectType.SHELF));
    }
}
