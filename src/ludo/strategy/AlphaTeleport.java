package ludo.strategy;

import ludo.enums.MysteryEffect;
import ludo.event.GameEventListener;
import ludo.model.Board;
import ludo.model.Piece;
import ludo.util.BoardUtils;
import ludo.game.MysteryCell;

/**
 * Teleport to the Alpha special cell (cell 7).
 * Secondary effect: 50/50 roll — ENERGIZED or SICK for MYSTERY_DURATION rounds.
 */
public class AlphaTeleport implements TeleportEffect {

    @Override
    public int getDestination(Piece piece) {
        return BoardUtils.ALPHA_CELL;
    }

    @Override
    public String getDestinationName() {
        return "Alpha";
    }

    @Override
    public void applyEffect(Piece piece, Board board, GameEventListener listener, MysteryCell mysteryCell) {
        listener.onMysteryEffect(piece, "Teleport-Alpha");
        if (mysteryCell.rollAlphaEnergized()) {
            piece.applyMysteryEffect(MysteryEffect.ENERGIZED, BoardUtils.MYSTERY_DURATION);
            listener.onMysteryEffect(piece, "Alpha-Energized");
        } else {
            piece.applyMysteryEffect(MysteryEffect.SICK, BoardUtils.MYSTERY_DURATION);
            listener.onMysteryEffect(piece, "Alpha-Sick");
        }
    }
}
