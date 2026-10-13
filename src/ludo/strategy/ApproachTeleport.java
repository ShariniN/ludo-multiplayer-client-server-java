package ludo.strategy;

import ludo.event.GameEventListener;
import ludo.model.Board;
import ludo.model.Piece;
import ludo.game.MysteryCell;

/**
 * Teleport to the piece's colour approach cell. No secondary effects.
 */
public class ApproachTeleport implements TeleportEffect {

    @Override
    public int getDestination(Piece piece) {
        return piece.getColor().getApproachPosition();
    }

    @Override
    public String getDestinationName() {
        return "Approach";
    }

    @Override
    public void applyEffect(Piece piece, Board board, GameEventListener listener, MysteryCell mysteryCell) {
        listener.onMysteryEffect(piece, "Teleport-Approach");
    }
}
