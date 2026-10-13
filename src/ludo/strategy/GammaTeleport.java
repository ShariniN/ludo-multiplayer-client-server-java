package ludo.strategy;

import ludo.enums.Direction;
import ludo.event.GameEventListener;
import ludo.model.Board;
import ludo.model.Piece;
import ludo.util.BoardUtils;
import ludo.game.MysteryCell;

/**
 * Teleport to the Gamma special cell (cell 44).
 * Secondary effect: If CLOCKWISE, changes direction to COUNTERCLOCKWISE.
 * If already COUNTERCLOCKWISE, forwards piece to Beta and applies BRIEFING.
 */
public class GammaTeleport implements TeleportEffect {

    @Override
    public int getDestination(Piece piece) {
        return BoardUtils.GAMMA_CELL;
    }

    @Override
    public String getDestinationName() {
        return "Gamma";
    }

    @Override
    public void applyEffect(Piece piece, Board board, GameEventListener listener, MysteryCell mysteryCell) {
        listener.onMysteryEffect(piece, "Teleport-Gamma");

        if (piece.getDirection() == Direction.CLOCKWISE) {
            piece.setDirection(Direction.COUNTERCLOCKWISE);
            listener.onMysteryEffect(piece, "Gamma-Direction");
        } else {
            // Already counter-clockwise: forward to Beta and apply Beta's effect
            listener.onMysteryEffect(piece, "Gamma-ToBeta");
            board.removePieceFromStandard(piece);
            board.placePieceOnStandard(piece, BoardUtils.BETA_CELL);

            TeleportEffect betaEffect = new BetaTeleport();
            betaEffect.applyEffect(piece, board, listener, mysteryCell);
        }
    }
}
