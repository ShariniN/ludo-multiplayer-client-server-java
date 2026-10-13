package ludo.util;

import ludo.enums.Color;
import ludo.enums.Direction;
import ludo.enums.PieceState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BoardUtils Tests")
class BoardUtilsTest {

    @Test
    @DisplayName("PATH_SIZE is 52")
    void pathSizeIs52() {
        assertEquals(52, BoardUtils.PATH_SIZE);
    }

    @Test
    @DisplayName("HOME_STRAIGHT_SIZE is 5")
    void homeStraightSizeIs5() {
        assertEquals(5, BoardUtils.HOME_STRAIGHT_SIZE);
    }

    @Test
    @DisplayName("Alpha, Beta, Gamma positions are correct")
    void specialCellPositions() {
        assertEquals(7, BoardUtils.ALPHA_CELL);
        assertEquals(25, BoardUtils.BETA_CELL);
        assertEquals(44, BoardUtils.GAMMA_CELL);
    }

    @Test
    @DisplayName("CW distance 0→10 = 10")
    void cwDistanceStraight() {
        assertEquals(10, BoardUtils.clockwiseDistance(0, 10));
    }

    @Test
    @DisplayName("CW distance 50→2 = 4 (wraparound)")
    void cwDistanceWraparound() {
        assertEquals(4, BoardUtils.clockwiseDistance(50, 2));
    }

    @Test
    @DisplayName("CW distance same cell = 0")
    void cwDistanceSameCell() {
        assertEquals(0, BoardUtils.clockwiseDistance(25, 25));
    }

    @Test
    @DisplayName("CCW distance 10→0 = 10")
    void ccwDistanceStraight() {
        assertEquals(10, BoardUtils.counterclockwiseDistance(10, 0));
    }

    @Test
    @DisplayName("CCW distance 2→50 = 4 (wraparound)")
    void ccwDistanceWraparound() {
        assertEquals(4, BoardUtils.counterclockwiseDistance(2, 50));
    }

    @Test
    @DisplayName("CCW distance same cell = 0")
    void ccwDistanceSameCell() {
        assertEquals(0, BoardUtils.counterclockwiseDistance(25, 25));
    }

    @Test
    @DisplayName("moveClockwise(50, 5) = 3 (wraparound)")
    void moveClockwiseWraparound() {
        assertEquals(3, BoardUtils.moveClockwise(50, 5));
    }

    @Test
    @DisplayName("moveClockwise no wraparound")
    void moveClockwiseNoWrap() {
        assertEquals(15, BoardUtils.moveClockwise(10, 5));
    }

    @Test
    @DisplayName("moveCounterclockwise(2, 5) = 49 (wraparound)")
    void moveCcwWraparound() {
        assertEquals(49, BoardUtils.moveCounterclockwise(2, 5));
    }

    @Test
    @DisplayName("moveCounterclockwise no wraparound")
    void moveCcwNoWrap() {
        assertEquals(5, BoardUtils.moveCounterclockwise(10, 5));
    }

    @Test
    @DisplayName("CW distance to approach from start = 50 (full loop minus 2)")
    void distToApproachFromStart() {
        
        assertEquals(50, BoardUtils.distanceToApproach(26, Color.RED, Direction.CLOCKWISE));
    }

    @Test
    @DisplayName("CCW distance to approach from start = 2")
    void distToApproachCcwFromStart() {
        
        assertEquals(2, BoardUtils.distanceToApproach(26, Color.RED, Direction.COUNTERCLOCKWISE));
    }

    @Test
    @DisplayName("CW piece distance to home includes approach + 6")
    void distToHomeCw() {
        
        assertEquals(10, BoardUtils.distanceToHome(20, Color.RED, Direction.CLOCKWISE, false));
    }

    @Test
    @DisplayName("CCW piece NOT passed approach — must loop twice")
    void distToHomeCcwNotPassed() {
        int dist = BoardUtils.distanceToHome(20, Color.RED, Direction.COUNTERCLOCKWISE, false);

        assertTrue(dist > 52, "CCW piece not passed must travel > one full loop");
    }

    @Test
    @DisplayName("CCW piece already passed approach — just reach + 6")
    void distToHomeCcwPassed() {
        int dist = BoardUtils.distanceToHome(20, Color.RED, Direction.COUNTERCLOCKWISE, true);
        
        assertEquals(54, dist);
    }

    @Test
    @DisplayName("standardCellName returns position as string")
    void standardCellName() {
        assertEquals("0", BoardUtils.standardCellName(0));
        assertEquals("51", BoardUtils.standardCellName(51));
    }

    @Test
    @DisplayName("homeStraightCellName includes colour and position")
    void homeStraightCellName() {
        assertEquals("Red homepath[0]", BoardUtils.homeStraightCellName(Color.RED, 0));
        assertEquals("Blue homepath[4]", BoardUtils.homeStraightCellName(Color.BLUE, 4));
    }

    @Test
    @DisplayName("locationName returns correct name for each PieceState")
    void locationNameForEachState() {
        assertEquals("25", BoardUtils.locationName(PieceState.ACTIVE, 25, Color.RED));
        assertEquals("Red homepath[3]", BoardUtils.locationName(PieceState.HOME_STRAIGHT, 3, Color.RED));
        assertEquals("Base", BoardUtils.locationName(PieceState.BASE, -1, Color.RED));
        assertEquals("Home", BoardUtils.locationName(PieceState.HOME, -1, Color.RED));
    }
}
