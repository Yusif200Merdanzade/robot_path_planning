package com.example.robotPath.scenario;

import com.example.robotPath.algorithm.CloudPathPlanner;
import com.example.robotPath.algorithm.LidarSensor;
import com.example.robotPath.fuzzy.AttentionResponse;
import com.example.robotPath.fuzzy.FuzzyResult;
import com.example.robotPath.fuzzy.MamdaniFuzzyInferenceSystem;
import com.example.robotPath.model.Cell;
import com.example.robotPath.model.GridMap;
import com.example.robotPath.obstacle.ObstacleManager;
import com.example.robotPath.ui.WarehouseVisualizer;

import javax.swing.*;
import java.util.List;
import java.util.Random;

import static com.example.robotPath.scenario.ScenarioConfig.BOX_CELL;
import static com.example.robotPath.scenario.ScenarioConfig.GOAL;
import static com.example.robotPath.scenario.ScenarioConfig.LIDAR_RANGE;
import static com.example.robotPath.scenario.ScenarioConfig.START;
import static com.example.robotPath.scenario.ScenarioConfig.STEP_INTERVAL;
import static com.example.robotPath.scenario.ScenarioConfig.createWorld;
import static com.example.robotPath.scenario.ScenarioConfig.delayThen;
import static com.example.robotPath.scenario.ScenarioConfig.dropBox;
import static com.example.robotPath.scenario.ScenarioConfig.pickUpBox;
import static com.example.robotPath.scenario.ScenarioConfig.status;

public class Scenario3 {

    private final CloudPathPlanner planner;
    private final WarehouseVisualizer panel;
    private final List<Cell> cleanPath;
    private final Runnable onComplete;

    // Hərəkət edən maneə ilə qarşılaşanda qərarı fuzzy verir: DAYAN / EHTİYATLA KEÇ / KEÇ
    private final MamdaniFuzzyInferenceSystem obstacleFis = new MamdaniFuzzyInferenceSystem();
    private final Random obstacleRandom = new Random();
    private double simFacialExpression = 15;
    private double simMotionRate = 15;
    private boolean cautiousTick = false;

    public Scenario3(CloudPathPlanner planner, WarehouseVisualizer panel,
                     List<Cell> cleanPath, Runnable onComplete) {
        this.planner = planner;
        this.panel = panel;
        this.cleanPath = cleanPath;
        this.onComplete = onComplete;
    }

