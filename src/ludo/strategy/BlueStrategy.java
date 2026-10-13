package ludo.strategy;

import ludo.model.Board;
import ludo.model.Piece;
import ludo.model.Player;
import ludo.util.BoardUtils;

/**
 * Blue AI (Strategy): moves pieces in round-robin order; the turn is wasted if the designated piece can't move.
 */
public class BlueStrategy implements PlayerStrategy {

    private int cyclicIndex = 0;

    public int getAndAdvanceCyclicIndex() {
        int current = cyclicIndex;
        cyclicIndex = (cyclicIndex + 1) % BoardUtils.PIECES_PER_PLAYER;
        return current;
    }

    public void resetCyclicIndex() {
        cyclicIndex = 0;
    }

    @Override
    public Piece choosePiece(Player player, int diceValue, Board board, int mysteryCellPosition) {
        int idx = getAndAdvanceCyclicIndex();
        Piece designatedPiece = player.getPiece(idx);

        if (!designatedPiece.isMovable()) {
            return null;
        }

        if (designatedPiece.isAtBase()) {
            return (diceValue == 6) ? designatedPiece : null;
        }

        if (designatedPiece.isInHomeStraight()) {
            int newPos = designatedPiece.getPosition()
                    + designatedPiece.getEffectiveMovement(diceValue);
            return (newPos <= BoardUtils.HOME_STRAIGHT_SIZE) ? designatedPiece : null;
        }

        if (designatedPiece.isActive()) {
            int effectiveMove = designatedPiece.getEffectiveMovement(diceValue);
            if (effectiveMove <= 0) return null;

            return designatedPiece;
        }

        return null;
    }

    @Override
    public boolean preferBlockMove(Player player, Piece piece, int diceValue, Board board) {
        return false;
    }
}
