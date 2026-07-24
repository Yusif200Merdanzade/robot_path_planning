package com.example.robotPath.scenario;

import com.example.robotPath.algorithm.CloudPathPlanner;
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

import static com.example.robotPath.scenario.ScenarioConfig.*;

public class Scenario2 {

    private final GridMap world;
    private final CloudPathPlanner planner;
    private final WarehouseVisualizer panel;
    private final List<Cell> cleanPath;
    private final Runnable onComplete;

    // Maneə ilə qarşılaşanda qərarı fuzzy verir: statik maneə = pis vəziyyət (gözlə),
    // hərəkət edən maneə = yaxşı vəziyyət (davam et)
    private final MamdaniFuzzyInferenceSystem obstacleFis = new MamdaniFuzzyInferenceSystem();
    private static final int STATIC_WAIT_TICKS = 3;
    private int waitTicksRemaining = 0;

    private static final int BOUNCE_INTERVAL_MS = 500;
    private static final int FACEOFF_WAIT_MIN_TICKS = 1000 / STEP_INTERVAL;
    private static final int FACEOFF_WAIT_MAX_TICKS = 2000 / STEP_INTERVAL;
    private static final int FACEOFF_COOLDOWN_TICKS = 1000 / STEP_INTERVAL;
    private static final String[] CHAT_LINES = {
            "🗣️ \"Salam, necəsən?\"",
            "🗣️ \"Yaxşıyam, sağ ol!\"",
            "🗣️ \"Bu qutunu haraya aparırsan?\"",
            "🗣️ \"Anbarın o biri başına...\"",
            "🗣️ \"Uğurlar, mən gedirəm!\""
    };
    private final Random faceoffRandom = new Random();
    private int faceoffWaitRemaining = -1;
    private int faceoffWaitTotal = 0;
    private int faceoffCooldownRemaining = 0;

    // Operatorun diqqətini avtomatik simulyasiya edən fuzzy modul (Ssenari 1-dəki kimi)
    private final MamdaniFuzzyInferenceSystem operatorFis = new MamdaniFuzzyInferenceSystem();
    private final Random operatorRandom = new Random();
    private double simFacialExpression = 15;
    private double simMotionRate = 15;

    public Scenario2(GridMap world, CloudPathPlanner planner, WarehouseVisualizer panel,
                     List<Cell> cleanPath, Runnable onComplete) {
        this.world = world;
        this.planner = planner;
        this.panel = panel;
        this.cleanPath = cleanPath;
        this.onComplete = onComplete;
    }

