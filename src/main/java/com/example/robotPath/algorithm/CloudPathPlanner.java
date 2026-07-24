package com.example.robotPath.algorithm;

import com.example.robotPath.model.Cell;
import com.example.robotPath.model.GridMap;

import java.util.*;

public class CloudPathPlanner {

    private static final int[][] DIRS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    public List<Cell> planPathWithLidar(GridMap map, Cell start, Cell goal, LidarSensor lidar) {
        return findPath(map, start, goal, lidar);
    }

    public List<Cell> planPath(GridMap map, Cell start, Cell goal) {
        return findPath(map, start, goal, null);
    }

    private List<Cell> findPath(GridMap map, Cell start, Cell goal, LidarSensor lidar) {
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingInt(n -> n.f));
        Set<Cell> closed = new HashSet<>();
        Map<Cell, Cell> cameFrom = new HashMap<>();
        Map<Cell, Integer> gScore = new HashMap<>();
        gScore.put(start, 0);
        open.add(new Node(start, heuristic(start, goal)));

        while (!open.isEmpty()) {
            Node current = open.poll();
            if (closed.contains(current.cell)) continue;
            closed.add(current.cell);
            if (current.cell.equals(goal)) {
                return reconstructPath(cameFrom, goal);
            }
            for (int[] d : DIRS) {
            Cell neighbor = new Cell(current.cell.row + d[0], current.cell.col + d[1]);
                if (!map.inBounds(neighbor) || closed.contains(neighbor)) continue;

                boolean blocked = (lidar != null)
                        ? map.isBlockedForLidar(neighbor, lidar)
                        : map.isBlocked(neighbor);
                if (blocked) continue;

                int tentativeG = gScore.get(current.cell) + 1;
                if (tentativeG < gScore.getOrDefault(neighbor, Integer.MAX_VALUE)) {
                    cameFrom.put(neighbor, current.cell);
                    gScore.put(neighbor, tentativeG);
                    open.add(new Node(neighbor, tentativeG + heuristic(neighbor, goal)));
                }   }  }
        return null;    }

    private int heuristic(Cell a, Cell b) {
        return Math.abs(a.row - b.row) + Math.abs(a.col - b.col);
    }

    private List<Cell> reconstructPath(Map<Cell, Cell> cameFrom, Cell current) {
        LinkedList<Cell> path = new LinkedList<>();
        path.addFirst(current);
        while (cameFrom.containsKey(current)) {
            current = cameFrom.get(current);
            path.addFirst(current);
        }
        return path;
    }

    private static class Node {
        final Cell cell;
        final int f;
        Node(Cell cell, int f) {
            this.cell = cell;
            this.f = f;
        }
    }
}
