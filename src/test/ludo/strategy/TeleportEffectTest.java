package ludo.strategy;

import ludo.enums.Color;
import ludo.enums.Direction;
import ludo.enums.MysteryEffect;
import ludo.event.GameEventListener;
import ludo.model.Board;
import ludo.model.Piece;
import ludo.util.BoardUtils;
import ludo.game.MysteryCell;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Teleport Strategy Tests")
class TeleportEffectTest {

    private Board board;
    private GameEventListener listener;
    private MysteryCell mysteryCell;
    private Piece piece;

    @BeforeEach
    void setUp() {
        board      = mock(Board.class);
        listener   = mock(GameEventListener.class);
        mysteryCell = mock(MysteryCell.class);
        piece = new Piece("R1", Color.RED);
    }

    @Test
    @DisplayName("AlphaTeleport energizes or makes sick")
    void alphaTeleportEffect() {
        AlphaTeleport alpha = new AlphaTeleport();
        assertEquals(BoardUtils.ALPHA_CELL, alpha.getDestination(piece));
        assertEquals("Alpha", alpha.getDestinationName());

        when(mysteryCell.rollAlphaEnergized()).thenReturn(true);
        alpha.applyEffect(piece, board, listener, mysteryCell);
        assertEquals(MysteryEffect.ENERGIZED, piece.getMysteryEffect());

        when(mysteryCell.rollAlphaEnergized()).thenReturn(false);
        alpha.applyEffect(piece, board, listener, mysteryCell);
        assertEquals(MysteryEffect.SICK, piece.getMysteryEffect());
    }

    @Test
    @DisplayName("BetaTeleport applies BRIEFING")
    void betaTeleportEffect() {
        BetaTeleport beta = new BetaTeleport();
        assertEquals(BoardUtils.BETA_CELL, beta.getDestination(piece));
        assertEquals("Beta", beta.getDestinationName());

        beta.applyEffect(piece, board, listener, mysteryCell);
        assertEquals(MysteryEffect.BRIEFING, piece.getMysteryEffect());
    }

    @Test
    @DisplayName("GammaTeleport CW to CCW")
    void gammaTeleportEffectCW() {
        GammaTeleport gamma = new GammaTeleport();
        assertEquals(BoardUtils.GAMMA_CELL, gamma.getDestination(piece));
        assertEquals("Gamma", gamma.getDestinationName());

        piece.setDirection(Direction.CLOCKWISE);
        gamma.applyEffect(piece, board, listener, mysteryCell);

        assertEquals(Direction.COUNTERCLOCKWISE, piece.getDirection());
        verify(board, never()).placePieceOnStandard(any(), anyInt());
    }

    @Test
    @DisplayName("GammaTeleport CCW forwards to Beta")
    void gammaTeleportEffectCCW() {
        GammaTeleport gamma = new GammaTeleport();
        piece.setDirection(Direction.COUNTERCLOCKWISE);

        gamma.applyEffect(piece, board, listener, mysteryCell);

        verify(board).removePieceFromStandard(piece);
        verify(board).placePieceOnStandard(piece, BoardUtils.BETA_CELL);
        assertEquals(MysteryEffect.BRIEFING, piece.getMysteryEffect());
    }

    @Test
    @DisplayName("BaseTeleport resets piece")
    void baseTeleportEffect() {
        BaseTeleport base = new BaseTeleport();
        assertEquals(BoardUtils.NO_POSITION, base.getDestination(piece));
        assertEquals("Base", base.getDestinationName());

        piece.incrementCaptureCount();
        base.applyEffect(piece, board, listener, mysteryCell);
        assertEquals(0, piece.getCaptureCount());
    }

    @Test
    @DisplayName("XTeleport returns start position")
    void xTeleportEffect() {
        XTeleport x = new XTeleport();
        assertEquals(Color.RED.getStartPosition(), x.getDestination(piece));
        assertEquals("X", x.getDestinationName());
    }

    @Test
    @DisplayName("ApproachTeleport returns approach position")
    void approachTeleportEffect() {
        ApproachTeleport approach = new ApproachTeleport();
        assertEquals(Color.RED.getApproachPosition(), approach.getDestination(piece));
        assertEquals("Approach", approach.getDestinationName());
    }
}