    public void start() {
        panel.resetForScenario2(planner.planPath(world, START, BOX_CELL));
        System.out.println("\n=== SSENARİ 2: Maneəli daşıma ===");
        status(panel, "SSENARİ 2: Robot yenidən qutuya gedir...");

        Cell obstacle1 = cleanPath.get(2);
        Cell orig2 = cleanPath.get(cleanPath.size() - 4);
        Cell obstacle2 = new Cell(orig2.row - 2, orig2.col - 2);
        Cell obstacle4 = new Cell(obstacle1.row - 2, obstacle1.col + 1);
        Cell obstacle3 = cleanPath.get(cleanPath.size() / 2 + 3);

        ObstacleManager obstacles = new ObstacleManager(world, buildMovingPath(obstacle3));
        final boolean[] pickedUp = {false};
        final boolean[] ob1 = {false}, ob2 = {false}, ob3 = {false}, obMove = {false};

        Timer timer = new Timer(STEP_INTERVAL, null);
        timer.addActionListener(e -> {
            if (!pickedUp[0] && panel.isAtGoal()) {
                pickedUp[0] = true;
                pickUpBox(world, panel);
                panel.replanPath(planner.planPath(world, panel.robotCell(), GOAL));
                return;
            }

            if (pickedUp[0] && panel.isAtGoal()) {
                timer.stop();
                obstacles.stop();
                dropBox(panel);
                status(panel, "SSENARİ 2 TAMAM! Maneəsiz: " + (cleanPath.size() - 1)
                        + " addım | Maneəli: " + panel.getTotalStepCount() + " addım. Lidar rejimi başlanır...");
                System.out.println("\n=== NƏTİCƏ ===");
                System.out.println("Ssenari 1 (maneəsiz): " + (cleanPath.size() - 1) + " addım");
                System.out.println("Ssenari 2 (maneəli):  " + panel.getTotalStepCount() + " addım");
                System.out.println("A* hər iki halda ƏN QISA yolu tapdı! Qutu çatdırıldı!");
                delayThen(2500, onComplete);
                return;
            }

            if (waitTicksRemaining > 0) {
                waitTicksRemaining--;
                status(panel, "🤖 FUZZY: Statik maneə — vəziyyət PİS, robot gözləyir (" + (waitTicksRemaining + 1) + ")");
                System.out.println("[DEBUG] waiting, remaining=" + waitTicksRemaining + " robotIndex=" + panel.getRobotIndex());
                return;
            }

            if (faceoffWaitRemaining >= 0) {
                if (faceoffWaitRemaining > 0) {
                    int elapsed = faceoffWaitTotal - faceoffWaitRemaining;
                    int lineIndex = Math.min(elapsed / 4, CHAT_LINES.length - 1);
                    faceoffWaitRemaining--;
                    status(panel, CHAT_LINES[lineIndex]);
                    return;
                }
                faceoffWaitRemaining = -1;
                faceoffCooldownRemaining = FACEOFF_COOLDOWN_TICKS;
                obstacles.resumeBounce(BOUNCE_INTERVAL_MS);
                status(panel, "İkisi də sağollaşıb yollarına davam etdi...");
                return;
            }

            if (faceoffCooldownRemaining > 0) {
                faceoffCooldownRemaining--;
            } else if (obMove[0]) {
                Cell humanCell = obstacles.getCurrentBounceCell();
                if (humanCell != null) {
                    Cell robot = panel.robotCell();
                    int humanDist = Math.abs(robot.row - humanCell.row) + Math.abs(robot.col - humanCell.col);
                    if (humanDist == 1) {
                        obstacles.pauseBounce();
                        faceoffWaitTotal = FACEOFF_WAIT_MIN_TICKS
                                + faceoffRandom.nextInt(FACEOFF_WAIT_MAX_TICKS - FACEOFF_WAIT_MIN_TICKS + 1);
                        faceoffWaitRemaining = faceoffWaitTotal;
                        status(panel, "🚶 Robot və insan üzbəüz — dayanıb söhbət edirlər...");
                        return;
                    }
                }
            }

            if (pickedUp[0]) {
                Cell robot = panel.robotCell();
                int d1 = Math.abs(robot.row - obstacle1.row) + Math.abs(robot.col - obstacle1.col);
                int d2 = Math.abs(robot.row - obstacle2.row) + Math.abs(robot.col - obstacle2.col);
                int d4 = Math.abs(robot.row - obstacle4.row) + Math.abs(robot.col - obstacle4.col);

                if (!ob1[0] && d1 <= 2) {
                    ob1[0] = true;
                    obstacles.placeStaticObstacles(obstacle1);
                    List<Cell> p = planner.planPath(world, robot, GOAL);
                    if (p != null) panel.replanPath(p);
                    status(panel, "MANEə ÇIXDI!");
                    evaluateStaticObstacleFuzzy();
                    return;
                }
                if (!ob2[0] && d2 <= 2) {
                    ob2[0] = true;
                    obstacles.placeStaticObstacles(obstacle2);
                    List<Cell> p = planner.planPath(world, robot, GOAL);
                    if (p != null) panel.replanPath(p);
                    status(panel, "MANEə ÇIXDI!");
                    evaluateStaticObstacleFuzzy();
                    return;
                }
                if (!ob3[0] && d4 <= 2) {
                    ob3[0] = true;
                    obstacles.placeStaticObstacles(obstacle4);
                    List<Cell> p = planner.planPath(world, robot, GOAL);
                    if (p != null) panel.replanPath(p);
                    status(panel, "MANEə ÇIXDI!");
                    evaluateStaticObstacleFuzzy();
                    return;
                }
                if (!obMove[0] && panel.getPath().size() - panel.getRobotIndex() <= 5) {
                    obMove[0] = true;
                    obstacles.startBouncingObstacle(0, 8, 10, 8, BOUNCE_INTERVAL_MS);
                    List<Cell> p = planner.planPath(world, robot, GOAL);
                    if (p != null) panel.replanPath(p);
                    status(panel, "HƏRƏKƏT EDƏN MANEə ÇIXDI!");
                    evaluateMovingObstacleFuzzy();
                    return;
                }
            }

            if (world.isBlocked(panel.nextCell())) {
                Cell target = pickedUp[0] ? GOAL : BOX_CELL;
                List<Cell> newPath = planner.planPath(world, panel.robotCell(), target);
                if (newPath != null) {
                    panel.replanPath(newPath);
                    status(panel, "Maneə aşkar! Yan keçir...");
                }
                return;
            }

            if (!tryOperatorStep()) return;
            if (!panel.advanceRobot()) return;
            status(panel, pickedUp[0] ? "Robot qutu daşıyır (maneəli yol)..." : "SSENARİ 2: Robot qutuya gedir...");
        });
        timer.start();
    }

