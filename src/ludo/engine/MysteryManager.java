package ludo.engine;

import ludo.event.GameEventListener;
import ludo.game.MysteryCell;
import ludo.model.Board;
import ludo.model.Piece;
import ludo.model.Player;
import ludo.util.BoardUtils;

import java.util.HashMap;
import java.util.Map;

import static ludo.game.MysteryCell.*;
import ludo.strategy.*;

/**
 * Handles the mystery cell lifecycle and teleports (one TeleportEffect strategy per outcome).
 */
public class MysteryManager {

    private final MysteryCell mysteryCell;
    private final Map<Integer, TeleportEffect> teleportEffects;

    private int firstPieceOnBoardRound = BoardUtils.UNSET_ROUND;
    private boolean spawned = false;

    public MysteryManager(MysteryCell mysteryCell) {
        this.mysteryCell = mysteryCell;
        this.teleportEffects = new HashMap<>();

        teleportEffects.put(TELEPORT_ALPHA,    new AlphaTeleport());
        teleportEffects.put(TELEPORT_BETA,     new BetaTeleport());
        teleportEffects.put(TELEPORT_GAMMA,    new GammaTeleport());
        teleportEffects.put(TELEPORT_BASE,     new BaseTeleport());
        teleportEffects.put(TELEPORT_X,        new XTeleport());
        teleportEffects.put(TELEPORT_APPROACH, new ApproachTeleport());
    }

    /** Notify that a piece just entered the board; used to start the spawn countdown. */
    public void notifyFirstPieceOnBoard(int roundNumber) {
        if (firstPieceOnBoardRound == BoardUtils.UNSET_ROUND) {
            firstPieceOnBoardRound = roundNumber;
        }
    }

    /**
     * Called at the end of every round. Spawns or relocates the mystery cell
     * once enough rounds have passed since the first piece entered the board.
     */
    public void onRoundEnd(Board board, int roundNumber, GameEventListener listener) {
        if (!spawned) {
            if (firstPieceOnBoardRound != BoardUtils.UNSET_ROUND
                    && (roundNumber - firstPieceOnBoardRound) >= BoardUtils.MYSTERY_SPAWN_DELAY) {
                if (mysteryCell.spawn(board)) {
                    spawned = true;
                    listener.onMysteryCellSpawned(mysteryCell.getPosition());
                }
            }
        } else {
            int oldPos = mysteryCell.getPosition();
            mysteryCell.onRoundEnd(board);
            if (mysteryCell.getPosition() != oldPos) {
                listener.onMysteryCellSpawned(mysteryCell.getPosition());
            }
        }
    }

    /**
     * Checks whether the given cell is the active mystery cell.
     * Used by MoveProcessor after placing a piece to decide whether to trigger a teleport.
     */
    public boolean isActiveAt(int position) {
        return mysteryCell.isActive() && position == mysteryCell.getPosition();
    }

    /**
     * Handles a piece landing on the mystery cell: rolls the teleport outcome,
     * moves the piece to its destination, applies secondary effects, and fires
     * events. Capture after teleport is delegated back to the caller.
     *
     * @return destination cell index, or NO_POSITION if the piece went to base
     */
    public int handleLanding(Player player, Piece piece, Board board, GameEventListener listener,
                             CaptureCallback captureCallback) {
        int teleportOption = mysteryCell.rollTeleportOption();
        TeleportEffect effect = teleportEffects.get(teleportOption);
        if (effect == null) return BoardUtils.NO_POSITION;

        listener.onMysteryCellLanding(piece, effect.getDestinationName());
        board.removePieceFromStandard(piece);

        int dest = effect.getDestination(piece);
        if (dest != BoardUtils.NO_POSITION) {
            board.placePieceOnStandard(piece, dest);
        }

        effect.applyEffect(piece, board, listener, mysteryCell);

        if (dest != BoardUtils.NO_POSITION) {
            captureCallback.handle(player, piece, dest);
        }

        return dest;
    }

    /** Returns the current mystery cell position (-1 if inactive). */
    public int getPosition() {
        return mysteryCell.isActive() ? mysteryCell.getPosition() : BoardUtils.NO_POSITION;
    }

    /** Returns remaining rounds for the active mystery cell (0 if inactive). */
    public int getRoundsRemaining() {
        return mysteryCell.isActive() ? mysteryCell.getRoundsRemaining() : 0;
    }

    public boolean isActive() { return mysteryCell.isActive(); }

    /**
     * Functional callback so MysteryManager can delegate capture-after-teleport
     * back to MoveProcessor without creating a circular dependency.
     */
    @FunctionalInterface
    public interface CaptureCallback {
        void handle(Player player, Piece piece, int position);
    }
}
