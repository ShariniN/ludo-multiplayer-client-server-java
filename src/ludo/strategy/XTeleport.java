package ludo.strategy;

import ludo.event.GameEventListener;
import ludo.model.Board;
import ludo.model.Piece;
import ludo.game.MysteryCell;

/**
 * Teleport to the piece's colour starting cell (X). No secondary effects.
 */
public class XTeleport implements TeleportEffect {

    @Override
    public int getDestination(Piece piece) {
        return piece.getColor().getStartPosition();
    }

    @Override
    public String getDestinationName() {
        return "X";
    }

    @Override
    public void applyEffect(Piece piece, Board board, GameEventListener listener, MysteryCell mysteryCell) {
        listener.onMysteryEffect(piece, "Teleport-X");
    }
}
