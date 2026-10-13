package ludo.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Direction Enum Tests")
class DirectionTest {

    @Test
    @DisplayName("Clockwise display string is 'clockwise'")
    void clockwiseDisplayString() {
        assertEquals("clockwise", Direction.CLOCKWISE.toDisplayString());
    }

    @Test
    @DisplayName("Counter-clockwise display string is 'counter-clockwise'")
    void counterClockwiseDisplayString() {
        assertEquals("counter-clockwise", Direction.COUNTERCLOCKWISE.toDisplayString());
    }

    @Test
    @DisplayName("Exactly 2 directions exist")
    void exactlyTwoDirections() {
        assertEquals(2, Direction.values().length);
    }

    @Test
    @DisplayName("valueOf returns correct enum constant")
    void valueOfWorks() {
        assertEquals(Direction.CLOCKWISE, Direction.valueOf("CLOCKWISE"));
        assertEquals(Direction.COUNTERCLOCKWISE, Direction.valueOf("COUNTERCLOCKWISE"));
    }
}
