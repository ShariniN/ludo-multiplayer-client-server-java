package ludo.strategy;

import ludo.enums.*;
import ludo.model.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GreenStrategy Tests")
class GreenStrategyTest {

    private GreenStrategy strategy;
    private Board board;
    private Player greenPlayer;

    @BeforeEach
    void setUp() {
        strategy = new GreenStrategy();
        board = new Board();
        greenPlayer = new Player(Color.GREEN, strategy);
    }

    @Test
    @DisplayName("Roll 6: prefers block creation over emptying base")
    void rollSixPrefersBlockCreation() {

        Piece g1 = greenPlayer.getPiece(0);
        Piece g3 = greenPlayer.getPiece(2);
        board.placePieceOnStandard(g1, 10);
        g1.setDirection(Direction.CLOCKWISE);
        board.placePieceOnStandard(g3, 16);
        g3.setDirection(Direction.CLOCKWISE);

        Piece chosen = strategy.choosePiece(greenPlayer, 6, board, -1);
        
        assertEquals("G1", chosen.getName());
    }

    @Test
    @DisplayName("Roll 6: no block opportunity → empty base")
    void rollSixEmptiesBase() {
        Piece chosen = strategy.choosePiece(greenPlayer, 6, board, -1);
        assertNotNull(chosen);
        assertTrue(chosen.isAtBase());
    }

    @Test
    @DisplayName("Prioritises home straight movement")
    void homeStraightPriority() {
        Piece g1 = greenPlayer.getPiece(0);
        g1.setState(PieceState.HOME_STRAIGHT);
        g1.setPosition(2);
        g1.setDirection(Direction.CLOCKWISE);
        g1.incrementCaptureCount(); 

        Piece chosen = strategy.choosePiece(greenPlayer, 2, board, -1);
        assertEquals("G1", chosen.getName());
    }

    @Test
    @DisplayName("Moves non-block piece before breaking a block")
    void nonBlockBeforeBreak() {
        Piece g1 = greenPlayer.getPiece(0);
        Piece g2 = greenPlayer.getPiece(1);
        Piece g3 = greenPlayer.getPiece(2);

        board.placePieceOnStandard(g1, 10);
        g1.setDirection(Direction.CLOCKWISE);
        board.placePieceOnStandard(g2, 10);
        g2.setDirection(Direction.CLOCKWISE);

        board.placePieceOnStandard(g3, 20);
        g3.setDirection(Direction.CLOCKWISE);

        Piece chosen = strategy.choosePiece(greenPlayer, 3, board, -1);
        assertEquals("G3", chosen.getName());
    }

    @Test
    @DisplayName("preferBlockMove always returns true")
    void alwaysPrefersBlock() {
        assertTrue(strategy.preferBlockMove(greenPlayer, greenPlayer.getPiece(0), 4, board));
    }

    @Test
    @DisplayName("No valid move returns null")
    void noValidMove() {
        Piece chosen = strategy.choosePiece(greenPlayer, 3, board, -1);
        assertNull(chosen);
    }
}
