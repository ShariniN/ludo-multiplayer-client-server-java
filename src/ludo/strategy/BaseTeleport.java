package ludo.strategy;

import ludo.event.GameEventListener;
import ludo.model.Board;
import ludo.model.Piece;
import ludo.util.BoardUtils;
import ludo.game.MysteryCell;

/**
 * Teleport to Base. Resets the piece fully via resetToBase().
 */
public class BaseTeleport implements TeleportEffect {

    @Override
    public int getDestination(Piece piece) {
        return BoardUtils.NO_POSITION;
    }

    @Override
    public String getDestinationName() {
        return "Base";
    }

    @Override
    public void applyEffect(Piece piece, Board board, GameEventListener listener, MysteryCell mysteryCell) {
        listener.onMysteryEffect(piece, "Teleport-Base");
        piece.resetToBase();
    }
}
