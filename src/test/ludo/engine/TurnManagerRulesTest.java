package ludo.engine;

import ludo.enums.Color;
import ludo.enums.MysteryEffect;
import ludo.event.GameEventListener;
import ludo.game.CoinToss;
import ludo.game.Dice;
import ludo.game.MysteryCell;
import ludo.model.Board;
import ludo.model.Piece;
import ludo.model.Player;
import ludo.strategy.RedStrategy;
import ludo.util.BoardUtils;
import org.junit.jupiter.api.*;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * The per-roll rules were extracted from TurnManager.playTurn() so the networked
 * GameRoom can apply them turn by turn; these tests pin their behaviour.
 */
@DisplayName("TurnManager shared rule tests")
class TurnManagerRulesTest {

    private Board board;
    private Player red;
    private TurnManager turnManager;
    private GameEventListener listener;
    private MoveProcessor moveProcessor;

    @BeforeEach
    void setUp() {
        Dice.resetInstance();
        Random random = new Random(7);
        board = new Board();
        red = new Player(Color.RED, new RedStrategy());
        listener = mock(GameEventListener.class);
        MysteryManager mysteryManager = new MysteryManager(new MysteryCell(random));
        moveProcessor = new MoveProcessor(board, new CoinToss(random), mysteryManager, listener);
        turnManager = new TurnManager(Dice.getInstance(random), moveProcessor, mysteryManager, listener);
    }

    @AfterEach
    void tearDown() {
        Dice.resetInstance();
    }

    @Test
    @DisplayName("a non-six resets the consecutive-six counter and never forfeits")
    void nonSixResets() {
        red.incrementConsecutiveSixCount();
        assertFalse(turnManager.applyConsecutiveSixRule(red, board, 3));
        assertEquals(0, red.getConsecutiveSixCount());
    }

    @Test
    @DisplayName("first and second six are allowed; the third forfeits the turn")
    void thirdSixForfeits() {
        assertFalse(turnManager.applyConsecutiveSixRule(red, board, 6));
        assertFalse(turnManager.applyConsecutiveSixRule(red, board, 6));
        assertEquals(2, red.getConsecutiveSixCount());
        assertTrue(turnManager.applyConsecutiveSixRule(red, board, 6));
        verify(listener).onConsecutiveSixPenalty(Color.RED);
        assertEquals(0, red.getConsecutiveSixCount(), "counter is cleared after the penalty");
    }

    @Test
    @DisplayName("third six with a blockade on the board force-breaks the blockade instead")
    void thirdSixBreaksBlockade() {
        Piece r1 = red.getPiece(0), r2 = red.getPiece(1);
        board.placePieceOnStandard(r1, 10);
        board.placePieceOnStandard(r2, 10);
        assertTrue(board.hasBlockOfColorAt(10, Color.RED));

        turnManager.applyConsecutiveSixRule(red, board, 6);
        turnManager.applyConsecutiveSixRule(red, board, 6);
        assertTrue(turnManager.applyConsecutiveSixRule(red, board, 6));
        verify(listener).onBlockadeForceBreak(Color.RED);
        verify(listener, never()).onConsecutiveSixPenalty(any());
        assertFalse(board.hasBlockOfColorAt(10, Color.RED), "blockade must be broken");
    }

    @Test
    @DisplayName("a briefed piece escapes to base after two consecutive threes")
    void briefingEscape() {
        Piece r1 = red.getPiece(0);
        board.placePieceOnStandard(r1, 20);
        r1.applyMysteryEffect(MysteryEffect.BRIEFING, BoardUtils.MYSTERY_DURATION);

        turnManager.trackBriefingEscape(red, board, 3);
        assertTrue(r1.isActive(), "one three is not enough");
        turnManager.trackBriefingEscape(red, board, 5);
        assertEquals(0, r1.getConsecutiveThreeCount(), "a non-three resets the count");
        turnManager.trackBriefingEscape(red, board, 3);
        turnManager.trackBriefingEscape(red, board, 3);
        assertTrue(r1.isAtBase(), "second consecutive three sends the piece home");
        assertTrue(board.getPiecesAtStandard(20).isEmpty());
        verify(listener).onMysteryEffect(r1, "Briefing-Escape");
    }

    @Test
    @DisplayName("pieces without BRIEFING are unaffected by threes")
    void briefingOnlyAffectsBriefedPieces() {
        Piece r1 = red.getPiece(0);
        board.placePieceOnStandard(r1, 20);
        turnManager.trackBriefingEscape(red, board, 3);
        turnManager.trackBriefingEscape(red, board, 3);
        assertTrue(r1.isActive());
    }
}
