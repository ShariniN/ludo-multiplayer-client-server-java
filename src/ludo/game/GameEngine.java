package ludo.game;

import ludo.engine.MoveProcessor;
import ludo.engine.MysteryManager;
import ludo.engine.TurnManager;
import ludo.enums.Color;
import ludo.event.ConsoleGameListener;
import ludo.event.GameEventListener;
import ludo.model.Board;
import ludo.model.Player;
import ludo.strategy.*;
import ludo.util.BoardUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Runs the game loop and wires TurnManager, MoveProcessor and MysteryManager together.
 * No listener is attached by default; add one (e.g. ConsoleGameListener) to see output.
 */
public class GameEngine {

    // Domain components

    private final Board board;
    private final Player[] players;
    private final TurnManager turnManager;
    private final MoveProcessor moveProcessor;
    private final MysteryManager mysteryManager;

    // Observer list

    private final List<GameEventListener> listeners = new ArrayList<>();

    /** Composite listener that broadcasts each event to all registered observers. */
    private final GameEventListener broadcast = new GameEventListener() {

        @Override public void onPlayerPiecesAnnounced(Player p)               { listeners.forEach(l -> l.onPlayerPiecesAnnounced(p)); }
        @Override public void onInitialRoll(Color c, int v)                   { listeners.forEach(l -> l.onInitialRoll(c, v)); }
        @Override public void onTieDetected(int max)                          { listeners.forEach(l -> l.onTieDetected(max)); }
        @Override public void onFirstPlayerDetermined(Color c)                { listeners.forEach(l -> l.onFirstPlayerDetermined(c)); }
        @Override public void onRoundOrderSet(Player[] o)                     { listeners.forEach(l -> l.onRoundOrderSet(o)); }
        @Override public void onDiceRolled(Color c, int v)                    { listeners.forEach(l -> l.onDiceRolled(c, v)); }
        @Override public void onPieceMoved(ludo.model.Piece p, String f, String t, int s, ludo.enums.Direction d) { listeners.forEach(l -> l.onPieceMoved(p, f, t, s, d)); }
        @Override public void onMoveFromBase(Player pl, ludo.model.Piece p, ludo.enums.Direction d)               { listeners.forEach(l -> l.onMoveFromBase(pl, p, d)); }
        @Override public void onPieceBlocked(ludo.model.Piece p, String f, String t, Color bc, String bn)        { listeners.forEach(l -> l.onPieceBlocked(p, f, t, bc, bn)); }
        @Override public void onBlockedIgnored(Color c)                       { listeners.forEach(l -> l.onBlockedIgnored(c)); }
        @Override public void onBlockedPartialMove(Color c, String cb)        { listeners.forEach(l -> l.onBlockedPartialMove(c, cb)); }
        @Override public void onCapture(ludo.model.Piece a, String loc, ludo.model.Piece cap) { listeners.forEach(l -> l.onCapture(a, loc, cap)); }
        @Override public void onPieceCount(Player p)                          { listeners.forEach(l -> l.onPieceCount(p)); }
        @Override public void onBlockMove(Color c, int f, int t, int s, ludo.enums.Direction d) { listeners.forEach(l -> l.onBlockMove(c, f, t, s, d)); }
        @Override public void onPieceReachedHome(ludo.model.Piece p)          { listeners.forEach(l -> l.onPieceReachedHome(p)); }
        @Override public void onConsecutiveSixPenalty(Color c)                { listeners.forEach(l -> l.onConsecutiveSixPenalty(c)); }
        @Override public void onBlockadeForceBreak(Color c)                   { listeners.forEach(l -> l.onBlockadeForceBreak(c)); }
        @Override public void onNoValidMove(Color c)                          { listeners.forEach(l -> l.onNoValidMove(c)); }
        @Override public void onMysteryCellSpawned(int pos)                   { listeners.forEach(l -> l.onMysteryCellSpawned(pos)); }
        @Override public void onMysteryCellLanding(ludo.model.Piece p, String dest) { listeners.forEach(l -> l.onMysteryCellLanding(p, dest)); }
        @Override public void onMysteryEffect(ludo.model.Piece p, String type)      { listeners.forEach(l -> l.onMysteryEffect(p, type)); }
        @Override public void onRoundEnd(Player[] pl, int mcPos, int mcR, boolean mcA) { listeners.forEach(l -> l.onRoundEnd(pl, mcPos, mcR, mcA)); }
        @Override public void onPlayerWon(Color c)                            { listeners.forEach(l -> l.onPlayerWon(c)); }
        @Override public void onGameOver(int maxR)                            { listeners.forEach(l -> l.onGameOver(maxR)); }
    };

    // Construction

    public GameEngine(long seed) {
        Random random = new Random(seed);

        this.board = new Board();
        Dice dice  = Dice.getInstance(random);

        this.players = new Player[BoardUtils.PLAYERS_COUNT];
        players[Color.RED.ordinal()]    = new Player(Color.RED,    new RedStrategy());
        players[Color.GREEN.ordinal()]  = new Player(Color.GREEN,  new GreenStrategy());
        players[Color.YELLOW.ordinal()] = new Player(Color.YELLOW, new YellowStrategy());
        players[Color.BLUE.ordinal()]   = new Player(Color.BLUE,   new BlueStrategy());

        MysteryCell   mysteryCell    = new MysteryCell(random);
        CoinToss      coinToss       = new CoinToss(random);

        this.mysteryManager = new MysteryManager(mysteryCell);
        this.moveProcessor  = new MoveProcessor(board, coinToss, mysteryManager, broadcast);
        this.turnManager    = new TurnManager(dice, moveProcessor, mysteryManager, broadcast);
    }

    // Observer registration

    /** Adds a listener to receive game events. Must be called before runGame(). */
    public void addListener(GameEventListener listener) {
        listeners.add(listener);
    }

    // Game loop

    public void runGame() {
        for (Player player : players) broadcast.onPlayerPiecesAnnounced(player);

        // Determine starting player
        Player[] playOrder = turnManager.determineFirstPlayer(players);

        List<Player> finishOrder = new ArrayList<>(BoardUtils.PLAYERS_COUNT);
        boolean gameOver = false;
        int roundNumber  = 0;

        while (!gameOver) {
            roundNumber++;

            for (Player player : playOrder) {
                if (player.hasAllPiecesHome()) continue;

                turnManager.playTurn(player, board, roundNumber);

                if (player.hasAllPiecesHome() && !finishOrder.contains(player)) {
                    finishOrder.add(player);
                    broadcast.onPlayerWon(player.getColor());
                    if (finishOrder.size() == BoardUtils.PLAYERS_COUNT) {
                        gameOver = true;
                        break;
                    }
                }
            }

            if (!gameOver) {
                mysteryManager.onRoundEnd(board, roundNumber, broadcast);
                decrementAllEffects();

                broadcast.onRoundEnd(playOrder,
                        mysteryManager.getPosition(),
                        mysteryManager.getRoundsRemaining(),
                        mysteryManager.isActive());

                if (roundNumber >= BoardUtils.MAX_ROUNDS) {
                    broadcast.onGameOver(BoardUtils.MAX_ROUNDS);
                    gameOver = true;
                }
            }
        }
    }

    // Helpers

    private void decrementAllEffects() {
        for (Player player : players) {
            for (ludo.model.Piece piece : player.getPieces()) {
                piece.decrementEffectRounds();
            }
        }
    }
}
