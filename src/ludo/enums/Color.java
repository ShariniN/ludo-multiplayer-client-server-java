package ludo.enums;

/**
 * Defines the four player colors with associated track coordinates.
 * Clockwise seating order: Red (0), Green (1), Yellow (2), Blue (3).
 */
public enum Color {
    RED("Red", 26, 24),
    GREEN("Green", 39, 37),
    YELLOW("Yellow", 0, 50),
    BLUE("Blue", 13, 11);

    private final String displayName;
    private final int startPosition;
    private final int approachPosition;

    Color(String displayName, int startPosition, int approachPosition) {
        this.displayName = displayName;
        this.startPosition = startPosition;
        this.approachPosition = approachPosition;
    }

    public String getDisplayName() { return displayName; }
    public int getStartPosition()  { return startPosition; }
    public int getApproachPosition() { return approachPosition; }

    // First letter used in piece names, e.g. "R1"
    public String getInitial() {
        return displayName.substring(0, 1);
    }
}
