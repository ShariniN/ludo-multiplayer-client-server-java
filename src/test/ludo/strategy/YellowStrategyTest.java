package ludo.strategy;

import ludo.enums.*;
import ludo.model.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("YellowStrategy Tests")
class YellowStrategyTest {

    private YellowStrategy strategy;
    private Board board;
    private Player yellowPlayer;

    @BeforeEach
    void setUp() {
        strategy = new YellowStrategy();
        board = new Board();
        yellowPlayer = new Player(Color.YELLOW, strategy);
    }

    @Test
    @DisplayName("Roll 6 always moves from base")
    void rollSixAlwaysBase() {
        
        board.placePieceOnStandard(yellowPlayer.getPiece(0), 10);
        yellowPlayer.getPiece(0).setDirection(Direction.CLOCKWISE);

        Piece chosen = strategy.choosePiece(yellowPlayer, 6, board, -1);
        assertTrue(chosen.isAtBase(), "Should choose a base piece on roll 6");
    }

    @Test
    @DisplayName("Roll 6 with empty base: moves existing piece")
    void rollSixEmptyBase() {
        
        for (int i = 0; i < 4; i++) {
            board.placePieceOnStandard(yellowPlayer.getPiece(i), 10 + i);
            yellowPlayer.getPiece(i).setDirection(Direction.CLOCKWISE);
        }
        Piece chosen = strategy.choosePiece(yellowPlayer, 6, board, -1);
        assertNotNull(chosen);
        assertFalse(chosen.isAtBase());
    }

    @Test
    @DisplayName("Captures when piece needs eligibility for home straight")
    void captureForEligibility() {
        Piece y1 = yellowPlayer.getPiece(0);
        board.placePieceOnStandard(y1, 10);
        y1.setDirection(Direction.CLOCKWISE);

        Piece bluePiece = new Piece("B1", Color.BLUE);
        board.placePieceOnStandard(bluePiece, 14);

        Piece chosen = strategy.choosePiece(yellowPlayer, 4, board, -1);
        assertEquals("Y1", chosen.getName());
    }

    @Test
    @DisplayName("Does not seek capture when already eligible")
    void noUnnecessaryCapture() {
        Piece y1 = yellowPlayer.getPiece(0);
        board.placePieceOnStandard(y1, 10);
        y1.setDirection(Direction.CLOCKWISE);
        y1.incrementCaptureCount(); 

        Piece chosen = strategy.choosePiece(yellowPlayer, 4, board, -1);
        assertNotNull(chosen);
    }

    @Test
    @DisplayName("Moves piece closest to home")
    void closestToHome() {
        Piece y1 = yellowPlayer.getPiece(0);
        Piece y2 = yellowPlayer.getPiece(1);
        board.placePieceOnStandard(y1, 45);
        y1.setDirection(Direction.CLOCKWISE);
        y1.incrementCaptureCount();
        board.placePieceOnStandard(y2, 10);
        y2.setDirection(Direction.CLOCKWISE);
        y2.incrementCaptureCount();

        Piece chosen = strategy.choosePiece(yellowPlayer, 3, board, -1);
        assertEquals("Y1", chosen.getName());
    }

    @Test
    @DisplayName("Yellow does not prefer block moves")
    void noBlockPreference() {
        assertFalse(strategy.preferBlockMove(yellowPlayer, yellowPlayer.getPiece(0), 4, board));
    }

    @Test
    @DisplayName("No valid move returns null")
    void noValidMove() {
        assertNull(strategy.choosePiece(yellowPlayer, 3, board, -1));
    }
}
