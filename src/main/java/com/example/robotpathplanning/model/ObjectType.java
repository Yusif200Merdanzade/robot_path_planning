package com.example.robotpathplanning.model;

/**
 * Xəritədəki obyekt növləri. PACKAGE-dən başqa hamısı robot üçün maneədir
 * (bax: {@link GridMap#isBlocked}). Adlar JSON-da brauzerə olduğu kimi gedir.
 */
public enum ObjectType {
    SHELF,
    FORKLIFT,
    MOVING_ROBOT,
    PACKAGE
}
