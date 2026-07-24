package com.example.robotPath.websocket;

import com.example.robotPath.algorithm.CloudPathPlanner;
import com.example.robotPath.algorithm.LidarSensor;
import com.example.robotPath.fuzzy.AttentionResponse;
import com.example.robotPath.fuzzy.FuzzyResult;
import com.example.robotPath.fuzzy.MamdaniFuzzyInferenceSystem;
import com.example.robotPath.model.*;
import com.example.robotPath.obstacle.ObstacleManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import static com.example.robotPath.scenario.ScenarioConfig.*;

/**
 * Swing tərəfindəki Scenario1..Scenario4 ilə eyni məntiqi web (WebSocket) üçün
 * təkrarlayır. Fərq yalnız görüntüləmə üsulundadır (Swing paintComponent əvəzinə
 * hər tick-də JSON vəziyyəti brauzerə göndərilir).
 */
public class WebScenarioRunner {

    private final RobotWebSocketHandler wsHandler;
    private final CloudPathPlanner planner = new CloudPathPlanner();
    private GridMap world;
    private List<Cell> path;
    private int robotIndex = 0;
    private int totalSteps = 0;
    private boolean carrying = false;
    private int boxCount = 0;
    private String statusMessage = "";
    private final List<Cell> trail = new ArrayList<>();
    private Timer timer;
    private int currentScenario = 0;
    private LidarSensor lidar = null;

    private String fuzzyInfo = "";
    private boolean fuzzyAlert = false;

    public WebScenarioRunner(RobotWebSocketHandler wsHandler) {
        this.wsHandler = wsHandler;
    }

    public void startAllScenarios() {
        runScenario1();
    }

    private void reset() {
        world = createWorld();
        robotIndex = 0;
        totalSteps = 0;
        carrying = false;
        boxCount = 0;
        trail.clear();
        trail.add(START);
        lidar = null;
        fuzzyInfo = "";
        fuzzyAlert = false;
    }

