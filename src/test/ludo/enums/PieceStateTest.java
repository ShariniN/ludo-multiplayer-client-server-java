package ludo.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PieceState Enum Tests")
class PieceStateTest {

    @Test
    @DisplayName("All four lifecycle states exist")
    void allStatesExist() {
        PieceState[] values = PieceState.values();
        assertEquals(4, values.length);
    }

    @Test
    @DisplayName("States are BASE, ACTIVE, HOME_STRAIGHT, HOME")
    void stateNames() {
        assertNotNull(PieceState.valueOf("BASE"));
        assertNotNull(PieceState.valueOf("ACTIVE"));
        assertNotNull(PieceState.valueOf("HOME_STRAIGHT"));
        assertNotNull(PieceState.valueOf("HOME"));
    }

    @Test
    @DisplayName("Ordinal order matches lifecycle progression")
    void ordinalOrder() {
        assertTrue(PieceState.BASE.ordinal() < PieceState.ACTIVE.ordinal());
        assertTrue(PieceState.ACTIVE.ordinal() < PieceState.HOME_STRAIGHT.ordinal());
        assertTrue(PieceState.HOME_STRAIGHT.ordinal() < PieceState.HOME.ordinal());
    }
}
