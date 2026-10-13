package ludo.server;

import ludo.engine.MoveProcessor;
import ludo.engine.MysteryManager;
import ludo.engine.TurnManager;
import ludo.enums.Color;
import ludo.enums.Direction;
import ludo.event.GameEventListener;
import ludo.game.CoinToss;
import ludo.game.Dice;
import ludo.game.MysteryCell;
import ludo.model.Board;
import ludo.model.Piece;
import ludo.model.Player;
import ludo.network.GameMessage;
import ludo.network.MessageType;
import ludo.strategy.*;
import ludo.util.BoardUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe room running one 4-player LUDO-T match on the Assignment 1 domain.
 * Each action (join, roll, move) holds roomLock so QueueWorker threads can never
 * interleave a turn; every state change is broadcast as GAME_STATE_UPDATE.
 */
public class GameRoom {

    private final String roomId;
    private final GameRoomManager manager;
    private final ReentrantLock roomLock = new ReentrantLock();

    // Client slots mapped by Color (concurrent: broadcast() iterates without the room lock)
    private final Map<Color, ClientSession> clientMap = new ConcurrentHashMap<>();
    private final Map<Color, String> playerNameMap = new ConcurrentHashMap<>();

    // Domain models (Assignment 1, unchanged)
    private final Board board;
    private final Player[] players;
    private final Dice dice;
    private final CoinToss coinToss;
    private final MysteryCell mysteryCell;
    private final MysteryManager mysteryManager;
    private final MoveProcessor moveProcessor;
    private final TurnManager turnManager;

    // Room match state — guarded by roomLock
    private boolean isGameStarted = false;
    private boolean isGameOver = false;
    private int currentTurnOrdinal = 0; // 0=RED, 1=GREEN, 2=YELLOW, 3=BLUE
    private int currentDiceValue = 0;
    private boolean waitingForPieceSelection = false;
    private int roundNumber = 1;
    private List<String> currentMovablePieceNames = new ArrayList<>();
    private Color winnerColor = null;
    private long matchStartMillis = 0;
    private final String matchId = "M-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

    // Observer: domain events -> network broadcasts (the GUI listener of Assignment 2)
    private final GameEventListener roomEventListener = new GameEventListener() {
        @Override public void onInitialRoll(Color c, int v) {}
        @Override public void onTieDetected(int max) {}
        @Override public void onFirstPlayerDetermined(Color c) {}
        @Override public void onRoundOrderSet(Player[] o) {}
        @Override public void onPlayerPiecesAnnounced(Player p) {}
        @Override public void onDiceRolled(Color c, int v) {}
        @Override public void onPieceMoved(Piece p, String f, String t, int s, Direction d) {
            broadcast(new GameMessage(MessageType.PIECE_MOVED, "SERVER", roomId)
                    .put("piece", p.getName())
                    .put("color", p.getColor().name())
                    .put("from", f)
                    .put("to", t)
                    .put("steps", s)
                    .put("direction", d != null ? d.toDisplayString() : ""));
        }
        @Override public void onMoveFromBase(Player pl, Piece p, Direction d) {
            broadcast(new GameMessage(MessageType.PIECE_MOVED, "SERVER", roomId)
                    .put("piece", p.getName())
                    .put("color", pl.getColor().name())
                    .put("from", "BASE")
                    .put("to", String.valueOf(pl.getColor().getStartPosition()))
                    .put("direction", d != null ? d.toDisplayString() : "clockwise"));
        }
        @Override public void onPieceBlocked(Piece p, String f, String t, Color bc, String bn) {
            systemMessage(p.getName() + " blocked at " + t + " by " + bc.getDisplayName() + " blockade " + bn);
        }
        @Override public void onBlockedIgnored(Color c) {}
        @Override public void onBlockedPartialMove(Color c, String cb) {
            systemMessage(c.getDisplayName() + " moves up to the cell before the blockade (" + cb + ")");
        }
        @Override public void onCapture(Piece a, String loc, Piece cap) {
            broadcast(new GameMessage(MessageType.CAPTURE_EVENT, "SERVER", roomId)
                    .put("attacker", a.getName())
                    .put("captured", cap.getName())
                    .put("location", loc));
        }
        @Override public void onPieceCount(Player p) {}
        @Override public void onBlockMove(Color c, int f, int t, int s, Direction d) {
            systemMessage(c.getDisplayName() + " moved a blockade from " + f + " to " + t);
        }
        @Override public void onPieceReachedHome(Piece p) {
            systemMessage(p.getName() + " reached HOME!");
        }
        @Override public void onConsecutiveSixPenalty(Color c) {
            systemMessage(c.getDisplayName() + " rolled three sixes - turn forfeited");
        }
        @Override public void onBlockadeForceBreak(Color c) {
            systemMessage(c.getDisplayName() + " rolled three sixes - blockade force-broken");
        }
        @Override public void onNoValidMove(Color c) {
            systemMessage(c.getDisplayName() + " could not complete a move with that roll");
        }
        @Override public void onMysteryCellSpawned(int pos) {
            broadcast(new GameMessage(MessageType.MYSTERY_EVENT, "SERVER", roomId)
                    .put("subType", "SPAWNED")
                    .put("position", pos));
        }
        @Override public void onMysteryCellLanding(Piece p, String dest) {
            broadcast(new GameMessage(MessageType.MYSTERY_EVENT, "SERVER", roomId)
                    .put("subType", "LANDED")
                    .put("piece", p.getName())
                    .put("destination", dest));
        }
        @Override public void onMysteryEffect(Piece p, String type) {
            broadcast(new GameMessage(MessageType.MYSTERY_EVENT, "SERVER", roomId)
                    .put("subType", "EFFECT")
                    .put("piece", p.getName())
                    .put("effect", type));
        }
        @Override public void onRoundEnd(Player[] pl, int mcPos, int mcR, boolean mcA) {}
        @Override public void onPlayerWon(Color c) {}
        @Override public void onGameOver(int maxR) {}
    };

