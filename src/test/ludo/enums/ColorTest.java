package ludo.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Color Enum Tests")
class ColorTest {

    @Test
    @DisplayName("Red display name is 'Red'")
    void redDisplayName() {
        assertEquals("Red", Color.RED.getDisplayName());
    }

    @Test
    @DisplayName("Green display name is 'Green'")
    void greenDisplayName() {
        assertEquals("Green", Color.GREEN.getDisplayName());
    }

    @Test
    @DisplayName("Yellow display name is 'Yellow'")
    void yellowDisplayName() {
        assertEquals("Yellow", Color.YELLOW.getDisplayName());
    }

    @Test
    @DisplayName("Blue display name is 'Blue'")
    void blueDisplayName() {
        assertEquals("Blue", Color.BLUE.getDisplayName());
    }

    @Test
    @DisplayName("Start positions match board layout")
    void startPositions() {
        assertEquals(26, Color.RED.getStartPosition());
        assertEquals(39, Color.GREEN.getStartPosition());
        assertEquals(0, Color.YELLOW.getStartPosition());
        assertEquals(13, Color.BLUE.getStartPosition());
    }

    @Test
    @DisplayName("Approach positions are 2 before start")
    void approachPositions() {
        assertEquals(24, Color.RED.getApproachPosition());
        assertEquals(37, Color.GREEN.getApproachPosition());
        assertEquals(50, Color.YELLOW.getApproachPosition());
        assertEquals(11, Color.BLUE.getApproachPosition());
    }

    @Test
    @DisplayName("Initials are first letter of display name")
    void initials() {
        assertEquals("R", Color.RED.getInitial());
        assertEquals("G", Color.GREEN.getInitial());
        assertEquals("Y", Color.YELLOW.getInitial());
        assertEquals("B", Color.BLUE.getInitial());
    }

    @Test
    @DisplayName("Enum ordinals match clockwise seating: R=0, G=1, Y=2, B=3")
    void seatOrderOrdinals() {
        Color[] values = Color.values();
        assertEquals(Color.RED, values[0]);
        assertEquals(Color.GREEN, values[1]);
        assertEquals(Color.YELLOW, values[2]);
        assertEquals(Color.BLUE, values[3]);
    }

    @Test
    @DisplayName("Exactly 4 colours exist")
    void exactlyFourColours() {
        assertEquals(4, Color.values().length);
    }

    @Test
    @DisplayName("Start positions are spaced 13 cells apart")
    void startPositionSpacing() {
        int spacing = (Color.GREEN.getStartPosition() - Color.RED.getStartPosition() + 52) % 52;
        assertEquals(13, spacing);
    }
}
