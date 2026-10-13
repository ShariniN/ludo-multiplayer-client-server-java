package ludo.event;

import ludo.enums.Color;
import ludo.enums.Direction;
import ludo.model.Piece;
import ludo.model.Player;

/**
 * Observer pattern: GameEngine fires these events and registered listeners
 * (console, GUI, test spies) decide how to present them.
 */
public interface GameEventListener {

    // Setup

    /** Called once for each player at game start to announce piece names. */
    void onPlayerPiecesAnnounced(Player player);

    /** Each player's initial dice roll to determine turn order. */
    void onInitialRoll(Color color, int value);

    /** Called when two or more players tie on the highest roll. */
    void onTieDetected(int maxRoll);

    /** Called once the starting player is resolved. */
    void onFirstPlayerDetermined(Color color);

    /** Called once the full clockwise play order is established. */
    void onRoundOrderSet(Player[] playOrder);

    // Turn

    /** The active player's dice roll value for this action. */
    void onDiceRolled(Color color, int value);

    /** A piece moves from one standard cell to another. */
    void onPieceMoved(Piece piece, String from, String to, int steps, Direction dir);

    /** A piece enters the board from base (always a 6). */
    void onMoveFromBase(Player player, Piece piece, Direction dir);

    /** A piece's intended move is obstructed by an opponent block. */
    void onPieceBlocked(Piece piece, String from, String to, Color blockColor, String blockName);

    /** The blocked piece has no alternative; the turn is forfeited. */
    void onBlockedIgnored(Color color);

    /** The blocked piece is placed on the cell immediately before the block. */
    void onBlockedPartialMove(Color color, String cellBefore);

    /** A piece lands on an opponent and sends it back to base. */
    void onCapture(Piece attacker, String location, Piece captured);

    /** Piece count on board/base changes; snapshot after a capture. */
    void onPieceCount(Player player);

    /** A blockade (two same-colour pieces) moves together. */
    void onBlockMove(Color color, int from, int to, int steps, Direction dir);

    /** A piece crosses the finish line into HOME. */
    void onPieceReachedHome(Piece piece);

    // Six / Penalty

    /** Third consecutive six: no blockade — roll is simply ignored. */
    void onConsecutiveSixPenalty(Color color);

    /** Third consecutive six with an active blockade: blockade is force-broken. */
    void onBlockadeForceBreak(Color color);

    /** The player has no piece that can legally move this turn. */
    void onNoValidMove(Color color);

    // Mystery Cell

    /** A mystery cell appears or relocates to the given board position. */
    void onMysteryCellSpawned(int position);

    /** A piece lands on the mystery cell and is being teleported. */
    void onMysteryCellLanding(Piece piece, String destination);

    /** Teleport or status effect; type is a key such as "Teleport-Alpha" or "Beta-Briefing". */
    void onMysteryEffect(Piece piece, String type);

    // Round End

    /** Called at the end of every complete round with the full game snapshot. */
    void onRoundEnd(Player[] players, int mcPosition, int mcRounds, boolean mcActive);

    // Game Over

    /** Called when a player gets all four pieces home. */
    void onPlayerWon(Color color);

    /** Called when the game hits MAX_ROUNDS without all players finishing. */
    void onGameOver(int maxRounds);
}
