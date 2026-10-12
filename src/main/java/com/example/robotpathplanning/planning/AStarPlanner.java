package com.example.robotpathplanning.planning;

import com.example.robotpathplanning.model.Cell;
import com.example.robotpathplanning.model.GridMap;
import com.example.robotpathplanning.sensor.LidarSensor;

import java.util.*;

/**
 * A* yol planlaşdırıcısı (qlobal planlaşdırıcı, hər dəfə sıfırdan hesablayır).
 *
 * Grid üzərində 4 istiqamətdə (yuxarı, aşağı, sol, sağ) ən qısa yolu tapır.
 * Hər addımın qiyməti 1-dir, evristika Manhattan məsafəsidir. Bu evristika
 * real məsafəni heç vaxt artıq qiymətləndirmir, ona görə A* həmişə ən qısa yolu tapır.
 *
 * Planlaşdırıcının yaddaşı yoxdur: hər çağırışda axtarış sıfırdan başlayır.
 * Artımlı (incremental) versiya üçün bax: {@link DStarLitePlanner}.
 */
public class AStarPlanner {

    private static final int[][] DIRS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    private int lastExpanded;   // son axtarışda açılan (closed-a düşən) xana sayı — D* Lite ilə müqayisə üçün

    /** Tam məlum xəritədə yol. Yol yoxdursa null qaytarır. */
    public List<Cell> planPath(GridMap map, Cell start, Cell goal) {
        return planPath(map, start, goal, null);
    }

    /** lidar != null olduqda robot yalnız gördüyü maneələri nəzərə alır. */
    public List<Cell> planPath(GridMap map, Cell start, Cell goal, LidarSensor lidar) {
        lastExpanded = 0;
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingInt(n -> n.f));
        Set<Cell> closed = new HashSet<>();
        Map<Cell, Cell> cameFrom = new HashMap<>();
        Map<Cell, Integer> g = new HashMap<>();
        g.put(start, 0);
        open.add(new Node(start, start.distanceTo(goal)));

        while (!open.isEmpty()) {
            Cell current = open.poll().cell;
            if (!closed.add(current)) continue;          // artıq işlənib (köhnə növbə elementi)
            lastExpanded = closed.size();
            if (current.equals(goal)) return buildPath(cameFrom, goal);

            for (int[] d : DIRS) {
                Cell next = new Cell(current.row + d[0], current.col + d[1]);
                if (!map.inBounds(next) || closed.contains(next)) continue;
                boolean blocked = lidar != null ? map.isBlockedForLidar(next, lidar) : map.isBlocked(next);
                if (blocked) continue;

                int newG = g.get(current) + 1;
                if (newG < g.getOrDefault(next, Integer.MAX_VALUE)) {
                    cameFrom.put(next, current);
                    g.put(next, newG);
                    open.add(new Node(next, newG + next.distanceTo(goal)));
                }
            }
        }
        return null;
    }

    public int getLastExpanded() {
        return lastExpanded;
    }

    /** Məqsəddən başlayıb cameFrom zənciri ilə geriyə gedərək yolu qurur. */
    private static List<Cell> buildPath(Map<Cell, Cell> cameFrom, Cell current) {
        LinkedList<Cell> path = new LinkedList<>();
        for (Cell c = current; c != null; c = cameFrom.get(c)) {
            path.addFirst(c);
        }
        return path;
    }

    /** Prioritet növbəsinin elementi: xana və onun f = g + h qiyməti. */
    private static class Node {
        final Cell cell;
        final int f;

        Node(Cell cell, int f) {
            this.cell = cell;
            this.f = f;
        }
    }
}