    /**
     * Operator vəziyyəti hər tick-də özbaşına yüngül-random şəkildə dəyişir və
     * fuzzy FIS-lə hesablanır. STOP halında robot həmin tick-i gözləyir —
     * sırf maraqlı bir effekt üçün, ciddi təhlükəsizlik məntiqi deyil. (Ssenari 1-dəki kimi)
     */
    private boolean tryOperatorStep() {
        simFacialExpression = clamp(simFacialExpression + operatorRandom.nextInt(31) - 15);
        simMotionRate = clamp(simMotionRate + operatorRandom.nextInt(31) - 15);

        FuzzyResult result = operatorFis.infer(simFacialExpression, simMotionRate);
        AttentionResponse attention = AttentionResponse.from(result);
        boolean stop = "STOP".equals(attention.getRobotAction());

        panel.setFuzzyInfo(String.format("🧠 FUZZY: ifadə=%.0f hərəkət=%.0f -> %s (%s)",
                simFacialExpression, simMotionRate, attention.getAttentionLevel(), attention.getRobotAction()), stop);

        if (stop) {
            status(panel, "🤖 Operator diqqəti " + attention.getAttentionLevel() + " — robot 1 addım gözləyir");
            return false;
        }
        return true;
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(100, value));
    }

    /**
     * Statik maneə = pis vəziyyət (Disgust ifadə + Normal sürət -> HIGH -> STOP).
     * Robot bir neçə tick gözləyir, sonra avtomatik davam edir.
     */
    private void evaluateStaticObstacleFuzzy() {
        double facial = 83;
        double motion = 55;
        FuzzyResult result = obstacleFis.infer(facial, motion);
        AttentionResponse attention = AttentionResponse.from(result);

        panel.setFuzzyInfo(String.format("🧠 FUZZY: statik maneə — ifadə=%.0f hərəkət=%.0f -> %s (%s)",
                facial, motion, attention.getAttentionLevel(), attention.getRobotAction()), true);

        System.out.println("[DEBUG] evaluateStaticObstacleFuzzy -> level=" + attention.getAttentionLevel()
                + " action=" + attention.getRobotAction());
        if ("STOP".equals(attention.getRobotAction())) {
            waitTicksRemaining = STATIC_WAIT_TICKS;
            System.out.println("[DEBUG] waitTicksRemaining set to " + waitTicksRemaining);
        }
    }

    /**
     * Hərəkət edən maneə = yaxşı vəziyyət (Happy ifadə + VerySlow sürət -> NONE -> CONTINUE).
     * Robot dayanmadan davam edir.
     */
    private void evaluateMovingObstacleFuzzy() {
        double facial = 10;
        double motion = 10;
        FuzzyResult result = obstacleFis.infer(facial, motion);
        AttentionResponse attention = AttentionResponse.from(result);

        panel.setFuzzyInfo(String.format("🧠 FUZZY: hərəkətli maneə — ifadə=%.0f hərəkət=%.0f -> %s (%s)",
                facial, motion, attention.getAttentionLevel(), attention.getRobotAction()), false);
    }
}