    public GameRoom(String roomId, long seed, GameRoomManager manager) {
        this.roomId = roomId;
        this.manager = manager;
        Random random = new Random(seed);
        this.board = new Board();
        this.dice = Dice.getInstance(random);
        this.coinToss = new CoinToss(random);
        this.mysteryCell = new MysteryCell(random);
        this.mysteryManager = new MysteryManager(mysteryCell);
        this.moveProcessor = new MoveProcessor(board, coinToss, mysteryManager, roomEventListener);
        this.turnManager = new TurnManager(dice, moveProcessor, mysteryManager, roomEventListener);

        this.players = new Player[BoardUtils.PLAYERS_COUNT];
        players[Color.RED.ordinal()]    = new Player(Color.RED,    new RedStrategy());
        players[Color.GREEN.ordinal()]  = new Player(Color.GREEN,  new GreenStrategy());
        players[Color.YELLOW.ordinal()] = new Player(Color.YELLOW, new YellowStrategy());
        players[Color.BLUE.ordinal()]   = new Player(Color.BLUE,   new BlueStrategy());
    }

    public String getRoomId() {
        return roomId;
    }

    // Player actions (each runs as one critical section under roomLock)

    public boolean joinPlayer(ClientSession client, Color preferredColor, String playerName) {
        roomLock.lock();
        try {
            Color assignedColor = preferredColor;
            if (assignedColor == null || clientMap.containsKey(assignedColor)) {
                assignedColor = null;
                for (Color c : Color.values()) {
                    if (!clientMap.containsKey(c)) {
                        assignedColor = c;
                        break;
                    }
                }
            }

            if (assignedColor == null) {
                client.sendMessage(new GameMessage(MessageType.ROOM_FULL, "SERVER", roomId));
                return false;
            }

            clientMap.put(assignedColor, client);
            playerNameMap.put(assignedColor, playerName != null ? playerName : assignedColor.getDisplayName() + " Player");
            client.setAssignedColor(assignedColor);
            client.setRoom(this);

            client.sendMessage(new GameMessage(MessageType.ROOM_JOINED, "SERVER", roomId)
                    .put("color", assignedColor.name())
                    .put("playerName", playerNameMap.get(assignedColor))
                    .put("clientId", client.getClientId()));

            broadcastRoomMembers();
            broadcastGameState();

            if (isGameStarted && !isGameOver) {
                // late (re)join into a running match: tell the newcomer whose turn it is
                promptActivePlayerTurn();
            } else if (clientMap.size() == BoardUtils.PLAYERS_COUNT && !isGameStarted) {
                startGame();
            }

            return true;
        } finally {
            roomLock.unlock();
        }
    }

    public void removePlayer(ClientSession client) {
        roomLock.lock();
        try {
            Color color = client.getAssignedColor();
            if (color != null && clientMap.get(color) == client) {
                clientMap.remove(color);
                playerNameMap.remove(color);
                systemMessage(color.getDisplayName() + " left the room.");
                broadcastRoomMembers();
                if (isGameStarted && !isGameOver && getActivePlayer().getColor() == color) {
                    advanceTurn(); // don't leave the match waiting on a vanished player
                }
            }
        } finally {
            roomLock.unlock();
        }
    }

