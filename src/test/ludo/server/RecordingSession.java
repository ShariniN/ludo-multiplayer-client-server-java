package ludo.server;

import ludo.enums.Color;
import ludo.network.GameMessage;
import ludo.network.MessageType;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test double for ClientSession: records every message the server pushes so
 * tests can assert on the protocol without any sockets.
 */
public class RecordingSession implements ClientSession {

    private final String id;
    private String name;
    private Color color;
    private GameRoom room;
    public final List<GameMessage> received = new CopyOnWriteArrayList<>();

    public RecordingSession(String id) {
        this.id = id;
        this.name = id;
    }

    @Override public String getClientId() { return id; }
    @Override public void sendMessage(GameMessage message) { received.add(message); }
    @Override public Color getAssignedColor() { return color; }
    @Override public void setAssignedColor(Color color) { this.color = color; }
    @Override public String getPlayerName() { return name; }
    @Override public void setPlayerName(String playerName) { this.name = playerName; }
    @Override public GameRoom getRoom() { return room; }
    @Override public void setRoom(GameRoom room) { this.room = room; }

    public GameMessage last(MessageType type) {
        for (int i = received.size() - 1; i >= 0; i--) {
            if (received.get(i).getType() == type) return received.get(i);
        }
        return null;
    }

    public long count(MessageType type) {
        return received.stream().filter(m -> m.getType() == type).count();
    }

    public boolean has(MessageType type) {
        return last(type) != null;
    }
}
