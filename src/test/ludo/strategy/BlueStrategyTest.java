package ludo.strategy;

import ludo.enums.*;
import ludo.model.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BlueStrategy.
 *
 * BlueStrategy behaviour:
 *   - Cyclic selection: picks pieces B1→B2→B3→B4→B1...
 *   - Skips pieces that cannot legally move (HOME, BRIEFING, overshoot)
 *   - Dice=6 required to leave base; non-6 with all at base → null
 *   - Never prefers a block move
 *
 * NOTE: The cyclic state is tested via observable choosePiece() behaviour,
 * not via direct access to internal index fields.
 */
@DisplayName("BlueStrategy Tests")
class BlueStrategyTest {

    private BlueStrategy strategy;
    private Board board;
    private Player bluePlayer;

    @BeforeEach
    void setUp() {
        strategy   = new BlueStrategy();
        board      = new Board();
        bluePlayer = new Player(Color.BLUE, strategy);
    }

    // ── Cyclic turn order ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Selects B1→B2→B3→B4 in cyclic order, wrapping back to B1")
    void cyclicOrderWraps() {
        // All pieces in base; dice=6 to allow base exit
        assertEquals("B1", strategy.choosePiece(bluePlayer, 6, board, -1).getName());
        assertEquals("B2", strategy.choosePiece(bluePlayer, 6, board, -1).getName());
        assertEquals("B3", strategy.choosePiece(bluePlayer, 6, board, -1).getName());
        assertEquals("B4", strategy.choosePiece(bluePlayer, 6, board, -1).getName());
        assertEquals("B1", strategy.choosePiece(bluePlayer, 6, board, -1).getName(),
                "Should wrap back to B1 on the 5th call");
    }

    // ── Base exit guard ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Base piece cannot move without dice=6")
    void basePieceNonSix() {
        assertNull(strategy.choosePiece(bluePlayer, 3, board, -1),
                "Cannot exit base without rolling 6");
    }

    @Test
    @DisplayName("Base piece with dice=6 is returned")
    void basePieceSix() {
        Piece chosen = strategy.choosePiece(bluePlayer, 6, board, -1);
        assertNotNull(chosen);
        assertTrue(chosen.isAtBase());
    }

    // ── Active piece movement ──────────────────────────────────────────────────

    @Test
    @DisplayName("Active piece is returned for a valid non-6 roll")
    void activePieceMoves() {
        Piece b1 = bluePlayer.getPiece(0);
        board.placePieceOnStandard(b1, 13);
        b1.setDirection(Direction.CLOCKWISE);

        Piece chosen = strategy.choosePiece(bluePlayer, 4, board, -1);
        assertEquals("B1", chosen.getName());
    }

    // ── Home straight movement ────────────────────────────────────────────────

    @Test
    @DisplayName("HOME_STRAIGHT piece with valid movement is returned")
    void homeStraightValidMove() {
        Piece b1 = bluePlayer.getPiece(0);
        b1.setState(PieceState.HOME_STRAIGHT);
        b1.setPosition(2);
        b1.setDirection(Direction.CLOCKWISE);

        Piece chosen = strategy.choosePiece(bluePlayer, 3, board, -1);
        assertEquals("B1", chosen.getName());
    }

    @Test
    @DisplayName("HOME_STRAIGHT piece that would overshoot returns null")
    void homeStraightOvershoot() {
        Piece b1 = bluePlayer.getPiece(0);
        b1.setState(PieceState.HOME_STRAIGHT);
        b1.setPosition(4);
        b1.setDirection(Direction.CLOCKWISE);

        assertNull(strategy.choosePiece(bluePlayer, 3, board, -1),
                "Piece at index 4 + 3 > HOME_STRAIGHT_SIZE; should not move");
    }

    // ── Immobile piece skipping ────────────────────────────────────────────────

    @Test
    @DisplayName("HOME piece is skipped — returns null when only piece")
    void homePieceSkipped() {
        bluePlayer.getPiece(0).setState(PieceState.HOME);
        assertNull(strategy.choosePiece(bluePlayer, 4, board, -1));
    }

    @Test
    @DisplayName("BRIEFING piece is skipped — returns null when only piece on board")
    void briefingPieceSkipped() {
        Piece b1 = bluePlayer.getPiece(0);
        b1.setState(PieceState.ACTIVE);
        b1.setPosition(13);
        b1.applyMysteryEffect(MysteryEffect.BRIEFING, 4);

        assertNull(strategy.choosePiece(bluePlayer, 4, board, -1));
    }

    // ── Block preference ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Blue never prefers a block move")
    void noBlockPreference() {
        assertFalse(strategy.preferBlockMove(bluePlayer, bluePlayer.getPiece(0), 4, board));
    }
}
