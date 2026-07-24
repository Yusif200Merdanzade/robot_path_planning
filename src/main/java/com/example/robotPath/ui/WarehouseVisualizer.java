package com.example.robotPath.ui;

import com.example.robotPath.algorithm.LidarSensor;
import com.example.robotPath.model.*;
import com.example.robotPath.renderer.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class WarehouseVisualizer extends JPanel {

    private static final int CELL = 55;
    private static final int MARGIN = 30;

    private GridMap map;
    private final Cell start;
    private final Cell goal;
    private final GridRenderer gridRenderer = new GridRenderer(CELL, MARGIN);
    private final RobotRenderer robotRenderer = new RobotRenderer();
    private final List<Cell> robotTrail = new ArrayList<>();
    private final RobotPhysics physics;

    private List<Cell> path;
    private int robotIndex = 0;
    private int totalStepCount = 0;
    private int scenario = 1;
    private String statusMessage = "";
    private boolean carrying = false;
    private int boxCount = 0;
    private LidarSensor lidar = null;
    private String fuzzyInfo = "";
    private boolean fuzzyAlert = false;

    public WarehouseVisualizer(GridMap map, List<Cell> path, Cell start, Cell goal) {
        this.map = map;
        this.path = path;
        this.start = start;
        this.goal = goal;

        Point sp = cellCenter(start);
        physics = new RobotPhysics(sp.x, sp.y);

        setPreferredSize(new Dimension(
                map.getCols() * CELL + 2 * MARGIN,
                map.getRows() * CELL + 2 * MARGIN + 80));
        setBackground(new Color(230, 220, 200));
        robotTrail.add(start);

        new Timer(30, e -> { physics.update(); repaint(); }).start();
    }

    public boolean advanceRobot() {
        if (physics.isMoving()) return false;
        robotIndex++;
        totalStepCount++;
        robotRenderer.toggleLegs();
        Cell next = robotCell();
        robotTrail.add(next);
        Point target = cellCenter(next);
        physics.setTarget(target.x, target.y);
        return true;
    }

    public void setScenario(int s) { scenario = s; }

    public void resetForScenario2(List<Cell> newPath) {
        scenario = 2;
        robotIndex = 0;
        totalStepCount = 0;
        robotTrail.clear();
        robotTrail.add(start);
        path = newPath;
        Point sp = cellCenter(start);
        physics.teleport(sp.x, sp.y);
    }

    public void replanPath(List<Cell> newPath) {
        path = newPath;
        robotIndex = 0;
    }

    public Cell robotCell() {
        return path.get(Math.min(robotIndex, path.size() - 1));
    }

    public Cell nextCell() {
        return path.get(robotIndex + 1);
    }

    public boolean isAtGoal() {
        return robotIndex + 1 >= path.size();
    }

    public int getRobotIndex() { return robotIndex; }
    public int getTotalStepCount() { return totalStepCount; }
    public List<Cell> getPath() { return path; }
    public void setStatusMessage(String msg) { statusMessage = msg; }
    public void setCarrying(boolean c) { carrying = c; }
    public void setBoxCount(int n) { boxCount = n; }
    public void setLidar(LidarSensor lidar) { this.lidar = lidar; }
    public void setMap(GridMap map) { this.map = map; }

    public void setFuzzyInfo(String info, boolean alert) {
        this.fuzzyInfo = info;
        this.fuzzyAlert = alert;
    }

    public void robotTrailClear() {
        robotTrail.clear();
        robotTrail.add(start);
        totalStepCount = 0;
        Point sp = cellCenter(start);
        physics.teleport(sp.x, sp.y);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        draw((Graphics2D) g0);
    }

    public void draw(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawTitle(g);
        gridRenderer.drawGrid(g, map, lidar);
        gridRenderer.drawTrail(g, robotTrail);
        gridRenderer.drawGoal(g, goal);

        int rx = (int) physics.getX();
        int ry = (int) physics.getY();

        if (lidar != null) {
            drawLidarBeam(g, rx, ry);
        }

        robotRenderer.draw(g, rx, ry, physics.getAngle(), physics.isMoving(), carrying, boxCount);

        drawStatus(g);
    }

    private void drawTitle(Graphics2D g) {
        g.setColor(new Color(30, 30, 30));
        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        String title;
        switch (scenario) {
            case 1:  title = "SSENARİ 1: Maneəsiz ən qısa yol (A*)"; break;
            case 2:  title = "SSENARİ 2: Maneəli yenidən hesablama (A*)"; break;
            case 3:  title = "SSENARİ 3: Lidar sensoru ilə yol tapma (A*)"; break;
            case 4:  title = "SSENARİ 4: Yol tam bağlandı — A* sübutu"; break;
            case 5:  title = "SSENARİ 5: İnsan ilə qarşılaşma — söhbət & yan keçmə"; break;
            default: title = "Anbar Robotu"; break;
        }
        g.drawString(title, MARGIN, 20);
    }

    private void drawStatus(Graphics2D g) {
        g.setColor(new Color(19, 19, 19));
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        String status;
        Cell rc = robotCell();
        if (!statusMessage.isEmpty()) {
            status = statusMessage;
        } else if (rc.equals(goal)) {
            status = "Hədəfə çatdı! (" + (path.size() - 1) + " addım)";
        } else {
            status = "Addım " + robotIndex + "  mövqe " + rc;
        }
        g.drawString(status, MARGIN, MARGIN + map.getRows() * CELL + 26);

        if (!fuzzyInfo.isEmpty()) {
            g.setColor(fuzzyAlert ? new Color(200, 30, 30) : new Color(20, 110, 60));
            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            g.drawString(fuzzyInfo, MARGIN, MARGIN + map.getRows() * CELL + 46);
        }
    }

    private void drawLidarBeam(Graphics2D g, int rx, int ry) {
        int range = 3 * CELL;
        int rays = 12;
        double spread = Math.PI * 2;

        for (int i = 0; i < rays; i++) {
            double a = (i * spread / rays);
            int ex = rx + (int)(Math.cos(a) * range);
            int ey = ry + (int)(Math.sin(a) * range);

            g.setColor(new Color(0, 200, 255, 25));
            g.setStroke(new BasicStroke(1.5f));
            g.drawLine(rx, ry, ex, ey);

            g.setColor(new Color(0, 220, 255, 50));
            g.fillOval(ex - 2, ey - 2, 4, 4);
        }

        g.setColor(new Color(0, 200, 255, 15));
        g.fillOval(rx - range, ry - range, range * 2, range * 2);
        g.setColor(new Color(0, 200, 255, 40));
        g.setStroke(new BasicStroke(1f));
        g.drawOval(rx - range, ry - range, range * 2, range * 2);
    }

    private Point cellCenter(Cell c) {
        return new Point(MARGIN + c.col * CELL + CELL / 2, MARGIN + c.row * CELL + CELL / 2);
    }
}
