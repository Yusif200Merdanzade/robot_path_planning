package com.example.robotPath.algorithm;

import com.example.robotPath.model.Cell;
import com.example.robotPath.model.GridMap;

import java.util.*;

/**
 * D* Lite (Koenig & Likhachev, 2002). Searches backward from the goal and keeps
 * its state between calls, so after the map changes only the affected part is
 * recomputed instead of planning from scratch.
 *
 * Usage:
 *   DStarLitePlanner p = new DStarLitePlanner(map, start, goal, lidar);
 *   List<Cell> path = p.plan();
 *   // robot moves, obstacles appear/disappear:
 *   p.moveRobot(newPos);
 *   path = p.updateCells(changedCells);
 *
 * A new goal requires a new planner instance.
 */
public class DStarLitePlanner {

    private static final int[][] DIRS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    private static final int INF = Integer.MAX_VALUE / 2;

    private final GridMap map;
    private final LidarSensor lidar;
    private final Cell goal;
    private Cell start;
    private Cell last;
    private int km = 0;

    private final Map<Cell, Integer> g = new HashMap<>();
    private final Map<Cell, Integer> rhs = new HashMap<>();
    private final PriorityQueue<Node> open = new PriorityQueue<>();
    private final Map<Cell, Key> openKeys = new HashMap<>();

    private int expandedCount = 0;

    public DStarLitePlanner(GridMap map, Cell start, Cell goal) {
        this(map, start, goal, null);
    }

    public DStarLitePlanner(GridMap map, Cell start, Cell goal, LidarSensor lidar) {
        this.map = map;
        this.lidar = lidar;
        this.start = start;
        this.last = start;
        this.goal = goal;
        rhs.put(goal, 0);
        insert(goal, calculateKey(goal));
    }

    public List<Cell> plan() {
        computeShortestPath();
        return extractPath();
    }

    public void moveRobot(Cell newStart) {
        this.start = newStart;
    }

    /** Call with the cells whose blocked state changed (new obstacle, removed obstacle, new LiDAR scan). */
    public List<Cell> updateCells(Collection<Cell> changed) {
        km += heuristic(last, start);
        last = start;
        for (Cell c : changed) {
            if (!map.inBounds(c)) continue;
            updateVertex(c);
            for (Cell pred : neighbors(c)) {
                updateVertex(pred);
            }
        }
        return plan();
    }

    public int getExpandedCount() {
        return expandedCount;
    }

    public Cell getGoal() {
        return goal;
    }

    private void computeShortestPath() {
        while (true) {
            Node top = peek();
            Key startKey = calculateKey(start);
            boolean startInconsistent = getRhs(start) != getG(start);
            if (top == null) break;
            if (top.key.compareTo(startKey) >= 0 && !startInconsistent) break;

            open.poll();
            openKeys.remove(top.cell);
            Cell u = top.cell;
            Key kNew = calculateKey(u);
            expandedCount++;

            if (top.key.compareTo(kNew) < 0) {
                insert(u, kNew);
            } else if (getG(u) > getRhs(u)) {
                g.put(u, getRhs(u));
                for (Cell pred : neighbors(u)) updateVertex(pred);
            } else {
                g.put(u, INF);
                updateVertex(u);
                for (Cell pred : neighbors(u)) updateVertex(pred);
            }
        }
    }

    private void updateVertex(Cell u) {
        if (!u.equals(goal)) {
            int best = INF;
            for (Cell s : neighbors(u)) {
                best = Math.min(best, add(cost(s), getG(s)));
            }
            rhs.put(u, best);
        }
        openKeys.remove(u);
        if (getG(u) != getRhs(u)) {
            insert(u, calculateKey(u));
        }
    }

    private List<Cell> extractPath() {
        if (getG(start) >= INF) return null;
        LinkedList<Cell> path = new LinkedList<>();
        Cell current = start;
        path.add(current);
        int maxSteps = map.getRows() * map.getCols();
        while (!current.equals(goal)) {
            Cell next = null;
            int best = INF;
            for (Cell s : neighbors(current)) {
                int v = add(cost(s), getG(s));
                if (v < best) {
                    best = v;
                    next = s;
                }
            }
            if (next == null || --maxSteps < 0) return null;
            current = next;
            path.add(current);
        }
        return path;
    }

    private Node peek() {
        while (!open.isEmpty()) {
            Node top = open.peek();
            if (top.key == openKeys.get(top.cell)) return top;
            open.poll();
        }
        return null;
    }

    private void insert(Cell c, Key k) {
        openKeys.put(c, k);
        open.add(new Node(c, k));
    }

    private Key calculateKey(Cell s) {
        int m = Math.min(getG(s), getRhs(s));
        return new Key(add(m, heuristic(start, s) + km), m);
    }

    /** Cost of stepping into {@code to}; same rule as A*: only the target cell matters. */
    private int cost(Cell to) {
        boolean blocked = (lidar != null)
                ? map.isBlockedForLidar(to, lidar)
                : map.isBlocked(to);
        return blocked ? INF : 1;
    }

    private List<Cell> neighbors(Cell c) {
        List<Cell> result = new ArrayList<>(4);
        for (int[] d : DIRS) {
            Cell n = new Cell(c.row + d[0], c.col + d[1]);
            if (map.inBounds(n)) result.add(n);
        }
        return result;
    }

    private int getG(Cell c) {
        return g.getOrDefault(c, INF);
    }

    private int getRhs(Cell c) {
        return rhs.getOrDefault(c, INF);
    }

    private static int add(int a, int b) {
        return (a >= INF || b >= INF) ? INF : Math.min(a + b, INF);
    }

    private int heuristic(Cell a, Cell b) {
        return Math.abs(a.row - b.row) + Math.abs(a.col - b.col);
    }

    private static class Key implements Comparable<Key> {
        final int k1;
        final int k2;
        Key(int k1, int k2) {
            this.k1 = k1;
            this.k2 = k2;
        }
        @Override
        public int compareTo(Key o) {
            if (k1 != o.k1) return Integer.compare(k1, o.k1);
            return Integer.compare(k2, o.k2);
        }
    }

    private static class Node implements Comparable<Node> {
        final Cell cell;
        final Key key;
        Node(Cell cell, Key key) {
            this.cell = cell;
            this.key = key;
        }
        @Override
        public int compareTo(Node o) {
            return key.compareTo(o.key);
        }
    }
}
