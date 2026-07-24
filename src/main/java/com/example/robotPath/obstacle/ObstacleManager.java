package com.example.robotPath.obstacle;

import com.example.robotPath.model.*;

import javax.swing.*;

public class ObstacleManager {

    private final GridMap world;
    private final Cell[] movingPath;
    private int moveIndex = -1;
    private Timer moveTimer;

    private int bounceRow = -1;
    private int bounceCol;
    private int bounceMin;
    private int bounceMax;
    private int bounceDir = 1;

    public ObstacleManager(GridMap world, Cell[] movingPath) {
        this.world = world;
        this.movingPath = movingPath;
    }

    public void placeStaticObstacles(Cell... obstacles) {
        for (Cell c : obstacles) {
            world.addObject(new WorldObject(c, ObjectType.FORKLIFT));
        }
    }

    public void startMovingObstacle(int intervalMs) {
        world.addObject(new WorldObject(movingPath[0], ObjectType.HUMAN));
        moveIndex = 0;

        moveTimer = new Timer(intervalMs, e -> {
            if (moveIndex >= 0 && moveIndex < movingPath.length) {
                world.removeObject(movingPath[moveIndex]);
            }
            moveIndex++;
            if (moveIndex >= movingPath.length) {
                moveIndex = 0;
            }
            if (!world.isBlocked(movingPath[moveIndex])) {
                world.addObject(new WorldObject(movingPath[moveIndex], ObjectType.HUMAN));
            }
        });
        moveTimer.start();
    }

    public void startBouncingObstacle(int row, int colMin, int colMax, int startCol, int intervalMs) {
        bounceRow = row;
        bounceCol = startCol;
        bounceMin = colMin;
        bounceMax = colMax;
        bounceDir = 1;

        Cell startCell = new Cell(bounceRow, bounceCol);
        if (world.inBounds(startCell) && !world.isBlocked(startCell)) {
            world.addObject(new WorldObject(startCell, ObjectType.HUMAN));
        }

        moveTimer = new Timer(intervalMs, e -> bounceTick());
        moveTimer.start();
    }

    private void bounceTick() {
        Cell oldCell = new Cell(bounceRow, bounceCol);
        world.removeObject(oldCell);

        int nextCol = bounceCol + bounceDir;
        Cell nextCell = new Cell(bounceRow, nextCol);

        if (nextCol < bounceMin || nextCol > bounceMax
                || !world.inBounds(nextCell) || world.isBlocked(nextCell)) {
            bounceDir *= -1;
            nextCol = bounceCol + bounceDir;
            nextCell = new Cell(bounceRow, nextCol);
        }

        if (world.inBounds(nextCell) && !world.isBlocked(nextCell)) {
            bounceCol = nextCol;
        }

        Cell newCell = new Cell(bounceRow, bounceCol);
        world.addObject(new WorldObject(newCell, ObjectType.HUMAN));
    }

    public void stop() {
        if (moveTimer != null) {
            moveTimer.stop();
        }
    }

    /** Bouncing maneəni olduğu xanada saxlayaraq dayandırır (robot keçənə qədər). */
    public void pauseBounce() {
        if (moveTimer != null) {
            moveTimer.stop();
        }
    }

    /** Dayandırılmış bouncing maneəni saxladığı yerdən yenidən hərəkətə salır. */
    public void resumeBounce(int intervalMs) {
        if (bounceRow == -1) return;
        moveTimer = new Timer(intervalMs, e -> bounceTick());
        moveTimer.start();
    }

    /** Bouncing (hərəkət edən) maneənin cari xanası, ya da başlamayıbsa null. */
    public Cell getCurrentBounceCell() {
        return bounceRow == -1 ? null : new Cell(bounceRow, bounceCol);
    }
}