    // ==================== SSENARİ 1 ====================
    private void runScenario1() {
        currentScenario = 1;
        reset();
        path = planner.planPath(world, START, BOX_CELL);
        statusMessage = "Robot qutuya gedir...";
        sendState();

        final MamdaniFuzzyInferenceSystem operatorFis = new MamdaniFuzzyInferenceSystem();
        final Random operatorRandom = new Random();
        final double[] simFacial = {15};
        final double[] simMotion = {15};

        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (isAtGoal()) {
                    timer.cancel();
                    pickUp();
                    statusMessage = BOX_CARRY + " QUTU GÖTÜRÜLDÜ! " + (BOX_TOTAL - BOX_CARRY) + " qutu qaldı.";
                    sendState();
                    scheduleNext(() -> runScenario1Delivery(operatorFis, operatorRandom, simFacial, simMotion), 1000);
                    return;
                }

                if (!tryOperatorStep(operatorFis, operatorRandom, simFacial, simMotion)) {
                    sendState();
                    return;
                }
                advance();
                statusMessage = "Robot qutuya gedir...";
                sendState();
            }
        }, WEB_STEP_INTERVAL, WEB_STEP_INTERVAL);
    }

    private void runScenario1Delivery(MamdaniFuzzyInferenceSystem operatorFis, Random operatorRandom,
                                       double[] simFacial, double[] simMotion) {
        List<Cell> cleanPath = planner.planPath(world, robotCell(), GOAL);
        if (cleanPath == null) {
            statusMessage = "Hədəfə yol tapılmadı!";
            sendState();
            return;
        }
        replan(cleanPath);

        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (isAtGoal()) {
                    timer.cancel();
                    dropBox();
                    statusMessage = "SSENARİ 1 TAMAM! Qutu çatdırıldı! (" + (cleanPath.size() - 1) + " addım). Maneələr əlavə olunur...";
                    sendState();
                    scheduleNext(() -> {
                        world.addObject(new WorldObject(BOX_CELL, ObjectType.PACKAGE, BOX_TOTAL));
                        List<Cell> nextCleanPath = planner.planPath(world, BOX_CELL, GOAL);
                        runScenario2(nextCleanPath);
                    }, 2000);
                    return;
                }

                if (!tryOperatorStep(operatorFis, operatorRandom, simFacial, simMotion)) {
                    sendState();
                    return;
                }
                advance();
                statusMessage = "Robot qutu daşıyır...";
                sendState();
            }
        }, WEB_STEP_INTERVAL, WEB_STEP_INTERVAL);
    }

    /**
     * Operator vəziyyəti hər tick-də özbaşına yüngül-random şəkildə dəyişir (frontend girişi
     * yoxdur) və fuzzy FIS-lə hesablanır. STOP halında robot həmin tick-i gözləyir, digər hallarda
     * addım atır — sırf maraqlı bir effekt üçün, ciddi təhlükəsizlik məntiqi deyil.
     */
    private boolean tryOperatorStep(MamdaniFuzzyInferenceSystem operatorFis, Random operatorRandom,
                                     double[] simFacial, double[] simMotion) {
        simFacial[0] = clamp(simFacial[0] + operatorRandom.nextInt(31) - 15);
        simMotion[0] = clamp(simMotion[0] + operatorRandom.nextInt(31) - 15);

        FuzzyResult result = operatorFis.infer(simFacial[0], simMotion[0]);
        AttentionResponse attention = AttentionResponse.from(result);
        boolean stop = "STOP".equals(attention.getRobotAction());

        setFuzzyInfo(String.format("🧠 FUZZY: ifadə=%.0f hərəkət=%.0f -> %s (%s)",
                simFacial[0], simMotion[0], attention.getAttentionLevel(), attention.getRobotAction()), stop);

        if (stop) {
            statusMessage = "🤖 Operator diqqəti " + attention.getAttentionLevel() + " — robot 1 addım gözləyir";
            return false;
        }
        return true;
    }

    // ==================== SSENARİ 2 ====================
    private void runScenario2(List<Cell> cleanPath) {
        currentScenario = 2;
        reset();
        path = planner.planPath(world, START, BOX_CELL);
        statusMessage = "SSENARİ 2: Robot yenidən qutuya gedir...";
        sendState();

        Cell obstacle1 = cleanPath.get(2);
        Cell orig2 = cleanPath.get(cleanPath.size() - 4);
        Cell obstacle2 = new Cell(orig2.row - 2, orig2.col - 2);
        Cell obstacle4 = new Cell(obstacle1.row - 2, obstacle1.col + 1);

        ObstacleManager obstacles = new ObstacleManager(world, null);
        final boolean[] pickedUp = {false};
        final boolean[] ob1 = {false}, ob2 = {false}, ob3 = {false}, obMove = {false};

        final MamdaniFuzzyInferenceSystem obstacleFis = new MamdaniFuzzyInferenceSystem();
        final int STATIC_WAIT_TICKS = 3;
        final int[] waitTicksRemaining = {0};

        final int BOUNCE_INTERVAL_MS = 500;
        final int FACEOFF_WAIT_MIN_TICKS = Math.max(1, 1000 / WEB_STEP_INTERVAL);
        final int FACEOFF_WAIT_MAX_TICKS = Math.max(FACEOFF_WAIT_MIN_TICKS, 2000 / WEB_STEP_INTERVAL);
        final int FACEOFF_COOLDOWN_TICKS = Math.max(1, 1000 / WEB_STEP_INTERVAL);
        final String[] CHAT_LINES = {
                "🗣️ \"Salam, necəsən?\"",
                "🗣️ \"Yaxşıyam, sağ ol!\"",
                "🗣️ \"Bu qutunu haraya aparırsan?\"",
                "🗣️ \"Anbarın o biri başına...\"",
                "🗣️ \"Uğurlar, mən gedirəm!\""
        };
        final Random faceoffRandom = new Random();
        final int[] faceoffWaitRemaining = {-1};
        final int[] faceoffWaitTotal = {0};
        final int[] faceoffCooldownRemaining = {0};

        final MamdaniFuzzyInferenceSystem operatorFis = new MamdaniFuzzyInferenceSystem();
        final Random operatorRandom = new Random();
        final double[] simFacial = {15};
        final double[] simMotion = {15};

        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!pickedUp[0] && isAtGoal()) {
                    pickedUp[0] = true;
                    pickUp();
                    replan(planner.planPath(world, robotCell(), GOAL));
                    sendState();
                    return;
                }

                if (pickedUp[0] && isAtGoal()) {
                    timer.cancel();
                    obstacles.stop();
                    dropBox();
                    statusMessage = "SSENARİ 2 TAMAM! Maneəsiz: " + (cleanPath.size() - 1)
                            + " addım | Maneəli: " + totalSteps + " addım. Lidar rejimi başlanır...";
                    sendState();
                    scheduleNext(() -> runScenario3(cleanPath), 2500);
                    return;
                }

                if (waitTicksRemaining[0] > 0) {
                    waitTicksRemaining[0]--;
                    statusMessage = "🤖 FUZZY: Statik maneə — vəziyyət PİS, robot gözləyir (" + (waitTicksRemaining[0] + 1) + ")";
                    sendState();
                    return;
                }

                if (faceoffWaitRemaining[0] >= 0) {
                    if (faceoffWaitRemaining[0] > 0) {
                        int elapsed = faceoffWaitTotal[0] - faceoffWaitRemaining[0];
                        int lineIndex = Math.min(elapsed / 4, CHAT_LINES.length - 1);
                        faceoffWaitRemaining[0]--;
                        statusMessage = CHAT_LINES[lineIndex];
                        sendState();
                        return;
                    }
                    faceoffWaitRemaining[0] = -1;
                    faceoffCooldownRemaining[0] = FACEOFF_COOLDOWN_TICKS;
                    obstacles.resumeBounce(BOUNCE_INTERVAL_MS);
                    statusMessage = "İkisi də sağollaşıb yollarına davam etdi...";
                    sendState();
                    return;
                }

                if (faceoffCooldownRemaining[0] > 0) {
                    faceoffCooldownRemaining[0]--;
                } else if (obMove[0]) {
                    Cell humanCell = obstacles.getCurrentBounceCell();
                    if (humanCell != null) {
                        Cell robot = robotCell();
                        int humanDist = Math.abs(robot.row - humanCell.row) + Math.abs(robot.col - humanCell.col);
                        if (humanDist == 1) {
                            obstacles.pauseBounce();
                            faceoffWaitTotal[0] = FACEOFF_WAIT_MIN_TICKS
                                    + faceoffRandom.nextInt(FACEOFF_WAIT_MAX_TICKS - FACEOFF_WAIT_MIN_TICKS + 1);
                            faceoffWaitRemaining[0] = faceoffWaitTotal[0];
                            statusMessage = "🚶 Robot və insan üzbəüz — dayanıb söhbət edirlər...";
                            sendState();
                            return;
                        }
                    }
                }

                if (pickedUp[0]) {
                    Cell robot = robotCell();
                    int d1 = Math.abs(robot.row - obstacle1.row) + Math.abs(robot.col - obstacle1.col);
                    int d2 = Math.abs(robot.row - obstacle2.row) + Math.abs(robot.col - obstacle2.col);
                    int d4 = Math.abs(robot.row - obstacle4.row) + Math.abs(robot.col - obstacle4.col);

                    if (!ob1[0] && d1 <= 2) {
                        ob1[0] = true;
                        obstacles.placeStaticObstacles(obstacle1);
                        List<Cell> p = planner.planPath(world, robot, GOAL);
                        if (p != null) replan(p);
                        statusMessage = "MANEə ÇIXDI!";
                        evaluateStaticObstacleFuzzy(obstacleFis, waitTicksRemaining, STATIC_WAIT_TICKS);
                        sendState();
                        return;
                    }
                    if (!ob2[0] && d2 <= 2) {
                        ob2[0] = true;
                        obstacles.placeStaticObstacles(obstacle2);
                        List<Cell> p = planner.planPath(world, robot, GOAL);
                        if (p != null) replan(p);
                        statusMessage = "MANEə ÇIXDI!";
                        evaluateStaticObstacleFuzzy(obstacleFis, waitTicksRemaining, STATIC_WAIT_TICKS);
                        sendState();
                        return;
                    }
                    if (!ob3[0] && d4 <= 2) {
                        ob3[0] = true;
                        obstacles.placeStaticObstacles(obstacle4);
                        List<Cell> p = planner.planPath(world, robot, GOAL);
                        if (p != null) replan(p);
                        statusMessage = "MANEə ÇIXDI!";
                        evaluateStaticObstacleFuzzy(obstacleFis, waitTicksRemaining, STATIC_WAIT_TICKS);
                        sendState();
                        return;
                    }
                    if (!obMove[0] && path.size() - robotIndex <= 5) {
                        obMove[0] = true;
                        obstacles.startBouncingObstacle(0, 8, 10, 8, BOUNCE_INTERVAL_MS);
                        List<Cell> p = planner.planPath(world, robot, GOAL);
                        if (p != null) replan(p);
                        statusMessage = "HƏRƏKƏT EDƏN MANEə ÇIXDI!";
                        evaluateMovingObstacleFuzzy(obstacleFis);
                        sendState();
                        return;
                    }
                }

                if (world.isBlocked(nextCell())) {
                    Cell target = pickedUp[0] ? GOAL : BOX_CELL;
                    List<Cell> newPath = planner.planPath(world, robotCell(), target);
                    if (newPath != null) {
                        replan(newPath);
                        statusMessage = "Maneə aşkar! Yan keçir...";
                    }
                    sendState();
                    return;
                }

                if (!tryOperatorStep(operatorFis, operatorRandom, simFacial, simMotion)) {
                    sendState();
                    return;
                }
                advance();
                statusMessage = pickedUp[0] ? "Robot qutu daşıyır (maneəli yol)..." : "SSENARİ 2: Robot qutuya gedir...";
                sendState();
            }
        }, WEB_STEP_INTERVAL, WEB_STEP_INTERVAL);
    }

    /**
     * Statik maneə = pis vəziyyət (Disgust ifadə + Normal sürət -> HIGH -> STOP).
     * Robot bir neçə tick gözləyir, sonra avtomatik davam edir.
     */
    private void evaluateStaticObstacleFuzzy(MamdaniFuzzyInferenceSystem obstacleFis, int[] waitTicksRemaining, int staticWaitTicks) {
        double facial = 83;
        double motion = 55;
        FuzzyResult result = obstacleFis.infer(facial, motion);
        AttentionResponse attention = AttentionResponse.from(result);

        setFuzzyInfo(String.format("🧠 FUZZY: statik maneə — ifadə=%.0f hərəkət=%.0f -> %s (%s)",
                facial, motion, attention.getAttentionLevel(), attention.getRobotAction()), true);

        if ("STOP".equals(attention.getRobotAction())) {
            waitTicksRemaining[0] = staticWaitTicks;
        }
    }

    /**
     * Hərəkət edən maneə = yaxşı vəziyyət (Happy ifadə + VerySlow sürət -> NONE -> CONTINUE).
     * Robot dayanmadan davam edir.
     */
    private void evaluateMovingObstacleFuzzy(MamdaniFuzzyInferenceSystem obstacleFis) {
        double facial = 10;
        double motion = 10;
        FuzzyResult result = obstacleFis.infer(facial, motion);
        AttentionResponse attention = AttentionResponse.from(result);

        setFuzzyInfo(String.format("🧠 FUZZY: hərəkətli maneə — ifadə=%.0f hərəkət=%.0f -> %s (%s)",
                facial, motion, attention.getAttentionLevel(), attention.getRobotAction()), false);
    }

    // ==================== SSENARİ 3 (LİDAR) ====================
    private void runScenario3(List<Cell> cleanPath) {
        currentScenario = 3;
        reset();
        lidar = new LidarSensor(LIDAR_RANGE);
        lidar.scan(START, world);

        Cell obstacle1 = cleanPath.get(2);
        Cell orig2 = cleanPath.get(cleanPath.size() - 4);
        Cell obstacle2 = new Cell(orig2.row - 2, orig2.col - 2);
        Cell obstacle4 = new Cell(obstacle1.row - 2, obstacle1.col + 1);

        path = planner.planPath(world, START, BOX_CELL);
        statusMessage = "LİDAR ilə kəşf başladı...";
        sendState();

        ObstacleManager obstacles = new ObstacleManager(world, null);
        final boolean[] pickedUp = {false};
        final boolean[] obstacleAdded = {false};

        final MamdaniFuzzyInferenceSystem obstacleFis = new MamdaniFuzzyInferenceSystem();
        final Random obstacleRandom = new Random();
        final double[] simFacial = {15};
        final double[] simMotion = {15};
        final boolean[] cautiousTick = {false};

        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!pickedUp[0] && isAtGoal()) {
                    pickedUp[0] = true;
                    pickUp();
                    replan(planner.planPath(world, robotCell(), GOAL));
                    statusMessage = "LİDAR: Qutu götürüldü!";
                    sendState();
                    return;
                }

                if (pickedUp[0] && isAtGoal()) {
                    timer.cancel();
                    obstacles.stop();
                    dropBox();
                    statusMessage = "SSENARİ 3 TAMAM! A* hər iki halda ƏN QISA yolu tapdı! Qutu çatdırıldı! (" + totalSteps + " addım)";
                    sendState();
                    scheduleNext(() -> runScenario4(), 2500);
                    return;
                }

                lidar.scan(robotCell(), world);

                if (pickedUp[0] && !obstacleAdded[0] && robotIndex + 1 >= 2) {
                    obstacleAdded[0] = true;
                    obstacles.placeStaticObstacles(obstacle1, obstacle2, obstacle4);
                    obstacles.startBouncingObstacle(0, 8, 10, 8, 500);
                    statusMessage = "LİDAR: 4 MANEə ÇIXDI!";
                    List<Cell> newPath = planner.planPath(world, robotCell(), GOAL);
                    if (newPath != null) replan(newPath);
                    sendState();
                    return;
                }

                if (world.isBlocked(nextCell())) {
                    Cell target = pickedUp[0] ? GOAL : BOX_CELL;
                    List<Cell> newPath = planner.planPath(world, robotCell(), target);
                    if (newPath == null) {
                        statusMessage = "LİDAR: YOL TAPILMADI!";
                        sendState();
                        timer.cancel();
                    } else {
                        replan(newPath);
                        statusMessage = "LİDAR: Maneə aşkar! Yan keçir...";
                        sendState();
                    }
                    return;
                }

                Cell bounceCell = obstacles.getCurrentBounceCell();
                if (bounceCell != null) {
                    String action = evaluateObstacleFuzzy(robotCell(), bounceCell, obstacleFis, obstacleRandom, simFacial, simMotion);
                    if ("STOP".equals(action)) {
                        statusMessage = "🤖 FUZZY: Hərəkət edən maneə yaxınlaşdı — robot DAYANIR";
                        sendState();
                        return;
                    }
                    if ("SLOW_DOWN".equals(action)) {
                        cautiousTick[0] = !cautiousTick[0];
                        if (cautiousTick[0]) {
                            statusMessage = "🐢 FUZZY: Ehtiyatla YAXINLAŞIR...";
                            sendState();
                            return;
                        }
                    }
                }

                advance();
                statusMessage = pickedUp[0]
                        ? "LİDAR: Qutu daşıyır... (kəşf: " + lidar.getDiscoveredCells().size() + " xana)"
                        : "LİDAR: Qutuya gedir...";
                sendState();
            }
        }, WEB_STEP_INTERVAL, WEB_STEP_INTERVAL);
    }

    /**
     * Robot hərəkət edən maneəyə yaxınlaşanda (məsafə <= 2) fuzzy FIS qərar verir:
     * STOP = dayan, SLOW_DOWN = ehtiyatla keç, CONTINUE = normal keç.
     * Uzaqdadırsa fuzzy işə düşmür, robot sərbəst davam edir.
     */
    private String evaluateObstacleFuzzy(Cell robotCell, Cell obstacleCell, MamdaniFuzzyInferenceSystem obstacleFis,
                                          Random obstacleRandom, double[] simFacial, double[] simMotion) {
        int distance = Math.abs(robotCell.row - obstacleCell.row) + Math.abs(robotCell.col - obstacleCell.col);

        if (distance > 2) {
            setFuzzyInfo("🧠 FUZZY: maneə uzaqdır (məsafə=" + distance + ") — sərbəst", false);
            return "CONTINUE";
        }

        simFacial[0] = clamp(simFacial[0] + obstacleRandom.nextInt(31) - 15);
        simMotion[0] = clamp(simMotion[0] + obstacleRandom.nextInt(31) - 15);

        FuzzyResult result = obstacleFis.infer(simFacial[0], simMotion[0]);
        AttentionResponse attention = AttentionResponse.from(result);
        boolean alert = !"CONTINUE".equals(attention.getRobotAction());

        setFuzzyInfo(String.format("🧠 FUZZY (maneə məsafə=%d): ifadə=%.0f hərəkət=%.0f -> %s (%s)",
                distance, simFacial[0], simMotion[0], attention.getAttentionLevel(), attention.getRobotAction()), alert);

        return attention.getRobotAction();
    }

    // ==================== SSENARİ 4 (YOL BAĞLI) ====================
    private void runScenario4() {
        currentScenario = 4;
        reset();
        lidar = new LidarSensor(LIDAR_RANGE);
        lidar.scan(START, world);

        int rows = world.getRows();
        int cols = world.getCols();
        for (int r = 0; r <= rows; r++) {
            addIfFree(world, new Cell(r, cols / 4));
            addIfFree(world, new Cell(r, cols / 2));
        }
        for (int c = 3; c <= cols; c++) {
            addIfFree(world, new Cell(0, c));
            addIfFree(world, new Cell(rows, c));
        }

        path = planner.planPathWithLidar(world, START, BOX_CELL, lidar);
        statusMessage = "LİDAR ilə yol tam bağlandı — skan başladı...";
        sendState();

        final boolean[] pickedUp = {false};
        final boolean[] returning = {false};

        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!pickedUp[0] && isAtGoal()) {
                    pickedUp[0] = true;
                    pickUp();
                    statusMessage = "LİDAR: Qutu götürüldü! Hədəfə gedir...";
                    sendState();
                    return;
                }

                if (returning[0] && isAtGoal()) {
                    timer.cancel();
                    dropBox();
                    WorldObject boxObj = world.getObjectAt(BOX_CELL);
                    if (boxObj != null) boxObj.count = BOX_TOTAL;
                    else world.addObject(new WorldObject(BOX_CELL, ObjectType.PACKAGE, BOX_TOTAL));
                    statusMessage = "LİDAR+A*: YOL YOXDUR! Qutu geri qoyuldu. Sübut: yol tam bağlıdır.";
                    sendState();
                    return;
                }

                Cell pos = robotCell();
                lidar.scan(pos, world);

                Cell target = returning[0] ? BOX_CELL : (pickedUp[0] ? GOAL : BOX_CELL);
                List<Cell> bestPath = planner.planPathWithLidar(world, pos, target, lidar);

                if (bestPath == null) {
                    if (pickedUp[0] && !returning[0]) {
                        returning[0] = true;
                        List<Cell> backPath = planner.planPathWithLidar(world, pos, BOX_CELL, lidar);
                        if (backPath != null) replan(backPath);
                        statusMessage = "LİDAR: Bütün yollar bağlıdır! Qutu geri qaytarılır...";
                    } else {
                        statusMessage = "LİDAR: HEÇ BİR YOL YOXDUR!";
                        timer.cancel();
                    }
                    sendState();
                    return;
                }

                replan(bestPath);

                if (world.isBlocked(nextCell())) {
                    statusMessage = "LİDAR: Maneə kəşf edildi! Yan keçir...";
                    sendState();
                    return;
                }

                advance();
                statusMessage = returning[0] ? "LİDAR: Qutu geri qaytarılır..."
                        : "LİDAR: Skan... (kəşf: " + lidar.getDiscoveredCells().size() + " xana)";
                sendState();
            }
        }, WEB_STEP_INTERVAL, WEB_STEP_INTERVAL);
    }

    // ==================== ORTAQ METODLAR ====================
    private Cell robotCell() {
        return path.get(Math.min(robotIndex, path.size() - 1));
    }

    private Cell nextCell() {
        return path.get(robotIndex + 1);
    }

    private boolean isAtGoal() {
        return robotIndex + 1 >= path.size();
    }

    private void advance() {
        robotIndex++;
        totalSteps++;
        trail.add(robotCell());
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(100, value));
    }

    private void replan(List<Cell> newPath) {
        path = newPath;
        robotIndex = 0;
    }

    private void pickUp() {
        WorldObject boxObj = world.getObjectAt(BOX_CELL);
        if (boxObj != null) boxObj.count = BOX_TOTAL - BOX_CARRY;
        carrying = true;
        boxCount = BOX_CARRY;
    }

    private void dropBox() {
        carrying = false;
        boxCount = 0;
    }

    private void setFuzzyInfo(String info, boolean alert) {
        this.fuzzyInfo = info;
        this.fuzzyAlert = alert;
    }

    private void sendState() {
        wsHandler.sendState(world, robotCell(), GOAL,
                "SSENARİ " + currentScenario + ": " + statusMessage,
                carrying, boxCount, trail, lidar, fuzzyInfo, fuzzyAlert);
    }

    private void scheduleNext(Runnable action, int delayMs) {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() { action.run(); }
        }, delayMs);
    }

    private void addIfFree(GridMap w, Cell c) {
        if (!w.isBlocked(c))
            w.addObject(new WorldObject(c, ObjectType.FORKLIFT));
    }
}
