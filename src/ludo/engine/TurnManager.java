package ludo.engine;

import ludo.event.GameEventListener;
import ludo.game.Dice;
import ludo.model.Board;
import ludo.model.Piece;
import ludo.model.Player;
import ludo.util.BoardUtils;

import java.util.List;

/**
 * Manages turn execution: play order determination, dice rolling, and the
 * per-turn rule loop (consecutive sixes, briefing tracking, extra turns).
 */
public class TurnManager {

    private final Dice dice;
    private final MoveProcessor moveProcessor;
    private final MysteryManager mysteryManager;
    private final GameEventListener listener;

    public TurnManager(Dice dice, MoveProcessor moveProcessor,
                       MysteryManager mysteryManager, GameEventListener listener) {
        this.dice           = dice;
        this.moveProcessor  = moveProcessor;
        this.mysteryManager = mysteryManager;
        this.listener       = listener;
    }

    /**
     * Determines play order by having all players roll once; re-rolls on ties.
     *
     * @param players   all players indexed by Color ordinal
     * @return          the clockwise play order starting from the highest roller
     */
    public Player[] determineFirstPlayer(Player[] players) {
        int[] rolls = new int[BoardUtils.PLAYERS_COUNT];

        for (int i = 0; i < BoardUtils.PLAYERS_COUNT; i++) {
            rolls[i] = dice.roll();
            listener.onInitialRoll(players[i].getColor(), rolls[i]);
        }

        int winnerIndex = findHighestUniqueRoller(rolls);

        while (winnerIndex == -1) {
            int maxRoll = 0;
            for (int r : rolls) if (r > maxRoll) maxRoll = r;
            listener.onTieDetected(maxRoll);

            for (int i = 0; i < BoardUtils.PLAYERS_COUNT; i++) {
                if (rolls[i] == maxRoll) {
                    rolls[i] = dice.roll();
                    listener.onInitialRoll(players[i].getColor(), rolls[i]);
                }
            }
            winnerIndex = findHighestUniqueRoller(rolls);
        }

        Player[] playOrder = new Player[BoardUtils.PLAYERS_COUNT];
        for (int i = 0; i < BoardUtils.PLAYERS_COUNT; i++) {
            playOrder[i] = players[(winnerIndex + i) % BoardUtils.PLAYERS_COUNT];
        }

        listener.onFirstPlayerDetermined(players[winnerIndex].getColor());
        listener.onRoundOrderSet(playOrder);

        return playOrder;
    }

    /**
     * Executes a full turn for the given player, including the consecutive-six
     * bonus-turn loop, briefing escape tracking, and extra-turn-on-capture.
     *
     * @param player        the player whose turn it is
     * @param board         the game board (for strategy AI access)
     * @param roundNumber   current round (for mystery cell spawn notification)
     */
    public void playTurn(Player player, Board board, int roundNumber) {
        player.resetConsecutiveSixCount();
        boolean continueTurn = true;

        while (continueTurn) {
            continueTurn = false;

            int diceValue = dice.roll();
            listener.onDiceRolled(player.getColor(), diceValue);

            if (applyConsecutiveSixRule(player, board, diceValue)) {
                break;
            }

            trackBriefingEscape(player, board, diceValue);

            int mysteryCellPos = mysteryManager.isActive()
                    ? mysteryManager.getPosition() : BoardUtils.NO_POSITION;

            Piece chosenPiece = player.getStrategy().choosePiece(
                    player, diceValue, board, mysteryCellPos);

            boolean captured = moveProcessor.executeTurnAction(player, chosenPiece, diceValue);

            // Notify mystery manager if this was the first piece placed this game
            if (chosenPiece != null && chosenPiece.isAtBase() == false) {
                mysteryManager.notifyFirstPieceOnBoard(roundNumber);
            }

            if (diceValue == 6 && player.getConsecutiveSixCount() < BoardUtils.MAX_CONSECUTIVE_SIXES) {
                continueTurn = true;
            }
            if (captured) continueTurn = true;
            if (player.hasAllPiecesHome()) break;
        }
    }

    // Per-roll rules (shared with the networked GameRoom)

    /**
     * Rule T-8: tracks consecutive sixes. On the third six the player forfeits the
     * turn — a blockade of theirs is force-broken if one exists, otherwise the turn
     * is simply lost.
     *
     * @return true if the turn is forfeited and no move should be made
     */
    public boolean applyConsecutiveSixRule(Player player, Board board, int diceValue) {
        if (diceValue != BoardUtils.DICE_FACES) {
            player.resetConsecutiveSixCount();
            return false;
        }
        player.incrementConsecutiveSixCount();
        if (player.getConsecutiveSixCount() < BoardUtils.MAX_CONSECUTIVE_SIXES) {
            return false;
        }
        List<Integer> blockPositions = board.getPlayerBlockPositions(player.getColor());
        if (!blockPositions.isEmpty()) {
            listener.onBlockadeForceBreak(player.getColor());
            moveProcessor.forceBreakBlockade(player, blockPositions.get(0));
        } else {
            listener.onConsecutiveSixPenalty(player.getColor());
        }
        player.resetConsecutiveSixCount();
        return true;
    }

    /**
     * Rule T-14: if a piece under BRIEFING rolls 3 twice consecutively, the piece
     * is returned to base as an escape penalty.
     */
    public void trackBriefingEscape(Player player, Board board, int diceValue) {
        for (Piece piece : player.getPiecesInBriefing()) {
            if (diceValue == 3) {
                piece.incrementConsecutiveThreeCount();
                if (piece.getConsecutiveThreeCount() >= BoardUtils.BRIEFING_ESCAPE_THREES) {
                    listener.onMysteryEffect(piece, "Briefing-Escape");
                    board.removePieceFromStandard(piece);
                    piece.resetToBase();
                }
            } else {
                piece.resetConsecutiveThreeCount();
            }
        }
    }

    // Helpers

    private int findHighestUniqueRoller(int[] rolls) {
        int maxRoll  = -1;
        int maxIndex = -1;
        boolean tied = false;

        for (int i = 0; i < rolls.length; i++) {
            if (rolls[i] > maxRoll) {
                maxRoll  = rolls[i];
                maxIndex = i;
                tied     = false;
            } else if (rolls[i] == maxRoll) {
                tied = true;
            }
        }
        return tied ? -1 : maxIndex;
    }
}
