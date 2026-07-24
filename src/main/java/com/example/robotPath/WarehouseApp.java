package com.example.robotPath;

import com.example.robotPath.algorithm.CloudPathPlanner;
import com.example.robotPath.model.*;
import com.example.robotPath.scenario.*;
import com.example.robotPath.ui.WarehouseVisualizer;

import javax.swing.*;
import java.util.List;

import static com.example.robotPath.scenario.ScenarioConfig.*;

public class WarehouseApp {
    private final GridMap map;
    private final CloudPathPlanner plan = new CloudPathPlanner();
    private final WarehouseVisualizer panel;

    public static void main(String[] args) {
        new WarehouseApp().start();
    }
    private WarehouseApp() {
        map = createWorld();
        List<Cell> toBox = plan.planPath(map, START, BOX_CELL);
        panel = new WarehouseVisualizer(map, toBox, START, GOAL);
        createFrame();
    }
    private void start() {
        Scenario1 s1 = new Scenario1(map, plan, panel, () -> {
            List<Cell> cleanPath = plan.planPath(map, BOX_CELL, GOAL);
            new Scenario2(map, plan, panel, cleanPath, () ->
                new Scenario3(plan, panel, cleanPath, () ->
                    new Scenario4(plan, panel, () -> {}).start()).start()).start();
        });
        s1.start();
    }
    private JFrame createFrame() {
        JFrame frame = new JFrame("Anbar Robotu — A* Yol Tapma Simulyasiyası");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(panel); frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        return frame;  } }
