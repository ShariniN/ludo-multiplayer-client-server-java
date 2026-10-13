package ludo.game;

import ludo.model.Board;
import ludo.util.BoardUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Manages the single Mystery Cell's spawning cycle and effects.
 */
public class MysteryCell {

    private int position;
    private int roundsRemaining;
    private int previousPosition;
    private boolean active;
    private final Random random;

    public static final int TELEPORT_ALPHA     = 0;
    public static final int TELEPORT_BETA      = 1;
    public static final int TELEPORT_GAMMA     = 2;
    public static final int TELEPORT_BASE      = 3;
    public static final int TELEPORT_X         = 4;
    public static final int TELEPORT_APPROACH  = 5;

    public MysteryCell(Random random) {
        this.random = random;
        this.position = BoardUtils.NO_POSITION;
        this.roundsRemaining = 0;
        this.previousPosition = BoardUtils.NO_POSITION;
        this.active = false;
    }

    public boolean isActive()           { return active; }
    public int getPosition()            { return position; }
    public int getRoundsRemaining()     { return roundsRemaining; }

    // Rule T-10: spawn on a random empty standard cell
    public boolean spawn(Board board) {
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < BoardUtils.PATH_SIZE; i++) {

            if (i != previousPosition && board.getPiecesAtStandard(i).isEmpty()) {
                candidates.add(i);
            }
        }

        if (candidates.isEmpty()) {

            active = false;
            return false;
        }

        position = candidates.get(random.nextInt(candidates.size()));
        roundsRemaining = BoardUtils.MYSTERY_DURATION;
        active = true;
        return true;
    }

    // Relocates once the countdown reaches zero
    public void onRoundEnd(Board board) {
        if (!active) return;

        roundsRemaining--;
        if (roundsRemaining <= 0) {
            previousPosition = position;
            spawn(board);
        }
    }

    // Rule T-11: pick a teleport outcome in [0, TELEPORT_OPTIONS - 1]
    public int rollTeleportOption() {
        return random.nextInt(BoardUtils.TELEPORT_OPTIONS);
    }

    public boolean rollAlphaEnergized() {
        return random.nextBoolean();
    }
}
