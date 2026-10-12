package com.example.robotpathplanning.obstacle;

import com.example.robotpathplanning.model.*;

/**
 * Ssenarilər zamanı xəritəyə maneə əlavə edir.
 *
 * İki növ maneə var:
 *   - statik: bir dəfə qoyulan forkliftlər;
 *   - hərəkətli: bir sətirdə sağa-sola gedib-gələn robot (AMR).
 *
 * Hərəkət edən robotun öz taymeri yoxdur: ssenari hər addımda {@link #tick(Cell)} çağırır. Beləliklə
 * xəritəni yalnız bir axın (thread) dəyişir və yarış vəziyyəti (race condition) yaranmır.
 */
public class ObstacleManager {

    private final GridMap world;
    private Cell movingRobot;    // null = hərəkət edən robot hələ çıxmayıb
    private int minCol, maxCol;
    private int dir = 1;         // +1 sağa, -1 sola
    private boolean paused;
    private boolean waitOneTick; // çıxandan / dayanandan sonra ilk tick yerində qalır

    public ObstacleManager(GridMap world) {
        this.world = world;
    }

    public void placeStatic(Cell... cells) {
        for (Cell c : cells) {
            if (!world.inBounds(c)) continue;   // xəritədən kənar maneə görünməz və mənasızdır
            world.addObject(new WorldObject(c, ObjectType.FORKLIFT));
        }
    }

    /** Hərəkət edən robotu {@code row} sətrində [minCol, maxCol] aralığında gəzməyə buraxır. */
    public void startMovingRobot(int row, int minCol, int maxCol, int startCol) {
        this.minCol = minCol;
        this.maxCol = maxCol;
        this.movingRobot = new Cell(row, startCol);
        this.paused = false;
        this.waitOneTick = true;
        if (world.inBounds(movingRobot) && !world.isBlocked(movingRobot)) {
            world.addObject(new WorldObject(movingRobot, ObjectType.MOVING_ROBOT));
        }
    }

    /**
     * Hərəkət edən robotu bir xana irəli aparır; qarşısı bağlıdırsa və ya sərhədə çatıbsa geri dönür.
     * Robot xəritədə obyekt kimi saxlanmadığı üçün onun xanası ayrıca verilir: hərəkət edən robot bizim robotun
     * xanasına girmir, robot keçənə qədər yerində gözləyir (əks halda ikisi bir-birinin içindən keçirdi).
     */
    public void tick(Cell robot) {
        if (movingRobot == null || paused) return;
        if (waitOneTick) {
            waitOneTick = false;
            return;
        }
        Cell next = new Cell(movingRobot.row, movingRobot.col + dir);
        if (next.col < minCol || next.col > maxCol || !world.inBounds(next) || world.isBlocked(next)) {
            dir = -dir;
            next = new Cell(movingRobot.row, movingRobot.col + dir);
        }
        if (next.equals(robot)) return;

        world.removeObject(movingRobot);
        if (world.inBounds(next) && !world.isBlocked(next)) movingRobot = next;

        world.addObject(new WorldObject(movingRobot, ObjectType.MOVING_ROBOT));
    }

    public void pause()  { paused = true; }
    public void resume() {
        paused = false;
        waitOneTick = true;
    }

    /** Hərəkət edən robotun olduğu xana, ya da hələ çıxmayıbsa null. */
    public Cell movingRobot() {
        return movingRobot;
    }
}
