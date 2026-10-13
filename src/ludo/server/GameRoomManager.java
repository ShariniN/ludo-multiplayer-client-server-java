package ludo.server;

import ludo.db.MatchRecord;
import ludo.db.PersistenceGateway;
import ludo.db.PlayerRecord;
import ludo.enums.Color;
import ludo.network.GameMessage;
import ludo.network.MessageType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Use-case coordinator: owns the active Game Rooms and dispatches queued client
 * messages to them. Called only from RequestQueueProcessor worker threads, so
 * every method here must be safe under concurrent invocation.
 */
public final class GameRoomManager {

    public static final String DEFAULT_ROOM_ID = "ROOM-1";

    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private final Map<String, ClientSession> activeClients = new ConcurrentHashMap<>();
    private final PersistenceGateway persistence;

    /** Single thread: DB writes apply in issue order and never stall a game turn. */
    private final ExecutorService persistenceExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "PersistenceWriter");
        t.setDaemon(true);
        return t;
    });

    public GameRoomManager(PersistenceGateway persistence) {
        this.persistence = persistence;
        getOrCreateDefaultRoom();
    }

    public GameRoom getOrCreateDefaultRoom() {
        return getOrCreateRoom(DEFAULT_ROOM_ID);
    }

    public GameRoom getOrCreateRoom(String roomId) {
        return rooms.computeIfAbsent(roomId, id -> new GameRoom(id, System.nanoTime(), this));
    }

    public GameRoom getRoom(String roomId) {
        return rooms.get(roomId);
    }

    public void registerClient(ClientSession client) {
        activeClients.put(client.getClientId(), client);
    }

    public void removeClient(ClientSession client) {
        activeClients.remove(client.getClientId());
    }

    public ClientSession getClient(String clientId) {
        return activeClients.get(clientId);
    }

    public int getActiveClientCount() {
        return activeClients.size();
    }

    /** Snapshot of registered session ids (safe to iterate while sessions come and go). */
    public java.util.List<String> getClientIds() {
        return new java.util.ArrayList<>(activeClients.keySet());
    }

    public PersistenceGateway getPersistence() {
        return persistence;
    }

    /** Hands a persistence action to the ordered background writer. */
    public void persistAsync(Runnable action) {
        try {
            persistenceExecutor.submit(() -> {
                try {
                    action.run();
                } catch (Exception e) {
                    System.err.println("[GameRoomManager] Persistence action failed: " + e.getMessage());
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // shutting down
        }
    }

    /** Dispatches one queued message; runs on a QueueWorker thread. */
    public void handleClientMessage(ClientSession client, GameMessage message) {
        if (message == null || message.getType() == null) return;

        MessageType type = message.getType();
        String roomId = message.getRoomId();

        switch (type) {
            case JOIN_ROOM:
                handleJoinRoom(client, message);
                break;

            case LEAVE_ROOM:
                if (client.getRoom() != null) {
                    client.getRoom().removePlayer(client);
                    client.setRoom(null);
                }
                break;

            case ROLL_DICE:
                if (client.getRoom() != null) {
                    client.getRoom().handleRollDice(client);
                }
                break;

            case MOVE_PIECE:
                String piece = message.get("piece");
                if (client.getRoom() != null && piece != null) {
                    client.getRoom().handleMovePiece(client, piece);
                }
                break;

            case CHAT_MESSAGE:
                String text = message.get("message");
                if (client.getRoom() != null && text != null) {
                    client.getRoom().handleChat(client, text);
                }
                break;

            case LEADERBOARD_REQUEST:
                handleLeaderboardRequest(client, roomId);
                break;

            case HEARTBEAT:
                GameMessage ack = new GameMessage(MessageType.HEARTBEAT, "SERVER", roomId)
                        .put("ack", System.currentTimeMillis())
                        .put("worker", Thread.currentThread().getName());
                if (message.get("sentNanos") != null) ack.put("sentNanos", message.get("sentNanos"));
                client.sendMessage(ack);
                break;

            default:
                break;
        }
    }

    /**
     * Reads the Database Tier on the persistence thread (not a QueueWorker), then
     * pushes the rows to the requesting client. Format: id,name,played,won,captures;...
     */
    private void handleLeaderboardRequest(ClientSession client, String roomId) {
        persistAsync(() -> {
            StringBuilder rows = new StringBuilder();
            for (PlayerRecord r : persistence.getLeaderboard()) {
                if (rows.length() > 0) rows.append(';');
                rows.append(r.id().replace(',', ' ').replace(';', ' ')).append(',')
                    .append(r.name().replace(',', ' ').replace(';', ' ')).append(',')
                    .append(r.gamesPlayed()).append(',').append(r.gamesWon()).append(',').append(r.totalCaptures());
            }
            StringBuilder history = new StringBuilder();
            for (MatchRecord m : persistence.getMatchHistory()) {
                if (history.length() > 0) history.append(';');
                history.append(m.matchId()).append(',').append(m.winnerColor()).append(',')
                       .append(m.winnerName().replace(',', ' ').replace(';', ' ')).append(',')
                       .append(m.totalRounds()).append(',').append(m.durationSeconds());
            }
            client.sendMessage(new GameMessage(MessageType.LEADERBOARD_DATA, "SERVER", roomId)
                    .put("source", persistence.describe())
                    .put("rows", rows.toString())
                    .put("history", history.toString()));
        });
    }

    private void handleJoinRoom(ClientSession client, GameMessage message) {
        String reqRoomId = message.get("roomId");
        if (reqRoomId == null || reqRoomId.trim().isEmpty()) {
            reqRoomId = DEFAULT_ROOM_ID;
        }

        String prefColorStr = message.get("preferredColor");
        Color prefColor = null;
        if (prefColorStr != null) {
            try {
                prefColor = Color.valueOf(prefColorStr.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // "AUTO" or garbage: let the room assign the next free colour
            }
        }

        String name = message.get("playerName");
        if (name != null && !name.trim().isEmpty()) {
            client.setPlayerName(name.trim());
        }

        // A full room spills into <id>-2, <id>-3, ... so overflow players are seated together
        GameRoom room = getOrCreateRoom(reqRoomId);
        for (int overflow = 2; !room.joinPlayer(client, prefColor, client.getPlayerName()); overflow++) {
            room = getOrCreateRoom(reqRoomId + "-" + overflow);
        }
    }

    public Map<String, GameRoom> getRooms() {
        return rooms;
    }

    public void shutdown() {
        persistenceExecutor.shutdown();
        try {
            persistenceExecutor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
