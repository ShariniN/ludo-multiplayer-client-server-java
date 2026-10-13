package ludo.game;

import ludo.enums.PieceState;

/**
 * Data transfer object encapsulating the resulting state and position of a move.
 */
public class MoveResult {

    private final PieceState newState;
    private final int newPosition;

    public MoveResult(PieceState newState, int newPosition) {
        this.newState = newState;
        this.newPosition = newPosition;
    }

    public PieceState getNewState()  { return newState; }
    public int getNewPosition()      { return newPosition; }

    public boolean reachedHome() {
        return newState == PieceState.HOME;
    }

    public boolean enteredHomeStraight() {
        return newState == PieceState.HOME_STRAIGHT;
    }

    @Override
    public String toString() {
        return "MoveResult{state=" + newState + ", pos=" + newPosition + "}";
    }
}
