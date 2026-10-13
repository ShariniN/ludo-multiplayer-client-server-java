package ludo.strategy;

import ludo.enums.*;
import ludo.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RedStrategy.
 *
 * RedStrategy behaviour:
 *   Priority 1 — capture opponent if a landing would hit an unprotected opponent piece
 *   Priority 2 — move the piece furthest along the board
 *   Special   — roll 6 with 0 pieces on board → bring piece out of base
 *   Special   — roll 6 with pieces on board but no capture available → prefer moving
 *   Never     — prefers a block move
 */
@DisplayName("RedStrategy Tests")
class RedStrategyTest {

    private RedStrategy strategy;
    private Board board;
    private Player redPlayer;

    @BeforeEach
    void setUp() {
        strategy  = new RedStrategy();
        board     = new Board();
        redPlayer = new Player(Color.RED, strategy);
    }

    // ── Capture priority ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Captures opponent piece when a 4-step move lands on one")
    void capturesPriority() {
        board.placePieceOnStandard(redPlayer.getPiece(0), 10);
        redPlayer.getPiece(0).setDirection(Direction.CLOCKWISE);
        Piece bluePiece = new Piece("B1", Color.BLUE);
        board.placePieceOnStandard(bluePiece, 14);

        Piece chosen = strategy.choosePiece(redPlayer, 4, board, -1);
        assertEquals("R1", chosen.getName());
    }

    @Test
    @DisplayName("When two pieces can capture, selects the one hitting the opponent closer to their home")
    void captureClosestToOpponentHome() {
        // Two red pieces; two blue pieces, each reachable
        board.placePieceOnStandard(redPlayer.getPiece(0), 10);
        redPlayer.getPiece(0).setDirection(Direction.CLOCKWISE);
        board.placePieceOnStandard(redPlayer.getPiece(1), 20);
        redPlayer.getPiece(1).setDirection(Direction.CLOCKWISE);

        Piece b1 = new Piece("B1", Color.BLUE); b1.setDirection(Direction.CLOCKWISE);
        Piece b2 = new Piece("B2", Color.BLUE); b2.setDirection(Direction.CLOCKWISE);
        board.placePieceOnStandard(b1, 14);
        board.placePieceOnStandard(b2, 24);

        Piece chosen = strategy.choosePiece(redPlayer, 4, board, -1);
        assertEquals("R2", chosen.getName(),
                "Should prefer R2 because B2 at 24 is closer to its home than B1 at 14");
    }

    // ── Dice=6 logic ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Roll 6 with no pieces on board brings a piece from base")
    void rollSixEmptyBoard() {
        Piece chosen = strategy.choosePiece(redPlayer, 6, board, -1);
        assertNotNull(chosen);
        assertTrue(chosen.isAtBase());
    }

    @Test
    @DisplayName("Roll 6 with a piece already on board and no capture available: prefers moving that piece")
    void rollSixOneOnBoardNoCapture() {
        board.placePieceOnStandard(redPlayer.getPiece(0), 10);
        redPlayer.getPiece(0).setDirection(Direction.CLOCKWISE);

        Piece chosen = strategy.choosePiece(redPlayer, 6, board, -1);
        assertEquals("R1", chosen.getName());
    }

    // ── General movement ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Non-6 roll with a piece on board: moves that piece")
    void moveExistingPiece() {
        board.placePieceOnStandard(redPlayer.getPiece(0), 10);
        redPlayer.getPiece(0).setDirection(Direction.CLOCKWISE);

        Piece chosen = strategy.choosePiece(redPlayer, 3, board, -1);
        assertEquals("R1", chosen.getName());
    }

    @Test
    @DisplayName("SICK piece with dice=1 yields 0 effective movement — returns null")
    void sickPieceZeroMovement() {
        board.placePieceOnStandard(redPlayer.getPiece(0), 10);
        redPlayer.getPiece(0).setDirection(Direction.CLOCKWISE);
        redPlayer.getPiece(0).applyMysteryEffect(ludo.enums.MysteryEffect.SICK, 4);

        Piece chosen = strategy.choosePiece(redPlayer, 1, board, -1);
        // SICK(dice=1) = 0; no base piece because not rolling 6
        assertNull(chosen, "SICK piece with dice=1 cannot move");
    }

    // ── No valid move ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("No valid move returns null (all pieces at base, dice != 6)")
    void noValidMoveReturnsNull() {
        Piece chosen = strategy.choosePiece(redPlayer, 3, board, -1);
        assertNull(chosen);
    }

    // ── Block preference ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Red never prefers a block move over individual movement")
    void neverPrefersBlock() {
        assertFalse(strategy.preferBlockMove(redPlayer, redPlayer.getPiece(0), 4, board));
    }

    // ── Dice range ────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "Dice = {0}: no pieces available, all at base → null")
    @ValueSource(ints = {1, 2, 3, 4, 5})
    @DisplayName("Non-6 roll with all pieces at base always returns null")
    void nonSixAllAtBaseReturnsNull(int dice) {
        assertNull(strategy.choosePiece(redPlayer, dice, board, -1));
    }
}