    public void startGame() {
        roomLock.lock();
        try {
            this.isGameStarted = true;
            this.currentTurnOrdinal = 0;
            this.roundNumber = 1;
            this.waitingForPieceSelection = false;
            this.matchStartMillis = System.currentTimeMillis();

            broadcast(new GameMessage(MessageType.GAME_START, "SERVER", roomId)
                    .put("round", roundNumber)
                    .put("matchId", matchId)
                    .put("activeColor", getActivePlayer().getColor().name()));

            broadcastGameState();
            promptActivePlayerTurn();
        } finally {
            roomLock.unlock();
        }
    }

    public void handleRollDice(ClientSession client) {
        roomLock.lock();
        try {
            if (rejectIfNotInPlay(client)) return;
            Player activePlayer = getActivePlayer();
            if (client.getAssignedColor() != activePlayer.getColor()) {
                client.sendMessage(new GameMessage(MessageType.ERROR, "SERVER", roomId)
                        .put("message", "Not your turn!"));
                return;
            }
            if (waitingForPieceSelection) {
                client.sendMessage(new GameMessage(MessageType.ERROR, "SERVER", roomId)
                        .put("message", "Please select a piece to move."));
                return;
            }

            currentDiceValue = dice.roll();
            broadcast(new GameMessage(MessageType.DICE_ROLLED, "SERVER", roomId)
                    .put("color", activePlayer.getColor().name())
                    .put("diceValue", currentDiceValue));

            // Rule T-8: three consecutive sixes forfeits the turn
            if (turnManager.applyConsecutiveSixRule(activePlayer, board, currentDiceValue)) {
                broadcastGameState();
                advanceTurn();
                return;
            }

            // Rule T-14: briefing escape on consecutive threes
            turnManager.trackBriefingEscape(activePlayer, board, currentDiceValue);

            currentMovablePieceNames = calculateMovablePieces(activePlayer, currentDiceValue);

            if (currentMovablePieceNames.isEmpty()) {
                systemMessage(activePlayer.getColor().getDisplayName() + " has no valid moves with roll " + currentDiceValue);
                broadcastGameState();
                if (currentDiceValue == BoardUtils.DICE_FACES) {
                    promptActivePlayerTurn(); // a six always earns another roll
                } else {
                    advanceTurn();
                }
            } else {
                waitingForPieceSelection = true;
                String piecesCsv = String.join(",", currentMovablePieceNames);
                client.sendMessage(new GameMessage(MessageType.YOUR_TURN, "SERVER", roomId)
                        .put("state", "SELECT_PIECE")
                        .put("diceValue", currentDiceValue)
                        .put("validPieces", piecesCsv));
            }
        } finally {
            roomLock.unlock();
        }
    }

    /** Answers a turn action that arrives before the match starts or after it ends. Caller holds roomLock. */
    private boolean rejectIfNotInPlay(ClientSession client) {
        if (isGameStarted && !isGameOver) return false;
        String reason = isGameOver ? "The game is over." : "The game has not started yet - waiting for 4 players.";
        client.sendMessage(new GameMessage(MessageType.ERROR, "SERVER", roomId).put("message", reason));
        return true;
    }

    public void handleMovePiece(ClientSession client, String pieceName) {
        roomLock.lock();
        try {
            if (rejectIfNotInPlay(client)) return;
            Player activePlayer = getActivePlayer();
            if (client.getAssignedColor() != activePlayer.getColor()) {
                client.sendMessage(new GameMessage(MessageType.ERROR, "SERVER", roomId)
                        .put("message", "Not your turn!"));
                return;
            }
            if (!waitingForPieceSelection) {
                client.sendMessage(new GameMessage(MessageType.ERROR, "SERVER", roomId)
                        .put("message", "Roll the dice before moving a piece."));
                return;
            }

            Piece targetPiece = null;
            for (Piece p : activePlayer.getPieces()) {
                if (p.getName().equalsIgnoreCase(pieceName)) {
                    targetPiece = p;
                    break;
                }
            }

            if (targetPiece == null || !currentMovablePieceNames.contains(targetPiece.getName())) {
                client.sendMessage(new GameMessage(MessageType.ERROR, "SERVER", roomId)
                        .put("message", "Invalid piece selection!"));
                return;
            }

            boolean captureOccurred = moveProcessor.executeTurnAction(activePlayer, targetPiece, currentDiceValue);
            waitingForPieceSelection = false;
            currentMovablePieceNames = new ArrayList<>();

            // Rule T-10: the mystery cell countdown starts once the first piece is on the board
            if (!targetPiece.isAtBase()) {
                mysteryManager.notifyFirstPieceOnBoard(roundNumber);
            }

            if (activePlayer.hasAllPiecesHome()) {
                finishGame(activePlayer.getColor());
                return;
            }

            broadcastGameState();

            // Bonus turn on rolling 6 or capturing an opponent (Rule T-2)
            if (currentDiceValue == BoardUtils.DICE_FACES || captureOccurred) {
                promptActivePlayerTurn();
            } else {
                advanceTurn();
            }
        } finally {
            roomLock.unlock();
        }
    }

