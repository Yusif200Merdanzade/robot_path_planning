package com.example.robotpathplanning.planning;

import com.example.robotpathplanning.model.Cell;
import com.example.robotpathplanning.model.GridMap;
import com.example.robotpathplanning.model.ObjectType;
import com.example.robotpathplanning.model.WorldObject;
import com.example.robotpathplanning.scenario.ScenarioConfig;
import com.example.robotpathplanning.sensor.LidarSensor;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D* Lite-ın düzgünlüyü A* ilə müqayisə olunaraq yoxlanır: hər iki alqoritm optimaldır,
 * ona görə eyni vəziyyətdə yolların uzunluğu eyni olmalıdır (yollar fərqli ola bilər,
 * çünki bərabər uzunluqlu bir neçə ən qısa yol ola bilər).
 */
class DStarLitePlannerTest {

    private final AStarPlanner astar = new AStarPlanner();

    @Test
    void findsSameLengthAsAStarOnWarehouseMap() {
        GridMap map = ScenarioConfig.createWorld();
        assertSameAsAStar(map, ScenarioConfig.START, ScenarioConfig.GOAL, null,
                new DStarLitePlanner(map, ScenarioConfig.START, ScenarioConfig.GOAL).plan());
    }

    @Test
    void returnsNullWhenGoalIsWalledOff() {
        GridMap map = new GridMap(5, 5);
        for (int r = 0; r < 5; r++) block(map, new Cell(r, 2));
        assertThat(new DStarLitePlanner(map, new Cell(0, 0), new Cell(4, 4)).plan()).isNull();
    }

    @Test
    void matchesAStarOnRandomStaticMaps() {
        Random rnd = new Random(1);
        for (int i = 0; i < 300; i++) {
            GridMap map = randomMap(rnd, 4 + rnd.nextInt(10), 4 + rnd.nextInt(10), 0.3);
            Cell start = freeCell(rnd, map), goal = freeCell(rnd, map);
            assertSameAsAStar(map, start, goal, null, new DStarLitePlanner(map, start, goal).plan());
        }
    }

    /** Robot yol boyu gedir, arada maneələr çıxır və yox olur: hər yenilənmədən sonra nəticə A* ilə eynidir. */
    @Test
    void incrementalUpdatesMatchFreshAStar() {
        Random rnd = new Random(2);
        for (int i = 0; i < 200; i++) {
            GridMap map = randomMap(rnd, 6 + rnd.nextInt(8), 6 + rnd.nextInt(8), 0.2);
            Cell start = freeCell(rnd, map), goal = freeCell(rnd, map);
            DStarLitePlanner dstar = new DStarLitePlanner(map, start, goal);
            List<Cell> path = dstar.plan();
            assertSameAsAStar(map, start, goal, null, path);

            Cell robot = start;
            for (int step = 0; step < 30 && path != null && !robot.equals(goal); step++) {
                robot = path.get(1);
                dstar.moveRobot(robot);

                // Robotun və məqsədin xanasından başqa təsadüfi xanalar bağlanır/açılır
                List<Cell> changed = new ArrayList<>();
                for (int k = 0; k < 1 + rnd.nextInt(3); k++) {
                    Cell c = randomCell(rnd, map);
                    if (c.equals(robot) || c.equals(goal)) continue;
                    if (map.isBlocked(c)) map.removeObject(c); else block(map, c);
                    changed.add(c);
                }
                path = changed.isEmpty() ? dstar.plan() : dstar.updateCells(changed);
                assertSameAsAStar(map, robot, goal, null, path);
            }
        }
    }

    /** LiDAR rejimi: görülməmiş xana boş sayılır, robot hər addımda yeni xanalar kəşf edir. */
    @Test
    void lidarDiscoveryMatchesAStar() {
        Random rnd = new Random(3);
        for (int i = 0; i < 200; i++) {
            GridMap map = randomMap(rnd, 6 + rnd.nextInt(8), 6 + rnd.nextInt(8), 0.25);
            Cell start = freeCell(rnd, map), goal = freeCell(rnd, map);
            LidarSensor lidar = new LidarSensor(2);
            lidar.scan(start, map);
            DStarLitePlanner dstar = new DStarLitePlanner(map, start, goal, lidar);
            List<Cell> path = dstar.plan();
            assertSameAsAStar(map, start, goal, lidar, path);

            Cell robot = start;
            for (int step = 0; step < 60 && path != null && !robot.equals(goal); step++) {
                robot = path.get(1);
                dstar.moveRobot(robot);
                Set<Cell> discovered = lidar.scan(robot, map);
                path = discovered.isEmpty() ? dstar.plan() : dstar.updateCells(discovered);
                assertSameAsAStar(map, robot, goal, lidar, path);
            }
        }
    }

