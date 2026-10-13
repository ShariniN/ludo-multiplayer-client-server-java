package ludo.strategy;

import ludo.model.Board;
import ludo.model.Piece;
import ludo.model.Player;

/** Strategy pattern: one AI heuristic per player colour. */
public interface PlayerStrategy {

    // Returns the piece to move, or null to skip the turn
    Piece choosePiece(Player player, int diceValue, Board board, int mysteryCellPosition);

    // True if this strategy moves blockades as a unit (Rule T-4)
    default boolean preferBlockMove(Player player, Piece piece, int diceValue, Board board) {
        return false;
    }
}
