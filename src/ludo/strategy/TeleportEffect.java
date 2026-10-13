package ludo.strategy;

import ludo.event.GameEventListener;
import ludo.model.Board;
import ludo.model.Piece;
import ludo.game.MysteryCell;

/**
 * Strategy pattern: one implementation per Mystery Cell teleport outcome
 * (Alpha, Beta, Gamma, Base, X, Approach). Effects report via GameEventListener,
 * so they stay independent of how output is rendered.
 */
public interface TeleportEffect {

    /** Returns the board cell index of the teleport destination,
     *  or BoardUtils.NO_POSITION (-1) when the piece is sent to Base. */
    int getDestination(Piece piece);

    /** Returns the display name of the destination used in log messages. */
    String getDestinationName();

    /** Applies secondary effects after the piece has been moved to its destination. */
    void applyEffect(Piece piece, Board board, GameEventListener listener, MysteryCell mysteryCell);
}
