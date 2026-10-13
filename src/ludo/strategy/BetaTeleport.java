package ludo.strategy;

import ludo.enums.MysteryEffect;
import ludo.event.GameEventListener;
import ludo.model.Board;
import ludo.model.Piece;
import ludo.util.BoardUtils;
import ludo.game.MysteryCell;

/**
 * Teleport to the Beta special cell (cell 25).
 * Secondary effect: Applies BRIEFING for MYSTERY_DURATION rounds.
 */
public class BetaTeleport implements TeleportEffect {

    @Override
    public int getDestination(Piece piece) {
        return BoardUtils.BETA_CELL;
    }

    @Override
    public String getDestinationName() {
        return "Beta";
    }

    @Override
    public void applyEffect(Piece piece, Board board, GameEventListener listener, MysteryCell mysteryCell) {
        listener.onMysteryEffect(piece, "Teleport-Beta");
        piece.applyMysteryEffect(MysteryEffect.BRIEFING, BoardUtils.MYSTERY_DURATION);
        listener.onMysteryEffect(piece, "Beta-Briefing");
    }
}
