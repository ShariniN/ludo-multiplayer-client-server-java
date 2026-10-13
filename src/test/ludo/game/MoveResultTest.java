package ludo.game;

import ludo.enums.PieceState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MoveResult Tests")
class MoveResultTest {

    @Test
    @DisplayName("Constructor stores state and position")
    void constructorStoresValues() {
        MoveResult result = new MoveResult(PieceState.ACTIVE, 25);
        assertEquals(PieceState.ACTIVE, result.getNewState());
        assertEquals(25, result.getNewPosition());
    }

    @Test
    @DisplayName("reachedHome returns true only for HOME state")
    void reachedHomeTrue() {
        MoveResult home = new MoveResult(PieceState.HOME, -1);
        assertTrue(home.reachedHome());
    }

    @Test
    @DisplayName("reachedHome returns false for non-HOME states")
    void reachedHomeFalse() {
        assertFalse(new MoveResult(PieceState.ACTIVE, 10).reachedHome());
        assertFalse(new MoveResult(PieceState.HOME_STRAIGHT, 3).reachedHome());
        assertFalse(new MoveResult(PieceState.BASE, -1).reachedHome());
    }

    @Test
    @DisplayName("enteredHomeStraight returns true for HOME_STRAIGHT")
    void enteredHomeStraightTrue() {
        MoveResult hs = new MoveResult(PieceState.HOME_STRAIGHT, 2);
        assertTrue(hs.enteredHomeStraight());
    }

    @Test
    @DisplayName("enteredHomeStraight returns false for other states")
    void enteredHomeStraightFalse() {
        assertFalse(new MoveResult(PieceState.ACTIVE, 10).enteredHomeStraight());
        assertFalse(new MoveResult(PieceState.HOME, -1).enteredHomeStraight());
    }

    @Test
    @DisplayName("toString contains state and position")
    void toStringFormat() {
        MoveResult r = new MoveResult(PieceState.ACTIVE, 42);
        String s = r.toString();
        assertTrue(s.contains("ACTIVE"));
        assertTrue(s.contains("42"));
    }
}