    public void handleChat(ClientSession client, String text) {
        String name = client.getAssignedColor() != null
                ? playerNameMap.getOrDefault(client.getAssignedColor(), client.getPlayerName())
                : client.getPlayerName();
        broadcast(new GameMessage(MessageType.CHAT_BROADCAST,
                client.getAssignedColor() != null ? client.getAssignedColor().name() : "GUEST", roomId)
                .put("sender", name)
                .put("message", text));
    }

    // Turn flow (callers hold roomLock)

    private void advanceTurn() {
        waitingForPieceSelection = false;
        currentDiceValue = 0;
        currentMovablePieceNames = new ArrayList<>();

        for (int attempts = 0; attempts < BoardUtils.PLAYERS_COUNT; attempts++) {
            currentTurnOrdinal = (currentTurnOrdinal + 1) % BoardUtils.PLAYERS_COUNT;
            if (currentTurnOrdinal == 0) {
                endRound();
                if (isGameOver) return;
            }
            Player next = getActivePlayer();
            if (clientMap.containsKey(next.getColor()) && !next.hasAllPiecesHome()) {
                break; // found a connected player who still has pieces to move
            }
        }
        getActivePlayer().resetConsecutiveSixCount();
        // Re-send the snapshot so clients highlight the new active player
        broadcastGameState();
        promptActivePlayerTurn();
    }

    private void endRound() {
        roundNumber++;
        mysteryManager.onRoundEnd(board, roundNumber, roomEventListener);
        for (Player p : players) {
            for (Piece piece : p.getPieces()) {
                piece.decrementEffectRounds();
            }
        }
        broadcast(new GameMessage(MessageType.ROUND_END, "SERVER", roomId)
                .put("round", roundNumber)
                .put("mysteryPos", mysteryManager.getPosition())
                .put("mysteryActive", mysteryManager.isActive()));
        broadcastGameState();

        final String snapshot = serializePieces();
        final int round = roundNumber;
        final String active = getActivePlayer().getColor().name();
        manager.persistAsync(() -> manager.getPersistence().saveGameState(roomId, roomId, snapshot, round, active));

        if (roundNumber > BoardUtils.MAX_ROUNDS) {
            finishGame(leaderByPiecesHome());
        }
    }

    private void finishGame(Color winner) {
        winnerColor = winner;
        isGameOver = true;
        String winnerName = playerNameMap.getOrDefault(winnerColor, winnerColor.getDisplayName());
        int durationSec = (int) ((System.currentTimeMillis() - matchStartMillis) / 1000);

        broadcast(new GameMessage(MessageType.GAME_OVER, "SERVER", roomId)
                .put("winnerColor", winnerColor.name())
                .put("winnerName", winnerName)
                .put("rounds", roundNumber)
                .put("durationSeconds", durationSec)
                .put("matchId", matchId));
        broadcastGameState();

        // Persist off the game thread: match result + per-player stats, in order
        final int rounds = roundNumber;
        final Map<Color, String> names = new HashMap<>(playerNameMap);
        final Map<Color, Integer> captures = new HashMap<>();
        for (Player p : players) {
            int c = 0;
            for (Piece piece : p.getPieces()) c += piece.getCaptureCount();
            captures.put(p.getColor(), c);
        }
        manager.persistAsync(() -> {
            manager.getPersistence().recordMatchResult(matchId, roomId, winner.name(), winnerName, rounds, durationSec);
            for (Map.Entry<Color, String> e : names.entrySet()) {
                boolean won = e.getKey() == winner;
                manager.getPersistence().recordPlayerStats(
                        "P-" + e.getValue().toUpperCase().replaceAll("[^A-Z0-9]", ""),
                        e.getValue(), 1, won ? 1 : 0, captures.getOrDefault(e.getKey(), 0));
            }
        });
    }

    private Color leaderByPiecesHome() {
        Player best = players[0];
        for (Player p : players) {
            if (p.getPiecesAtHome().size() > best.getPiecesAtHome().size()) best = p;
        }
        return best.getColor();
    }

