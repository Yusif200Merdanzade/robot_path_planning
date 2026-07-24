package com.example.robotPath.scenario;

import com.example.robotPath.algorithm.CloudPathPlanner;
import com.example.robotPath.fuzzy.AttentionResponse;
import com.example.robotPath.fuzzy.FuzzyResult;
import com.example.robotPath.fuzzy.MamdaniFuzzyInferenceSystem;
import com.example.robotPath.model.Cell;
import com.example.robotPath.model.GridMap;
import com.example.robotPath.model.ObjectType;
import com.example.robotPath.model.WorldObject;
import com.example.robotPath.ui.WarehouseVisualizer;

import javax.swing.*;
import java.util.List;
import java.util.Random;

import static com.example.robotPath.scenario.ScenarioConfig.BOX_CARRY;
import static com.example.robotPath.scenario.ScenarioConfig.BOX_CELL;
import static com.example.robotPath.scenario.ScenarioConfig.BOX_TOTAL;
import static com.example.robotPath.scenario.ScenarioConfig.GOAL;
import static com.example.robotPath.scenario.ScenarioConfig.STEP_INTERVAL;
import static com.example.robotPath.scenario.ScenarioConfig.delayThen;
import static com.example.robotPath.scenario.ScenarioConfig.dropBox;
import static com.example.robotPath.scenario.ScenarioConfig.pickUpBox;
import static com.example.robotPath.scenario.ScenarioConfig.status;

public class Scenario1 {

    private final GridMap world;
    private final CloudPathPlanner planner;
    private final WarehouseVisualizer panel;
    private final Runnable onComplete;
    private List<Cell> cleanPath;

    // Operatorun diqqətini avtomatik simulyasiya edən fuzzy modul (giriş yoxdur, özbaşına dəyişir)
    private final MamdaniFuzzyInferenceSystem operatorFis = new MamdaniFuzzyInferenceSystem();
    private final Random operatorRandom = new Random();
    private double simFacialExpression = 15;
    private double simMotionRate = 15;

    public Scenario1(GridMap world, CloudPathPlanner planner, WarehouseVisualizer panel, Runnable onComplete) {
        this.world = world;
        this.planner = planner;
        this.panel = panel;
        this.onComplete = onComplete;
    }

    public void start() {
        status(panel, "Robot qutuya gedir...");
        Timer timer = new Timer(STEP_INTERVAL, null);
        timer.addActionListener(e -> {
            if (panel.isAtGoal()) {
                timer.stop();
                pickUpBox(world, panel);
                status(panel, BOX_CARRY + " QUTU GÖTÜRÜLDÜ! " + (BOX_TOTAL - BOX_CARRY) + " qutu qaldı.");
                delayThen(1000, this::runDelivery);
            } else {
                if (!tryOperatorStep()) return;
                if (!panel.advanceRobot()) return;
                status(panel, "Robot qutuya gedir...");
            }
        });
        timer.start();
    }

    private void runDelivery() {
        cleanPath = planner.planPath(world, panel.robotCell(), GOAL);
        if (cleanPath == null) {
            status(panel, "Hədəfə yol tapılmadı!");
            return;
        }
        panel.replanPath(cleanPath);
        System.out.println("=== SSENARİ 1: Maneəsiz daşıma ===");
        System.out.println("Hədəfə yol: " + (cleanPath.size() - 1) + " addım");

        Timer timer = new Timer(STEP_INTERVAL, null);
        timer.addActionListener(e -> {
            if (panel.isAtGoal()) {
                timer.stop();
                dropBox(panel);
                status(panel, "SSENARİ 1 TAMAM! Qutu çatdırıldı! (" + (cleanPath.size() - 1) + " addım). Maneələr əlavə olunur...");
                System.out.println("Qutu hədəfə çatdırıldı!");
                delayThen(2000, () -> {
                    world.addObject(new WorldObject(BOX_CELL, ObjectType.PACKAGE, BOX_TOTAL));
                    onComplete.run();
                });
            } else {
                if (!tryOperatorStep()) return;
                if (!panel.advanceRobot()) return;
                status(panel, "Robot qutu daşıyır...");
            }
        });
        timer.start();
    }

    /**
     * Operator vəziyyəti hər tick-də özbaşına yüngül-random şəkildə dəyişir və
     * fuzzy FIS-lə hesablanır. STOP halında robot həmin tick-i gözləyir —
     * sırf maraqlı bir effekt üçün, ciddi təhlükəsizlik məntiqi deyil.
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

    public List<Cell> getCleanPath() {
        return cleanPath;
    }
}