    public void start() {
        GridMap lidarWorld = createWorld();

        Cell obstacle1 = cleanPath.get(2);
        Cell orig2 = cleanPath.get(cleanPath.size() - 4);
        Cell obstacle2 = new Cell(orig2.row - 2, orig2.col - 2);
        Cell obstacle4 = new Cell(obstacle1.row - 2, obstacle1.col + 1);

        LidarSensor lidar = new LidarSensor(LIDAR_RANGE);
        lidar.scan(START, lidarWorld);

        panel.setScenario(3);
        panel.setMap(lidarWorld);
        panel.setLidar(lidar);
        panel.robotTrailClear();
        panel.replanPath(planner.planPath(lidarWorld, START, BOX_CELL));
        panel.repaint();

        System.out.println("\n=== SSENARİ 3: Lidar sensoru (eyni maneələr) ===");

        ObstacleManager obstacles = new ObstacleManager(lidarWorld, null);
        final boolean[] pickedUp = {false};
        final boolean[] obstacleAdded = {false};

        Timer timer = new Timer(STEP_INTERVAL, null);
        timer.addActionListener(e -> {
            if (!pickedUp[0] && panel.isAtGoal()) {
                pickedUp[0] = true;
                pickUpBox(lidarWorld, panel);
                panel.replanPath(planner.planPath(lidarWorld, panel.robotCell(), GOAL));
                status(panel, "LİDAR: Qutu götürüldü!");
                return;
            }

            if (pickedUp[0] && panel.isAtGoal()) {
                timer.stop();
                obstacles.stop();
                dropBox(panel);
                status(panel, "SSENARİ 3 TAMAM! A* hər iki halda ƏN QISA yolu tapdı! Qutu çatdırıldı! (" + panel.getTotalStepCount() + " addım)");
                System.out.println("\n=== SSENARİ 3 NƏTİCƏ ===");
                System.out.println("A* hər iki halda ƏN QISA yolu tapdı! Qutu çatdırıldı! " + panel.getTotalStepCount() + " addım");
                delayThen(2500, onComplete);
                return;
            }

            lidar.scan(panel.robotCell(), lidarWorld);

            if (pickedUp[0] && !obstacleAdded[0] && panel.getRobotIndex() + 1 >= 2) {
                obstacleAdded[0] = true;
                obstacles.placeStaticObstacles(obstacle1, obstacle2, obstacle4);
                obstacles.startBouncingObstacle(0, 8, 10, 8, 500);
                System.out.println("Lidar: Sabit: " + obstacle1 + ", " + obstacle2 + ", " + obstacle4);
                List<Cell> newPath = planner.planPath(lidarWorld, panel.robotCell(), GOAL);
                if (newPath != null) panel.replanPath(newPath);
                status(panel, "LİDAR: 4 MANEə ÇIXDI!");
                return;
            }

            if (lidarWorld.isBlocked(panel.nextCell())) {
                Cell target = pickedUp[0] ? GOAL : BOX_CELL;
                List<Cell> newPath = planner.planPath(lidarWorld, panel.robotCell(), target);
                if (newPath == null) {
                    status(panel, "LİDAR: YOL TAPILMADI!");
                    timer.stop();
                } else {
                    panel.replanPath(newPath);
                    status(panel, "LİDAR: Maneə aşkar! Yan keçir...");
                }
                return;
            }

            Cell bounceCell = obstacles.getCurrentBounceCell();
            if (bounceCell != null) {
                String action = evaluateObstacleFuzzy(panel.robotCell(), bounceCell);
                if ("STOP".equals(action)) {
                    status(panel, "🤖 FUZZY: Hərəkət edən maneə yaxınlaşdı — robot DAYANIR");
                    return;
                }
                if ("SLOW_DOWN".equals(action)) {
                    cautiousTick = !cautiousTick;
                    if (cautiousTick) {
                        status(panel, "🐢 FUZZY: Ehtiyatla YAXINLAŞIR...");
                        return;
                    }
                }
            }

            if (!panel.advanceRobot()) return;
            status(panel, pickedUp[0]
                    ? "LİDAR: Qutu daşıyır... (kəşf: " + lidar.getDiscoveredCells().size() + " xana)"
                    : "LİDAR: Qutuya gedir...");
        });
        timer.start();
    }

    /**
     * Robot hərəkət edən maneəyə yaxınlaşanda (məsafə <= 2) fuzzy FIS qərar verir:
     * STOP = dayan, SLOW_DOWN = ehtiyatla keç, CONTINUE = normal keç.
     * Uzaqdadırsa fuzzy işə düşmür, robot sərbəst davam edir.
     */
    private String evaluateObstacleFuzzy(Cell robotCell, Cell obstacleCell) {
        int distance = Math.abs(robotCell.row - obstacleCell.row) + Math.abs(robotCell.col - obstacleCell.col);

        if (distance > 2) {
            panel.setFuzzyInfo("🧠 FUZZY: maneə uzaqdır (məsafə=" + distance + ") — sərbəst", false);
            return "CONTINUE";
        }

        simFacialExpression = clamp(simFacialExpression + obstacleRandom.nextInt(31) - 15);
        simMotionRate = clamp(simMotionRate + obstacleRandom.nextInt(31) - 15);

        FuzzyResult result = obstacleFis.infer(simFacialExpression, simMotionRate);
        AttentionResponse attention = AttentionResponse.from(result);
        boolean alert = !"CONTINUE".equals(attention.getRobotAction());

        panel.setFuzzyInfo(String.format("🧠 FUZZY (maneə məsafə=%d): ifadə=%.0f hərəkət=%.0f -> %s (%s)",
                distance, simFacialExpression, simMotionRate, attention.getAttentionLevel(), attention.getRobotAction()), alert);

        return attention.getRobotAction();
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(100, value));
    }
}
