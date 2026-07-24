package com.example.robotPath.renderer;

import java.awt.*;

public class RobotRenderer {

    private int animFrame = 0;

    public void toggleLegs() {
        animFrame++;
    }

    public void draw(Graphics2D g, int cx, int cy) {
        draw(g, cx, cy, 0, false, false, 0);
    }

    public void draw(Graphics2D g, int cx, int cy, double angle, boolean isMoving,
                     boolean carrying, int boxCount) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double phase = isMoving ? (animFrame % 8) * Math.PI / 4.0 : 0;

        double clampedAngle = angle + Math.PI / 2;
        while (clampedAngle > Math.PI) clampedAngle -= 2 * Math.PI;
        while (clampedAngle < -Math.PI) clampedAngle += 2 * Math.PI;
        if (clampedAngle > Math.PI / 2) clampedAngle = Math.PI / 2;
        if (clampedAngle < -Math.PI / 2) clampedAngle = -Math.PI / 2;

        g2.translate(cx, cy);
        g2.rotate(clampedAngle);
        g2.translate(-cx, -cy);

        drawShadow(g2, cx, cy);
        drawTracks(g2, cx, cy, phase);
        drawBody(g2, cx, cy);

        if (carrying && boxCount > 0) {
            drawClaws(g2, cx, cy, true);
            drawCarriedBox(g2, cx, cy, boxCount);
        } else {
            drawClaws(g2, cx, cy, false);
        }

        drawHead(g2, cx, cy);
        drawAntenna(g2, cx, cy);
        g2.dispose();
    }

    private void drawShadow(Graphics2D g, int cx, int cy) {
        g.setColor(new Color(0, 0, 0, 18));
        g.fillOval(cx - 12, cy + 16, 24, 6);
    }

    private void drawTracks(Graphics2D g, int cx, int cy, double phase) {
        int offset = (int)(phase * 3) % 6;

        g.setColor(new Color(50, 55, 60));
        g.fillOval(cx - 13, cy + 4, 8, 8);
        g.setColor(new Color(80, 85, 90));
        g.drawOval(cx - 13, cy + 4, 8, 8);
        g.drawLine(cx - 9, cy + 5 + offset % 3, cx - 9, cy + 10 - offset % 3);

        g.setColor(new Color(50, 55, 60));
        g.fillOval(cx - 13, cy + 14, 8, 8);
        g.setColor(new Color(80, 85, 90));
        g.drawOval(cx - 13, cy + 14, 8, 8);

        g.setColor(new Color(50, 55, 60));
        g.fillOval(cx + 5, cy + 4, 8, 8);
        g.setColor(new Color(80, 85, 90));
        g.drawOval(cx + 5, cy + 4, 8, 8);
        g.drawLine(cx + 9, cy + 5 + offset % 3, cx + 9, cy + 10 - offset % 3);

        g.setColor(new Color(50, 55, 60));
        g.fillOval(cx + 5, cy + 14, 8, 8);
        g.setColor(new Color(80, 85, 90));
        g.drawOval(cx + 5, cy + 14, 8, 8);
    }

    private void drawBody(Graphics2D g, int cx, int cy) {
        g.setColor(new Color(90, 100, 115));
        g.fillRoundRect(cx - 10, cy - 4, 20, 14, 4, 4);
        g.setColor(new Color(110, 120, 135));
        g.fillRoundRect(cx - 8, cy - 4, 16, 5, 3, 3);
        g.setColor(new Color(46, 204, 113));
        g.fillOval(cx - 7, cy + 6, 3, 3);
        g.setColor(new Color(231, 76, 60));
        g.fillOval(cx + 4, cy + 6, 3, 3);
    }

    private void drawClaws(Graphics2D g, int cx, int cy, boolean closed) {
        g.setColor(new Color(70, 80, 90));
        g.setStroke(new BasicStroke(2f));
        if (closed) {
            g.drawLine(cx - 10, cy - 1, cx - 13, cy + 3);
            g.drawLine(cx - 13, cy + 3, cx - 10, cy + 6);
            g.drawLine(cx + 10, cy - 1, cx + 13, cy + 3);
            g.drawLine(cx + 13, cy + 3, cx + 10, cy + 6);
        } else {
            g.drawLine(cx - 10, cy, cx - 14, cy - 2);
            g.drawLine(cx - 14, cy - 2, cx - 15, cy + 4);
            g.drawLine(cx + 10, cy, cx + 14, cy - 2);
            g.drawLine(cx + 14, cy - 2, cx + 15, cy + 4);
        }
        g.setStroke(new BasicStroke(2.5f));
    }

    private void drawCarriedBox(Graphics2D g, int cx, int cy, int count) {
        for (int i = 0; i < count; i++) {
            int bx = cx - 7, by = cy - 1 + (i * 3);
            g.setColor(new Color(160, 120, 55));
            g.fillRoundRect(bx, by, 14, 9, 3, 3);
            g.setColor(new Color(130, 95, 40));
            g.drawRoundRect(bx, by, 14, 9, 3, 3);
        }
    }

    private void drawHead(Graphics2D g, int cx, int cy) {
        g.setColor(new Color(75, 85, 100));
        g.fillRoundRect(cx - 7, cy - 14, 14, 11, 4, 4);
        g.setColor(new Color(52, 152, 219));
        g.fillRoundRect(cx - 5, cy - 13, 10, 5, 2, 2);
        g.setColor(new Color(0, 230, 255));
        g.fillOval(cx - 4, cy - 12, 3, 3);
        g.fillOval(cx + 1, cy - 12, 3, 3);
        g.setColor(new Color(55, 65, 75));
        g.drawLine(cx - 3, cy - 6, cx + 3, cy - 6);
        g.drawLine(cx - 2, cy - 5, cx + 2, cy - 5);
    }

    private void drawAntenna(Graphics2D g, int cx, int cy) {
        g.setColor(new Color(90, 100, 110));
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(cx, cy - 14, cx, cy - 20);
        g.setColor(new Color(231, 76, 60));
        g.fillOval(cx - 2, cy - 22, 4, 4);
    }
}
