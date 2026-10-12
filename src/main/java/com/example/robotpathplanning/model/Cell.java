package com.example.robotpathplanning.model;

import java.util.Objects;

/**
 * Anbar xəritəsində bir xana (sətir, sütun).
 *
 * Dəyişməzdir (immutable) və equals/hashCode-u düzgün yazılıb, ona görə
 * HashMap/HashSet-də açar kimi istifadə olunur. Sahələr public olduğu üçün
 * Jackson onu avtomatik {"row":..,"col":..} JSON-a çevirir.
 */
public class Cell {
    public final int row;
    public final int col;

    public Cell(int row, int col) {
        this.row = row;
        this.col = col;
    }

    /** Manhattan məsafəsi: robot yalnız 4 istiqamətdə getdiyi üçün real addım sayına bərabərdir. */
    public int distanceTo(Cell o) {
        return Math.abs(row - o.row) + Math.abs(col - o.col);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Cell)) return false;
        Cell c = (Cell) o;
        return row == c.row && col == c.col;
    }

    @Override
    public int hashCode() {
        return Objects.hash(row, col);
    }

    @Override
    public String toString() {
        return "(" + row + "," + col + ")";
    }
}