    private void promptActivePlayerTurn() {
        Player active = getActivePlayer();
        for (Map.Entry<Color, ClientSession> entry : clientMap.entrySet()) {
            if (entry.getKey() == active.getColor()) {
                entry.getValue().sendMessage(new GameMessage(MessageType.YOUR_TURN, "SERVER", roomId)
                        .put("state", "ROLL_DICE")
                        .put("round", roundNumber)
                        .put("activeColor", active.getColor().name()));
            } else {
                entry.getValue().sendMessage(new GameMessage(MessageType.WAITING_TURN, "SERVER", roomId)
                        .put("activeColor", active.getColor().name())
                        .put("round", roundNumber));
            }
        }
    }

    /**
     * Pieces the GUI may highlight for this roll. Mirrors MoveProcessor: a base piece
     * needs a 6, a briefed/finished piece cannot move, a SICK piece needs an effective
     * step above zero, and a home-straight piece must not overshoot the home cell.
     */
    private List<String> calculateMovablePieces(Player player, int diceValue) {
        List<String> list = new ArrayList<>();
        for (Piece p : player.getPieces()) {
            if (p.isAtBase()) {
                if (diceValue == BoardUtils.DICE_FACES) list.add(p.getName());
                continue;
            }
            if (!p.isMovable()) continue;
            int steps = p.getEffectiveMovement(diceValue);
            if (steps <= 0) continue;
            if (p.isInHomeStraight() && p.getPosition() + steps > BoardUtils.HOME_STRAIGHT_SIZE) continue;
            list.add(p.getName());
        }
        return list;
    }

    // Broadcasting

    /** Serialize piece locations: R1:ACTIVE:12:NONE;R2:BASE:-1:NONE;... */
    public String serializePieces() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Player p : players) {
            for (Piece piece : p.getPieces()) {
                if (!first) sb.append(";");
                sb.append(piece.getName()).append(":")
                  .append(piece.getState().name()).append(":")
                  .append(piece.getPosition()).append(":")
                  .append(piece.getMysteryEffect().name());
                first = false;
            }
        }
        return sb.toString();
    }

    public GameMessage buildStateMessage() {
        GameMessage stateMsg = new GameMessage(MessageType.GAME_STATE_UPDATE, "SERVER", roomId);
        stateMsg.put("round", roundNumber);
        stateMsg.put("activeColor", getActivePlayer().getColor().name());
        stateMsg.put("mysteryPos", mysteryManager.getPosition());
        stateMsg.put("mysteryActive", mysteryManager.isActive());
        stateMsg.put("started", isGameStarted);
        stateMsg.put("gameOver", isGameOver);
        stateMsg.put("diceValue", currentDiceValue);
        stateMsg.put("players", clientMap.size());
        if (winnerColor != null) stateMsg.put("winner", winnerColor.name());
        stateMsg.put("pieces", serializePieces());
        return stateMsg;
    }

    public void broadcastGameState() {
        broadcast(buildStateMessage());
    }

    private void broadcastRoomMembers() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Color c : Color.values()) {
            if (!first) sb.append(";");
            String name = playerNameMap.getOrDefault(c, "Open Slot");
            sb.append(c.name()).append("=").append(name);
            first = false;
        }
        broadcast(new GameMessage(MessageType.CHAT_BROADCAST, "SERVER", roomId)
                .put("sender", "SYSTEM")
                .put("members", sb.toString())
                .put("message", "Lobby updated. Total players: " + clientMap.size() + "/" + BoardUtils.PLAYERS_COUNT));
    }

    private void systemMessage(String text) {
        broadcast(new GameMessage(MessageType.CHAT_BROADCAST, "SERVER", roomId)
                .put("sender", "SYSTEM")
                .put("message", text));
    }

    public void broadcast(GameMessage msg) {
        for (ClientSession client : clientMap.values()) {
            client.sendMessage(msg);
        }
    }

    // Read-only accessors (for servlets, stats and tests)

    public Player getActivePlayer() {
        return players[currentTurnOrdinal];
    }

    public boolean isGameStarted() { return isGameStarted; }
    public boolean isGameOver()    { return isGameOver; }
    public int getRoundNumber()    { return roundNumber; }
    public Color getWinnerColor()  { return winnerColor; }
    public int getPlayerCount()    { return clientMap.size(); }
    public String getMatchId()     { return matchId; }
    public boolean isWaitingForPieceSelection() { return waitingForPieceSelection; }
    public List<String> getCurrentMovablePieceNames() { return new ArrayList<>(currentMovablePieceNames); }
    public Map<Color, String> getPlayerNames() { return new LinkedHashMap<>(playerNameMap); }
}
