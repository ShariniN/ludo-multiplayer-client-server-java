package ludo.enums;

/**
 * Defines the binary movement directions: CLOCKWISE or COUNTERCLOCKWISE.
 */
public enum Direction {
    CLOCKWISE,
    COUNTERCLOCKWISE;

    public String toDisplayString() {
        return this == CLOCKWISE ? "clockwise" : "counter-clockwise";
    }
}
