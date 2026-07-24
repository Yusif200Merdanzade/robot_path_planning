package com.example.robotPath.model;

public class RobotPhysics {

    private double x, y;
    private double targetX, targetY;
    private double angle = 0;
    private final double speed = 1.8;
    private final double turnSpeed = 0.06;

    public RobotPhysics(double startX, double startY) {
        this.x = startX;
        this.y = startY;
        this.targetX = startX;
        this.targetY = startY;
    }

    public void setTarget(double tx, double ty) {
        targetX = tx;
        targetY = ty;
    }

    public void teleport(double px, double py) {
        x = px;
        y = py;
        targetX = px;
        targetY = py;
    }

    public void update() {
        double dx = targetX - x;
        double dy = targetY - y;
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (dist < 1.0) {
            x = targetX;
            y = targetY;
            // Dayananda dik vəziyyətə qayıt
            double upAngle = -Math.PI / 2;
            double diff = upAngle - angle;
            while (diff > Math.PI) diff -= 2 * Math.PI;
            while (diff < -Math.PI) diff += 2 * Math.PI;
            if (Math.abs(diff) > 0.01) {
                angle += Math.signum(diff) * Math.min(turnSpeed * 2, Math.abs(diff));
            }
            return;
        }

        double targetAngle = Math.atan2(dy, dx);
        double angleDiff = targetAngle - angle;
        while (angleDiff > Math.PI) angleDiff -= 2 * Math.PI;
        while (angleDiff < -Math.PI) angleDiff += 2 * Math.PI;
        angle += Math.signum(angleDiff) * Math.min(turnSpeed, Math.abs(angleDiff));

        x += (dx / dist) * speed;
        y += (dy / dist) * speed;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getAngle() { return angle; }
    public boolean isMoving() { return Math.abs(x - targetX) > 1 || Math.abs(y - targetY) > 1; }
}
