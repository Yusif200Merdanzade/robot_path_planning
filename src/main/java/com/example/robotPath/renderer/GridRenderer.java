package com.example.robotPath.renderer;

import com.example.robotPath.algorithm.LidarSensor;
import com.example.robotPath.model.*;

import java.awt.*;
import java.util.List;

public class GridRenderer {

    private final int cellSize;
    private final int margin;

    public GridRenderer(int cellSize, int margin) {
        this.cellSize = cellSize;
        this.margin = margin;
    }

    public void drawGrid(Graphics2D g, GridMap map, LidarSensor lidar) {
        g.setColor(new Color(180, 170, 155));
        g.fillRect(margin, margin, map.getCols() * cellSize, map.getRows() * cellSize);

        for (int r = 0; r < map.getRows(); r++) {
            for (int c = 0; c < map.getCols(); c++) {
                int x = margin + c * cellSize, y = margin + r * cellSize;
                Cell cell = new Cell(r, c);

                if (lidar != null && !lidar.isDiscovered(cell)) {
                    drawUnknownCell(g, x, y, c, r);
                } else {
                    drawFloorTile(g, x, y, r, c);
                    drawObject(g, map.getObjectAt(cell), x, y, c, r);
                }
            }
        }
        drawWarehouseFrame(g, map);
    }

