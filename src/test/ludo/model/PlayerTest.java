package ludo.model;

import ludo.enums.*;
import ludo.strategy.PlayerStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Player Tests")
class PlayerTest {

    private PlayerStrategy mockStrategy;
    private Player player;

    @BeforeEach
    void setUp() {
        mockStrategy = mock(PlayerStrategy.class);
        player = new Player(Color.RED, mockStrategy);
    }

    @Test
    @DisplayName("Player has correct colour")
    void playerColour() {
        assertEquals(Color.RED, player.getColor());
    }

    @Test
    @DisplayName("Player has correct strategy")
    void playerStrategy() {
        assertSame(mockStrategy, player.getStrategy());
    }

    @Test
    @DisplayName("Player has exactly 4 pieces")
    void fourPieces() {
        assertEquals(4, player.getPieces().length);
    }

    @Test
    @DisplayName("Pieces are named R1, R2, R3, R4")
    void pieceNames() {
        assertEquals("R1", player.getPiece(0).getName());
        assertEquals("R2", player.getPiece(1).getName());
        assertEquals("R3", player.getPiece(2).getName());
        assertEquals("R4", player.getPiece(3).getName());
    }

    @Test
    @DisplayName("All pieces start at BASE")
    void allPiecesStartAtBase() {
        for (Piece p : player.getPieces()) {
            assertTrue(p.isAtBase());
        }
    }

    @Test
    @DisplayName("All 4 pieces in base initially")
    void getPiecesInBase() {
        assertEquals(4, player.getPiecesInBase().size());
    }

    @Test
    @DisplayName("No pieces on board initially")
    void getPiecesOnBoardEmpty() {
        assertEquals(0, player.getPiecesOnBoard().size());
    }

    @Test
    @DisplayName("No pieces in home straight initially")
    void getPiecesInHomeStraightEmpty() {
        assertEquals(0, player.getPiecesInHomeStraight().size());
    }

    @Test
    @DisplayName("No pieces at home initially")
    void getPiecesAtHomeEmpty() {
        assertEquals(0, player.getPiecesAtHome().size());
    }

    @Test
    @DisplayName("getPiecesOnBoard returns only ACTIVE pieces")
    void getPiecesOnBoardAfterMove() {
        player.getPiece(0).setState(PieceState.ACTIVE);
        player.getPiece(0).setPosition(10);
        assertEquals(1, player.getPiecesOnBoard().size());
        assertEquals("R1", player.getPiecesOnBoard().get(0).getName());
    }

    @Test
    @DisplayName("getPiecesInHomeStraight returns only HOME_STRAIGHT pieces")
    void getPiecesInHomeStraightAfterEntry() {
        player.getPiece(1).setState(PieceState.HOME_STRAIGHT);
        assertEquals(1, player.getPiecesInHomeStraight().size());
    }

    @Test
    @DisplayName("getPiecesAtHome returns only HOME pieces")
    void getPiecesAtHomeAfterWin() {
        player.getPiece(2).setState(PieceState.HOME);
        assertEquals(1, player.getPiecesAtHome().size());
    }

    @Test
    @DisplayName("getMovablePieces excludes BASE and HOME pieces")
    void getMovablePiecesExcludes() {
        
        player.getPiece(0).setState(PieceState.ACTIVE);
        player.getPiece(1).setState(PieceState.HOME);
        player.getPiece(3).setState(PieceState.ACTIVE);
        player.getPiece(3).applyMysteryEffect(MysteryEffect.BRIEFING, 4);

        assertEquals(1, player.getMovablePieces().size());
        assertEquals("R1", player.getMovablePieces().get(0).getName());
    }

    @Test
    @DisplayName("getMovablePieces includes HOME_STRAIGHT pieces")
    void movablePiecesIncludeHomeStraight() {
        player.getPiece(0).setState(PieceState.HOME_STRAIGHT);
        assertEquals(1, player.getMovablePieces().size());
    }

    @Test
    @DisplayName("getPiecesInBriefing returns pieces with BRIEFING effect")
    void getPiecesInBriefing() {
        player.getPiece(0).setState(PieceState.ACTIVE);
        player.getPiece(0).applyMysteryEffect(MysteryEffect.BRIEFING, 4);
        assertEquals(1, player.getPiecesInBriefing().size());
    }

    @Test
    @DisplayName("getPiecesInBriefing returns empty when no briefing")
    void getPiecesInBriefingEmpty() {
        assertEquals(0, player.getPiecesInBriefing().size());
    }

    @Test
    @DisplayName("hasAllPiecesHome returns false when not all home")
    void notAllHome() {
        assertFalse(player.hasAllPiecesHome());
    }

    @Test
    @DisplayName("hasAllPiecesHome returns true when all 4 are HOME")
    void allHome() {
        for (Piece p : player.getPieces()) {
            p.setState(PieceState.HOME);
        }
        assertTrue(player.hasAllPiecesHome());
    }

    @Test
    @DisplayName("hasAllPiecesHome returns false with 3 HOME + 1 ACTIVE")
    void threeHome() {
        player.getPiece(0).setState(PieceState.HOME);
        player.getPiece(1).setState(PieceState.HOME);
        player.getPiece(2).setState(PieceState.HOME);
        player.getPiece(3).setState(PieceState.ACTIVE);
        assertFalse(player.hasAllPiecesHome());
    }

    @Test
    @DisplayName("getPiecesOnBoardCount counts ACTIVE + HOME_STRAIGHT")
    void onBoardCount() {
        player.getPiece(0).setState(PieceState.ACTIVE);
        player.getPiece(1).setState(PieceState.HOME_STRAIGHT);
        assertEquals(2, player.getPiecesOnBoardCount());
    }

    @Test
    @DisplayName("getPiecesInBaseCount counts only BASE pieces")
    void inBaseCount() {
        player.getPiece(0).setState(PieceState.ACTIVE);
        assertEquals(3, player.getPiecesInBaseCount());
    }

    @Test
    @DisplayName("Consecutive six count starts at 0")
    void consecutiveSixInitial() {
        assertEquals(0, player.getConsecutiveSixCount());
    }

    @Test
    @DisplayName("Consecutive six count increments and resets")
    void consecutiveSixIncrementReset() {
        player.incrementConsecutiveSixCount();
        assertEquals(1, player.getConsecutiveSixCount());
        player.incrementConsecutiveSixCount();
        assertEquals(2, player.getConsecutiveSixCount());
        player.resetConsecutiveSixCount();
        assertEquals(0, player.getConsecutiveSixCount());
    }

    @Test
    @DisplayName("toString returns colour display name + ' player'")
    void toStringFormat() {
        assertEquals("Red player", player.toString());
    }
}
