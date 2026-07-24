package com.example.robotPath.model;

public class WorldObject {
    public final Cell cell;
    public final ObjectType type;
    public int count;

    public WorldObject(Cell cell, ObjectType type) {
        this.cell = cell;
        this.type = type;
        this.count = 1;
    }

    public WorldObject(Cell cell, ObjectType type, int count) {
        this.cell = cell;
        this.type = type;
        this.count = count;
    }
}