    public void drawTrail(Graphics2D g, List<Cell> trail) {
        if (trail.size() < 2) return;
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < trail.size() - 1; i++) {
            g.setColor(new Color(46, 204, 113, 70));
            Point a = center(trail.get(i)), b = center(trail.get(i + 1));
            g.drawLine(a.x, a.y, b.x, b.y);
        }
        for (Cell c : trail) {
            Point p = center(c);
            g.setColor(new Color(39, 174, 96, 110));
            g.fillOval(p.x - 3, p.y - 3, 6, 6);
        }
    }

    public void drawGoal(Graphics2D g, Cell goal) {
        int x = margin + goal.col * cellSize, y = margin + goal.row * cellSize;
        int cx = x + cellSize / 2, cy = y + cellSize / 2;

        // Stol/masa — üst
        g.setColor(new Color(120, 85, 50));
        g.fillRoundRect(x + 3, y + 8, cellSize - 6, cellSize / 2, 3, 3);
        // Stol ayaqları
        g.setColor(new Color(100, 70, 40));
        g.fillRect(x + 6, y + 8 + cellSize / 2, 4, 10);
        g.fillRect(x + cellSize - 10, y + 8 + cellSize / 2, 4, 10);
        // Stol üstündə boş qutu yeri
        g.setColor(new Color(39, 174, 96, 60));
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{3, 3}, 0));
        g.drawRoundRect(x + 10, y + 10, cellSize - 22, 10, 2, 2);
        g.setStroke(new BasicStroke(1f));
        // Yazı
        g.setColor(new Color(80, 60, 35));
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        drawCentered(g, "YIĞIM", cx, cy + 12);
    }

    public Point center(Cell c) {
        return new Point(margin + c.col * cellSize + cellSize / 2, margin + c.row * cellSize + cellSize / 2);
    }

    private void drawFloorTile(Graphics2D g, int x, int y, int row, int col) {
        Color base = ((row + col) % 2 == 0) ? new Color(210, 200, 180) : new Color(200, 190, 170);
        g.setColor(base);
        g.fillRect(x, y, cellSize, cellSize);
        g.setColor(new Color(185, 175, 155));
        g.drawRect(x, y, cellSize, cellSize);
    }

    private void drawWarehouseFrame(Graphics2D g, GridMap map) {
        g.setColor(new Color(90, 80, 65));
        g.setStroke(new BasicStroke(3f));
        g.drawRect(margin, margin, map.getCols() * cellSize, map.getRows() * cellSize);
        g.setStroke(new BasicStroke(1f));
    }

    private void drawUnknownCell(Graphics2D g, int x, int y, int col, int row) {
        g.setColor(new Color(55, 50, 45));
        g.fillRect(x, y, cellSize, cellSize);
        g.setColor(new Color(75, 70, 60));
        g.drawRect(x, y, cellSize, cellSize);
        g.setColor(new Color(100, 95, 85));
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        drawCentered(g, "?", margin + col * cellSize + cellSize / 2, margin + row * cellSize + cellSize / 2);
    }

    private void drawObject(Graphics2D g, WorldObject o, int x, int y, int col, int row) {
        if (o == null) return;
        int cx = margin + col * cellSize + cellSize / 2;
        int cy = margin + row * cellSize + cellSize / 2;

        switch (o.type) {
            case SHELF:
                drawShelf(g, x, y, cx, cy);
                break;
            case FORKLIFT:
                g.setColor(new Color(192, 57, 43));
                g.fillRoundRect(x + 4, y + 4, cellSize - 8, cellSize - 8, 5, 5);
                g.setColor(Color.WHITE);
                g.setFont(new Font("SansSerif", Font.BOLD, 9));
                drawCentered(g, "M", cx, cy);
                break;
            case HUMAN:
                drawHuman(g, x, y, cx, cy);
                break;
            case PACKAGE:
                drawPackageStack(g, x, y, cx, cy, o.count);
                break;
        }
    }

    private void drawShelf(Graphics2D g, int x, int y, int cx, int cy) {
        g.setColor(new Color(80, 90, 105));
        g.fillRect(x + 3, y + 3, cellSize - 6, cellSize - 6);
        g.setColor(new Color(100, 110, 125));
        int h = cellSize / 3;
        g.drawLine(x + 5, y + h, x + cellSize - 5, y + h);
        g.drawLine(x + 5, y + 2 * h, x + cellSize - 5, y + 2 * h);
        g.setColor(new Color(165, 140, 95));
        g.fillRect(x + 6, y + 5, 9, 7);
        g.fillRect(x + 17, y + 5, 9, 7);
        g.setColor(new Color(145, 120, 75));
        g.fillRect(x + 6, y + h + 3, 11, 7);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        drawCentered(g, "RƏF", cx, cy + h);
    }

    private void drawHuman(Graphics2D g, int x, int y, int cx, int cy) {
        // Xəbərdarlıq dairəsi
        g.setColor(new Color(241, 196, 15, 25));
        g.fillOval(x + 2, y + 2, cellSize - 4, cellSize - 4);

        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // Ayaqlar
        g.setColor(new Color(44, 62, 80));
        g.drawLine(cx - 3, cy + 6, cx - 5, cy + 15);
        g.drawLine(cx + 3, cy + 6, cx + 5, cy + 15);
        // Ayaqqabı
        g.setColor(new Color(60, 50, 40));
        g.fillRoundRect(cx - 8, cy + 14, 6, 3, 2, 2);
        g.fillRoundRect(cx + 2, cy + 14, 6, 3, 2, 2);

        // Gövdə — narıncı iş geyimi
        g.setColor(new Color(230, 126, 34));
        g.fillRoundRect(cx - 7, cy - 5, 14, 13, 4, 4);
        // Yaxalıq
        g.setColor(new Color(241, 148, 60));
        g.fillRoundRect(cx - 5, cy - 5, 10, 3, 2, 2);

        // Qollar
        g.setColor(new Color(230, 126, 34));
        g.drawLine(cx - 7, cy - 2, cx - 12, cy + 5);
        g.drawLine(cx + 7, cy - 2, cx + 12, cy + 5);
        // Əllər
        g.setColor(new Color(210, 180, 140));
        g.fillOval(cx - 14, cy + 3, 5, 5);
        g.fillOval(cx + 9, cy + 3, 5, 5);

        // Boyun
        g.setColor(new Color(210, 180, 140));
        g.fillRect(cx - 2, cy - 8, 4, 4);
        // Baş
        g.fillOval(cx - 6, cy - 17, 12, 11);
        // Saç
        g.setColor(new Color(60, 40, 20));
        g.fillArc(cx - 6, cy - 18, 12, 8, 0, 180);
        // Gözlər
        g.setColor(new Color(30, 30, 30));
        g.fillOval(cx - 3, cy - 13, 2, 2);
        g.fillOval(cx + 1, cy - 13, 2, 2);

        g.setStroke(new BasicStroke(1f));
    }

    private void drawPackageStack(Graphics2D g, int x, int y, int cx, int cy, int count) {
        // Stol üstü
        g.setColor(new Color(130, 90, 50));
        g.fillRoundRect(x + 3, y + 10, cellSize - 6, cellSize / 2 - 2, 3, 3);
        g.setColor(new Color(110, 75, 40));
        g.drawRoundRect(x + 3, y + 10, cellSize - 6, cellSize / 2 - 2, 3, 3);
        // Stol ayaqları
        g.setColor(new Color(100, 70, 40));
        g.fillRect(x + 6, y + 10 + cellSize / 2 - 2, 4, 12);
        g.fillRect(x + cellSize - 10, y + 10 + cellSize / 2 - 2, 4, 12);
        // Qutular stol üstündə
        for (int i = 0; i < count; i++) {
            int bx = x + 7 + i * 3, by = y + 4 - i * 2;
            g.setColor(new Color(180, 140, 70));
            g.fillRoundRect(bx, by, 14, 9, 2, 2);
            g.setColor(new Color(150, 115, 50));
            g.drawRoundRect(bx, by, 14, 9, 2, 2);
        }
        g.setColor(new Color(90, 70, 35));
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        drawCentered(g, "x" + count, cx, cy + 14);
    }

    private void drawCentered(Graphics2D g, String text, int cx, int cy) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, cx - fm.stringWidth(text) / 2, cy + fm.getAscent() / 2 - 2);
    }
}
