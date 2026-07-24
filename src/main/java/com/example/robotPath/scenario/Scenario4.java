package com.example.robotPath.scenario;

import com.example.robotPath.algorithm.CloudPathPlanner;
import com.example.robotPath.algorithm.LidarSensor;
import com.example.robotPath.model.Cell;
import com.example.robotPath.model.GridMap;
import com.example.robotPath.model.ObjectType;
import com.example.robotPath.model.WorldObject;
import com.example.robotPath.ui.WarehouseVisualizer;

import javax.swing.*;
import java.util.List;

import static com.example.robotPath.scenario.ScenarioConfig.BOX_CELL;
import static com.example.robotPath.scenario.ScenarioConfig.BOX_TOTAL;
import static com.example.robotPath.scenario.ScenarioConfig.GOAL;
import static com.example.robotPath.scenario.ScenarioConfig.LIDAR_RANGE;
import static com.example.robotPath.scenario.ScenarioConfig.START;
import static com.example.robotPath.scenario.ScenarioConfig.STEP_INTERVAL;
import static com.example.robotPath.scenario.ScenarioConfig.createWorld;
import static com.example.robotPath.scenario.ScenarioConfig.delayThen;
import static com.example.robotPath.scenario.ScenarioConfig.dropBox;
import static com.example.robotPath.scenario.ScenarioConfig.pickUpBox;
import static com.example.robotPath.scenario.ScenarioConfig.status;

public class Scenario4 {

    private final CloudPathPlanner planner;
    private final WarehouseVisualizer panel;
    private final Runnable onComplete;

    public Scenario4(CloudPathPlanner planner, WarehouseVisualizer panel) {
        this(planner, panel, null);
    }

    public Scenario4(CloudPathPlanner planner, WarehouseVisualizer panel, Runnable onComplete) {
        this.planner = planner;
        this.panel = panel;
        this.onComplete = onComplete;
    }

    public void start() {
        GridMap blockedWorld = createWorld();

        for (int r = 0; r <= blockedWorld.getRows(); r++) {
            addIfFree(blockedWorld, new Cell(r, blockedWorld.getCols()/4));
            addIfFree(blockedWorld, new Cell(r, blockedWorld.getCols()/2));
        }
        for (int col = 3; col <= blockedWorld.getCols(); col++) {
            addIfFree(blockedWorld, new Cell(0, col));
            addIfFree(blockedWorld, new Cell(blockedWorld.getRows(), col));
        }

        LidarSensor lidar = new LidarSensor(LIDAR_RANGE);
        lidar.scan(START, blockedWorld);

        panel.setScenario(4);
        panel.setMap(blockedWorld);
        panel.setLidar(lidar);
        panel.robotTrailClear();
        panel.replanPath(planner.planPathWithLidar(blockedWorld, START, BOX_CELL, lidar));
        panel.repaint();

        System.out.println("\n=== SSENARİ 4: Lidar ilə yol tam bağlandı ===");

        final boolean[] pickedUp = {false};
        final boolean[] returning = {false};

        Timer timer = new Timer(STEP_INTERVAL, null);
        timer.addActionListener(e -> {
            if (!pickedUp[0] && panel.isAtGoal()) {
                pickedUp[0] = true;
                pickUpBox(blockedWorld, panel);
                status(panel, "LİDAR: Qutu götürüldü! Hədəfə gedir...");
                return;
            }

            if (returning[0] && panel.isAtGoal()) {
                timer.stop();
                dropBox(panel);
                WorldObject boxObj = blockedWorld.getObjectAt(BOX_CELL);
                if (boxObj != null) boxObj.count = BOX_TOTAL;
                else blockedWorld.addObject(new WorldObject(BOX_CELL, ObjectType.PACKAGE, BOX_TOTAL));
                status(panel, "LİDAR+A*: YOL YOXDUR! Qutu geri qoyuldu. Sübut: yol tam bağlıdır.");
                System.out.println("Robot qutuyu geri qoydu.");
                System.out.println("\n========================================");
                System.out.println("BÜTÜN SSENARİLƏRİN YEKUNLARl:");
                System.out.println("Ssenari 1: Maneəsiz — ən qısa yol tapıldı");
                System.out.println("Ssenari 2: Maneəli — yan keçib ən qısa yol tapıldı");
                System.out.println("Ssenari 3: Lidar — sensor ilə kəşf edib yol tapıldı");
                System.out.println("Ssenari 4: Yol bağlı — lidar ilə yoxladı, qutuyu geri qoydu");
                System.out.println("========================================");
                if (onComplete != null) delayThen(2500, onComplete);
                return;
            }

            Cell pos = panel.robotCell();
            lidar.scan(pos, blockedWorld);

            Cell target = returning[0] ? BOX_CELL : (pickedUp[0] ? GOAL : BOX_CELL);
            List<Cell> bestPath = planner.planPathWithLidar(blockedWorld, pos, target, lidar);

            if (bestPath == null) {
                if (pickedUp[0] && !returning[0]) {
                    returning[0] = true;
                    System.out.println("Lidar: Hədəfə yol yoxdur, geri qayıdılır.");
                    List<Cell> backPath = planner.planPathWithLidar(blockedWorld, pos, BOX_CELL, lidar);
                    if (backPath != null) panel.replanPath(backPath);
                    status(panel, "LİDAR: Bütün yollar bağlıdır! Qutu geri qaytarılır...");
                } else {
                    status(panel, "LİDAR: HEÇ BİR YOL YOXDUR!");
                    timer.stop();
                }
                return;
            }

            panel.replanPath(bestPath);

            if (blockedWorld.isBlocked(panel.nextCell())) {
                status(panel, "LİDAR: Maneə kəşf edildi! Yan keçir...");
                return;
            }

            if (!panel.advanceRobot()) return;
            status(panel, returning[0] ? "LİDAR: Qutu geri qaytarılır..."
                    : "LİDAR: Skan... (kəşf: " + lidar.getDiscoveredCells().size() + " xana)");
        });
        timer.start();
    }

    private void addIfFree(GridMap world, Cell c) {
        if (!world.isBlocked(c))
            world.addObject(new WorldObject(c, ObjectType.FORKLIFT));
    }
}