    /** Hərəkət edən maneə yalnız sensor onu indi görürsə bilinir; statik maneə isə görüləndən sonra yadda qalır. */
    @Test
    void movingObstacleIsKnownOnlyWhileVisible() {
        GridMap map = new GridMap(5, 5);
        Cell movingRobot = new Cell(0, 4), shelf = new Cell(1, 4);
        map.addObject(new WorldObject(movingRobot, ObjectType.MOVING_ROBOT));
        block(map, shelf);
        LidarSensor lidar = new LidarSensor(1);

        lidar.scan(new Cell(4, 0), map);
        assertThat(map.isBlockedForLidar(movingRobot, lidar)).as("görünməmiş robot bilinmir").isFalse();

        lidar.scan(new Cell(0, 3), map);
        assertThat(map.isBlockedForLidar(movingRobot, lidar)).as("sensor zonasında robot bilinir").isTrue();
        assertThat(map.isBlockedForLidar(shelf, lidar)).isFalse(); // (1,4) bu skanın zonasında deyil

        lidar.scan(new Cell(1, 3), map);
        assertThat(map.isBlockedForLidar(shelf, lidar)).isTrue();

        lidar.scan(new Cell(4, 0), map);
        assertThat(map.isBlockedForLidar(movingRobot, lidar)).as("zonadan çıxan robotun yeri yadda qalmır").isFalse();
        assertThat(map.isBlockedForLidar(shelf, lidar)).as("statik maneə yadda qalır").isTrue();
    }

    /**
     * LiDAR kəşfi + gedib-gələn robot birlikdə: hər addımda yeni xanalar və robotun köhnə/yeni
     * xanası D* Lite-a bildirilir (ScenarioRunner-dəki kimi) — nəticə hər dəfə A* ilə eynidir.
     */
    @Test
    void lidarWithMovingObstacleMatchesAStar() {
        Random rnd = new Random(4);
        for (int i = 0; i < 200; i++) {
            GridMap map = randomMap(rnd, 6 + rnd.nextInt(8), 6 + rnd.nextInt(8), 0.2);
            Cell start = freeCell(rnd, map), goal = freeCell(rnd, map);
            Cell movingRobot = freeCell(rnd, map);
            if (movingRobot.equals(start) || movingRobot.equals(goal)) continue;
            map.addObject(new WorldObject(movingRobot, ObjectType.MOVING_ROBOT));

            LidarSensor lidar = new LidarSensor(2);
            lidar.scan(start, map);
            DStarLitePlanner dstar = new DStarLitePlanner(map, start, goal, lidar);
            List<Cell> path = dstar.plan();
            assertSameAsAStar(map, start, goal, lidar, path);

            Cell robot = start;
            for (int step = 0; step < 60 && path != null && !robot.equals(goal); step++) {
                Set<Cell> changed = new HashSet<>();
                // Robot təsadüfi qonşu boş xanaya keçir (robotun və məqsədin üstünə yox)
                Cell next = new Cell(movingRobot.row + rnd.nextInt(3) - 1, movingRobot.col + rnd.nextInt(3) - 1);
                if (map.inBounds(next) && !map.isBlocked(next) && !next.equals(robot) && !next.equals(goal)) {
                    map.removeObject(movingRobot);
                    changed.add(movingRobot);
                    movingRobot = next;
                    map.addObject(new WorldObject(movingRobot, ObjectType.MOVING_ROBOT));
                }
                changed.add(movingRobot);
                if (!map.isBlocked(path.get(1))) robot = path.get(1);
                dstar.moveRobot(robot);
                changed.addAll(lidar.scan(robot, map));
                path = dstar.updateCells(changed);
                assertSameAsAStar(map, robot, goal, lidar, path);
            }
        }
    }

    // ---- köməkçilər ----

    private void assertSameAsAStar(GridMap map, Cell start, Cell goal, LidarSensor lidar, List<Cell> dstarPath) {
        List<Cell> expected = astar.planPath(map, start, goal, lidar);
        if (expected == null) {
            assertThat(dstarPath).as("A* yol tapmadı, D* Lite da tapmamalıdır").isNull();
            return;
        }
        assertThat(dstarPath).as("D* Lite yol tapmalıdır").isNotNull();
        assertThat(dstarPath).hasSameSizeAs(expected);
        assertThat(dstarPath.get(0)).isEqualTo(start);
        assertThat(dstarPath.get(dstarPath.size() - 1)).isEqualTo(goal);
        for (int k = 1; k < dstarPath.size(); k++) {
            Cell c = dstarPath.get(k);
            assertThat(dstarPath.get(k - 1).distanceTo(c)).as("addımlar qonşu olmalıdır").isEqualTo(1);
            boolean blocked = lidar != null ? map.isBlockedForLidar(c, lidar) : map.isBlocked(c);
            assertThat(blocked).as("yol maneədən keçməməlidir: " + c).isFalse();
        }
    }

    private static GridMap randomMap(Random rnd, int rows, int cols, double density) {
        GridMap map = new GridMap(rows, cols);
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                if (rnd.nextDouble() < density) block(map, new Cell(r, c));
        return map;
    }

    private static Cell freeCell(Random rnd, GridMap map) {
        while (true) {
            Cell c = randomCell(rnd, map);
            if (!map.isBlocked(c)) return c;
        }
    }

    private static Cell randomCell(Random rnd, GridMap map) {
        return new Cell(rnd.nextInt(map.getRows()), rnd.nextInt(map.getCols()));
    }

    private static void block(GridMap map, Cell c) {
        map.addObject(new WorldObject(c, ObjectType.SHELF));
    }
}
