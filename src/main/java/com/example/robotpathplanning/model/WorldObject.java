package com.example.robotpathplanning.model;

/**
 * Xəritədə bir xanada duran obyekt. {@code count} yalnız qutular üçün
 * mənalıdır: həmin xanada neçə qutu qaldığını göstərir.
 */
public class WorldObject {
    public final Cell cell;
    public final ObjectType type;
    public int count;

    public WorldObject(Cell cell, ObjectType type) {
        this(cell, type, 1);
    }

    public WorldObject(Cell cell, ObjectType type, int count) {
        this.cell = cell;
        this.type = type;
        this.count = count;
    }
}
